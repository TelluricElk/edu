package com.eduappml.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Проигрывание PCM16, который приходит с релея кусками.
 *
 * Отдельный класс, а не пара строк внутри клиента, ровно из-за barge-in:
 * когда пользователь перебивает ассистента, нужно одним движением выбросить
 * всю накопленную очередь и то, что уже лежит в буфере AudioTrack. Держать
 * эту логику рядом с сетью — верный способ потом её сломать.
 */
internal class VoicePlayer {

    private val queue = LinkedBlockingQueue<ByteArray>()
    private val running = AtomicBoolean(false)
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    /** true, пока в очереди или в буфере ещё есть звук. */
    @Volatile
    var isSpeaking: Boolean = false
        private set

    fun start() {
        if (running.getAndSet(true)) return

        val minBuf = AudioTrack.getMinBufferSize(
            VoiceConfig.PLAYBACK_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // VOICE_COMMUNICATION, а не MEDIA: так система включает
                    // эхоподавление тракта и звук не улетает обратно в микрофон.
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(VoiceConfig.PLAYBACK_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }

        thread = Thread({
            while (running.get()) {
                val chunk = try {
                    queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    null
                }
                if (chunk == null) {
                    isSpeaking = false
                    continue
                }
                isSpeaking = true
                try {
                    var off = 0
                    while (off < chunk.size && running.get()) {
                        val written = track?.write(chunk, off, chunk.size - off) ?: break
                        if (written <= 0) break
                        off += written
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "не удалось проиграть кусок: ${e.message}")
                }
            }
        }, "voice-player").also { it.start() }
    }

    fun enqueue(pcm: ByteArray) {
        if (running.get() && pcm.isNotEmpty()) queue.offer(pcm)
    }

    /** Мгновенно оборвать текущую реплику (пользователь перебил). */
    fun flush() {
        queue.clear()
        isSpeaking = false
        try {
            track?.pause()
            track?.flush()
            track?.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "flush: ${e.message}")
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        queue.clear()
        isSpeaking = false
        thread?.interrupt()
        thread = null
        try {
            track?.pause()
            track?.flush()
            track?.stop()
        } catch (_: IllegalStateException) {
        }
        track?.release()
        track = null
    }

    private companion object {
        const val TAG = "VoicePlayer"
    }
}

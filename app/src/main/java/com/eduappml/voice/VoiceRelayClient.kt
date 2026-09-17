package com.eduappml.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.eduappml.network.ApiClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Клиент голосового режима: WebSocket до нашего релея + микрофон + динамик.
 *
 * Сознательно не знает ничего про Яндекс: протокол с релеем свой и простой
 * (audio / text / speech_started / ...). Если Яндекс переименует событие,
 * правка будет только на сервере, APK пересобирать не придётся.
 *
 * Микрофон здесь ОДНОРАЗОВЫЙ, как в голосовом вводе поиска Google: нажали —
 * слушает одну фразу, фраза закончилась — микрофон закрылся сам. Так сделано
 * не ради простоты, а потому что иначе при включённой озвучке звук из динамика
 * возвращается в микрофон, детектор речи считает его репликой пользователя, и
 * модель начинает отвечать сама себе.
 *
 * Сессия при этом остаётся открытой: следующее нажатие микрофона или
 * напечатанный вопрос продолжают тот же разговор.
 */
class VoiceRelayClient(
    private val accessToken: String,
    private val speakAnswers: Boolean,
    private val listener: Listener
) {

    interface Listener {
        fun onState(state: VoiceState)

        /** Открыт ли микрофон прямо сейчас. */
        fun onMicActive(active: Boolean)

        /** Реплика принята, ждём ответа модели. */
        fun onAwaitingAnswer(active: Boolean)

        /** Распознанная реплика пользователя. */
        fun onUserText(text: String)

        /** Кусок ответа ассистента текстом. */
        fun onAssistantDelta(text: String)

        /** Ответ ассистента дописан целиком. */
        fun onAssistantDone(text: String)

        fun onError(message: String)
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        // Голосовая сессия живёт минутами: нулевой readTimeout обязателен,
        // иначе OkHttp разорвёт соединение в первой же паузе разговора.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val player = VoicePlayer()
    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var recordThread: Thread? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    private val assistantBuffer = StringBuilder()

    /** Идёт незакрытый ответ: пузырь в ленте уже создан и растёт. */
    private var answerOpen = false

    /** Озвучивает ли модель ответ. Значение приходит от релея в `ready`. */
    private var audioEnabled = false

    /** Нужно ли открыть микрофон, как только сессия будет готова. */
    private var micOnReady = true

    /** Текст, отправленный до того, как релей прислал `ready`. */
    private val pendingTexts = ConcurrentLinkedQueue<String>()

    @Volatile
    private var sessionReady = false

    @Volatile
    private var micOpen = false

    /** Была ли в текущей записи хоть какая-то речь. */
    private var speechHeard = false

    private val noSpeechTimeout = Runnable {
        if (micOpen && !speechHeard) {
            Log.i(TAG, "тишина — закрываю микрофон")
            closeMic(awaitingAnswer = false)
            listener.onError("Не расслышал. Нажмите микрофон и скажите ещё раз")
            listener.onState(VoiceState.IDLE_SESSION)
        }
    }

    private val maxPhraseTimeout = Runnable {
        if (micOpen) {
            Log.i(TAG, "реплика затянулась — закрываю микрофон")
            send(JSONObject().put("type", "commit"))
            closeMic(awaitingAnswer = true)
        }
    }

    // ------------------------------------------------------------ жизненный цикл

    fun start(withMic: Boolean = true) {
        if (running.getAndSet(true)) return
        assistantBuffer.setLength(0)
        audioEnabled = false
        sessionReady = false
        micOnReady = withMic
        listener.onState(VoiceState.CONNECTING)

        val token = accessToken.removePrefix("Bearer ").trim()
        // Режим озвучки просим сразу в адресе, чтобы первый же ответ пришёл
        // так, как выбрал пользователь, а не как стоит по умолчанию на сервере.
        val separator = if (VoiceConfig.RELAY_URL.contains('?')) "&" else "?"
        val url = "${VoiceConfig.RELAY_URL}${separator}audio=${if (speakAnswers) 1 else 0}"

        val request = Request.Builder()
            .url(url)
            // Токен пользователя — основной способ представиться. Но приложение
            // его не обновляет, и через час после входа он протухает, поэтому
            // рядом кладём тот же ключ приложения, с которым ходит весь
            // остальной API: релей примет его как запасной вариант.
            .addHeader("Authorization", "Bearer $token")
            .addHeader("apikey", ApiClient.ANON_KEY)
            .build()

        socket = http.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "релей подключён")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post { handleServerEvent(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                val message = when (code) {
                    401 -> "Голосовой сервис не принял вход — проверьте SUPABASE_ANON_KEY в его .env"
                    503 -> "Голосовой сервис занят, попробуйте через минуту"
                    null -> "Нет связи с голосовым сервисом"
                    else -> "Голосовой сервис недоступен (HTTP $code)"
                }
                Log.w(TAG, "сбой WebSocket: ${t.message} (code=$code)")
                main.post {
                    listener.onError(message)
                    stopInternal(VoiceState.ERROR)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "релей закрыл соединение: $code $reason")
                main.post { stopInternal(VoiceState.IDLE) }
            }
        })
    }

    /**
     * Открыть микрофон под одну фразу. Если ассистент в этот момент говорит —
     * глушим его: пользователь перебивает осознанно, нажав кнопку.
     */
    fun startListening() {
        if (!running.get()) return
        if (micOpen) return
        if (audioEnabled) player.flush()
        send(JSONObject().put("type", "cancel"))
        if (sessionReady) openMic() else micOnReady = true
    }

    /** Закрыть микрофон, не отправляя начатую фразу. */
    fun cancelListening() {
        if (!micOpen) return
        closeMic(awaitingAnswer = false)
        listener.onState(VoiceState.IDLE_SESSION)
    }

    /**
     * Напечатанный вопрос. Может прийти раньше, чем релей сконфигурирует
     * сессию, — тогда придерживаем его и отправляем по `ready`, иначе первый
     * же вопрос после переключения на «Яндекс» потерялся бы молча.
     */
    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        listener.onAwaitingAnswer(true)
        if (!sessionReady) {
            pendingTexts.add(trimmed)
            return
        }
        send(JSONObject().put("type", "text").put("text", trimmed))
    }

    /** Включить или выключить озвучку посреди разговора. */
    fun setAudioOutput(enabled: Boolean) {
        send(JSONObject().put("type", "set_audio").put("enabled", enabled))
    }

    /** Прервать ответ ассистента по кнопке. */
    fun cancel() {
        if (audioEnabled) player.flush()
        send(JSONObject().put("type", "cancel"))
        listener.onAwaitingAnswer(false)
        listener.onState(VoiceState.IDLE_SESSION)
    }

    fun stop() = stopInternal(VoiceState.IDLE)

    private fun stopInternal(finalState: VoiceState) {
        if (!running.getAndSet(false)) return
        if (answerOpen) finishAnswer(assistantBuffer.toString())
        sessionReady = false
        pendingTexts.clear()
        cancelTimers()
        closeMic(awaitingAnswer = false)
        player.stop()
        try {
            socket?.close(1000, "bye")
        } catch (_: Exception) {
        }
        socket = null
        listener.onAwaitingAnswer(false)
        listener.onState(finalState)
    }

    // -------------------------------------------------------------- события сервера

    private fun handleServerEvent(raw: String) {
        val ev = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return
        }
        when (ev.optString("type")) {
            "ready" -> {
                audioEnabled = ev.optBoolean("audio", false)
                sessionReady = true
                if (audioEnabled) player.start()
                while (true) {
                    val queued = pendingTexts.poll() ?: break
                    send(JSONObject().put("type", "text").put("text", queued))
                }
                if (micOnReady) {
                    micOnReady = false
                    openMic()
                } else {
                    listener.onState(VoiceState.IDLE_SESSION)
                }
            }

            "audio_mode" -> {
                audioEnabled = ev.optBoolean("audio", false)
                if (audioEnabled) player.start() else player.stop()
            }

            // Пользователь заговорил: фраза началась, снимаем сторожевой таймер
            // тишины и ставим потолок длительности.
            "speech_started" -> {
                if (!micOpen) return
                speechHeard = true
                main.removeCallbacks(noSpeechTimeout)
                main.removeCallbacks(maxPhraseTimeout)
                main.postDelayed(maxPhraseTimeout, VoiceConfig.MAX_PHRASE_MS)
                listener.onState(VoiceState.LISTENING)
            }

            // Фраза закончилась — микрофон закрывается сам, как в Google.
            "speech_stopped" -> {
                if (!micOpen) return
                closeMic(awaitingAnswer = true)
            }

            "audio" -> {
                if (!audioEnabled) return
                val pcm = decode(ev.optString("audio"))
                if (pcm.isNotEmpty()) {
                    player.enqueue(pcm)
                    listener.onState(VoiceState.SPEAKING)
                }
            }

            "turn_done" -> {
                // Страховка: если ответ шёл кусками, а события «текст дописан»
                // не пришло (такое бывает, когда модель ответила только
                // голосом), закрываем пузырь тем, что успело накопиться.
                if (answerOpen) finishAnswer(assistantBuffer.toString())
                listener.onAwaitingAnswer(false)
                if (running.get() && !micOpen) listener.onState(VoiceState.IDLE_SESSION)
            }

            "user_text" -> {
                val text = ev.optString("text")
                if (text.isNotBlank()) listener.onUserText(text)
            }

            "assistant_text_delta" -> {
                val piece = ev.optString("text")
                if (piece.isNotEmpty()) {
                    answerOpen = true
                    assistantBuffer.append(piece)
                    listener.onAssistantDelta(piece)
                    if (!audioEnabled) listener.onState(VoiceState.SPEAKING)
                }
            }

            "assistant_text_done" -> {
                finishAnswer(ev.optString("text").ifBlank { assistantBuffer.toString() })
            }

            "error" -> {
                listener.onAwaitingAnswer(false)
                listener.onError(ev.optString("message", "Ошибка голосового режима"))
            }
        }
    }

    /**
     * Закрыть ответ. Текст с сервера считаем итоговым, накопленные куски —
     * запасным вариантом; закрывать нужно ровно один раз, иначе в ленте
     * появится второй пузырь с тем же ответом.
     */
    private fun finishAnswer(text: String) {
        answerOpen = false
        assistantBuffer.setLength(0)
        listener.onAwaitingAnswer(false)
        listener.onAssistantDone(text)
    }

    // ------------------------------------------------------------------- микрофон

    private fun openMic() {
        if (micOpen) return
        if (!startRecording()) return
        micOpen = true
        speechHeard = false
        // Пользователь перебил незаконченный ответ — закрываем его пузырь тем,
        // что успело прозвучать, иначе следующий ответ начнёт дописываться в
        // тот же самый.
        if (answerOpen) finishAnswer(assistantBuffer.toString())
        listener.onMicActive(true)
        listener.onState(VoiceState.LISTENING)
        main.removeCallbacks(noSpeechTimeout)
        main.postDelayed(noSpeechTimeout, VoiceConfig.NO_SPEECH_TIMEOUT_MS)
    }

    private fun closeMic(awaitingAnswer: Boolean) {
        cancelTimers()
        if (!micOpen) {
            stopRecording()
            return
        }
        micOpen = false
        stopRecording()
        listener.onMicActive(false)
        listener.onAwaitingAnswer(awaitingAnswer)
    }

    private fun cancelTimers() {
        main.removeCallbacks(noSpeechTimeout)
        main.removeCallbacks(maxPhraseTimeout)
    }

    private fun startRecording(): Boolean {
        if (recorder != null) return true

        val bytesPerChunk =
            VoiceConfig.MIC_SAMPLE_RATE * 2 * VoiceConfig.MIC_CHUNK_MS / 1000
        val minBuf = AudioRecord.getMinBufferSize(
            VoiceConfig.MIC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(bytesPerChunk * 4)

        val rec = try {
            @Suppress("MissingPermission")
            AudioRecord(
                // VOICE_COMMUNICATION включает аппаратное эхоподавление там,
                // где оно есть, — иначе ассистент слышит сам себя.
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                VoiceConfig.MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
        } catch (e: Exception) {
            listener.onError("Не удалось открыть микрофон")
            return false
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            listener.onError("Микрофон занят другим приложением")
            return false
        }

        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
        }

        recorder = rec
        rec.startRecording()

        recordThread = Thread({
            val buf = ByteArray(bytesPerChunk)
            while (micOpen && running.get()) {
                val read = try {
                    rec.read(buf, 0, buf.size)
                } catch (e: Exception) {
                    -1
                }
                if (read <= 0) continue
                val payload = if (read == buf.size) buf else buf.copyOf(read)
                send(
                    JSONObject()
                        .put("type", "audio")
                        .put("audio", Base64.encodeToString(payload, Base64.NO_WRAP))
                )
            }
        }, "voice-mic").also { it.start() }

        return true
    }

    private fun stopRecording() {
        // Сначала дожидаемся, пока поток выйдет из AudioRecord.read(), и только
        // потом освобождаем объект: release() под активным чтением роняет
        // нативный слой, а не бросает исключение.
        val thread = recordThread
        recordThread = null
        thread?.interrupt()
        try {
            thread?.join(400)
        } catch (_: InterruptedException) {
        }
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {
        }
        recorder?.release()
        recorder = null
        aec?.release(); aec = null
        ns?.release(); ns = null
    }

    // -------------------------------------------------------------------- мелочи

    private fun send(obj: JSONObject) {
        val ws = socket ?: return
        try {
            ws.send(obj.toString())
        } catch (e: Exception) {
            Log.w(TAG, "не удалось отправить кадр: ${e.message}")
        }
    }

    private fun decode(b64: String): ByteArray = try {
        if (b64.isEmpty()) ByteArray(0) else Base64.decode(b64, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        ByteArray(0)
    }

    private companion object {
        const val TAG = "VoiceRelayClient"
    }
}

package com.eduappml.voice

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Обёртка над [VoiceRelayClient] для Compose.
 *
 * Отдельная ViewModel, а не поле в ChatViewModel: голосовой режим — второй,
 * независимый путь в том же экране. Текстовый чат с GigaChat остаётся
 * нетронутым.
 *
 * Расшифровки реплик отдаются наружу колбэками [onUserSaid] / [onAssistantSaid]:
 * ChatScreen подставляет туда добавление сообщений в ленту, так что история
 * разговора выглядит так же, как переписка.
 */
class VoiceViewModel : ViewModel() {

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    /** Живая расшифровка того, что ассистент произносит прямо сейчас. */
    private val _liveText = MutableStateFlow("")
    val liveText: StateFlow<String> = _liveText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Открыт ли микрофон прямо сейчас. Микрофон одноразовый: держится ровно
     * одну фразу, поэтому этот флаг — то, что показывает полосу состояния.
     */
    private val _micActive = MutableStateFlow(false)
    val micActive: StateFlow<Boolean> = _micActive.asStateFlow()

    /** Реплика принята, ответа ещё нет — на это время поднимаем «печатает». */
    private val _awaitingAnswer = MutableStateFlow(false)
    val awaitingAnswer: StateFlow<Boolean> = _awaitingAnswer.asStateFlow()

    /**
     * Озвучивать ли ответы. Живёт здесь, а не в клиенте: переключатель должен
     * помнить выбор и между сессиями, и при повороте экрана.
     */
    private val _speakAnswers = MutableStateFlow(true)
    val speakAnswers: StateFlow<Boolean> = _speakAnswers.asStateFlow()

    /** Вызывается, когда распознана реплика пользователя. */
    var onUserSaid: ((String) -> Unit)? = null

    /**
     * Кусок ответа по мере того, как модель его произносит. Экран пишет его
     * в ленту сразу, параллельно озвучке.
     */
    var onAssistantDelta: ((String) -> Unit)? = null

    /** Вызывается, когда ассистент договорил реплику целиком. */
    var onAssistantSaid: ((String) -> Unit)? = null

    /** Ответ оборвался, не начавшись, — экран убирает пустой пузырь. */
    var onAssistantAborted: (() -> Unit)? = null

    private var client: VoiceRelayClient? = null

    val isActive: Boolean
        get() = _state.value != VoiceState.IDLE && _state.value != VoiceState.ERROR

    // ----------------------------------------------------------------- команды

    /**
     * Нажали микрофон. Поднимаем сессию, если её нет, и открываем микрофон под
     * одну фразу; повторное нажатие во время записи её отменяет.
     */
    fun toggleListening(accessToken: String) {
        val current = client
        when {
            current == null -> start(accessToken, withMic = true)
            _micActive.value -> current.cancelListening()
            else -> current.startListening()
        }
    }

    /**
     * Отправить напечатанный вопрос, подняв сессию, если её ещё нет.
     * Микрофон при этом не трогаем — разрешение не нужно.
     * Текст, отправленный до готовности сессии, клиент придержит сам.
     */
    fun sendText(accessToken: String, text: String) {
        if (client == null) start(accessToken, withMic = false)
        client?.sendText(text)
    }

    /** Включить или выключить озвучку ответов. */
    fun setSpeakAnswers(enabled: Boolean) {
        if (_speakAnswers.value == enabled) return
        _speakAnswers.value = enabled
        // Живой сессии говорим сразу; если сессии нет, значение подхватится
        // при следующем подключении.
        client?.setAudioOutput(enabled)
    }

    fun start(accessToken: String, withMic: Boolean = true) {
        if (client != null) return
        _error.value = null
        _liveText.value = ""

        client = VoiceRelayClient(
            accessToken = accessToken,
            speakAnswers = _speakAnswers.value,
            listener = object : VoiceRelayClient.Listener {

                override fun onState(state: VoiceState) {
                    _state.value = state
                    if (state == VoiceState.IDLE || state == VoiceState.ERROR) {
                        _liveText.value = ""
                        _micActive.value = false
                        _awaitingAnswer.value = false
                        // Сессия закончилась — отпускаем ссылку, иначе повторное
                        // нажатие после ошибки упрётся в проверку `client != null`.
                        client = null
                    }
                }

                override fun onMicActive(active: Boolean) {
                    _micActive.value = active
                }

                override fun onAwaitingAnswer(active: Boolean) {
                    _awaitingAnswer.value = active
                }

                override fun onUserText(text: String) {
                    onUserSaid?.invoke(text)
                }

                override fun onAssistantDelta(text: String) {
                    _liveText.value = _liveText.value + text
                    // Первый же кусок означает, что ответ пошёл: «печатает»
                    // больше не нужен, его место занимает растущий пузырь.
                    _awaitingAnswer.value = false
                    onAssistantDelta?.invoke(text)
                }

                override fun onAssistantDone(text: String) {
                    _liveText.value = ""
                    onAssistantSaid?.invoke(text)
                }

                override fun onError(message: String) {
                    _error.value = message
                    onAssistantAborted?.invoke()
                }
            }
        ).also { it.start(withMic) }
    }

    /** Прервать ответ ассистента, не выходя из сессии. */
    fun interrupt() {
        client?.cancel()
    }

    fun stop() {
        client?.stop()
        client = null
        _state.value = VoiceState.IDLE
        _liveText.value = ""
        _micActive.value = false
        _awaitingAnswer.value = false
        onAssistantAborted?.invoke()
    }

    fun clearError() {
        _error.value = null
    }

    /** Показать сообщение в той же строке ошибок (например, «нет токена сессии»). */
    fun reportError(message: String) {
        _error.value = message
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

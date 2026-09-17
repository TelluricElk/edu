package com.eduappml.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduappml.data.models.ArmintelChatRequest
import com.eduappml.data.models.ArmintelDefaults
import com.eduappml.data.models.ArmintelModel
import com.eduappml.data.models.ChatAttachmentPayload
import com.eduappml.data.models.ChatHistoryItem
import com.eduappml.data.models.ChatRequest
import com.eduappml.data.models.ChatResponse
import com.eduappml.data.models.ChatRole
import com.eduappml.network.ApiClient
import com.eduappml.network.LlmGatewayClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

// ---------------------------------------------------------------------------
// Модель сообщения на экране
// ---------------------------------------------------------------------------

/** Вложение так, как оно показывается в уже отправленном сообщении. */
class ChatUiAttachment(
    val id: String,
    val displayName: String,
    val readableSize: String,
    val extension: String,
    val isImage: Boolean,
    val preview: Bitmap?
)

/** Картинка, которую сгенерировал GigaChat. */
class ChatUiImage(
    val id: String,
    val bytes: ByteArray
)

class ChatUiMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val attachments: List<ChatUiAttachment> = emptyList(),
    val images: List<ChatUiImage> = emptyList()
)

/**
 * Кто отвечает на вопросы. Переключается в шапке чата.
 *
 * Это три разных канала, а не три параметра одного запроса:
 * [GIGACHAT] — HTTP в нашу Edge Function, [YANDEX] — WebSocket в voice-relay,
 * [ARMINTEL] — HTTP в наш же llm-gateway (`server/llm-gateway/`). Поэтому
 * картинки от модели работают только у первого, голос — только у второго,
 * а выбор модели — только у третьего. История диалога есть у всех трёх, но
 * у «Алисы» её держит сама сессия.
 *
 * Названия — официальные: «GigaChat» у Сбера, «Алиса» у Яндекса (под капотом
 * у неё Yandex AI Studio Realtime, но пользователю знакомо имя ассистента),
 * «Armintel» — не модель, а площадка, поэтому в списке она представлена не
 * собой, а своими моделями (Gemma, Qwen, DeepSeek, FastContext).
 */
enum class ChatEngine(val label: String) {
    GIGACHAT("GigaChat"),
    YANDEX("Алиса"),
    ARMINTEL("Armintel")
}

/** Что именно сейчас происходит — подпись показывается в «печатает»-пузыре. */
enum class ChatStage(val label: String) {
    Idle(""),
    Reading("Читаю файлы…"),
    Sending("Отправляю вложения…"),
    Thinking("Edu.AI думает…")
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class ChatViewModel(context: Context) : ViewModel() {
    private val TAG = "ChatViewModel"

    /**
     * ViewModel переживает пересоздание Activity, поэтому держать в поле
     * контекст самой Activity — прямая утечка. Для ContentResolver достаточно
     * контекста приложения, и живёт он ровно столько, сколько нужно.
     */
    private val appContext = context.applicationContext

    /**
     * Сколько последних сообщений уходит в history. Без ограничения длинный
     * диалог начинает весить больше, чем сам вопрос, а GigaChat всё равно
     * обрежет контекст — только уже без нашего контроля над тем, что выпадет.
     */
    private val HistoryLimit = 20

    private val _messages = MutableStateFlow(
        listOf(
            ChatUiMessage(
                text = "Привет! Я — Edu.AI, помогу разобраться с материалом. " +
                    "Можно задать вопрос текстом или прикрепить файл — фото конспекта, " +
                    "скриншот задачи, PDF или таблицу. 🙂",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatUiMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _stage = MutableStateFlow(ChatStage.Idle)
    val stage: StateFlow<ChatStage> = _stage

    /** Файлы, выбранные, но ещё не отправленные. */
    private val _pending = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val pending: StateFlow<List<PendingAttachment>> = _pending

    /** Текст последней ошибки прикрепления — показывается плашкой над полем ввода. */
    private val _attachError = MutableStateFlow<String?>(null)
    val attachError: StateFlow<String?> = _attachError

    // ---------------------------------------------------------------------
    // Вложения
    // ---------------------------------------------------------------------

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _stage.value = ChatStage.Reading
            var lastError: String? = null

            for (uri in uris) {
                val current = _pending.value
                if (current.size >= ChatFileLimits.MaxAttachments) {
                    lastError = "К одному сообщению можно прикрепить не больше " +
                        "${ChatFileLimits.MaxAttachments} файлов."
                    break
                }
                val used = current.sumOf { it.sizeBytes }
                // Чтение и сжатие — на IO: снимок с камеры сжимается заметное
                // время, и делать это в main-потоке значит подвесить анимацию
                // открытия панели вложений.
                val result = withContext(Dispatchers.IO) { readAttachment(appContext, uri, used) }
                when (result) {
                    is AttachmentResult.Ok -> _pending.value = current + result.attachment
                    is AttachmentResult.Failure -> lastError = result.message
                }
            }

            _attachError.value = lastError
            _stage.value = ChatStage.Idle
        }
    }

    fun removeAttachment(id: String) {
        _pending.value = _pending.value.filterNot { it.id == id }
        _attachError.value = null
    }

    fun clearAttachments() {
        _pending.value = emptyList()
        _attachError.value = null
    }

    fun dismissAttachError() {
        _attachError.value = null
    }

    // ---------------------------------------------------------------------
    // Отправка
    // ---------------------------------------------------------------------

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        val attachments = _pending.value
        // Пустой текст допустим, если есть вложения: «посмотри, что тут» —
        // нормальный сценарий, но модели нужен хоть какой-то промпт, поэтому
        // ниже подставляется defaultPrompt.
        if ((trimmed.isEmpty() && attachments.isEmpty()) || _isLoading.value) return

        val outgoingText = trimmed.ifEmpty { defaultPromptFor(attachments) }

        _messages.value = _messages.value + ChatUiMessage(
            text = trimmed,
            isUser = true,
            attachments = attachments.map {
                ChatUiAttachment(
                    id = it.id,
                    displayName = it.displayName,
                    readableSize = it.readableSize,
                    extension = it.extension,
                    isImage = it.isImage,
                    preview = it.thumbnail
                )
            }
        )
        _pending.value = emptyList()
        _attachError.value = null
        _isLoading.value = true
        _stage.value = if (attachments.isEmpty()) ChatStage.Thinking else ChatStage.Sending

        // История — всё, КРОМЕ только что добавленного сообщения: сам вопрос
        // едет отдельным полем `message`, и дублировать его в history значит
        // отправлять модели один и тот же текст дважды.
        val history = _messages.value
            .dropLast(1)
            .filterNot { it.isError }
            .takeLast(HistoryLimit)
            .map { message ->
                ChatHistoryItem(
                    role = if (message.isUser) ChatRole.USER else ChatRole.ASSISTANT,
                    content = historyContent(message)
                )
            }

        viewModelScope.launch {
            try {
                // base64-кодирование мегабайтных массивов — тоже не для main-потока.
                val payload = withContext(Dispatchers.Default) {
                    attachments.map {
                        ChatAttachmentPayload(
                            name = it.name,
                            mimeType = it.mimeType,
                            dataBase64 = encodeBase64(it.bytes)
                        )
                    }
                }

                _stage.value = ChatStage.Thinking

                val response = ApiClient.authApi.sendChatMessage(
                    ChatRequest(
                        message = outgoingText,
                        history = history,
                        attachments = payload,
                        allowImages = true
                    )
                )

                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    val images = withContext(Dispatchers.Default) {
                        body.images.orEmpty().mapNotNull { image ->
                            val data = image.dataBase64 ?: return@mapNotNull null
                            val bytes = decodeBase64(data) ?: return@mapNotNull null
                            if (bytes.isEmpty()) null
                            else ChatUiImage(id = image.id ?: UUID.randomUUID().toString(), bytes = bytes)
                        }
                    }
                    val reply = body.reply?.trim().orEmpty()
                    val visibleText = when {
                        reply.isNotEmpty() -> reply
                        images.isNotEmpty() -> "Готово — вот изображение."
                        else -> "Сервер не вернул ответ."
                    }
                    _messages.value = _messages.value + ChatUiMessage(
                        text = visibleText,
                        isUser = false,
                        images = images
                    )
                } else {
                    val err = body?.error
                        ?: response.errorBody()?.string()
                        ?: "неизвестная ошибка сервера"
                    Log.e(TAG, "sendMessage error: $err")
                    _messages.value = _messages.value + ChatUiMessage(
                        text = errorTextFor(response.code(), err),
                        isUser = false,
                        isError = true
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error: ${e.message}", e)
                _messages.value = _messages.value + ChatUiMessage(
                    text = if (attachments.isEmpty()) {
                        "Не удалось подключиться к серверу чата. Проверьте подключение к интернету."
                    } else {
                        "Не удалось отправить вложения — соединение прервалось. " +
                            "Попробуйте ещё раз или прикрепите файл поменьше."
                    },
                    isUser = false,
                    isError = true
                )
            } catch (e: HttpException) {
                Log.e(TAG, "HTTP error: ${e.message}", e)
                _messages.value = _messages.value + ChatUiMessage(
                    text = "Сервер вернул ошибку. Попробуйте позже.",
                    isUser = false,
                    isError = true
                )
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM while encoding attachments", e)
                _messages.value = _messages.value + ChatUiMessage(
                    text = "Не хватило памяти на обработку вложения. Попробуйте файл поменьше.",
                    isUser = false,
                    isError = true
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error/throwable: ${t.message}", t)
                _messages.value = _messages.value + ChatUiMessage(
                    text = "Произошла непредвиденная ошибка при обращении к серверу чата.",
                    isUser = false,
                    isError = true
                )
            } finally {
                _isLoading.value = false
                _stage.value = ChatStage.Idle
            }
        }
    }

    // ---------------------------------------------------------------------
    // Выбор движка и голосовой режим (Yandex Realtime)
    // ---------------------------------------------------------------------

    /**
     * Кто отвечает. Живёт в ViewModel, а не в `remember` экрана, чтобы выбор
     * не сбрасывался при повороте и возврате на экран.
     */
    private val _engine = MutableStateFlow(ChatEngine.GIGACHAT)
    val engine: StateFlow<ChatEngine> = _engine

    fun setEngine(value: ChatEngine) {
        _engine.value = value
    }

    /**
     * Показать «печатает»-пузырь, пока Яндекс отвечает на напечатанный вопрос.
     *
     * У голосового пути нет своего `isLoading`: запрос не HTTP-шный, ждать
     * нечего — но пользователю всё равно нужно видеть, что вопрос принят.
     * Когда микрофон включён, эту роль играет полоса состояния, и метод
     * не вызывается.
     */
    fun setVoiceThinking(active: Boolean) {
        _isLoading.value = active
        _stage.value = if (active) ChatStage.Thinking else ChatStage.Idle
    }

    /**
     * Дописать в ленту реплику из голосового разговора.
     *
     * Голос идёт мимо GigaChat — по WebSocket через наш релей (см. пакет
     * `com.eduappml.voice`), — но выглядеть в истории должен так же, как
     * обычная переписка. Отдельный метод, а не переиспользование
     * [sendMessage]: там сетевой запрос, здесь его не нужно.
     */
    fun appendVoiceMessage(text: String, isUser: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _messages.value = _messages.value + ChatUiMessage(text = trimmed, isUser = isUser)
    }

    // ---------------------------------------------------------------------
    // «Живой» ответ голосового режима
    // ---------------------------------------------------------------------
    // Пузырь ассистента наполняется по мере того, как модель говорит: текст
    // идёт в ленту параллельно озвучке, а не сваливается целиком в конце.
    // ChatUiMessage неизменяемый, поэтому пузырь пересобирается с ТЕМ ЖЕ id —
    // ключ в LazyColumn не меняется, и список обновляет строку на месте,
    // вместо того чтобы дёргать её как новую.

    private var voiceStreamId: String? = null

    /** Дописать кусок ответа; первый кусок создаёт сам пузырь. */
    fun appendVoiceStream(delta: String) {
        if (delta.isEmpty()) return
        val id = voiceStreamId ?: UUID.randomUUID().toString().also {
            voiceStreamId = it
            _messages.value = _messages.value + ChatUiMessage(id = it, text = "", isUser = false)
        }
        _messages.value = _messages.value.map { message ->
            if (message.id == id) {
                ChatUiMessage(id = id, text = message.text + delta, isUser = false)
            } else {
                message
            }
        }
    }

    /**
     * Ответ дописан. [finalText] с сервера считаем итоговым — в нём уже нет
     * расхождений, которые могли накопиться в кусках. Если модель не сказала
     * ничего, пустой пузырь убираем, чтобы не висел в ленте.
     */
    fun finishVoiceStream(finalText: String) {
        val id = voiceStreamId
        voiceStreamId = null

        if (id == null) {
            appendVoiceMessage(finalText, isUser = false)
            return
        }

        _messages.value = _messages.value.mapNotNull { message ->
            when {
                message.id != id -> message
                finalText.isNotBlank() ->
                    ChatUiMessage(id = id, text = finalText.trim(), isUser = false)
                message.text.isNotBlank() -> message
                else -> null
            }
        }
    }

    /** Оборвать незаконченный пузырь — при ошибке или выходе из режима. */
    fun dropVoiceStream() {
        val id = voiceStreamId ?: return
        voiceStreamId = null
        _messages.value = _messages.value.filterNot { it.id == id && it.text.isBlank() }
    }

    // ---------------------------------------------------------------------
    // Движок Armintel (llm-gateway)
    // ---------------------------------------------------------------------

    /**
     * Список моделей. Источник правды — сервер (`GET /models`), поэтому набор
     * моделей меняется правкой `.env` шлюза, без новой сборки APK. Стартовое
     * значение — [ArmintelDefaults.MODELS]: список в шапке должен быть полным
     * сразу, а не достраиваться на глазах у пользователя.
     */
    private val _armintelModels = MutableStateFlow(ArmintelDefaults.MODELS)
    val armintelModels: StateFlow<List<ArmintelModel>> = _armintelModels

    /** Выбранная модель. */
    private val _armintelModel = MutableStateFlow(ArmintelDefaults.MODEL)
    val armintelModel: StateFlow<String> = _armintelModel

    /** Отдельный флаг, а не «список непустой»: список непуст с самого начала. */
    private var armintelModelsLoaded = false

    fun setArmintelModel(id: String) {
        _armintelModel.value = id
    }

    /**
     * Подтянуть список моделей с сервера. Повторные вызовы бесплатны.
     * Молчаливая: не смогли получить список — остаёмся на встроенном,
     * ругаться пользователю не на что.
     */
    fun loadArmintelModels(token: String?) {
        if (token.isNullOrBlank() || armintelModelsLoaded) return
        armintelModelsLoaded = true
        viewModelScope.launch {
            try {
                val response = LlmGatewayClient.api.models("Bearer $token")
                val body = response.body()
                // Спрятанные модели отсеиваются здесь, в одном месте: и меню,
                // и выбор по умолчанию ниже должны видеть один и тот же
                // список, иначе в шапке окажется имя, которого в меню нет.
                val visible = body?.models.orEmpty()
                    .filterNot { it.id in ArmintelDefaults.HIDDEN }

                if (response.isSuccessful && body != null && visible.isNotEmpty()) {
                    _armintelModels.value = visible
                    // Выбранная модель могла исчезнуть из нового списка —
                    // тогда переезжаем на серверную по умолчанию, иначе
                    // запрос ушёл бы с несуществующим id.
                    if (visible.none { it.id == _armintelModel.value }) {
                        _armintelModel.value = body.default?.takeIf { def ->
                            visible.any { it.id == def }
                        } ?: visible.first().id
                    }
                } else {
                    Log.w(TAG, "armintel models: код ${response.code()}")
                    armintelModelsLoaded = false
                }
            } catch (t: Throwable) {
                Log.w(TAG, "armintel models: ${t.message}")
                armintelModelsLoaded = false
            }
        }
    }

    /**
     * Отправить вопрос в llm-gateway.
     *
     * Отдельный метод, а не ветка внутри [sendMessage]: у GigaChat свой
     * клиент, свои вложения и свои картинки в ответе, и трогать работающий
     * путь ради третьего движка незачем. Общее у них только формат истории
     * и вид ленты сообщений.
     *
     * Токен передаётся снаружи, как и в голосовом режиме: ViewModel не лезет
     * в SessionManager, чтобы её можно было держать без Activity-контекста.
     */
    fun sendArmintelMessage(token: String?, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isLoading.value) return

        if (token.isNullOrBlank()) {
            _messages.value = _messages.value + ChatUiMessage(
                text = "Этот движок доступен после входа в аккаунт.",
                isUser = false,
                isError = true
            )
            return
        }

        _messages.value = _messages.value + ChatUiMessage(text = trimmed, isUser = true)
        _isLoading.value = true
        _stage.value = ChatStage.Thinking

        // Та же логика, что у GigaChat: сам вопрос едет полем `message`,
        // поэтому в историю он не дублируется.
        val history = _messages.value
            .dropLast(1)
            .filterNot { it.isError }
            .takeLast(HistoryLimit)
            .map { message ->
                ChatHistoryItem(
                    role = if (message.isUser) ChatRole.USER else ChatRole.ASSISTANT,
                    content = historyContent(message)
                )
            }

        viewModelScope.launch {
            try {
                val response = LlmGatewayClient.api.chat(
                    token = "Bearer $token",
                    request = ArmintelChatRequest(
                        message = trimmed,
                        history = history,
                        model = _armintelModel.value.takeIf { it.isNotBlank() }
                    )
                )

                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    val reply = body.reply?.trim().orEmpty()
                    _messages.value = _messages.value + ChatUiMessage(
                        text = reply.ifEmpty { "Сервер не вернул ответ." },
                        isUser = false
                    )
                } else {
                    // Шлюз пишет ошибки по-русски и по делу («модель не успела
                    // ответить», «нужен вход»), поэтому его текст показываем
                    // как есть, а свою формулировку подставляем, только если
                    // разобрать ответ не удалось.
                    val message = body?.error
                        ?: parseGatewayError(response.errorBody()?.string())
                        ?: armintelFallbackError(response.code())
                    Log.e(TAG, "armintel chat: ${response.code()} $message")
                    _messages.value = _messages.value + ChatUiMessage(
                        text = message,
                        isUser = false,
                        isError = true
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "armintel network: ${e.message}", e)
                _messages.value = _messages.value + ChatUiMessage(
                    text = "Не удалось подключиться к серверу моделей. Проверьте подключение к интернету.",
                    isUser = false,
                    isError = true
                )
            } catch (e: HttpException) {
                Log.e(TAG, "armintel http: ${e.message}", e)
                _messages.value = _messages.value + ChatUiMessage(
                    text = "Сервер моделей вернул ошибку. Попробуйте позже.",
                    isUser = false,
                    isError = true
                )
            } catch (t: Throwable) {
                Log.e(TAG, "armintel unexpected: ${t.message}", t)
                _messages.value = _messages.value + ChatUiMessage(
                    text = "Произошла непредвиденная ошибка при обращении к серверу моделей.",
                    isUser = false,
                    isError = true
                )
            } finally {
                _isLoading.value = false
                _stage.value = ChatStage.Idle
            }
        }
    }

    /** При не-2xx тело уезжает в errorBody, и Retrofit его уже не разбирает. */
    private fun parseGatewayError(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            Gson().fromJson(raw, ChatResponse::class.java)?.error?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun armintelFallbackError(code: Int): String = when (code) {
        401, 403 -> "Сервер моделей не принял вход. Попробуйте перезайти в аккаунт."
        503 -> "Сервер моделей сейчас занят. Попробуйте через минуту."
        504 -> "Модель не ответила вовремя. Попробуйте ещё раз или выберите другую."
        else -> "Сервер моделей вернул ошибку. Попробуйте позже."
    }

    // ---------------------------------------------------------------------
    // Вспомогательное
    // ---------------------------------------------------------------------

    private fun defaultPromptFor(attachments: List<PendingAttachment>): String = when {
        attachments.isEmpty() -> ""
        attachments.all { it.isImage } && attachments.size == 1 ->
            "Что изображено на картинке? Если там задача или формула — разбери подробно."
        attachments.all { it.isImage } ->
            "Что изображено на этих картинках? Если там задачи или формулы — разбери подробно."
        attachments.size == 1 -> "Разбери этот файл и объясни главное."
        else -> "Разбери эти файлы и объясни главное."
    }

    /**
     * В history вложения не едут (они уже загружены и разобраны моделью в
     * своём запросе), но упомянуть их надо: иначе на «а что было на той
     * картинке?» модель отвечает, что картинки не было.
     */
    private fun historyContent(message: ChatUiMessage): String {
        if (message.attachments.isEmpty()) return message.text
        val names = message.attachments.joinToString(", ") { it.displayName }
        return if (message.text.isBlank()) "[вложения: $names]"
        else "${message.text}\n[вложения: $names]"
    }

    private fun errorTextFor(code: Int, raw: String): String = when {
        code == 413 || raw.contains("too large", ignoreCase = true) ->
            "Файл оказался слишком большим для сервера. Попробуйте вложение поменьше."
        code == 401 || code == 403 ->
            "Сервер чата отклонил запрос (нет доступа). Сообщите об этом разработчику."
        else -> "Сервер чата вернул ошибку. Попробуйте позже."
    }
}

package com.eduappml.data.models

import com.google.gson.annotations.SerializedName

/**
 * Модели данных третьего движка чата — шлюза к llm.armintel.ru
 * (`server/llm-gateway/`).
 *
 * Отдельный файл, а не дополнение ChatModels.kt: путь к GigaChat трогать
 * незачем, а формат ответа у шлюза намеренно тот же, поэтому [ChatResponse]
 * и [ChatHistoryItem] переиспользуются как есть. Лишнее поле `model`,
 * которое шлюз кладёт в ответ, Gson просто пропустит.
 */
data class ArmintelChatRequest(
    @SerializedName("message") val message: String,
    @SerializedName("history") val history: List<ChatHistoryItem> = emptyList(),
    /** Пусто — шлюз возьмёт модель по умолчанию из своего `.env`. */
    @SerializedName("model") val model: String? = null,
    @SerializedName("attachments") val attachments: List<ChatAttachmentPayload> = emptyList()
)

/** Одна строка выпадающего списка моделей. */
data class ArmintelModel(
    @SerializedName("id") val id: String = "",
    @SerializedName("label") val label: String? = null,
    @SerializedName("note") val note: String? = null
) {
    val title: String get() = label?.takeIf { it.isNotBlank() } ?: id
}

/**
 * Ответ `GET /models`.
 *
 * Список приходит с сервера, а не зашит в APK: набор моделей на той стороне
 * меняется, и менять его правкой `.env` дешевле, чем новой сборкой.
 */
data class ArmintelModelsResponse(
    @SerializedName("default") val default: String? = null,
    @SerializedName("models") val models: List<ArmintelModel> = emptyList()
)

/**
 * Модели, которые 17.09.2026 отвечали быстро и по-русски (замеры в
 * `server/llm-gateway/README.md`), и список временно скрытых.
 *
 * Запасной список нужен, чтобы меню в шапке было полным сразу при открытии
 * экрана, ещё до ответа шлюза: иначе на медленной сети пользователь секунду
 * видит два пункта вместо всех. Пришедший с сервера список эти значения
 * замещает — источник правды остаётся на сервере.
 *
 * Названия — официальные, как их зовут авторы: Google Gemma, Alibaba Qwen,
 * DeepSeek. Подписи в меню не показываются, поэтому в [ALL] их и нет.
 */
object ArmintelDefaults {

    /**
     * Спрятанные из выбора модели — и во встроенном списке, и в том, что
     * приходит с сервера ([ChatViewModel] фильтрует по этому множеству).
     *
     * Gemma спрятана по просьбе владельца 17.09.2026. Сама модель никуда
     * не делась: она осталась моделью по умолчанию на сервере и ответит,
     * если запрос придёт без поля `model`. Вернуть в меню — убрать строку
     * отсюда, пересборка нужна только ради этого.
     */
    val HIDDEN: Set<String> = setOf("gemma-3-27b-it")

    /** Модель, выбранная при первом открытии чата. Не из [HIDDEN]. */
    const val MODEL = "Qwen3.8-Flash-Next"

    private val ALL: List<ArmintelModel> = listOf(
        ArmintelModel("gemma-3-27b-it", "Gemma 3 27B"),
        ArmintelModel("Qwen3.8-Flash-Next", "Qwen3.8 Flash"),
        ArmintelModel("DeepSeek-V4-Flash-Vision-Exp", "DeepSeek V4 Flash"),
        ArmintelModel("FastContext-1.0-4B-SFT", "FastContext 4B")
    )

    val MODELS: List<ArmintelModel> = ALL.filterNot { it.id in HIDDEN }
}

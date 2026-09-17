package com.eduappml.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eduappml.ThemeManager
import com.eduappml.data.models.ArmintelModel
import com.eduappml.managers.SessionManager
import com.eduappml.ui.common.Adaptive
import com.eduappml.ui.common.MarkdownText
import com.eduappml.ui.common.WaveBackground
import com.eduappml.voice.VoiceErrorLine
import com.eduappml.voice.VoiceModeButton
import com.eduappml.voice.VoicePanel
import com.eduappml.voice.VoiceSpeakerToggle
import com.eduappml.voice.VoiceState
import com.eduappml.voice.VoiceViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Экран чата «Edu.AI».
 *
 * Помимо переписки текстом умеет прикреплять файлы: картинки (галерея или
 * камера) и документы. Они уезжают на нашу Edge Function вместе с вопросом,
 * а оттуда — в GigaChat; ответ может прийти текстом, картинкой или и тем,
 * и другим. Подготовка файлов — ChatAttachments.kt, визуал вложений —
 * ChatAttachUi.kt, просмотр картинки во весь экран — ChatImageViewer.kt.
 *
 * Второй, независимый способ общения — голосовой режим на Yandex AI Studio
 * Realtime (кнопка с микрофоном в строке ввода). Он идёт не через Edge
 * Function, а по WebSocket в собственный релей (`server/voice-relay/`), см.
 * пакет `com.eduappml.voice`. Текстовый путь к GigaChat при этом не меняется:
 * выключенный голос не влияет на экран вообще никак.
 *
 * Третий — открытые модели (Gemma, Qwen, DeepSeek, FastContext) через
 * собственный шлюз (`server/llm-gateway/`). Вложений и картинок в ответе
 * у них нет, зато модель выбирается явно.
 *
 * Все шестеро собраны в одном выезжающем списке в шапке ([ModelMenu]):
 * пользователю незачем знать, что за одними стоит чужой сервис, а за другими
 * наш шлюз — он просто выбирает, кто ответит.
 */
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    prefillMessage: String? = null
) {
    val context = LocalContext.current
    val isDark = ThemeManager.isDarkThemeActive(context)
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(context))

    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val stage by viewModel.stage.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val attachError by viewModel.attachError.collectAsState()

    // Голосовой режим живёт в собственной ViewModel: он ничего не знает про
    // GigaChat, а ChatViewModel — про WebSocket. Связаны они только двумя
    // колбэками ниже, которые кладут расшифровки в общую ленту сообщений.
    val voiceViewModel: VoiceViewModel = viewModel()
    val voiceState by voiceViewModel.state.collectAsState()
    val voiceLiveText by voiceViewModel.liveText.collectAsState()
    val voiceError by voiceViewModel.error.collectAsState()
    val voiceMicActive by voiceViewModel.micActive.collectAsState()
    val voiceAwaiting by voiceViewModel.awaitingAnswer.collectAsState()
    val speakAnswers by voiceViewModel.speakAnswers.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val armintelModels by viewModel.armintelModels.collectAsState()
    val armintelModel by viewModel.armintelModel.collectAsState()

    // Список моделей тянем один раз при открытии экрана, а не при первом
    // заходе на Armintel: теперь модели стоят в общем меню рядом с GigaChat
    // и «Алисой», и их подписи нужны до того, как пользователь что-то выбрал.
    // Пока сервер не ответил, показываются встроенные ArmintelDefaults.
    LaunchedEffect(Unit) {
        viewModel.loadArmintelModels(runCatching { SessionManager.getToken() }.getOrNull())
    }

    // Что сейчас выбрано и что предложить в меню. Собирается заново только
    // когда пришёл новый список с сервера.
    val choices = remember(armintelModels) { buildChatChoices(armintelModels) }
    val current = remember(choices, engine, armintelModel) {
        choices.firstOrNull { it.matches(engine, armintelModel) } ?: choices.first()
    }

    // «Печатает»-пузырь поднимает голосовой путь: своего isLoading у него нет,
    // ждать нечего — но пользователю нужно видеть, что реплика принята.
    LaunchedEffect(voiceAwaiting) {
        viewModel.setVoiceThinking(voiceAwaiting)
    }

    LaunchedEffect(viewModel, voiceViewModel) {
        voiceViewModel.onUserSaid = { viewModel.appendVoiceMessage(it, isUser = true) }
        // Ответ пишется в ленту по мере того, как модель его произносит:
        // пузырь растёт параллельно озвучке, а не появляется целиком в конце.
        voiceViewModel.onAssistantDelta = { viewModel.appendVoiceStream(it) }
        voiceViewModel.onAssistantSaid = { viewModel.finishVoiceStream(it) }
        voiceViewModel.onAssistantAborted = { viewModel.dropVoiceStream() }
    }

    // Предзаполняем поле ввода, но НЕ отправляем автоматически — пользователь
    // должен увидеть готовый черновик вопроса и сам решить, отправлять его
    // как есть или поправить. См. HANDOFF_BRIEFING.md / обсуждение в чате.
    var input by remember { mutableStateOf(prefillMessage.orEmpty()) }
    var viewer by remember { mutableStateOf<ViewerTarget?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Ширина пузыря — от ширины экрана, а не фиксированные 280dp: на планшете
    // сообщения жались в узкую колонку слева, а на маленьком телефоне,
    // наоборот, упирались в край. Потолок — та же ContentMaxWidth, что и у
    // текстовых экранов уроков.
    val configuration = LocalConfiguration.current
    val bubbleMaxWidth = (configuration.screenWidthDp.dp * 0.82f)
        .coerceAtMost(Adaptive.ContentMaxWidth)
    val bubbleContentWidth = bubbleMaxWidth - 28.dp

    // ---------------------------------------------------------------------
    // Вложения временно отключены
    // ---------------------------------------------------------------------
    // Кнопка «+» убрана из строки ввода по решению владельца, вместе с ней —
    // системные пикеры и панель выбора источника. Сама механика цела и никуда
    // не делась: ChatAttachments.kt, ChatAttachUi.kt, ChatImageViewer.kt,
    // методы addAttachments/removeAttachment в ChatViewModel и <provider>
    // FileProvider в манифесте — всё на месте. Чтобы вернуть, нужно поднять
    // обратно три куска: пикеры здесь, AttachButton в строке ввода и
    // AttachSourcePanel над ней; см. историю git. Уже отправленные сообщения
    // с вложениями по-прежнему рисуются в ленте — за это отвечает ChatBubble.

    // ---------------------------------------------------------------------
    // Голосовой режим
    // ---------------------------------------------------------------------

    /**
     * Токен для релея. Пустая строка — не ошибка и не повод отказывать:
     * приложение не обновляет access-токен, так что через час после входа он
     * протухает у всех. Релей на этот случай принимает ключ приложения,
     * который клиент шлёт рядом с токеном.
     */
    fun voiceToken(): String = runCatching { SessionManager.getToken() }.getOrNull().orEmpty()

    fun beginListening() {
        voiceViewModel.toggleListening(voiceToken())
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginListening()
        else voiceViewModel.reportError("Без доступа к микрофону голосовой режим не работает")
    }

    /**
     * Микрофон одноразовый: нажатие слушает одну фразу и закрывается само,
     * как в голосовом вводе Google. Повторное нажатие во время записи её
     * отменяет — сессию при этом не рвём, разговор продолжается.
     */
    fun toggleVoice() {
        voiceViewModel.clearError()
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) beginListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Экран ушёл с переднего плана — микрофон надо отпустить, иначе он остаётся
    // занятым и следующее приложение его не получит.
    DisposableEffect(Unit) {
        onDispose { voiceViewModel.stop() }
    }

    // ---------------------------------------------------------------------

    fun trySend() {
        // Вопрос к «Яндексу» уходит в голосовую сессию: если её ещё нет, она
        // поднимется без микрофона — разрешение для печатного вопроса не
        // нужно, а ответ всё равно придёт голосом.
        if (engine == ChatEngine.YANDEX) {
            val text = input.trim()
            if (text.isEmpty()) return
            val token = voiceToken()
            input = ""
            viewModel.appendVoiceMessage(text, isUser = true)
            voiceViewModel.sendText(token, text)
            return
        }

        // Armintel — обычный HTTP-запрос в наш шлюз, но у него свой клиент и
        // свой метод: путь к GigaChat трогать незачем.
        if (engine == ChatEngine.ARMINTEL) {
            val text = input.trim()
            if (text.isEmpty() || isLoading) return
            input = ""
            val token = runCatching { SessionManager.getToken() }.getOrNull()
            scope.launch { viewModel.sendArmintelMessage(token, text) }
            return
        }

        val hasSomething = input.isNotBlank() || pending.isNotEmpty()
        if (hasSomething && !isLoading) {
            val text = input
            input = ""
            scope.launch { viewModel.sendMessage(text) }
        }
    }

    // Длина последнего сообщения — третий ключ наравне с их количеством:
    // «живой» ответ голосового режима растёт внутри одного пузыря, число
    // сообщений при этом не меняется, и без этого лента переставала следовать
    // за текстом ровно тогда, когда он и появляется.
    val lastMessageLength = messages.lastOrNull()?.text?.length ?: 0

    LaunchedEffect(messages.size, isLoading, lastMessageLength) {
        if (messages.isNotEmpty()) {
            // Пузырь «печатает» — отдельный элемент списка, и когда он показан,
            // последний индекс на единицу больше числа сообщений. Без этого
            // индикатор оказывался наполовину за нижней границей экрана.
            val lastIndex = messages.size - 1 + if (isLoading) 1 else 0
            listState.animateScrollToItem(lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!isDark) {
            WaveBackground(modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )

        // Отступ снизу — union() высоты клавиатуры и высоты навигационной
        // панели, применённый ОДИН раз на весь столбец разговора. Раньше
        // тут был отдельный imePadding() на Column И ОТДЕЛЬНЫЙ
        // navigationBarsPadding() на строке ввода ниже — а это разные типы
        // инсетов, Compose их не гасит друг другом. При открытой клавиатуре
        // высота навигационной панели прибавлялась ПОВЕРХ высоты клавиатуры —
        // это и был тот самый некрасивый "подскок" поля ввода над клавиатурой.
        // union() берёт max(ime, navigationBars), а не сумму — снизу всегда
        // ровно нужный отступ и ни каплей больше. Именно так это работает в
        // Telegram и подобных чатах.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            // Верхняя панель
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(12.dp))
                // weight(1f) на заголовке, а не Spacer между ним и
                // переключателем: на узком экране ужиматься должна подпись,
                // а не уезжать за край переключатель.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edu.AI",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Имя собеседника видно на кнопке справа, поэтому здесь
                    // повторять его незачем. У «Алисы» подпись всё же нужна:
                    // ответит она голосом или текстом, по кнопке не понять.
                    Text(
                        text = when {
                            engine == ChatEngine.YANDEX && speakAnswers -> "Отвечает голосом"
                            engine == ChatEngine.YANDEX -> "Отвечает текстом"
                            else -> "ИИ-помощник (бета)"
                        },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                // Озвучка принадлежит «Алисе» и работает одинаково для
                // голосовых и напечатанных вопросов.
                if (engine == ChatEngine.YANDEX) {
                    VoiceSpeakerToggle(
                        enabled = speakAnswers,
                        onToggle = { voiceViewModel.setSpeakAnswers(it) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                ModelMenu(
                    choices = choices,
                    current = current,
                    enabled = !isLoading,
                    onSelect = { chosen ->
                        if (chosen.matches(engine, armintelModel)) return@ModelMenu
                        // Голосовая сессия принадлежит «Алисе»; уходя с неё,
                        // закрываем её, иначе микрофон останется открытым, а
                        // ответы будут приходить мимо выбранного собеседника.
                        // «Печатает»-пузырь гасить руками не надо: stop()
                        // сбрасывает awaitingAnswer, а за ним и индикатор.
                        if (chosen.engine != ChatEngine.YANDEX) voiceViewModel.stop()
                        chosen.modelId?.let { viewModel.setArmintelModel(it) }
                        viewModel.setEngine(chosen.engine)
                    }
                )
            }

            // Список сообщений
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        maxWidth = bubbleMaxWidth,
                        contentWidth = bubbleContentWidth,
                        onOpenAttachment = { attachment ->
                            attachment.preview?.let { viewer = ViewerTarget(it, null) }
                        },
                        onOpenGenerated = { image ->
                            decodeSampled(image.bytes, 2048)?.let {
                                viewer = ViewerTarget(it, image.bytes)
                            }
                        }
                    )
                }
                if (isLoading) {
                    item(key = "typing") {
                        TypingBubble(label = stage.label.ifEmpty { "Edu.AI думает…" })
                    }
                }
            }

            // Полоса состояния — только когда открыт микрофон. Сессия без
            // микрофона (напечатанный вопрос к «Яндексу») идёт незаметно, там
            // о ходе дела рассказывает обычный «печатает»-пузырь.
            VoicePanel(
                state = if (voiceMicActive) voiceState else VoiceState.IDLE,
                liveText = voiceLiveText,
                onInterrupt = { voiceViewModel.interrupt() },
                onStop = { voiceViewModel.stop() }
            )

            VoiceErrorLine(message = voiceError)

            AttachErrorBanner(message = attachError, onDismiss = { viewModel.dismissAttachError() })

            // Поле ввода — отдельный нижний отступ здесь больше не нужен,
            // union(navigationBars, ime) уже применён один раз на Column выше
            // и сам решает, под клавиатуру сейчас подстраиваться или под
            // системную навигацию.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Микрофон принадлежит «Яндексу» — у GigaChat голоса нет,
                // и показывать неработающую кнопку незачем.
                if (engine == ChatEngine.YANDEX) {
                    VoiceModeButton(
                        active = voiceMicActive,
                        onClick = { toggleVoice() }
                    )
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp)),
                    placeholder = {
                        Text(
                            text = when {
                                voiceMicActive -> "Можно и напечатать — отвечу голосом"
                                engine == ChatEngine.YANDEX -> "Спросите голосом или текстом…"
                                else -> "Спросите что-нибудь..."
                            },
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.12f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                        focusedBorderColor = ChatAccent.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        cursorColor = ChatAccent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { trySend() }),
                    maxLines = 4
                )

                // Кнопка активна и при пустом тексте, если прикреплён файл:
                // «вот скриншот, разберись» — законный запрос, промпт по
                // умолчанию подставит ViewModel.
                val canSend = (input.isNotBlank() || pending.isNotEmpty()) && !isLoading
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) ChatAccent.copy(alpha = 0.85f)
                            else Color.White.copy(alpha = 0.15f)
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable(enabled = canSend) { trySend() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Отправить",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        viewer?.let { target ->
            ChatImageViewer(
                bitmap = target.bitmap,
                saveBytes = target.saveBytes,
                onClose = { viewer = null }
            )
        }
    }
}

/** Что сейчас открыто во весь экран. */
private class ViewerTarget(val bitmap: Bitmap, val saveBytes: ByteArray?)

// ---------------------------------------------------------------------------
// Выбор собеседника: один выезжающий список
// ---------------------------------------------------------------------------

/** Цвет подложки меню — тот же тёмно-синий, что у надписи на акцентной пилюле. */
private val MenuSurface = Color(0xFF1A1A2E)

/**
 * Пункт меню в шапке чата.
 *
 * Ассистенты (GigaChat, Алиса) и открытые модели лежат в одном списке
 * намеренно: пользователю всё равно, что за одними стоит чужой сервис, а за
 * другими наш шлюз — он выбирает, кто ответит. Поэтому [modelId] есть только
 * у моделей шлюза, а у ассистентов он null.
 *
 * Подписей под именами нет: одно имя в строке читается быстрее, а чем
 * модели отличаются, видно по ответам.
 */
private class ChatChoice(
    val engine: ChatEngine,
    val modelId: String?,
    val title: String
) {
    /** Этот ли пункт сейчас выбран. */
    fun matches(current: ChatEngine, currentModel: String): Boolean =
        engine == current && (modelId == null || modelId == currentModel)
}

/**
 * Собирает список: два ассистента и модели шлюза, пришедшие с сервера
 * (до ответа сервера — встроенные `ArmintelDefaults.MODELS`).
 *
 * Имена ассистентов берутся из `ChatEngine`, имена моделей — с сервера.
 * Разделителей и заголовков групп нет: для шести коротких строк они только
 * утяжеляют меню.
 */
private fun buildChatChoices(models: List<ArmintelModel>): List<ChatChoice> = buildList {
    add(ChatChoice(ChatEngine.GIGACHAT, null, ChatEngine.GIGACHAT.label))
    add(ChatChoice(ChatEngine.YANDEX, null, ChatEngine.YANDEX.label))
    models.forEach { model ->
        add(ChatChoice(ChatEngine.ARMINTEL, model.id, model.title))
    }
}

/**
 * Кнопка в шапке с именем текущего собеседника и выезжающее под ней окно.
 *
 * Собрано на [Popup], а не на `DropdownMenu`: меню Material3 рисуется
 * цветами темы — на тёмном стекле чата получается светлый прямоугольник
 * из другого приложения. Здесь та же стилистика, что у панели вложений:
 * полупрозрачное стекло, [GlassStroke], скругление 18dp, акцент [ChatAccent]
 * на выбранном пункте.
 *
 * Popup лежит внутри того же Box, что и кнопка, поэтому выравнивание
 * `TopEnd` со сдвигом на высоту кнопки ставит окно ровно под ней и прижимает
 * к правому краю — считать координаты вручную не приходится. Высота кнопки
 * приходит из `onSizeChanged`: она зависит от шрифта системы, и зашивать её
 * числом нельзя.
 */
@Composable
private fun ModelMenu(
    choices: List<ChatChoice>,
    current: ChatChoice,
    enabled: Boolean,
    onSelect: (ChatChoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableIntStateOf(0) }
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }

    Box {
        Row(
            modifier = Modifier
                .onSizeChanged { anchorHeight = it.height }
                .clip(RoundedCornerShape(20.dp))
                .background(GlassFill)
                .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = current.title,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Потолок ширины: длинное имя модели иначе выдавливает
                // заголовок «Edu.AI» за край на узком экране.
                modifier = Modifier.widthIn(max = 116.dp)
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Выбрать собеседника",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(22.dp)
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, anchorHeight + gap),
                onDismissRequest = { expanded = false },
                // focusable = true, иначе системная «назад» уходит мимо меню
                // и закрывает весь экран чата.
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 176.dp, max = 260.dp)
                        .shadow(16.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        // Подложка почти непрозрачная: под меню проезжает
                        // лента сообщений, и сквозь стекло имена читались бы
                        // поверх чужого текста.
                        .background(MenuSurface.copy(alpha = 0.97f))
                        .border(1.dp, GlassStroke, RoundedCornerShape(18.dp))
                        .padding(6.dp)
                ) {
                    choices.forEach { choice ->
                        val selected = choice === current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) ChatAccent.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    expanded = false
                                    onSelect(choice)
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = choice.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold
                                else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = ChatAccent,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Кнопка «скрепка»
// ---------------------------------------------------------------------------

@Composable
private fun AttachButton(
    expanded: Boolean,
    enabled: Boolean,
    badge: Int,
    onClick: () -> Unit
) {
    // Иконка «плюс» поворачивается в «крестик» — одна кнопка вместо двух и
    // сразу видно, что панель открыта.
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(180),
        label = "attachRotation"
    )
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (expanded) ChatAccent.copy(alpha = 0.35f)
                    else Color.White.copy(alpha = 0.12f)
                )
                .border(1.dp, GlassStroke, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = if (expanded) "Скрыть выбор файла" else "Прикрепить файл",
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(22.dp)
                    // 45° от крестика — это плюс; поворачиваем обратно, когда
                    // панель раскрыта, и получаем крестик.
                    .rotate(rotation + 45f)
            )
        }
        if (badge > 0 && !expanded) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(ChatAccent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge.toString(),
                    color = Color(0xFF1A1A2E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Пузырь сообщения
// ---------------------------------------------------------------------------

@Composable
private fun ChatBubble(
    message: ChatUiMessage,
    maxWidth: androidx.compose.ui.unit.Dp,
    contentWidth: androidx.compose.ui.unit.Dp,
    onOpenAttachment: (ChatUiAttachment) -> Unit,
    onOpenGenerated: (ChatUiImage) -> Unit
) {
    val bubbleColor = when {
        message.isUser -> ChatAccent.copy(alpha = 0.35f)
        message.isError -> Color(0xFFFF6B6B).copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val borderColor = if (message.isUser) ChatAccent.copy(alpha = 0.5f) else GlassStroke

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(RoundedCornerShape(18.dp))
                .background(bubbleColor)
                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (message.attachments.isNotEmpty()) {
                MessageAttachments(
                    attachments = message.attachments,
                    bubbleWidth = contentWidth,
                    onOpenImage = onOpenAttachment
                )
            }

            if (message.images.isNotEmpty()) {
                GeneratedImages(images = message.images, onOpen = onOpenGenerated)
            }

            if (message.text.isNotBlank()) {
                // MarkdownText вместо обычного Text — тот же рендерер, что и в
                // "Мат. основе" темы, поэтому формулы из ответа GigaChat
                // отображаются корректно. normalizeChatMath() приводит разные
                // обозначения LaTeX из ответа к единому блочному формату
                // (см. ChatMathFormat.kt).
                MarkdownText(
                    markdown = normalizeChatMath(message.text),
                    textColor = Color.White,
                    textSizeSp = 15f
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Камера
// ---------------------------------------------------------------------------

private fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"

/**
 * Готовит пустой файл в кэше и отдаёт на него content-URI: именно туда
 * системная камера положит снимок. Возвращает null, если FileProvider не
 * объявлен — кнопка камеры в этом случае и не показывается.
 */
private fun createCameraUri(context: Context): Uri? = runCatching {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
}.getOrNull()

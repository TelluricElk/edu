# Состояние проекта

**Проверено:** 15.09.2026, чтением реальной рабочей копии
`C:\Users\sasaa\yurovskiy\kozlov\EduAppML v.19` (не по снэпшоту репозитория).
Ниже — факты, а не пересказ документации.

---

## Что изменилось с 15.09 (дописано 16.09.2026)

**Первая вкладка переведена на ИБ полностью.** Группа `classic` — девять тем
(`lr`, `logr`, `knn`, `nb`, `svm`, `dt`, `rf`, `gb`, `km`), у каждой заменены
все четыре ассета в обеих ветках и переписаны `*Lab.kt`, `*Interactive.kt`,
`*Result.kt`. Диспетчеры `InteractiveScreen.kt` и `ResultScreen.kt` для этих
девяти id указывают уже на гражданские экраны, а не на военные.

**Расхождение у `knn` устранено.** `KnnInteractive.kt` и `KnnResult.kt`
написаны при переводе темы; гражданская пара теперь есть у всех 19 тем.

**Перекрёстных зависимостей между темами больше нет.** Были две — `gb` -> `lr`
и `rf` -> `dt`, обе расцеплены. Подробности в `20-RULES.md`.

**Военный слой пока не тронут.** Все `*Military.kt` и `assets/info/military/`
на месте и продолжают собираться; переведённые ассеты кладутся в обе ветки,
потому что `InfoRepository` сначала смотрит в военный путь. Удаление военного
слоя — финальный шаг после всех 19 тем.

**Вторая вкладка (10 тем нейросетей) не начата.**

---

## Голосовой режим Edu.AI (добавлено 16.09.2026)

В чат добавлен **второй, независимый** способ общения — голосовой, на
Yandex AI Studio Realtime. Текстовый путь к GigaChat (`functions/v1/chat`,
`ApiClient.authApi.sendChatMessage`) не изменён ни строкой.

**Клиент** — новый пакет `ui/../voice/` (`com.eduappml.voice`), шесть файлов:

- `VoiceConfig.kt` — адрес релея и частоты (16 кГц запись, 24 кГц воспроизведение);
- `VoiceState.kt` — `IDLE / CONNECTING / LISTENING / SPEAKING / ERROR`;
- `VoicePlayer.kt` — `AudioTrack` + очередь кусков, умеет мгновенный сброс (barge-in);
- `VoiceRelayClient.kt` — OkHttp WebSocket, `AudioRecord`, эхоподавитель;
- `VoiceViewModel.kt` — состояние для Compose, колбэки расшифровок;
- `VoiceUi.kt` — кнопка микрофона и полоса состояния в стиле чата.

**Правки в существующих файлах — только добавления:**

- `ChatViewModel.kt` — один новый метод `appendVoiceMessage(text, isUser)`;
- `ChatScreen.kt` — импорты, блок голосового режима, кнопка в строке ввода,
  `VoicePanel` над полем; в `trySend()` добавлена ветка «если голос активен»;
- `AndroidManifest.xml` — `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`,
  `uses-feature microphone required=false`.

**Сервер** — `server/voice-relay/` (Node + `ws`), отдельный процесс, не Edge
Function: Realtime — долгоживущий WebSocket, деноский сандбокс его рвёт, а ключ
Яндекса нельзя класть в APK. Релей проверяет Supabase-токен пользователя через
`/auth/v1/user`, держит `YANDEX_API_KEY` у себя и при необходимости
пересчитывает частоту дискретизации. Развёртывание — в `server/voice-relay/README.md`.

Протокол «приложение ↔ релей» намеренно свой (`audio` / `text` / `interrupt` /
`user_text` / `assistant_text_*`), а не события Яндекса: переименуют событие —
правка только в `server.js`, APK не пересобирается.

**Не сделано:** релей не развёрнут на сервере, `VoiceConfig.RELAY_URL` указывает
на `ws://157.22.206.53:8787/voice` и требует сверки после установки; сборка
Gradle после правок не запускалась.

---

## Третий движок чата — Armintel (добавлено 17.09.2026)

В шапке чата теперь три пилюли: **GigaChat**, **Яндекс**, **Armintel**.
Третий — текстовый чат с открытыми моделями `llm.armintel.ru` (Open WebUI,
OpenAI-совместимый API) через собственный шлюз `server/llm-gateway/`
(Node, порт 8788, systemd-юнит `eduappml-llm-gateway`). Ни путь к GigaChat,
ни голосовой путь не изменены.

**Клиент** — три новых файла (`data/models/LlmGatewayModels.kt`,
`network/LlmGatewayApi.kt`, `network/LlmGatewayClient.kt`) плюс добавления
в `ChatViewModel.kt` (`ChatEngine.ARMINTEL`, `loadArmintelModels`,
`setArmintelModel`, `sendArmintelMessage`) и `ChatScreen.kt` (ветка в
`trySend()`, выпадашка `ModelPicker` над строкой ввода, подпись в шапке,
ужатый `EngineSwitch`). `ChatModels.kt` и `ApiClient.kt` не тронуты.

**Список моделей приезжает с сервера** (`GET /models`), а не зашит в APK:
набор моделей на той стороне меняется, и правится он в `.env` шлюза.
Годных моделей мало — подробности и живые замеры в
`server/llm-gateway/README.md`, раздел «Какие модели годятся». Коротко:
всё, что `owned_by: ollama`, сейчас отдаёт 504; часть моделей пишет ход
рассуждений прямо в текст ответа. По умолчанию стоит `gemma-3-27b-it`.

**Развёрнут 17.09.2026.** `deploy.sh` отработал на 157.22.206.53:
`/opt/eduappml-llm-gateway`, systemd-юнит `eduappml-llm-gateway`, порт 8788,
автозапуск включён. `/health` отвечает `{"ok":true,"model":"gemma-3-27b-it",
"curated":4}`; проверочный запрос к llm.armintel.ru прошёл с кодом 200.
Обновление списка моделей потом — `server/llm-gateway/apply-models.sh`.

Грабля установки: `ssh host "bash deploy.sh"` запускается без терминала, и
`read` внутри скрипта мгновенно получает конец файла — скрипт падал на «без
токена шлюз не поднимется». Лечится `ssh -t` либо переменной окружения;
`deploy.sh` теперь принимает `ARMINTEL_API_KEY` / `SUPABASE_ANON_KEY` /
`ARMINTEL_MODEL` из окружения и в отсутствие терминала пишет обе команды
прямо в тексте ошибки.

**Не сделано:** сборка Gradle после правок не запускалась; живого запроса
из приложения ещё не было; доступность порта 8788 снаружи (ufw) не
проверялась — `deploy.sh` открывает его, только если ufw включён.

---

## Даты последних правок (по mtime рабочей копии)

- 28.08.2026 — `ui/chat/*` (вложения), `network/ApiClient.kt`,
  `data/models/ChatModels.kt`, `ВАЖНОЕ.txt`
- 26.08.2026 — `ui/common/Adaptive.kt`, `auth/*`, `SplashForeground.kt`,
  большой пласт `*Interactive.kt`, `BubbleGraph.kt`, глоссарии, `MainActivity.kt`
- 25.08.2026 — все `*Result*.kt`, `QuizSection.kt`, `GameManager.kt`
  (проброс `nodeId` и разблокировка соседей)
- 23.08.2026 — весь военный слой ассетов и `*Military.kt`
- 27.07.2026 — `HANDOFF_BRIEFING.md`; 26.07.2026 — `DEVELOPMENT_NOTES.md`

Отсюда: **оба канонических документа старше кода примерно на месяц.** Всё, что
сделано в августе (военный слой, механика прохождения, адаптивность, вложения
в чате), в `DEVELOPMENT_NOTES.md` не описано; частично описано в `AGENTS.md`.

## Темы: 19, контент на месте

Классика (9): `lr`, `logr`, `knn`, `nb`, `svm`, `dt`, `rf`, `gb`, `km`
Нейросети (10): `fc`, `som`, `rl`, `ae`, `gan`, `cnn`, `rnn`, `gnn`, `tr`, `dm`

Ассеты: `assets/info/classic/{id}/` — 19 папок × 4 файла.
`assets/info/military/classic/{id}/` — **полное зеркало**, 19 папок × 4 файла.
Расхождений в составе файлов нет.

## Расхождение в коде: у `knn` нет гражданских экранов

`ui/knn/` содержит только:
`KnnLab.kt`, `KnnLabMilitary.kt`, `KnnInteractiveMilitary.kt`, `KnnResultMilitary.kt`.

Отсутствуют `KnnInteractive.kt` и `KnnResult.kt` — **единственная тема из 19**,
где гражданской пары нет. У остальных 18 обе версии на месте, включая `KnnLab.kt`.
Пока гражданский режим отключён, на работу приложения это не влияет; при попытке
вернуть гражданскую версию `knn` отвалится. Вопрос вынесен в `40-QUESTIONS.md`.

## Военный слой шире, чем описано в `AGENTS.md`

`AGENTS.md` упоминает только `*ResultMilitary.kt`. Фактически военные варианты
есть у трёх слоёв кода: `*Lab.kt`, `*Interactive.kt`, `*Result.kt` — плюс
отдельная ветка ассетов. Правило «править оба варианта» касается всех трёх.

## Чат: появились вложения

`ui/chat/` вырос до: `ChatScreen.kt`, `ChatViewModel.kt`, `ChatViewModelFactory.kt`,
`ChatMathFormat.kt`, **`ChatAttachments.kt`**, **`ChatAttachUi.kt`**,
**`ChatImageViewer.kt`** (все три — 28.08.2026). Ни `DEVELOPMENT_NOTES.md`, ни
`AGENTS.md` про вложения ничего не говорят; в Проекте есть отдельный документ
`claude/chat-attachments.md`. Перед правками в чате читать его.

## `assets/datasets/` пуста

Папка существует, файлов внутри нет. Либо задел под «загрузку своих данных»
из списка идей, либо остаток. Вопрос в `40-QUESTIONS.md`.

## Что НЕ проверялось на 15.09.2026

Проверялись состав файлов и даты, не содержимое. Ещё не сверялось:

- 9 разделов `###` в каждом `general.ru.md` (обеих веток);
- ровно 4 вопроса в каждом `*Result*.kt` (38 файлов);
- LaTeX-чистота 38 `math.ru.md` (военная ветка с 23.08 вообще не проверялась
  скриптом из `DEVELOPMENT_NOTES.md` §3);
- подключение всех 19 id в `InteractiveScreen.kt` и `ResultScreen.kt`;
- достижимость всех узлов из стартового в `defaultEdges()` / `thirdEdges()`.

Это содержание задачи «Аудит текущего состояния» в `30-BACKLOG.md`.

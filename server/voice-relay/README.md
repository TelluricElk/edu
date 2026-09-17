# voice-relay — голосовой режим Edu.AI на Yandex AI Studio Realtime

Отдельный Node-сервис. Держит WebSocket с `wss://ai.api.cloud.yandex.net/v1/realtime`
и проксирует аудио в приложение. Ничего из существующего контура (Supabase,
Edge Function `functions/v1/chat`, GigaChat) не трогает — текстовый чат
продолжает работать как раньше.

```
Android ──ws──> voice-relay :8787/voice ──wss──> Yandex Realtime API
                     │
                     └── GoTrue /auth/v1/user (проверка токена пользователя)
```

## Почему не Edge Function

Realtime — долгоживущее WS-соединение с непрерывным потоком аудио. Deno-сандбокс
Supabase Edge Functions рассчитан на короткие HTTP-запросы и рвёт такие
соединения. Плюс ключ Yandex Cloud не должен попадать в APK.

## Установка

```bash
sudo mkdir -p /opt/eduappml-voice-relay
sudo chown "$USER" /opt/eduappml-voice-relay
cp server.js package.json /opt/eduappml-voice-relay/
cd /opt/eduappml-voice-relay
npm install --omit=dev

cp /path/to/.env.example .env
nano .env        # заполнить YANDEX_API_KEY, YANDEX_FOLDER_ID, SUPABASE_ANON_KEY

sudo cp eduappml-voice-relay.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now eduappml-voice-relay
sudo systemctl status eduappml-voice-relay
journalctl -u eduappml-voice-relay -f
```

Проверка, что процесс жив:

```bash
curl -s http://127.0.0.1:8787/health
# {"ok":true,"active":0,"max":8}
```

## Что положить в .env

| Переменная | Что это |
|---|---|
| `YANDEX_API_KEY` | секретный ключ сервисного аккаунта Yandex Cloud с ролью `ai.languageModels.user` |
| `YANDEX_FOLDER_ID` | идентификатор каталога (`b1g...`), из него собирается `gpt://<folder>/<model>` |
| `YANDEX_VOICE` | голос синтеза (`marina`, `dasha`, `alena`, …), нужен только при `OUTPUT_MODE=audio` |
| `OUTPUT_MODE` | `audio` — отвечает синтезированной речью (по умолчанию); `text` — только текстом |
| `SUPABASE_URL` | адрес Supabase; релей проверяет токен пользователя через `/auth/v1/user` |
| `SUPABASE_ANON_KEY` | тот же `ANON_KEY`, что в `.env` Supabase |
| `VOICE_RELAY_KEY` | опционально, статический ключ для отладки через `wscat`; в проде пусто |
| `ALLOW_APP_KEY` | `1` — пускать по `ANON_KEY`, когда токен пользователя протух (см. ниже) |

## Про авторизацию и протухшие токены

Релей проверяет вход в два шага: сначала токен пользователя через
`/auth/v1/user`, и если он не принят — ключ приложения (`ANON_KEY`).

Второй шаг нужен из-за того, как устроено приложение: оно сохраняет
access-токен при входе и **никогда его не обновляет**, а живёт тот час.
Остальной API этого не замечает, потому что интерцептор в `ApiClient` всё равно
подменяет `Authorization` на `ANON_KEY` в каждом запросе. Голосовой режим — 
единственное место, где токен пользователя реально используется, поэтому без
запасного пути он отваливался у любого, кто вошёл больше часа назад, с
сообщением «сессия устарела».

То есть защита релея сейчас ровно такая же, как у остального бэкенда: ключ,
зашитый в APK. Настоящее лечение — refresh-токены в приложении; после этого
можно поставить `ALLOW_APP_KEY=0` и вернуть проверку по пользователю.

## Доступ снаружи

Порт 8787 наружу лучше не открывать напрямую. Если перед Supabase уже стоит
nginx, добавьте туда блок (WebSocket требует апгрейда соединения):

```nginx
location /voice {
    proxy_pass http://127.0.0.1:8787/voice;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
}
```

Тогда в приложении (`VoiceConfig.kt`) достаточно оставить
`ws://157.22.206.53/voice` — или `wss://…`, если на nginx поднят TLS.

Без nginx откройте порт точечно:

```bash
sudo ufw allow 8787/tcp
```

и в `VoiceConfig.kt` укажите `ws://157.22.206.53:8787/voice`.

## Протокол «приложение ↔ релей»

Намеренно не повторяет события Яндекса: если Яндекс переименует событие,
правка нужна только в `server.js`, приложение не трогаем.

**Приложение → релей**

| Сообщение | Смысл |
|---|---|
| `{"type":"audio","audio":"<base64 PCM16 mono 16 кГц>"}` | кусок звука с микрофона |
| `{"type":"text","text":"…"}` | напечатанный вопрос |
| `{"type":"commit"}` | закрыть фразу, не дожидаясь детектора речи |
| `{"type":"set_audio","enabled":true}` | включить/выключить озвучку на лету |
| `{"type":"cancel"}` | прервать текущий ответ |
| `{"type":"ping"}` | keepalive |

**Релей → приложение**

| Сообщение | Смысл |
|---|---|
| `{"type":"ready","audio":false}` | сессия готова; `audio` — будет ли синтез |
| `{"type":"audio_mode","audio":true}` | озвучку переключили |
| `{"type":"speech_started"}` | пользователь заговорил |
| `{"type":"speech_stopped"}` | фраза закончилась — приложение закрывает микрофон |
| `{"type":"user_text","text":"…"}` | расшифровка реплики пользователя |
| `{"type":"assistant_text_delta","text":"…"}` | кусок ответа текстом |
| `{"type":"assistant_text_done","text":"…"}` | ответ дописан |
| `{"type":"audio","audio":"<base64 PCM16 mono 24 кГц>"}` | кусок речи, только при `audio:true` |
| `{"type":"turn_done"}` | реплика ассистента закончилась |
| `{"type":"error","message":"…"}` | ошибка |

Озвучку приложение просит в адресе (`?audio=1|0`) и меняет на лету через
`set_audio`; `OUTPUT_MODE` — только значение по умолчанию.

## Почему в приложении одноразовый микрофон

Микрофон держится ровно одну фразу и закрывается по `speech_stopped` — как
голосовой ввод в поиске Google. Это не упрощение: при включённой озвучке
постоянно открытый микрофон ловит собственный динамик, детектор речи считает
его репликой пользователя, и модель начинает отвечать сама себе.

Вторая половина той же проблемы решается здесь, на сервере: Яндекс присылает
текст ответа ДВУМЯ дорожками сразу — расшифровку синтеза
(`response.output_audio_transcript.*`) и текстовую модальность
(`response.output_text.*`). Содержимое одинаковое, поэтому релей фиксируется на
той, что пришла первой, и до конца ответа игнорирует вторую. Без этого в ленте
появлялось по два одинаковых ответа на каждый вопрос.

Авторизация — заголовок `Authorization: Bearer <supabase access token>`
(то же, что `SessionManager.getToken()`). Отдельного секрета в APK нет.

## Если Яндекс ругается на частоту дискретизации

Приложение всегда пишет 16 кГц и играет 24 кГц. Если Realtime API откажется
принимать такие значения — в `.env` поставьте `YANDEX_IN_RATE=44100` и
`YANDEX_OUT_RATE=44100`. Релей пересчитает поток сам, APK пересобирать не нужно.

## Диагностика

```bash
# что реально приходит от Яндекса
sudo systemctl stop eduappml-voice-relay
cd /opt/eduappml-voice-relay && LOG_LEVEL=debug node server.js

# проверить сам релей без приложения (нужен VOICE_RELAY_KEY в .env)
npm i -g wscat
wscat -c "ws://127.0.0.1:8787/voice" -H "Authorization: Bearer <VOICE_RELAY_KEY>"
```

| Симптом | Причина |
|---|---|
| `HTTP 401` при подключении | токен пользователя протух — перелогиниться в приложении |
| `HTTP 503` | упёрлись в `MAX_CONCURRENT` |
| `Яндекс отклонил соединение (HTTP 401)` | неверный `YANDEX_API_KEY` или у сервисного аккаунта нет роли |
| `Яндекс отклонил соединение (HTTP 404)` | неверный `YANDEX_FOLDER_ID` или `YANDEX_MODEL` |
| тишина в динамике, текст идёт | несовпадение частот — см. раздел выше |

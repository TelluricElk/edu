'use strict';

/**
 * EduAppML — voice relay для Yandex AI Studio Realtime API.
 *
 * Зачем нужен отдельный сервис, а не Supabase Edge Function:
 *   1. Realtime API — это долгоживущий WebSocket с двусторонним потоком аудио.
 *      Deno-сандбокс Edge Functions рассчитан на короткие HTTP-запросы и рвёт
 *      такие соединения по таймауту.
 *   2. Ключ Yandex Cloud нельзя класть в APK — его тривиально вытащить.
 *      Ключ живёт только здесь, в .env рядом с этим файлом.
 *
 * Схема:
 *   Android  --ws-->  этот релей  --wss-->  ai.api.cloud.yandex.net/v1/realtime
 *
 * Авторизация приложения: заголовок `Authorization: Bearer <supabase access token>`
 * (тот же токен, что SessionManager.getToken() хранит после логина). Релей
 * проверяет его через GoTrue /auth/v1/user. Отдельного секрета в APK не заводим.
 *
 * Протокол между приложением и релеем НАМЕРЕННО упрощён и не повторяет события
 * Яндекса: если Яндекс переименует событие, правка будет только здесь.
 *
 *   app -> relay
 *     {"type":"audio","audio":"<base64 PCM16 mono @ CLIENT_IN_RATE>"}
 *     {"type":"text","text":"..."}             // напечатанный вопрос
 *     {"type":"commit"}                        // закрыть фразу, не дожидаясь VAD
 *     {"type":"set_audio","enabled":true}      // включить/выключить озвучку на лету
 *     {"type":"cancel"}                        // прервать текущий ответ
 *     {"type":"ping"}
 *
 *   relay -> app
 *     {"type":"ready","audio":false}           // сессия готова; audio — будет ли озвучка
 *     {"type":"audio_mode","audio":true}       // озвучку переключили
 *     {"type":"speech_started"}                // пользователь заговорил
 *     {"type":"speech_stopped"}                // фраза закончилась — закрываем микрофон
 *     {"type":"user_text","text":"..."}        // распознанная реплика пользователя
 *     {"type":"assistant_text_delta","text":"..."}
 *     {"type":"assistant_text_done","text":"..."}
 *     {"type":"audio","audio":"<base64 PCM16 mono @ CLIENT_OUT_RATE>"}   // только при audio=true
 *     {"type":"turn_done"}                     // ассистент договорил
 *     {"type":"error","message":"..."}
 *     {"type":"pong"}
 *
 * Озвучка: значение по умолчанию берётся из OUTPUT_MODE, приложение может
 * задать своё в адресе (?audio=1|0) и переключить посреди сессии (`set_audio`).
 * Пересобирать APK ради этого не нужно.
 */

require('dotenv').config();

const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');

// ---------------------------------------------------------------- конфигурация

const cfg = {
  port: int(process.env.PORT, 8787),
  host: process.env.HOST || '0.0.0.0',
  path: process.env.VOICE_PATH || '/voice',

  yandexApiKey: process.env.YANDEX_API_KEY || '',
  yandexFolderId: process.env.YANDEX_FOLDER_ID || '',
  yandexModel: process.env.YANDEX_MODEL || 'speech-realtime-250923',
  yandexVoice: process.env.YANDEX_VOICE || 'marina',
  yandexUrlBase: process.env.YANDEX_URL || 'wss://ai.api.cloud.yandex.net/v1/realtime',

  // 'audio' — ассистент отвечает синтезированной речью (по умолчанию);
  // 'text'  — слушает голосом, но отвечает только текстом в ленту чата.
  // Приложение узнаёт режим из события `ready` и само решает, поднимать ли
  // динамик, поэтому переключение не требует пересборки APK.
  outputMode: (process.env.OUTPUT_MODE || 'audio').toLowerCase() === 'text' ? 'text' : 'audio',

  instructions: process.env.VOICE_INSTRUCTIONS || DEFAULT_INSTRUCTIONS(),

  supabaseUrl: stripSlash(process.env.SUPABASE_URL || ''),
  supabaseAnonKey: process.env.SUPABASE_ANON_KEY || '',
  // Запасной статический ключ — только для отладки с wscat/curl.
  // В проде оставляйте пустым, тогда пускает исключительно Supabase-токен.
  staticKey: process.env.VOICE_RELAY_KEY || '',
  // Пускать по ключу приложения (ANON_KEY), когда токен пользователя протух.
  // Выключить можно будет, когда в приложении появится обновление токенов.
  allowAppKey: String(process.env.ALLOW_APP_KEY || '1') !== '0',

  // Частоты. Клиент всегда работает на client*, релей при необходимости
  // пересчитывает поток в yandex*. Если Яндекс не примет 16 кГц/24 кГц —
  // достаточно выставить YANDEX_IN_RATE=44100 / YANDEX_OUT_RATE=44100,
  // приложение переписывать не придётся.
  // Детектор речи. Значения подобраны под поведение голосового ввода в поиске
  // Google: чуть выше порог, чтобы шум и дыхание не считались речью, и заметно
  // длиннее пауза — человек в середине фразы задумывается на полсекунды, и при
  // 400 мс его обрывали на полуслове.
  vadThreshold: Number(process.env.VAD_THRESHOLD || 0.6),
  vadSilenceMs: int(process.env.VAD_SILENCE_MS, 1200),
  vadPrefixMs: int(process.env.VAD_PREFIX_MS, 300),

  clientInRate: int(process.env.CLIENT_IN_RATE, 16000),
  clientOutRate: int(process.env.CLIENT_OUT_RATE, 24000),
  yandexInRate: int(process.env.YANDEX_IN_RATE, 0),
  yandexOutRate: int(process.env.YANDEX_OUT_RATE, 0),

  maxSessionMs: int(process.env.MAX_SESSION_MS, 10 * 60 * 1000),
  maxConcurrent: int(process.env.MAX_CONCURRENT, 8),
  idleTimeoutMs: int(process.env.IDLE_TIMEOUT_MS, 90 * 1000),

  logLevel: (process.env.LOG_LEVEL || 'info').toLowerCase(),
};

cfg.yandexInRate = cfg.yandexInRate || cfg.clientInRate;
cfg.yandexOutRate = cfg.yandexOutRate || cfg.clientOutRate;

function DEFAULT_INSTRUCTIONS() {
  return [
    'Ты — Edu.AI, голосовой помощник учебного приложения по машинному обучению.',
    'Отвечай по-русски, коротко и по делу: это разговор вслух, а не статья.',
    'Держись тем курса: классические алгоритмы (линейная и логистическая регрессия,',
    'kNN, наивный Байес, SVM, деревья, случайный лес, градиентный бустинг, k-means)',
    'и нейросети (полносвязные, SOM, обучение с подкреплением, автокодировщики, GAN,',
    'CNN, RNN, GNN, трансформеры, диффузионные модели).',
    'Формулы проговаривай словами, не диктуй LaTeX.',
    'Если вопрос не про учёбу — мягко возвращай к теме.',
  ].join(' ');
}

for (const required of ['yandexApiKey', 'yandexFolderId']) {
  if (!cfg[required]) {
    console.error(`[fatal] не задан ${required === 'yandexApiKey' ? 'YANDEX_API_KEY' : 'YANDEX_FOLDER_ID'} в .env`);
    process.exit(1);
  }
}
if (!cfg.supabaseUrl && !cfg.staticKey) {
  console.error('[fatal] не задан ни SUPABASE_URL (проверка токена), ни VOICE_RELAY_KEY');
  process.exit(1);
}

// ------------------------------------------------------------------ утилиты

function int(v, def) {
  const n = parseInt(v, 10);
  return Number.isFinite(n) ? n : def;
}

function stripSlash(s) {
  return s.replace(/\/+$/, '');
}

const LEVELS = { error: 0, warn: 1, info: 2, debug: 3 };
function log(level, ...args) {
  if ((LEVELS[level] ?? 2) <= (LEVELS[cfg.logLevel] ?? 2)) {
    console.log(`[${new Date().toISOString()}] [${level}]`, ...args);
  }
}

/**
 * Линейная передискретизация PCM16 mono.
 * Нужна редко (когда частоты клиента и Яндекса различаются), поэтому
 * намеренно простая: качество речи на таких соотношениях не страдает.
 */
function resamplePcm16(buf, fromRate, toRate) {
  if (fromRate === toRate || buf.length < 2) return buf;
  const src = new Int16Array(buf.buffer, buf.byteOffset, Math.floor(buf.length / 2));
  const ratio = toRate / fromRate;
  const outLen = Math.max(1, Math.floor(src.length * ratio));
  const out = new Int16Array(outLen);
  for (let i = 0; i < outLen; i++) {
    const pos = i / ratio;
    const i0 = Math.floor(pos);
    const i1 = Math.min(i0 + 1, src.length - 1);
    const frac = pos - i0;
    out[i] = (src[i0] * (1 - frac) + src[i1] * frac) | 0;
  }
  return Buffer.from(out.buffer, out.byteOffset, out.byteLength);
}

// ------------------------------------------------------- проверка токена

const tokenCache = new Map(); // token -> { userId, until }
const TOKEN_TTL_MS = 60 * 1000;

/**
 * Кого пускаем.
 *
 * Порядок проверок намеренно такой:
 *   1. токен пользователя — если он жив, знаем, кто именно говорит;
 *   2. ключ приложения (тот же ANON_KEY, с которым ходит весь остальной API).
 *
 * Второй пункт — не послабление, а приведение к тому, как устроен остальной
 * бэкенд. Приложение сохраняет access-токен при входе и НИКОГДА его не
 * обновляет (refresh-флоу в проекте нет), а живёт он час; остальные запросы
 * этого не замечают, потому что интерцептор в ApiClient всё равно подменяет
 * Authorization на ANON_KEY. Без запасного пути голосовой режим отваливался у
 * любого, кто вошёл больше часа назад, с бессмысленным «войдите заново».
 *
 * Настоящее лечение — refresh-токены в приложении; тогда второй пункт можно
 * будет выключить (ALLOW_APP_KEY=0).
 */
async function authorize(req) {
  const header = req.headers['authorization'] || '';
  const url = new URL(req.url, 'http://localhost');
  let token = '';
  if (/^Bearer\s+/i.test(header)) token = header.replace(/^Bearer\s+/i, '').trim();
  if (!token) token = (url.searchParams.get('token') || '').trim();

  const apikey = String(req.headers['apikey'] || url.searchParams.get('apikey') || '').trim();

  if (cfg.staticKey && token === cfg.staticKey) return { userId: 'static-key' };

  // --- 1. токен пользователя
  if (token && cfg.supabaseUrl) {
    const cached = tokenCache.get(token);
    if (cached && cached.until > Date.now()) return { userId: cached.userId };

    try {
      const res = await fetch(`${cfg.supabaseUrl}/auth/v1/user`, {
        headers: {
          Authorization: `Bearer ${token}`,
          apikey: cfg.supabaseAnonKey,
        },
      });
      if (res.ok) {
        const user = await res.json();
        const userId = user && user.id ? String(user.id) : 'unknown';
        tokenCache.set(token, { userId, until: Date.now() + TOKEN_TTL_MS });
        return { userId };
      }
      log('info', `auth: GoTrue отклонил токен пользователя (${res.status}), пробую ключ приложения`);
    } catch (e) {
      // Сюда попадаем, если SUPABASE_URL недоступен с этой машины, — частый
      // случай, когда Kong не опубликован на 127.0.0.1:8000.
      log('warn', `auth: не достучался до ${cfg.supabaseUrl}/auth/v1/user: ${e.message}`);
    }
  }

  // --- 2. ключ приложения
  if (cfg.allowAppKey && cfg.supabaseAnonKey) {
    if (apikey === cfg.supabaseAnonKey || token === cfg.supabaseAnonKey) {
      return { userId: 'app-key' };
    }
  }

  log(
    'warn',
    `auth: отказ (токен ${token ? 'есть' : 'нет'}, apikey ${apikey ? 'есть' : 'нет'}, ` +
      `ключ приложения ${cfg.allowAppKey ? 'разрешён' : 'выключен'})`
  );
  return null;
}

// ------------------------------------------------------------------ сервер

let active = 0;

const httpServer = http.createServer((req, res) => {
  if (req.url === '/health' || req.url === '/healthz') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ ok: true, active, max: cfg.maxConcurrent }));
    return;
  }
  res.writeHead(404);
  res.end();
});

const wss = new WebSocketServer({ noServer: true });

httpServer.on('upgrade', async (req, socket, head) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname !== cfg.path) {
    socket.destroy();
    return;
  }
  if (active >= cfg.maxConcurrent) {
    socket.write('HTTP/1.1 503 Service Unavailable\r\n\r\n');
    socket.destroy();
    return;
  }
  const auth = await authorize(req);
  if (!auth) {
    socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
    socket.destroy();
    return;
  }
  wss.handleUpgrade(req, socket, head, (ws) => bridge(ws, auth, req.url || '/'));
});

// Две дорожки текста ответа у Яндекса. Раскладываем по семействам, чтобы
// пересылать приложению ровно одну (см. обработчик upstream-сообщений).
const TEXT_DELTA = {
  'response.output_audio_transcript.delta': 'transcript',
  'response.audio_transcript.delta': 'transcript',
  'response.output_text.delta': 'text',
  'response.text.delta': 'text',
};
const TEXT_DONE = {
  'response.output_audio_transcript.done': 'transcript',
  'response.audio_transcript.done': 'transcript',
  'response.output_text.done': 'text',
  'response.text.done': 'text',
};

// --------------------------------------------------------------- одна сессия

function bridge(client, auth, reqUrl) {
  active++;
  const tag = `${auth.userId.slice(0, 8)}#${Date.now().toString(36)}`;
  log('info', `[${tag}] сессия открыта (active=${active})`);

  const upstreamUrl =
    `${cfg.yandexUrlBase}?model=gpt://${cfg.yandexFolderId}/${cfg.yandexModel}`;

  const upstream = new WebSocket(upstreamUrl, {
    headers: { Authorization: `Api-Key ${cfg.yandexApiKey}` },
  });

  let closed = false;
  let lastActivity = Date.now();
  const queue = []; // кадры от клиента до готовности upstream

  // В текстовом режиме звук ассистента не нужен. Если Realtime откажется
  // принимать output_modalities: ["text"], пробуем ещё раз с аудио и просто
  // выбрасываем звуковые кадры — снаружи поведение не меняется.
  // Приложение может попросить свой режим озвучки прямо в адресе (?audio=1|0)
  // и переключить его посреди сессии сообщением `set_audio`. Переменная
  // OUTPUT_MODE остаётся значением по умолчанию.
  let wantAudioOut = cfg.outputMode === 'audio';
  const audioParam = new URL(`http://x${reqUrl}`).searchParams.get('audio');
  if (audioParam === '1' || audioParam === 'true') wantAudioOut = true;
  if (audioParam === '0' || audioParam === 'false') wantAudioOut = false;

  let modalityFallbackUsed = false;

  // Счётчики в пределах одного ответа — против дублей (см. обработчик ниже).
  let textStream = null;
  let textDoneSent = false;
  let turnDoneSent = false;

  const hardStop = setTimeout(() => {
    log('warn', `[${tag}] достигнут лимит длительности сессии`);
    toClient({ type: 'error', message: 'Лимит времени голосовой сессии исчерпан' });
    shutdown(1000, 'session limit');
  }, cfg.maxSessionMs);

  const idleTimer = setInterval(() => {
    if (Date.now() - lastActivity > cfg.idleTimeoutMs) {
      log('info', `[${tag}] простой, закрываю`);
      shutdown(1000, 'idle');
    }
  }, 10000);

  function toClient(obj) {
    if (client.readyState === WebSocket.OPEN) {
      client.send(JSON.stringify(obj));
    }
  }

  function toUpstream(obj) {
    const payload = JSON.stringify(obj);
    if (upstream.readyState === WebSocket.OPEN) upstream.send(payload);
    else if (upstream.readyState === WebSocket.CONNECTING) queue.push(payload);
  }

  function shutdown(code, reason) {
    if (closed) return;
    closed = true;
    clearTimeout(hardStop);
    clearInterval(idleTimer);
    active = Math.max(0, active - 1);
    try { if (upstream.readyState <= 1) upstream.close(code, reason); } catch (_) {}
    try { if (client.readyState <= 1) client.close(code, reason); } catch (_) {}
    log('info', `[${tag}] сессия закрыта: ${reason} (active=${active})`);
  }

  // ---- upstream -> app

  function sessionUpdate(withAudioOut) {
    const session = {
      instructions: cfg.instructions,
      output_modalities: withAudioOut ? ['audio'] : ['text'],
      audio: {
        input: {
          format: { type: 'audio/pcm', rate: cfg.yandexInRate },
          turn_detection: {
            type: 'server_vad',
            threshold: cfg.vadThreshold,
            silence_duration_ms: cfg.vadSilenceMs,
            prefix_padding_ms: cfg.vadPrefixMs,
          },
        },
      },
    };
    if (withAudioOut) {
      session.audio.output = {
        format: { type: 'audio/pcm', rate: cfg.yandexOutRate },
        voice: cfg.yandexVoice,
      };
    }
    toUpstream({ type: 'session.update', session });
  }

  upstream.on('open', () => {
    sessionUpdate(wantAudioOut);
    while (queue.length) upstream.send(queue.shift());
    // Приложение поднимает динамик только когда audio === true.
    toClient({ type: 'ready', audio: wantAudioOut });
    log(
      'debug',
      `[${tag}] upstream открыт, session.update отправлен (режим ${cfg.outputMode})`
    );
  });

  upstream.on('message', (raw) => {
    lastActivity = Date.now();
    let ev;
    try {
      ev = JSON.parse(raw.toString());
    } catch (_) {
      return;
    }
    const t = ev.type || '';

    // Новый ответ — сбрасываем всё, что считается в пределах одного ответа.
    if (t === 'response.created') {
      textStream = null;
      textDoneSent = false;
      turnDoneSent = false;
      return;
    }

    // Аудио ответа. Имена события у Яндекса менялись — принимаем оба варианта.
    if (t === 'response.output_audio.delta' || t === 'response.audio.delta') {
      // В текстовом режиме звук до приложения не доходит, даже если Яндекс
      // его всё-таки прислал.
      if (!wantAudioOut) return;
      const pcm = Buffer.from(ev.delta || ev.audio || '', 'base64');
      if (!pcm.length) return;
      const out = resamplePcm16(pcm, cfg.yandexOutRate, cfg.clientOutRate);
      toClient({ type: 'audio', audio: out.toString('base64') });
      return;
    }
    if (t === 'response.output_audio.done' || t === 'response.audio.done') {
      return; // конец ответа объявляет response.done, иначе turn_done придёт дважды
    }

    // Текст ответа. Яндекс присылает ДВЕ дорожки сразу: расшифровку синтеза
    // (`*audio_transcript*`) и текстовую модальность (`*text*`). Содержимое у
    // них одинаковое, и если пересылать обе, в ленте появляются два ответа на
    // один вопрос. Поэтому фиксируемся на той, что пришла первой, и до конца
    // ответа игнорируем вторую.
    const textFamily = TEXT_DELTA[t] || TEXT_DONE[t] || null;
    if (textFamily) {
      if (textStream === null) {
        textStream = textFamily;
        log('debug', `[${tag}] текстовая дорожка ответа: ${textFamily}`);
      }
      if (textFamily !== textStream) return;

      if (TEXT_DELTA[t]) {
        const piece = ev.delta || ev.text || '';
        if (piece) toClient({ type: 'assistant_text_delta', text: piece });
      } else if (!textDoneSent) {
        textDoneSent = true;
        toClient({ type: 'assistant_text_done', text: ev.transcript || ev.text || '' });
      }
      return;
    }

    // Пользователь заговорил. Приложение глушит динамик (barge-in) и
    // сбрасывает таймер «не слышу речи».
    if (t === 'input_audio_buffer.speech_started') {
      toClient({ type: 'speech_started' });
      return;
    }

    // Фраза закончилась — приложение закрывает микрофон, как это делает
    // голосовой ввод в поиске Google. Держать микрофон открытым между
    // репликами нельзя: динамик попадает обратно в микрофон, модель слышит
    // сама себя и отвечает снова и снова.
    if (
      t === 'input_audio_buffer.speech_stopped' ||
      t === 'input_audio_buffer.committed'
    ) {
      toClient({ type: 'speech_stopped' });
      return;
    }

    // Распознанная реплика пользователя.
    if (t === 'conversation.item.input_audio_transcription.completed') {
      const text = ev.transcript || '';
      if (text) toClient({ type: 'user_text', text });
      return;
    }

    if (t === 'response.done') {
      if (!turnDoneSent) {
        turnDoneSent = true;
        toClient({ type: 'turn_done' });
      }
      return;
    }

    if (t === 'error') {
      const msg =
        (ev.error && (ev.error.message || ev.error.code)) || ev.message || 'ошибка Realtime API';

      // Единственная ошибка, которую чиним сами: модель не приняла текстовую
      // модальность. Переспрашиваем сессию с аудио и продолжаем выбрасывать
      // звук — для приложения ничего не меняется.
      if (!wantAudioOut && !modalityFallbackUsed && /modalit/i.test(String(msg))) {
        modalityFallbackUsed = true;
        log('warn', `[${tag}] текстовая модальность не принята, переключаюсь на аудио с отбросом`);
        sessionUpdate(true);
        return;
      }

      log('warn', `[${tag}] upstream error:`, msg);
      toClient({ type: 'error', message: String(msg) });
      return;
    }

    log('debug', `[${tag}] upstream событие ${t}`);
  });

  upstream.on('unexpected-response', (_req, res) => {
    log('error', `[${tag}] Яндекс отклонил соединение: HTTP ${res.statusCode}`);
    toClient({
      type: 'error',
      message: `Яндекс отклонил соединение (HTTP ${res.statusCode})`,
    });
    shutdown(1011, 'upstream rejected');
  });

  upstream.on('error', (err) => {
    log('error', `[${tag}] upstream error:`, err.message);
    toClient({ type: 'error', message: 'Нет связи с голосовым сервисом' });
    shutdown(1011, 'upstream error');
  });

  upstream.on('close', (code) => shutdown(code === 1000 ? 1000 : 1011, 'upstream closed'));

  // ---- app -> upstream

  client.on('message', (raw) => {
    lastActivity = Date.now();
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch (_) {
      return;
    }

    switch (msg.type) {
      case 'audio': {
        const pcm = Buffer.from(msg.audio || '', 'base64');
        if (!pcm.length) return;
        const up = resamplePcm16(pcm, cfg.clientInRate, cfg.yandexInRate);
        toUpstream({ type: 'input_audio_buffer.append', audio: up.toString('base64') });
        return;
      }
      case 'text': {
        const text = String(msg.text || '').slice(0, 4000);
        if (!text) return;
        toUpstream({
          type: 'conversation.item.create',
          item: {
            type: 'message',
            role: 'user',
            content: [{ type: 'input_text', text }],
          },
        });
        toUpstream({ type: 'response.create' });
        return;
      }
      // Закрыть фразу вручную, если приложение решило не ждать VAD.
      // response.create здесь НЕ шлём: при server_vad Яндекс создаёт ответ сам,
      // и второй запрос давал второй ответ на ту же реплику.
      case 'commit':
        toUpstream({ type: 'input_audio_buffer.commit' });
        return;

      // Включить или выключить озвучку посреди разговора.
      case 'set_audio': {
        const enabled = msg.enabled === true;
        if (enabled === wantAudioOut) return;
        wantAudioOut = enabled;
        sessionUpdate(wantAudioOut);
        toClient({ type: 'audio_mode', audio: wantAudioOut });
        log('debug', `[${tag}] озвучка ${wantAudioOut ? 'включена' : 'выключена'}`);
        return;
      }
      case 'cancel':
        toUpstream({ type: 'response.cancel' });
        return;
      case 'ping':
        toClient({ type: 'pong' });
        return;
      default:
        return;
    }
  });

  client.on('close', () => shutdown(1000, 'client closed'));
  client.on('error', () => shutdown(1011, 'client error'));
}

httpServer.listen(cfg.port, cfg.host, () => {
  log('info', `voice-relay слушает ws://${cfg.host}:${cfg.port}${cfg.path}`);
  log(
    'info',
    `частоты: клиент ${cfg.clientInRate}/${cfg.clientOutRate} Гц, Яндекс ${cfg.yandexInRate}/${cfg.yandexOutRate} Гц`
  );
  log(
    'info',
    `вход: токен пользователя${
      cfg.allowAppKey
        ? `, запасной ключ приложения (ANON_KEY, ${cfg.supabaseAnonKey.length} символов)`
        : ' (запасной ключ выключен)'
    }`
  );
  log(
    'info',
    cfg.outputMode === 'audio'
      ? `модель ${cfg.yandexModel}, ответ голосом (${cfg.yandexVoice})`
      : `модель ${cfg.yandexModel}, ответ текстом (синтез выключен)`
  );
});

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    log('info', 'останавливаюсь');
    httpServer.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 3000);
  });
}

'use strict';

/**
 * EduAppML — llm-gateway для llm.armintel.ru (OpenAI-совместимый API).
 *
 * Зачем отдельный сервис, а не Edge Function и не прямой запрос из APK:
 *   1. Токен armintel нельзя класть в APK — его тривиально вытащить из пакета.
 *      Токен живёт только здесь, в .env рядом с этим файлом.
 *   2. Список моделей на той стороне меняется, а часть моделей «думает вслух»
 *      прямо в тексте ответа. И то, и другое чинится настройкой сервиса —
 *      без пересборки приложения.
 *   3. У armintel перед моделями стоит свой nginx с таймаутом 60 секунд:
 *      модели Ollama, которые не подгружены в память, отдают 504. Приложению
 *      про это знать незачем — шлюз превращает это в понятное сообщение.
 *
 * Схема:
 *   Android --http--> этот шлюз --https--> llm.armintel.ru/api/v1/chat/completions
 *                        |
 *                        └── GoTrue /auth/v1/user (проверка токена пользователя)
 *
 * Авторизация приложения: заголовок `Authorization: Bearer <supabase access token>`
 * — тот же токен, что SessionManager.getToken() хранит после логина, и та же
 * схема, что у voice-relay. Отдельного секрета в APK не заводим.
 *
 * Протокол намеренно повторяет формат Edge Function `functions/v1/chat`
 * (GigaChat), чтобы на клиенте переиспользовались те же модели данных:
 *
 *   POST {BASE_PATH}/chat
 *     {"message":"...", "history":[{"role":"user|assistant","content":"..."}],
 *      "model":"gemma-3-27b-it",            // необязательно; пусто = ARMINTEL_MODEL
 *      "attachments":[{"name","mime_type","data"}]}   // необязательно
 *   ->  {"success":true,"reply":"...","model":"gemma-3-27b-it"}
 *   ->  {"success":false,"error":"..."}
 *
 *   GET  {BASE_PATH}/models   -> {"default":"...","models":[{"id","label","note"}]}
 *   GET  {BASE_PATH}/health   -> {"ok":true,...}   (без авторизации)
 */

require('dotenv').config();

const http = require('http');

// ---------------------------------------------------------------- конфигурация

const cfg = {
  port: int(process.env.PORT, 8788),
  host: process.env.HOST || '0.0.0.0',
  // Префикс путей. Пустой — сервис отвечает на /chat, /models, /health.
  // При заходе через nginx удобно поставить /llm, тогда оба варианта работают.
  basePath: stripSlash(process.env.BASE_PATH || ''),

  apiKey: process.env.ARMINTEL_API_KEY || '',
  chatUrl: process.env.ARMINTEL_CHAT_URL || 'https://llm.armintel.ru/api/v1/chat/completions',
  modelsUrl: process.env.ARMINTEL_MODELS_URL || 'https://llm.armintel.ru/api/v1/models',

  model: process.env.ARMINTEL_MODEL || 'gemma-3-27b-it',
  // Список для выпадашки в приложении: "id|подпись|примечание" через запятую.
  // Пусто — шлюз отдаёт всё, что вернул upstream (годится для отладки, но в
  // этом списке есть модели, которые отвечают минутами).
  models: parseModels(process.env.ARMINTEL_MODELS || ''),

  temperature: num(process.env.ARMINTEL_TEMPERATURE, 0.7),
  maxTokens: int(process.env.ARMINTEL_MAX_TOKENS, 1024),
  // Верхняя граница ожидания ответа. Смысла ставить больше 60 с немного:
  // nginx на той стороне всё равно рвёт запрос по своему таймауту.
  requestTimeoutMs: int(process.env.ARMINTEL_TIMEOUT_MS, 75000),

  systemPrompt: process.env.ARMINTEL_SYSTEM_PROMPT || DEFAULT_SYSTEM_PROMPT(),
  historyLimit: int(process.env.HISTORY_LIMIT, 20),

  // Вырезать ли «размышления» модели из текста ответа (см. stripReasoning).
  stripReasoning: (process.env.STRIP_REASONING || 'true').toLowerCase() !== 'false',

  supabaseUrl: stripSlash(process.env.SUPABASE_URL || ''),
  supabaseAnonKey: process.env.SUPABASE_ANON_KEY || '',
  // Статический ключ только для отладки через curl. В проде — пустой.
  staticKey: process.env.LLM_GATEWAY_KEY || '',

  maxBodyBytes: int(process.env.MAX_BODY_BYTES, 12 * 1024 * 1024),
  maxConcurrent: int(process.env.MAX_CONCURRENT, 6),
  logLevel: (process.env.LOG_LEVEL || 'info').toLowerCase(),
};

function DEFAULT_SYSTEM_PROMPT() {
  return [
    'Ты — Edu.AI, помощник учебного приложения по машинному обучению.',
    'Отвечай по-русски, разбирай тему по шагам и не растекайся.',
    'Темы курса: классические алгоритмы (линейная и логистическая регрессия, kNN,',
    'наивный Байес, SVM, деревья решений, случайный лес, градиентный бустинг, k-means)',
    'и нейросети (полносвязные, SOM, обучение с подкреплением, автокодировщики, GAN,',
    'CNN, RNN, GNN, трансформеры, диффузионные модели).',
    'Формулы оформляй в LaTeX между $$ ... $$ — приложение их отрисует.',
    'Таблицы не используй: рендерер приложения их не умеет, вместо таблицы — список.',
    'Не пиши вслух ход своих рассуждений, сразу давай готовый ответ.',
  ].join(' ');
}

if (!cfg.apiKey) {
  console.error('[fatal] не задан ARMINTEL_API_KEY в .env');
  process.exit(1);
}
if (!cfg.supabaseUrl && !cfg.staticKey) {
  console.error('[fatal] не задан ни SUPABASE_URL (проверка токена), ни LLM_GATEWAY_KEY');
  process.exit(1);
}

// ------------------------------------------------------------------ утилиты

function int(v, def) {
  const n = parseInt(v, 10);
  return Number.isFinite(n) ? n : def;
}

function num(v, def) {
  const n = Number(v);
  return Number.isFinite(n) ? n : def;
}

function stripSlash(s) {
  return String(s).replace(/\/+$/, '');
}

const LEVELS = { error: 0, warn: 1, info: 2, debug: 3 };
function log(level, ...args) {
  if ((LEVELS[level] ?? 2) <= (LEVELS[cfg.logLevel] ?? 2)) {
    console.log(`[${new Date().toISOString()}] [${level}]`, ...args);
  }
}

/**
 * "id|Подпись|примечание, id2|Подпись2" -> [{id,label,note}]
 *
 * Подпись и примечание необязательны: без них подписью становится сам id.
 * Список лежит в .env, а не в коде, чтобы поменять набор моделей в приложении
 * можно было перезапуском сервиса, без новой сборки APK.
 */
function parseModels(raw) {
  return String(raw)
    .split(',')
    .map((chunk) => chunk.trim())
    .filter(Boolean)
    .map((chunk) => {
      const [id, label, note] = chunk.split('|').map((p) => (p || '').trim());
      return { id, label: label || id, note: note || '' };
    })
    .filter((m) => m.id);
}

/**
 * Часть моделей (GLM, qwen3.6, DeepSeek) выдаёт ход рассуждений прямо в тексте.
 * Проверенные форматы: теги <think>…</think>, блоки <|channel|>analysis…, и поле
 * reasoning_content рядом с content. Теги вырезаем, поле просто не читаем.
 *
 * Модели, которые пишут рассуждения без всяких тегов, этим не лечатся — для них
 * лечение одно: не класть их в ARMINTEL_MODELS.
 */
function stripReasoning(text) {
  if (!cfg.stripReasoning || !text) return text || '';
  let out = String(text);
  out = out.replace(/<think>[\s\S]*?<\/think>/gi, '');
  out = out.replace(/<thinking>[\s\S]*?<\/thinking>/gi, '');
  out = out.replace(/<reasoning>[\s\S]*?<\/reasoning>/gi, '');
  // Незакрытый <think> — модель не успела закрыть тег по лимиту токенов.
  out = out.replace(/<think>[\s\S]*$/i, '');
  // Harmony-разметка gpt-oss: аналитический канал до финального ответа.
  out = out.replace(/<\|channel\|>analysis[\s\S]*?<\|channel\|>final[^>]*>?/gi, '');
  out = out.replace(/<\|[a-z_]+\|>/gi, '');
  return out.trim();
}

// ------------------------------------------------------- проверка токена

const tokenCache = new Map(); // token -> { userId, until }
const TOKEN_TTL_MS = 60 * 1000;

async function authorize(req) {
  const header = req.headers['authorization'] || '';
  let token = '';
  if (/^Bearer\s+/i.test(header)) token = header.replace(/^Bearer\s+/i, '').trim();
  if (!token) return null;

  if (cfg.staticKey && token === cfg.staticKey) return { userId: 'static-key' };
  if (!cfg.supabaseUrl) return null;

  const cached = tokenCache.get(token);
  if (cached && cached.until > Date.now()) return { userId: cached.userId };

  try {
    const res = await fetch(`${cfg.supabaseUrl}/auth/v1/user`, {
      headers: {
        Authorization: `Bearer ${token}`,
        apikey: cfg.supabaseAnonKey,
      },
    });
    if (!res.ok) {
      log('warn', 'auth: GoTrue ответил', res.status);
      return null;
    }
    const user = await res.json();
    const userId = user && user.id ? String(user.id) : 'unknown';
    tokenCache.set(token, { userId, until: Date.now() + TOKEN_TTL_MS });
    return { userId };
  } catch (e) {
    log('error', 'auth: не удалось проверить токен:', e.message);
    return null;
  }
}

// ------------------------------------------------------------- список моделей

let modelsCache = { until: 0, list: [] };
const MODELS_TTL_MS = 10 * 60 * 1000;

async function listModels() {
  if (cfg.models.length) return cfg.models;

  if (modelsCache.until > Date.now() && modelsCache.list.length) return modelsCache.list;

  try {
    const res = await fetch(cfg.modelsUrl, {
      headers: { Authorization: `Bearer ${cfg.apiKey}` },
      signal: AbortSignal.timeout(15000),
    });
    if (!res.ok) throw new Error(`upstream ${res.status}`);
    const json = await res.json();
    const list = (json.data || [])
      .map((m) => ({ id: String(m.id), label: String(m.name || m.id), note: '' }))
      // Эмбеддинги в чате бесполезны — они не отвечают текстом.
      .filter((m) => !/embed/i.test(m.id));
    modelsCache = { until: Date.now() + MODELS_TTL_MS, list };
    return list;
  } catch (e) {
    log('warn', 'models: не удалось получить список:', e.message);
    return [{ id: cfg.model, label: cfg.model, note: '' }];
  }
}

// ------------------------------------------------------------- сборка запроса

/**
 * Собирает messages для upstream.
 *
 * Картинки уезжают частями content (формат OpenAI vision) — их понимают модели
 * с capabilities.vision. Текстовые вложения вклеиваются в текст вопроса: так
 * их видит любая модель, а не только зрячая. Остальные форматы (pdf, docx)
 * этот движок не разбирает — про них честно пишем строку-заглушку, чтобы
 * ответ «я не вижу файла» не выглядел сбоем.
 */
function buildMessages(body) {
  const messages = [{ role: 'system', content: cfg.systemPrompt }];

  const history = Array.isArray(body.history) ? body.history.slice(-cfg.historyLimit) : [];
  for (const item of history) {
    const role = item && item.role === 'assistant' ? 'assistant' : 'user';
    const content = item && typeof item.content === 'string' ? item.content : '';
    if (content.trim()) messages.push({ role, content });
  }

  const attachments = Array.isArray(body.attachments) ? body.attachments : [];
  const images = [];
  const textNotes = [];

  for (const att of attachments) {
    const mime = String(att.mime_type || att.mimeType || '');
    const name = String(att.name || 'файл');
    const data = String(att.data || att.dataBase64 || '');
    if (!data) continue;

    if (/^image\//i.test(mime)) {
      images.push({ type: 'image_url', image_url: { url: `data:${mime};base64,${data}` } });
    } else if (/^text\/|json|csv|xml/i.test(mime)) {
      let decoded = '';
      try {
        decoded = Buffer.from(data, 'base64').toString('utf8').slice(0, 20000);
      } catch (e) {
        decoded = '';
      }
      if (decoded) textNotes.push(`\n\n--- содержимое файла ${name} ---\n${decoded}`);
    } else {
      textNotes.push(`\n\n[файл ${name} (${mime}) этот движок не разбирает]`);
    }
  }

  const text = String(body.message || '').trim() + textNotes.join('');

  if (images.length) {
    messages.push({
      role: 'user',
      content: [{ type: 'text', text: text || 'Что на изображении?' }, ...images],
    });
  } else {
    messages.push({ role: 'user', content: text });
  }

  return messages;
}

// ------------------------------------------------------------------- запрос

async function askUpstream(body) {
  const model = String(body.model || '').trim() || cfg.model;

  const payload = {
    model,
    messages: buildMessages(body),
    temperature: num(body.temperature, cfg.temperature),
    max_tokens: int(body.max_tokens, cfg.maxTokens),
    stream: false,
  };

  const started = Date.now();
  let res;
  try {
    res = await fetch(cfg.chatUrl, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${cfg.apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(cfg.requestTimeoutMs),
    });
  } catch (e) {
    const timedOut = e && (e.name === 'TimeoutError' || e.name === 'AbortError');
    log('warn', `upstream ${model}: ${e.message} за ${Date.now() - started} мс`);
    return {
      status: 504,
      error: timedOut
        ? `Модель «${model}» не ответила вовремя. Скорее всего, она сейчас подгружается в память — попробуйте ещё раз или выберите другую модель.`
        : 'Не удалось связаться с сервером моделей.',
    };
  }

  const raw = await res.text();
  log('debug', `upstream ${model}: ${res.status} за ${Date.now() - started} мс, ${raw.length} байт`);

  if (!res.ok) {
    // nginx на той стороне отдаёт html — показывать его пользователю незачем.
    if (res.status === 504 || res.status === 502) {
      return {
        status: 504,
        error: `Модель «${model}» не успела ответить за минуту. Обычно так ведёт себя модель, которую только что начали загружать — попробуйте ещё раз или выберите другую.`,
      };
    }
    if (res.status === 401 || res.status === 403) {
      return { status: 502, error: 'Сервер моделей отклонил ключ доступа. Нужно обновить ARMINTEL_API_KEY.' };
    }
    if (res.status === 404) {
      return { status: 502, error: `Модель «${model}» на сервере не найдена.` };
    }
    return { status: 502, error: `Сервер моделей вернул ошибку ${res.status}.` };
  }

  let json;
  try {
    json = JSON.parse(raw);
  } catch (e) {
    return { status: 502, error: 'Сервер моделей вернул не-JSON.' };
  }

  const choice = json.choices && json.choices[0];
  const message = choice && choice.message;
  let reply = stripReasoning(message && message.content);

  if (!reply) {
    // Так ведёт себя рассуждающая модель, упершаяся в лимит токенов: весь
    // бюджет ушёл в reasoning_content, до ответа дело не дошло.
    if (message && message.reasoning_content) {
      return {
        status: 502,
        error: `Модель «${model}» потратила весь ответ на размышления. Увеличьте ARMINTEL_MAX_TOKENS или выберите другую модель.`,
      };
    }
    return { status: 502, error: 'Модель вернула пустой ответ.' };
  }

  return { status: 200, reply, model, usage: json.usage || null };
}

// ------------------------------------------------------------------- сервер

let active = 0;

function send(res, status, obj) {
  const data = JSON.stringify(obj);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(data),
  });
  res.end(data);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > cfg.maxBodyBytes) {
        reject(Object.assign(new Error('too large'), { code: 'TOO_LARGE' }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      try {
        const text = Buffer.concat(chunks).toString('utf8');
        resolve(text ? JSON.parse(text) : {});
      } catch (e) {
        reject(Object.assign(new Error('bad json'), { code: 'BAD_JSON' }));
      }
    });
    req.on('error', reject);
  });
}

/** Отрезает и BASE_PATH, и запасной префикс /llm — чтобы адрес не зависел от nginx. */
function route(url) {
  let path = new URL(url, 'http://localhost').pathname;
  if (cfg.basePath && path.startsWith(cfg.basePath)) path = path.slice(cfg.basePath.length);
  if (path.startsWith('/llm/')) path = path.slice(4);
  return path.replace(/\/+$/, '') || '/';
}

const server = http.createServer(async (req, res) => {
  const path = route(req.url);

  if (path === '/health' || path === '/healthz') {
    send(res, 200, {
      ok: true,
      active,
      max: cfg.maxConcurrent,
      model: cfg.model,
      curated: cfg.models.length,
    });
    return;
  }

  const auth = await authorize(req);
  if (!auth) {
    send(res, 401, { success: false, error: 'Нужен вход в аккаунт.' });
    return;
  }

  if (path === '/models' && req.method === 'GET') {
    const models = await listModels();
    send(res, 200, { default: cfg.model, models });
    return;
  }

  if (path === '/chat' && req.method === 'POST') {
    if (active >= cfg.maxConcurrent) {
      send(res, 503, { success: false, error: 'Сейчас слишком много запросов. Попробуйте через минуту.' });
      return;
    }

    let body;
    try {
      body = await readBody(req);
    } catch (e) {
      if (e.code === 'TOO_LARGE') {
        send(res, 413, { success: false, error: 'Запрос слишком большой.' });
      } else {
        send(res, 400, { success: false, error: 'Некорректный JSON.' });
      }
      return;
    }

    if (!String(body.message || '').trim() && !(body.attachments || []).length) {
      send(res, 400, { success: false, error: 'Пустой запрос.' });
      return;
    }

    active++;
    try {
      const result = await askUpstream(body);
      if (result.status === 200) {
        log('info', `chat: ${auth.userId} -> ${result.model}, ${result.reply.length} символов`);
        send(res, 200, { success: true, reply: result.reply, model: result.model });
      } else {
        send(res, result.status, { success: false, error: result.error });
      }
    } catch (e) {
      log('error', 'chat: непредвиденная ошибка:', e.stack || e.message);
      send(res, 500, { success: false, error: 'Внутренняя ошибка шлюза.' });
    } finally {
      active--;
    }
    return;
  }

  send(res, 404, { success: false, error: 'Неизвестный адрес.' });
});

server.requestTimeout = cfg.requestTimeoutMs + 15000;
server.headersTimeout = 30000;
server.keepAliveTimeout = 65000;

server.listen(cfg.port, cfg.host, () => {
  log('info', `llm-gateway слушает http://${cfg.host}:${cfg.port}${cfg.basePath}`);
  log('info', `модель по умолчанию: ${cfg.model}; в списке: ${cfg.models.length || 'весь upstream'}`);
});

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    log('info', `получен ${sig}, выключаюсь`);
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 3000).unref();
  });
}

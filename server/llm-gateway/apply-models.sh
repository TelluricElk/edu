#!/usr/bin/env bash
#
# Обновление списка моделей в llm-gateway (17.09.2026).
#
# Что делает: прописывает в /opt/eduappml-llm-gateway/.env четыре модели
# с правильными названиями, перезапускает сервис и проверяет каждую живым
# запросом. Больше ничего не трогает — ни Supabase, ни voice-relay, ни сам
# server.js.
#
# Запуск на сервере:
#   scp server/llm-gateway/apply-models.sh root@157.22.206.53:/root/
#   ssh root@157.22.206.53 'bash /root/apply-models.sh'
#
# Скрипт идемпотентный: гонять можно сколько угодно раз. Старый .env перед
# правкой копируется в .env.bak.<дата>.

set -euo pipefail

APP_DIR=/opt/eduappml-llm-gateway
ENV_FILE="$APP_DIR/.env"
SERVICE=eduappml-llm-gateway

# Модель по умолчанию — на ней отвечает приложение, пока пользователь
# не выбрал другую в списке.
DEFAULT_MODEL='gemma-3-27b-it'

# Список для выпадашки в приложении: "id|подпись|примечание", модели через
# запятую. ВНУТРИ подписи и примечания запятой быть не должно — она здесь
# разделитель. Названия официальные, как их зовут авторы моделей.
MODELS='gemma-3-27b-it|Gemma 3 27B|Google · быстрая,Qwen3.8-Flash-Next|Qwen3.8 Flash|Alibaba · быстрая,DeepSeek-V4-Flash-Vision-Exp|DeepSeek V4 Flash|DeepSeek · видит картинки,FastContext-1.0-4B-SFT|FastContext 4B|самая лёгкая'

# Те же id отдельным списком — для проверки в конце.
PROBE_IDS=(
  'gemma-3-27b-it'
  'Qwen3.8-Flash-Next'
  'DeepSeek-V4-Flash-Vision-Exp'
  'FastContext-1.0-4B-SFT'
)

say()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[32m%s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*"; }
die()  { printf '\n\033[31mОшибка: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "запускайте под root (или через sudo bash apply-models.sh)"

# ------------------------------------------------------- 1. Сервис на месте?

if [ ! -f "$ENV_FILE" ]; then
  die "не найден $ENV_FILE.
Похоже, шлюз ещё не установлен. Сначала:
  scp -r server/llm-gateway root@157.22.206.53:/root/
  ssh root@157.22.206.53 'cd /root/llm-gateway && bash deploy.sh'
deploy.sh уже пропишет этот же список — этот скрипт нужен только для обновления."
fi

# ------------------------------------------------------------ 2. Правка .env

say "Обновляю $ENV_FILE"

BACKUP="$ENV_FILE.bak.$(date +%Y%m%d-%H%M%S)"
cp "$ENV_FILE" "$BACKUP"
echo "старая версия сохранена: $BACKUP"

# Заменяет строку KEY=... или дописывает её в конец, если такой ещё нет.
# Через grep -v, а не sed: в значениях есть '|', '·' и слэши, на которых
# sed-подстановка ломается.
set_env() {
  local key="$1" val="$2"
  grep -v "^${key}=" "$ENV_FILE" > "$ENV_FILE.tmp" || true
  printf '%s=%s\n' "$key" "$val" >> "$ENV_FILE.tmp"
  mv "$ENV_FILE.tmp" "$ENV_FILE"
  echo "  $key обновлён"
}

set_env ARMINTEL_MODEL  "$DEFAULT_MODEL"
set_env ARMINTEL_MODELS "$MODELS"

# Рассуждающим моделям 512 токенов мало: весь бюджет уходит в размышления,
# а на сам ответ ничего не остаётся. Если значение уже стоит — не трогаем.
if ! grep -q '^ARMINTEL_MAX_TOKENS=' "$ENV_FILE"; then
  set_env ARMINTEL_MAX_TOKENS 1024
fi

# В .env лежит токен доступа — читать его должен только root.
chmod 600 "$ENV_FILE"

# ------------------------------------------------------------ 3. Перезапуск

say "Перезапускаю $SERVICE"
systemctl restart "$SERVICE"
sleep 2

PORT="$(grep -E '^PORT=' "$ENV_FILE" | head -1 | cut -d= -f2-)"
PORT="${PORT:-8788}"

if ! curl -fsS -m 5 "http://127.0.0.1:$PORT/health"; then
  printf '\n'
  warn "Сервис не ответил на /health. Откатываю .env и показываю лог."
  mv "$BACKUP" "$ENV_FILE"
  systemctl restart "$SERVICE" || true
  systemctl status "$SERVICE" --no-pager -l | tail -20
  journalctl -u "$SERVICE" -n 40 --no-pager
  exit 1
fi
printf '\n'

# --------------------------------------------------------- 4. Проверка моделей

say "Проверяю каждую модель живым запросом"

AR_KEY="$(grep -E '^ARMINTEL_API_KEY=' "$ENV_FILE" | head -1 | cut -d= -f2-)"
[ -n "$AR_KEY" ] || die "в .env пустой ARMINTEL_API_KEY"

FAILED=0
TMP=/tmp/llm-gw-probe.json

for MODEL in "${PROBE_IDS[@]}"; do
  START="$(date +%s)"
  CODE="$(curl -s -o "$TMP" -w '%{http_code}' -m 90 \
    https://llm.armintel.ru/api/v1/chat/completions \
    -H "Authorization: Bearer $AR_KEY" \
    -H 'Content-Type: application/json' \
    -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"Ответь одним словом: готово\"}],\"max_tokens\":64,\"stream\":false}" || echo 000)"
  SECS=$(( $(date +%s) - START ))

  if [ "$CODE" = "200" ]; then
    ok "  $MODEL — ответила за ${SECS} с"
  elif [ "$CODE" = "504" ] || [ "$CODE" = "502" ]; then
    warn "  $MODEL — $CODE за ${SECS} с (модель не загружена на их стороне)"
    FAILED=$((FAILED + 1))
  else
    warn "  $MODEL — код $CODE за ${SECS} с"
    head -c 200 "$TMP" 2>/dev/null; printf '\n'
    FAILED=$((FAILED + 1))
  fi
done

rm -f "$TMP"

# ---------------------------------------------------------------- 5. Итог

say "Что теперь отдаёт шлюз приложению"
grep -E '^ARMINTEL_(MODEL|MODELS)=' "$ENV_FILE"

printf '\n'
if [ "$FAILED" -eq 0 ]; then
  ok "Готово. Все четыре модели отвечают, список обновлён."
else
  warn "Готово, но $FAILED из ${#PROBE_IDS[@]} моделей сейчас недоступны."
  echo "Список в приложении всё равно обновлён — недоступная модель просто"
  echo "вернёт понятную ошибку, остальные работают."
fi

printf '\nПриложение подхватит список само: оно берёт его через GET /models.\n'
printf 'Пересобирать APK не нужно.\n'
printf 'Логи: journalctl -u %s -f\n\n' "$SERVICE"

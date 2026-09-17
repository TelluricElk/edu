#!/usr/bin/env bash
#
# Установка llm-gateway на сервер. Запускать из папки, в которую скопирован
# каталог llm-gateway:
#
#   sudo bash deploy.sh
#
# Скрипт идемпотентный: повторный запуск обновляет server.js и перезапускает
# сервис, не трогая уже заполненный .env. Voice-relay и Supabase не задеваются
# вообще — это отдельный сервис на отдельном порту.

set -euo pipefail

APP_DIR=/opt/eduappml-llm-gateway
UNIT=/etc/systemd/system/eduappml-llm-gateway.service
PORT_DEFAULT=8788
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[31mОшибка: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "запускайте под root (или через sudo bash deploy.sh)"
[ -f "$SRC/server.js" ] || die "рядом со скриптом нет server.js — скопируйте всю папку llm-gateway целиком"

# ---------------------------------------------------------------- 1. Node.js

say "Проверяю Node.js"
NEED_NODE=1
if command -v node >/dev/null 2>&1; then
  MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
  if [ "$MAJOR" -ge 18 ]; then
    echo "уже есть: $(node -v)"
    NEED_NODE=0
  else
    echo "найден $(node -v) — слишком старый, нужен >= 18"
  fi
fi

if [ "$NEED_NODE" -eq 1 ]; then
  say "Ставлю Node.js 20"
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
  echo "установлен: $(node -v)"
fi

# ------------------------------------------------------------ 2. Файлы и npm

say "Раскладываю файлы в $APP_DIR"
mkdir -p "$APP_DIR"
cp "$SRC/server.js" "$SRC/package.json" "$APP_DIR/"

say "Ставлю зависимости"
cd "$APP_DIR"
npm install --omit=dev --no-fund --no-audit

# ------------------------------------------------------------------- 3. .env

if [ -f "$APP_DIR/.env" ]; then
  say ".env уже есть — оставляю как есть"
else
  say "Собираю .env"

  # ANON_KEY берём из конфигурации самой Supabase, чтобы не искать вручную.
  ANON_KEY=""
  for candidate in /root/supabase/docker/.env /home/*/supabase/docker/.env; do
    if [ -f "$candidate" ]; then
      ANON_KEY="$(grep -E '^ANON_KEY=' "$candidate" | head -1 | cut -d= -f2-)"
      [ -n "$ANON_KEY" ] && { echo "ANON_KEY найден в $candidate"; break; }
    fi
  done
  [ -n "$ANON_KEY" ] || echo "ANON_KEY автоматически найти не удалось — спрошу ниже"

  read -rp "ARMINTEL_API_KEY (токен доступа к llm.armintel.ru): " AR_KEY
  [ -n "$AR_KEY" ] || die "без токена шлюз не поднимется"

  if [ -z "$ANON_KEY" ]; then
    read -rp "SUPABASE_ANON_KEY (из ~/supabase/docker/.env): " ANON_KEY
  fi

  read -rp "Модель по умолчанию [gemma-3-27b-it]: " AR_MODEL
  AR_MODEL="${AR_MODEL:-gemma-3-27b-it}"

  cat > "$APP_DIR/.env" <<EOF
ARMINTEL_API_KEY=$AR_KEY
ARMINTEL_CHAT_URL=https://llm.armintel.ru/api/v1/chat/completions
ARMINTEL_MODELS_URL=https://llm.armintel.ru/api/v1/models

ARMINTEL_MODEL=$AR_MODEL
ARMINTEL_MODELS=gemma-3-27b-it|Gemma 3 27B|Google · быстрая,Qwen3.8-Flash-Next|Qwen3.8 Flash|Alibaba · быстрая,DeepSeek-V4-Flash-Vision-Exp|DeepSeek V4 Flash|DeepSeek · видит картинки,FastContext-1.0-4B-SFT|FastContext 4B|самая лёгкая

ARMINTEL_TEMPERATURE=0.7
ARMINTEL_MAX_TOKENS=1024
ARMINTEL_TIMEOUT_MS=75000
STRIP_REASONING=true

SUPABASE_URL=http://127.0.0.1:8000
SUPABASE_ANON_KEY=$ANON_KEY
LLM_GATEWAY_KEY=

PORT=$PORT_DEFAULT
HOST=0.0.0.0
BASE_PATH=

HISTORY_LIMIT=20
MAX_BODY_BYTES=12582912
MAX_CONCURRENT=6
LOG_LEVEL=info
EOF

  # В .env лежит токен доступа — читать его должен только root.
  chmod 600 "$APP_DIR/.env"
fi

PORT="$(grep -E '^PORT=' "$APP_DIR/.env" | head -1 | cut -d= -f2-)"
PORT="${PORT:-$PORT_DEFAULT}"

# ---------------------------------------------------------------- 4. systemd

say "Ставлю systemd-юнит"
cat > "$UNIT" <<EOF
[Unit]
Description=EduAppML LLM gateway (llm.armintel.ru)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=$APP_DIR
ExecStart=$(command -v node) $APP_DIR/server.js
Restart=always
RestartSec=3
Environment=NODE_ENV=production
StandardOutput=journal
StandardError=journal

NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable eduappml-llm-gateway >/dev/null
systemctl restart eduappml-llm-gateway

# ------------------------------------------------------------- 5. Firewall

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
  say "Открываю порт $PORT в ufw"
  ufw allow "$PORT"/tcp >/dev/null || true
fi

# ------------------------------------------------------------- 6. Проверка

say "Проверяю, что сервис отвечает"
sleep 2
if ! curl -fsS -m 5 "http://127.0.0.1:$PORT/health"; then
  printf '\n\033[31mСервис не ответил. Смотрим, что он сказал:\033[0m\n\n'
  systemctl status eduappml-llm-gateway --no-pager -l | tail -20
  journalctl -u eduappml-llm-gateway -n 40 --no-pager
  exit 1
fi

say "Проверяю, что шлюз добирается до llm.armintel.ru"
AR_KEY="$(grep -E '^ARMINTEL_API_KEY=' "$APP_DIR/.env" | head -1 | cut -d= -f2-)"
AR_MODEL="$(grep -E '^ARMINTEL_MODEL=' "$APP_DIR/.env" | head -1 | cut -d= -f2-)"
UP_CODE="$(curl -s -o /tmp/llm-gw-check.json -w '%{http_code}' -m 90 \
  https://llm.armintel.ru/api/v1/chat/completions \
  -H "Authorization: Bearer $AR_KEY" -H 'Content-Type: application/json' \
  -d "{\"model\":\"$AR_MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"скажи одно слово: готово\"}],\"max_tokens\":32,\"stream\":false}" || true)"

if [ "$UP_CODE" = "200" ]; then
  echo "upstream ответил 200:"
  head -c 300 /tmp/llm-gw-check.json; echo
else
  printf '\n\033[33mВнимание: upstream ответил %s.\033[0m\n' "$UP_CODE"
  echo "Сам сервис при этом поднят. Причины обычно две: просроченный токен"
  echo "или выбранная модель сейчас не загружена (504 от их nginx)."
  head -c 300 /tmp/llm-gw-check.json 2>/dev/null; echo
fi
rm -f /tmp/llm-gw-check.json

printf '\n\033[32mГотово. Шлюз слушает http://0.0.0.0:%s\033[0m\n' "$PORT"
printf 'Проверка:  curl http://127.0.0.1:%s/health\n' "$PORT"
printf 'Логи:      journalctl -u eduappml-llm-gateway -f\n'
printf 'В приложении: LlmGatewayClient.BASE_URL = http://157.22.206.53:%s/\n\n' "$PORT"

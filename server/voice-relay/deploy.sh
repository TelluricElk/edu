#!/usr/bin/env bash
#
# Установка voice-relay на сервер. Запускать из папки, в которую скопирован
# каталог voice-relay:
#
#   bash deploy.sh
#
# Скрипт идемпотентный: повторный запуск обновляет server.js и перезапускает
# сервис, не трогая уже заполненный .env.

set -euo pipefail

APP_DIR=/opt/eduappml-voice-relay
UNIT=/etc/systemd/system/eduappml-voice-relay.service
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[31mОшибка: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "запускайте под root (или через sudo bash deploy.sh)"
[ -f "$SRC/server.js" ] || die "рядом со скриптом нет server.js — скопируйте всю папку voice-relay целиком"

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

  read -rp "YANDEX_API_KEY (секретный ключ сервисного аккаунта): " YA_KEY
  [ -n "$YA_KEY" ] || die "без ключа Яндекса релей не поднимется"

  read -rp "YANDEX_FOLDER_ID (идентификатор каталога, b1g...): " YA_FOLDER
  [ -n "$YA_FOLDER" ] || die "без идентификатора каталога релей не поднимется"

  if [ -z "$ANON_KEY" ]; then
    read -rp "SUPABASE_ANON_KEY (из ~/supabase/docker/.env): " ANON_KEY
  fi

  read -rp "Голос синтеза [marina]: " YA_VOICE
  YA_VOICE="${YA_VOICE:-marina}"

  cat > "$APP_DIR/.env" <<EOF
YANDEX_API_KEY=$YA_KEY
YANDEX_FOLDER_ID=$YA_FOLDER
YANDEX_MODEL=speech-realtime-250923
YANDEX_VOICE=$YA_VOICE

SUPABASE_URL=http://127.0.0.1:8000
SUPABASE_ANON_KEY=$ANON_KEY
VOICE_RELAY_KEY=

PORT=8787
HOST=0.0.0.0
VOICE_PATH=/voice

CLIENT_IN_RATE=16000
CLIENT_OUT_RATE=24000
YANDEX_IN_RATE=
YANDEX_OUT_RATE=

MAX_SESSION_MS=600000
MAX_CONCURRENT=8
IDLE_TIMEOUT_MS=90000
LOG_LEVEL=info
EOF

  # В .env лежит секретный ключ — читать его должен только root.
  chmod 600 "$APP_DIR/.env"
fi

# ---------------------------------------------------------------- 4. systemd

say "Ставлю systemd-юнит"
cat > "$UNIT" <<EOF
[Unit]
Description=EduAppML voice relay (Yandex AI Studio Realtime)
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
systemctl enable eduappml-voice-relay >/dev/null
systemctl restart eduappml-voice-relay

# ------------------------------------------------------------- 5. Firewall

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
  say "Открываю порт 8787 в ufw"
  ufw allow 8787/tcp >/dev/null || true
fi

# ------------------------------------------------------------- 6. Проверка

say "Проверяю, что сервис отвечает"
sleep 2
if curl -fsS -m 5 http://127.0.0.1:8787/health; then
  printf '\n\n\033[32mГотово. Релей слушает ws://0.0.0.0:8787/voice\033[0m\n'
  printf 'Логи:  journalctl -u eduappml-voice-relay -f\n\n'
else
  printf '\n\033[31mСервис не ответил. Смотрим, что он сказал:\033[0m\n\n'
  systemctl status eduappml-voice-relay --no-pager -l | tail -20
  journalctl -u eduappml-voice-relay -n 40 --no-pager
  exit 1
fi

#!/usr/bin/env bash
set -euo pipefail
command -v adb >/dev/null || { echo 'adb no está en PATH; usa platform-tools del SDK.' >&2; exit 1; }
count=$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')
if [ "$count" -ne 1 ]; then
  echo 'Conecta y autoriza exactamente un teléfono; para varios usa adb -s SERIAL manualmente.' >&2
  adb devices
  exit 1
fi
adb reverse tcp:8081 tcp:8000
adb reverse --list
echo 'API base Android debug: http://127.0.0.1:8081/api/v1/'

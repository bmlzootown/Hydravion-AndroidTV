#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/home/bmlzootown/.local/share/JetBrains/Toolbox/apps/android-studio/jbr}"
export PATH="$JAVA_HOME/bin:$PATH:${HOME}/Android/Sdk/platform-tools:${HOME}/Android/Sdk/emulator"

DEVICE="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
if [[ -z "${DEVICE}" ]]; then
  echo "No authorized adb device found" >&2
  adb devices -l >&2
  exit 1
fi

echo "Using device: ${DEVICE}"
bash ./gradlew :app:installDebug --no-daemon
adb -s "${DEVICE}" shell am start -n ml.bmlzootown.hydravion/ml.bmlzootown.hydravion.browse.MainActivity
echo "Launched Hydravion on ${DEVICE}"

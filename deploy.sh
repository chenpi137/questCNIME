#!/usr/bin/env bash
# Install the built APK, enable the IME and select it.
#
# Usage:
#   ./deploy.sh                         # uses adb on PATH and build/QuestCNIME.apk
#   ADB=/path/to/adb ./deploy.sh        # custom adb
#   ./deploy.sh path/to/QuestCNIME.apk  # custom APK
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
ADB="${ADB:-adb}"
APK="${1:-$PROJ/build/QuestCNIME.apk}"

if [ ! -f "$APK" ]; then
    echo "ERROR: APK not found: $APK" >&2
    echo "       Run ./build.sh first." >&2
    exit 1
fi

"$ADB" install -r "$APK"
"$ADB" shell ime enable com.questcn.ime/.QuestImeService
"$ADB" shell ime set com.questcn.ime/.QuestImeService
echo "QuestCNIME installed and selected."

#!/usr/bin/env bash
# Fetch the prebuilt native libraries from the official CyIME release APK.
#
# Why: this repository keeps source-only. The four arm64-v8a .so files are
# prebuilt by CyIME and are not committed. They are extracted from:
#   https://github.com/Cyletix/CyIME/releases/tag/1.15.106
#
# Usage:
#   ./scripts/fetch-libs.sh
#   CYIME_APK=/path/to/CyIME.apk ./scripts/fetch-libs.sh
#   CYIME_VERSION=1.15.106 HTTPS_PROXY=http://127.0.0.1:7897 ./scripts/fetch-libs.sh
set -euo pipefail

PROJ="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$PROJ/libs/arm64-v8a"
VERSION="${CYIME_VERSION:-1.15.106}"
ASSET="CyIME-${VERSION}-arm64-v8a.apk"
URL="https://github.com/Cyletix/CyIME/releases/download/${VERSION}/${ASSET}"
CACHE="$PROJ/.cache/$ASSET"

mkdir -p "$DEST" "$PROJ/.cache"

if [ -n "${CYIME_APK:-}" ]; then
    APK="$CYIME_APK"
    if [ ! -f "$APK" ]; then
        echo "ERROR: CYIME_APK not found: $APK" >&2
        exit 1
    fi
else
    APK="$CACHE"
    if [ ! -f "$APK" ]; then
        echo "Downloading $ASSET ..."
        echo "URL: $URL"
        curl -L --fail --retry 3 --retry-delay 2 -o "$APK" "$URL"
    fi
fi

echo "Using: $APK"
echo "SHA-256: $(sha256sum "$APK" 2>/dev/null | cut -d' ' -f1 || echo unavailable)"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

LIBS="librime_jni.so libonnxruntime.so libcylm.so libc++_shared.so"
for lib in $LIBS; do
    if ! unzip -l "$APK" "lib/arm64-v8a/$lib" >/dev/null 2>&1; then
        echo "ERROR: $lib not found in $APK" >&2
        exit 1
    fi
    unzip -p "$APK" "lib/arm64-v8a/$lib" > "$TMP/$lib"
    if [ ! -s "$TMP/$lib" ]; then
        echo "ERROR: failed to extract $lib" >&2
        exit 1
    fi
    mv "$TMP/$lib" "$DEST/$lib"
    echo "  extracted lib/arm64-v8a/$lib"
done

echo "OK: native libraries are in $DEST"
ls -lh "$DEST"

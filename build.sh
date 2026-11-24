#!/usr/bin/env bash
# QuestCNIME — no-Gradle build (aapt2 -> javac -> d8 -> zipalign -> apksigner)
#
# Requirements:
#   - JDK 17 (javac/java/keytool on PATH, or set JAVA_HOME)
#   - Android SDK build-tools (aapt2, d8, zipalign, apksigner)
#   - android.jar for API 35
#   - prebuilt native libs: run ./scripts/fetch-libs.sh once
#
# Optional environment variables:
#   ANDROID_HOME / ANDROID_SDK_ROOT   SDK root
#   BUILD_TOOLS                       build-tools directory (overrides SDK search)
#   ANDROID_JAR                       explicit android.jar path
#   JAVA_HOME                         JDK root
#   QUESTCN_KEYSTORE                  signing keystore path (default: ./keystore.jks)
#   QUESTCN_KEYSTORE_PASS             keystore/key password (default: questcn123)
#   QUESTCN_KEY_ALIAS                 key alias (default: questcn)
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJ"

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) EXE=".exe" ;;
    *) EXE="" ;;
esac

find_tool() {
    local name="$1"
    if command -v "$name" >/dev/null 2>&1; then
        command -v "$name"
    elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/$name$EXE" ]; then
        echo "$JAVA_HOME/bin/$name$EXE"
    elif [ -x "/d/Program Files/Java/jdk-17/bin/$name.exe" ]; then
        echo "/d/Program Files/Java/jdk-17/bin/$name.exe"
    else
        return 1
    fi
}

JAVA="$(find_tool java || true)"
JAVAC="$(find_tool javac || true)"
KEYTOOL="$(find_tool keytool || true)"
if [ -z "$JAVA" ] || [ -z "$JAVAC" ] || [ -z "$KEYTOOL" ]; then
    echo "ERROR: JDK 17 not found. Set JAVA_HOME or add java/javac/keytool to PATH." >&2
    exit 1
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
    for candidate in \
        "/c/Users/${USER:-${USERNAME:-}}/AppData/Local/Android/Sdk" \
        "$HOME/AppData/Local/Android/Sdk" \
        "/e/android-tools"; do
        if [ -d "$candidate" ]; then SDK="$candidate"; break; fi
    done
fi
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
    echo "ERROR: Android SDK not found. Set ANDROID_HOME." >&2
    exit 1
fi

BUILD_TOOLS="${BUILD_TOOLS:-}"
if [ -z "$BUILD_TOOLS" ]; then
    if [ -d "$SDK/build-tools" ]; then
        BUILD_TOOLS="$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
    elif [ -d "$SDK/android-14" ]; then
        # Local portable layout used during development: <sdk>/android-14
        BUILD_TOOLS="$SDK/android-14"
    fi
fi
if [ -z "$BUILD_TOOLS" ] || [ ! -x "$BUILD_TOOLS/aapt2$EXE" ]; then
    echo "ERROR: build-tools with aapt2 not found. Set BUILD_TOOLS." >&2
    exit 1
fi

if [ -z "${ANDROID_JAR:-}" ]; then
    if [ -f "$SDK/platforms/android-35/android.jar" ]; then
        ANDROID_JAR="$SDK/platforms/android-35/android.jar"
    elif [ -f "$SDK/android-35/android.jar" ]; then
        ANDROID_JAR="$SDK/android-35/android.jar"
    else
        echo "ERROR: android.jar (API 35) not found. Set ANDROID_JAR." >&2
        exit 1
    fi
fi

D8_JAR="$BUILD_TOOLS/lib/d8.jar"
APKSIGNER_JAR="$BUILD_TOOLS/lib/apksigner.jar"
AAPT2="$BUILD_TOOLS/aapt2$EXE"
ZIPALIGN="$BUILD_TOOLS/zipalign$EXE"
for f in "$D8_JAR" "$APKSIGNER_JAR" "$AAPT2" "$ZIPALIGN"; do
    if [ ! -e "$f" ]; then
        echo "ERROR: missing build tool: $f" >&2
        exit 1
    fi
done

if [ ! -d "assets/rime_ice" ] || [ ! -d "assets/rime_chinese" ]; then
    echo "ERROR: Rime assets are missing. Restore assets/ from the release source." >&2
    exit 1
fi
for lib in librime_jni.so libonnxruntime.so libcylm.so libc++_shared.so; do
    if [ ! -f "libs/arm64-v8a/$lib" ]; then
        echo "ERROR: missing libs/arm64-v8a/$lib" >&2
        echo "       Run ./scripts/fetch-libs.sh once (downloads CyIME prebuilt libs)." >&2
        exit 1
    fi
done

PYTHON=""
for candidate in python python3; do
    if command -v "$candidate" >/dev/null 2>&1 \
            && "$candidate" -c "import sys" >/dev/null 2>&1; then
        PYTHON="$candidate"
        break
    fi
done
if [ -z "$PYTHON" ]; then
    echo "ERROR: a working python3/python is required for APK packaging." >&2
    exit 1
fi

KS="${QUESTCN_KEYSTORE:-$PROJ/keystore.jks}"
KS_PASS="${QUESTCN_KEYSTORE_PASS:-questcn123}"
ALIAS="${QUESTCN_KEY_ALIAS:-questcn}"

rm -rf build/gen build/classes build/dex build/res.zip build/unsigned.apk build/aligned.apk build/QuestCNIME.apk
mkdir -p build/gen build/classes build/dex

echo "[1/6] aapt2 compile"
"$AAPT2" compile --dir res -o build/res.zip

echo "[2/6] aapt2 link"
"$AAPT2" link -o build/unsigned.apk \
    -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -R build/res.zip \
    --java build/gen \
    -A assets \
    --auto-add-overlay

echo "[3/6] javac"
find java build/gen -name "*.java" > build/sources.txt
"$JAVAC" -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR" \
    -d build/classes @build/sources.txt

echo "[4/6] d8"
"$JAVA" -cp "$D8_JAR" com.android.tools.r8.D8 \
    --min-api 24 --lib "$ANDROID_JAR" --output build/dex \
    $(find build/classes -name "*.class")

echo "[5/6] package"
"$PYTHON" -I - "$PROJ/build/unsigned.apk" "$PROJ/build/dex/classes.dex" "$PROJ" <<'PY'
import sys, zipfile, os
apk, dex, proj = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
    libdir = os.path.join(proj, "libs", "arm64-v8a")
    for name in sorted(os.listdir(libdir)):
        if name.endswith(".so"):
            z.write(os.path.join(libdir, name), "lib/arm64-v8a/" + name)
PY
"$ZIPALIGN" -f 4 build/unsigned.apk build/aligned.apk

echo "[6/6] sign"
if [ ! -f "$KS" ]; then
    echo "WARN: keystore not found; generating a local debug keystore at $KS"
    "$KEYTOOL" -genkeypair -v -keystore "$KS" -storetype JKS \
        -storepass "$KS_PASS" -keypass "$KS_PASS" -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=QuestCN,OU=IME,O=QuestCN,L=CN,ST=CN,C=CN"
fi
"$JAVA" -jar "$APKSIGNER_JAR" sign \
    --ks "$KS" --ks-key-alias "$ALIAS" \
    --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out build/QuestCNIME.apk build/aligned.apk

echo "OK: $PROJ/build/QuestCNIME.apk"
ls -lh "$PROJ/build/QuestCNIME.apk"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$PROJ/build/QuestCNIME.apk"
fi

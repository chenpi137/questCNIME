# libs/arm64-v8a

This directory is intentionally empty in git. The four prebuilt native
libraries are extracted from the official CyIME 1.15.106 arm64-v8a release:

- `librime_jni.so` — CyIME JNI bridge (GPL-3.0)
- `libcylm.so` — CyIME custom library (GPL-3.0)
- `libonnxruntime.so` — ONNX Runtime (MIT)
- `libc++_shared.so` — LLVM libc++ (Apache-2.0 with LLVM exception)

Run:

```bash
./scripts/fetch-libs.sh
```

or pass a local APK:

```bash
CYIME_APK=/path/to/CyIME-1.15.106-arm64-v8a.apk ./scripts/fetch-libs.sh
```

The script does not commit or redistribute these binaries; it downloads them
from the upstream release on your machine. See `THIRD_PARTY_NOTICES.md`.

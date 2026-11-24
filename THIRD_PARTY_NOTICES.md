# Third-Party Notices and Licenses

QuestCNIME is distributed under **GNU GPL v3.0** (see `LICENSE`) because it
integrates/bundles GPL-licensed CyIME components and rime-ice data.

This project is a personal, non-commercial research project. It is **not**
affiliated with, endorsed by, or sponsored by Meta Platforms, Inc. Meta, Quest,
Oculus and related marks are trademarks of their respective owners.

## Components

| Component | License | How it is used | Upstream |
| --- | --- | --- | --- |
| **CyIME 1.15.106** | GPL-3.0 | Prebuilt `librime_jni.so` / `libcylm.so`; JNI bridge and input-core integration reference | https://github.com/Cyletix/CyIME |
| **Rime / librime** | BSD-3-Clause | Chinese input engine (inside `librime_jni.so`) | https://github.com/rime/librime |
| **rime-ice / 雾凇拼音** | GPL-3.0 | `assets/rime_ice` dictionaries, schemas and Lua scripts | https://github.com/iDvel/rime-ice |
| **OpenCC** | Apache-2.0 | `assets/rime_chinese/opencc` conversion data | https://github.com/BYVoid/OpenCC |
| **ONNX Runtime** | MIT | Prebuilt `libonnxruntime.so` | https://github.com/microsoft/onnxruntime |
| **LLVM libc++** | Apache-2.0 with LLVM exception | Prebuilt `libc++_shared.so` | https://libcxx.llvm.org |
| **Android Open Source Project** | Apache-2.0 | Android platform APIs and input-method framework | https://source.android.com |
| **Meta Quest / VrShell** | Proprietary | Layout/behavior reference only; no VrShell APK, native library, audio sample or other proprietary binary is included in this repository | https://www.meta.com |

### Notes on prebuilt binaries

The four arm64-v8a `.so` files are **not committed** to this repository. Run
`./scripts/fetch-libs.sh` to download the official CyIME release APK and extract
them locally. CyIME's corresponding source is available at the upstream link
above. If you redistribute a built APK, you must comply with GPL-3.0, including
making the corresponding source available.

### Notes on data files

`assets/rime_ice` and `assets/rime_chinese` are data files (YAML, text, Lua and
small compiled OpenCC dictionaries). Their upstream licenses are listed above;
the rime-ice license text is included at `assets/rime_ice/LICENSE`, and the full
GPL-3.0 text is included at `LICENSE`.

### Sound

Key sounds are requested from the system service
`vros.spatial.media.SpatialMediaManager` using event names such as
`keyboardDirectPrimary`. The app does not bundle FMOD or VrShell audio samples.
If the spatial service is unavailable, it falls back to Android's built-in
`AudioManager` key-press effects.

## Copyright / takedown

If you are a rights holder and believe that any material in this repository
infringes your rights, please open an issue on the project repository (or
contact the maintainer through the repository) with a description of the
material and proof of ownership. The material will be removed promptly after
verification. No infringement is intended.

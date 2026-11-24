# QuestCNIME — Quest 中文输入法

<img src="docs/icon.png" width="96" alt="QuestCNIME icon">

仓库：<https://github.com/chenpi137/questCNIME>

一个面向 **Meta Quest 3 / 3S** 的独立中文输入法。键盘布局、按键几何和状态栏参考
Quest 原生 VrShell 键盘实现；中文输入核心使用 **Rime（librime）+ 雾凇拼音**，
通过 CyIME 的预编译 JNI 库接入。项目不依赖 VrShell 运行，也不是 Meta 官方产品。

> 个人学习 / 研究项目，非商业用途。使用前请阅读 [免责声明](#免责声明) 与
> [第三方许可证](THIRD_PARTY_NOTICES.md)。

## 功能

- VrShell 风格键盘：48dp 按键、8dp 间距、状态栏 + 候选栏、A/a/@ 长按变体
- Rime 中文输入：拼音候选、emoji、中英混输、符号页、候选翻页
- 首次启动 OOBE：免责声明 / 用户协议 → 启用输入法 → 初始化词库 → 选择输入法
- OOBE 第 1 步强制打开 **Android 原生设置**（`com.android.settings`），而不是 Quest 设置
- 按键音走系统 `SpatialMediaManager` 空间音频事件；服务不可用时退回 Android 系统按键音
- 无网络权限、无数据收集；词库在本机初始化与运行
- 暗色自适应图标、圆角图标与各密度 PNG

## 当前状态

- 版本：**1.1.0**（versionCode 2）
- 目标设备：Quest 3S / Quest 3，Android 14（arm64-v8a）
- 已在 Quest 3S 上验证：安装、IME 启用/切换、键盘弹出、Rime 候选、上屏、OOBE 页面启动
- 键盘窗口：`780x355`（状态栏 56dp + 键盘 216dp + 上下 4dp 边距）

## 快速开始

```bash
git clone https://github.com/chenpi137/questCNIME.git
cd questCNIME
```

### 1. 环境

- JDK 17（`java` / `javac` / `keytool`）
- Android SDK：build-tools（`aapt2`、`d8`、`zipalign`、`apksigner`）与 API 35 `android.jar`
- Python 3（APK 打包脚本使用标准库）
- Bash（Windows 可用 Git Bash）
- `curl` / `unzip`（仅首次获取预编译库时使用）

设置环境变量（按你的安装路径修改）：

```bash
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"
export JAVA_HOME="/c/Program Files/Java/jdk-17"
```

`build.sh` 会自动查找 SDK/build-tools/API 35；也可以用 `BUILD_TOOLS`、`ANDROID_JAR`
显式指定。

### 2. 获取预编译 Rime 库（仅一次）

仓库**不提交** `.so` 二进制。脚本会从 CyIME 官方 release 下载 arm64-v8a APK，
并只提取以下四个库到 `libs/arm64-v8a/`：

```bash
./scripts/fetch-libs.sh
```

如已手动下载 CyIME APK：

```bash
CYIME_APK=/path/to/CyIME-1.15.106-arm64-v8a.apk ./scripts/fetch-libs.sh
```

需要代理时：

```bash
HTTPS_PROXY=http://127.0.0.1:7897 ./scripts/fetch-libs.sh
```

### 3. 构建

```bash
./build.sh
```

输出：`build/QuestCNIME.apk`。

`build.sh` 的流程与 Gradle 无关：

1. `aapt2 compile/link` 编译资源、合并 manifest、打包 `assets/`
2. `javac` 编译 Java 源码（`-source 8 -target 8`，`android.jar` 作为 bootclasspath）
3. `d8` 生成 `classes.dex`
4. Python `zipfile` 把 `classes.dex` 和 `libs/arm64-v8a/*.so` 追加进 APK
5. `zipalign` 对齐
6. `apksigner` 签名

签名默认使用 `./keystore.jks`；不存在时脚本会生成一个本地调试 keystore
（默认密码 `questcn123`）。**不要把 `keystore.jks` 提交到公开仓库**，发布正式版请使用
自己的密钥并通过环境变量覆盖：

```bash
QUESTCN_KEYSTORE=/secure/release.jks \
QUESTCN_KEYSTORE_PASS='***' \
QUESTCN_KEY_ALIAS=release \
./build.sh
```

### 4. 安装

```bash
./deploy.sh
```

或手动：

```bash
adb install -r build/QuestCNIME.apk
adb shell ime enable com.questcn.ime/.QuestImeService
adb shell ime set com.questcn.ime/.QuestImeService
```

## 首次启动流程（OOBE）

1. **免责声明 / 用户协议 / 开源致谢**：必须勾选“我已阅读并同意”才能继续
2. **启用输入法**：点击按钮打开 Android 原生
   `com.android.settings/.Settings$AvailableVirtualKeyboardActivity`，启用
   “Quest 中文输入法”
3. **初始化文件（必要）**：复制 Rime 词库并编译索引，首次约 10–40 秒；完成前不能下一步
4. **选择输入法**：弹出系统输入法选择器；只有当前输入法切到 QuestCNIME 后，“进入键盘”才可用
5. 完成：写入 `oobe_done`，跳转到常规设置页；以后启动直接进入常规界面

> Quest 默认会把 `android.settings.INPUT_METHOD_SETTINGS` 解析到 VrShell 的 Quest 设置。
> OOBE 通过 `Intent.setPackage("com.android.settings")` 强制走原生 Android 设置。

## 项目结构

```
.
├── AndroidManifest.xml
├── build.sh                     # 无 Gradle 构建脚本
├── deploy.sh                    # adb 安装/启用/切换
├── scripts/fetch-libs.sh        # 从 CyIME release 提取预编译库
├── java/com/questcn/ime/        # IME、键盘绘制、Rime 桥、OOBE
├── res/                         # 布局、颜色、图标、字符串
├── assets/
│   ├── rime_ice/                # 雾凇拼音方案/词库（GPL-3.0）
│   ├── rime_chinese/            # CyIME 中文词库/OpenCC 数据
│   ├── rime/                    # default/symbols 配置
│   └── pinyin_dict.txt          # 紧凑拼音词典
├── libs/README.md               # 预编译库说明（.so 不入库）
├── docs/icon.png
├── LICENSE                      # GPL-3.0
└── THIRD_PARTY_NOTICES.md
```

## 实现说明

- **键盘 UI**：`KeyboardView` 自绘按键、圆角、图标、按下/悬停态；布局来自
  `res/xml/keyboard_qwerty.xml` 与 `keyboard_symbols*.xml`
- **IME**：`QuestImeService` 继承 `InputMethodService`，通过
  `InputConnection.setComposingText/commitText` 与宿主交互
- **Rime**：`RimeCore` 单例负责复制 assets、首次 deploy、会话/候选/上屏；
  `RimeEngine`/`RimeCandidate` 为 CyIME JNI 桥
- **按键音**：优先反射调用系统 `vros.spatial.media.SpatialMediaManager`
  （事件 `keyboardDirectPrimary/Secondary/Backspace`），不可用时退回
  `AudioManager.FX_KEYPRESS_*`
- **OOBE**：`OobeActivity` 使用 SharedPreferences 保存 `oobe_agreed` / `oobe_done`；
  `SettingsActivity` 在未完成引导时会重定向回 OOBE

## 权限与隐私

APK 仅申请：

- `android.permission.WAKE_LOCK`（Rime 初始化/长任务时保持唤醒）
- `android.permission.VIBRATE`（按键触觉反馈）

没有 `INTERNET` 权限，不收集、不上传输入内容或设备信息。中文输入完全在本机处理。
录音探针、`RECORD_AUDIO` 权限和 FMOD 回退采样均已移除。

## 已知限制

- Quest 手柄/输入设备在系统层报告 `hasVibrator=false`；触觉反馈只能走系统
  `performHapticFeedback`，无法保证手柄震动
- 键盘音依赖 Quest 系统空间音频服务；非 Quest 设备上会退回系统按键音
- 首次 Rime deploy 较慢（词库约 50MB，索引编译需要时间）
- 项目未使用 Gradle，构建依赖 Android SDK build-tools 的具体版本

## 许可证

本项目以 **GNU GPL v3.0** 分发，完整文本见 [LICENSE](LICENSE)。

主要第三方组件：

- **CyIME** — GPL-3.0 — https://github.com/Cyletix/CyIME
- **Rime / librime** — BSD-3-Clause — https://github.com/rime/librime
- **雾凇拼音 rime-ice** — GPL-3.0 — https://github.com/iDvel/rime-ice
- **OpenCC** — Apache-2.0 — https://github.com/BYVoid/OpenCC
- **ONNX Runtime** — MIT — https://github.com/microsoft/onnxruntime
- **Android Open Source Project** — Apache-2.0 — https://source.android.com

详细版权、二进制获取方式与再分发要求见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。预编译 `.so` 不入库，由
`scripts/fetch-libs.sh` 从 CyIME 官方 release 获取；再分发构建产物时请遵守 GPL-3.0。

## 免责声明

1. 本项目为个人学习与研究项目，不是 Meta / Oculus 官方产品，与 Meta Platforms, Inc.
   无隶属、授权或背书关系。
2. 本软件按“现状”提供，不提供任何明示或暗示担保。安装、启用或使用可能改变系统输入法
   设置，甚至造成输入异常、数据丢失或其他风险，风险由使用者自行承担。
3. 本项目仅供个人学习研究，不用于商业用途；不得用于违法、侵权或恶意用途。
4. 本项目不收集、不上传输入内容或设备信息。
5. 如权利人认为本仓库中的任何内容侵犯其权利，请通过 GitHub Issue 联系维护者并提供
   权属证明；核实后将立即删除相关内容或停止分发。

## 致谢

感谢 CyIME、Rime、rime-ice、OpenCC、ONNX Runtime、Android Open Source Project
以及所有开源作者。Quest / VrShell 的布局与交互仅作为个人研究参考；相关商标归其
权利人所有。

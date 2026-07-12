# AGENTS.md

本文件用于指导在本仓库中工作的 Codex/AI 编码代理。所有结论应以当前源码和构建配置为准，不要沿用旧版 README 或历史工具摘要中的版本信息。

## 项目概况

RunTrack（中文名：AI 跑伴）是一款 Android 跑步记录应用，核心能力包括：

- GPS 路线、距离、速度、时长、步数、步频和步幅追踪；
- 前台服务与会话检查点，支持锁屏、退后台及服务重启后的跑步恢复；
- Google Maps 与高德地图双地图实现；
- Coze、百炼、火山等 AI/语音服务接入，以及 ByteRTC 实时语音；
- 跑步新闻节目、本地 TTS、语音控制和跑后新闻历史；
- Room 跑步记录、AI 分析、趋势指标、分段数据和分享卡片。

应用采用 Jetpack Compose、MVVM、Clean Architecture 与单向数据流（UDF）。

## 当前工具链

- JDK：17
- Gradle Wrapper：8.13
- Android Gradle Plugin：8.13.2
- Kotlin / Compose Compiler Plugin：2.3.10
- `compileSdk` / `targetSdk`：36
- `minSdk`：24
- Compose BOM：2025.12.01
- Room：2.8.4，数据库版本 8
- Hilt：2.58

依赖版本统一维护在 `gradle/libs.versions.toml`。不要仅为消除版本提示而升级到要求 AGP 9 或 API 37 的组合；升级后必须重新执行单测、Lint、Debug 和 Release 构建。

## 目录结构

- `app/src/main/java/com/sdevprem/runtrack/ai/`：AI、实时语音、音频路由、跑步总结和新闻节目。
- `background/`：前台追踪服务、电池优化、通知与 WakeLock。
- `common/`：扩展、权限、隐私、路线编码和指标计算工具。
- `data/`：Room、Repository、DataStore、图片文件存储及追踪实现。
- `domain/`：业务模型、追踪协调器、接口和 Use Case。
- `di/`：Hilt 模块、Dispatcher 和应用级 CoroutineScope。
- `ui/`：Compose 页面、导航、ViewModel、主题和分享卡片。
- `app/schemas/`：Room 导出的数据库 Schema，数据库变更时必须同步提交。
- `app/src/test/`：JVM 单元测试。
- `docs/specs/`：功能规格和开发说明。

## 本地配置与密钥

首次构建时复制 `local.properties.example` 为 `local.properties`，并配置真实的 Android SDK 路径：

```properties
sdk.dir=/absolute/path/to/Android/sdk
MAPS_API_KEY=
AMAP_API_KEY=
COZE_ACCESS_TOKEN=
AI_WS_VOLCANO_TOKEN=
AI_WS_BAILIAN_TOKEN=
AI_BAILIAN_API_KEY=
AI_VOLCANO_ACCESS_KEY=
AI_VOLCANO_ARK_API_KEY=
NEWS_PROGRAM_API_KEY=
```

服务凭据按以下优先级读取：Gradle `-P` 属性、环境变量、`local.properties`。Google Maps Key 由 Maps Secrets Gradle Plugin 处理。

严禁把真实 Token、API Key 或 Access Key 写入 `strings.xml`、Kotlin 源码、测试、日志或文档。`strings.xml` 只保存非敏感的端点、模型名、资源 ID 等配置。若凭据曾被提交，应同时在服务端撤销并轮换。

## 常用命令

```bash
# 单元测试
./gradlew testDebugUnitTest

# Android Lint
./gradlew lintDebug

# Debug APK
./gradlew assembleDebug

# Release APK（当前未配置正式签名）
./gradlew assembleRelease

# 完整验证
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease

# 真机仪器测试
./gradlew connectedAndroidTest
```

CI 位于 `.github/workflows/android-ci.yml`，会执行单测、Lint 和 Debug 构建。依赖更新由 `.github/dependabot.yml` 跟踪。

## 追踪系统约束

- `TrackingManager` 是定位、计时、步数、前台服务和当前跑步状态的唯一协调入口。
- 计时使用 `SystemClock.elapsedRealtime()`，不要改回系统墙上时间。
- `LocationQualityFilter` 会过滤低精度、乱序、异常速度和无意义位移；新增定位来源时必须填充 accuracy 与时间戳。
- `EmptyLocationPoint` 表示暂停边界。路线编码、距离和指标计算都不得跨越该边界连接两个定位点。
- 活跃会话每 5 秒写入 DataStore 检查点。修改 `CurrentRunState` 中需要恢复的字段时，应同步更新检查点序列化。
- 前台服务使用有超时的 WakeLock，并在跑步活跃时续期；暂停和停止时必须释放。
- 后台/定位修改需要在真机验证锁屏、暂停恢复、进程被杀、弱 GPS 和省电模式。

## 数据保存约束

- 完成跑步时通过 `CompletedRunBundle` 与 `AppRepository.insertCompletedRun()` 在同一 Room 事务内写入跑步、AI、指标和新闻历史。
- 只有事务成功后才能清理当前路线、计时、步数和 AI 会话；失败时页面必须允许重试。
- Room 当前版本为 8。升级实体结构时必须：提高版本号、提供连续 Migration、更新 `AppModule`、导出 Schema，并补迁移测试。
- 分享卡片原图存储在应用内部文件目录，Room 只保留缩略图和 `imagePath`；删除跑步记录时同步删除原图。

## AI、音频与新闻

- `AIRunningCompanionManager` 负责 AI 会话生命周期；`AudioFocusCoordinator` 协调实时 AI、本地 TTS 和新闻播报。
- 麦克风和蓝牙能力必须在权限通过后调用，不要使用宽泛的 `MissingPermission` 抑制。
- 新闻 Provider、授权开关、API Key Header/Query 配置和 TTS 渠道位于 `ai/news/`。
- 网络、音频和 AI 失败必须可恢复，不能阻断核心跑步记录。
- 不要在主线程使用 `Thread.sleep` 或执行阻塞式网络/音频操作。

## UI、权限与隐私

- Compose 页面使用 ViewModel 暴露的 StateFlow/Flow，保持单向数据流，不在 Composable 中直接持有业务状态。
- 定位、通知、麦克风和蓝牙权限按功能场景申请，不要在应用启动时一次性索取全部权限。
- 高德 SDK 初始化必须在用户明确同意隐私条款后进行。
- 应用已关闭 Android 自动备份；新增敏感数据时同时检查备份、日志和分享路径。
- Android 12+ 使用 `BLUETOOTH_CONNECT`；旧蓝牙权限仅保留到 API 30。

## 测试与提交要求

- 业务计算优先写纯 Kotlin 单元测试，避免依赖 Android Framework Stub。
- 追踪改动至少覆盖：定位质量、暂停边界、计时恢复或保存一致性中的相关项。
- 数据库改动必须有 Migration 测试和更新后的 Schema。
- 提交前执行 `git diff --check`，并扫描真实密钥和大体积调试文件。
- 不提交 `local.properties`、日志、临时截图、IDE 工作区配置或个人 AI 工具权限配置。
- 保留用户已有的无关工作区改动；不要使用 `git reset --hard` 或覆盖未确认的文件。

## 已知升级边界

- Vico 暂时固定在 `2.0.0-alpha.12`；稳定版 API 与现有图表实现不兼容，需要独立迁移。
- VolcEngine RTC `3.58.1.19400` 的部分原生库仍有 16 KB 页面未对齐告警，升级前需验证供应商兼容性和实时语音回归。
- Release 已启用 R8 与资源压缩，但正式发布前仍需配置签名、真机回归以及地图/AI 服务的生产凭据。

# 邦古工具箱

面向 CraveSaga（神绊的导师）的 Android 自动操作工具。应用通过本地截图、中文 OCR 和无障碍手势完成页面识别与坐标操作，不依赖 Root、远程 OCR 或游戏进程注入。

## 关键架构

- **Jetpack Compose + Material 3**：配置页、任务选择、槽位表单、运行日志和明暗主题。
- **任务层**：`AutomationTask` / `AutomationSession` 抽象任务流程；目前包含魂友/神器能力锻造和清理日常（beta）。
- **截图层**：`MediaProjection` 创建本地屏幕捕获通道，`ScreenCaptureEngine` 输出当前屏幕帧。
- **OCR 层**：Google ML Kit Chinese Text Recognition 在设备本地识别；`TextMatcher`、`OcrTextSimilarity` 和页面匹配器负责模糊关键词、相似度及结果解析。
- **操作层**：`AutomationAccessibilityService` 使用 `dispatchGesture` 执行点击/按住手势；悬浮播放/暂停控件使用 `TYPE_ACCESSIBILITY_OVERLAY`，不申请普通悬浮窗权限。
- **坐标层**：操作坐标以 `540×960` 设计画布保存。`CoordinateMapper` 按实际屏幕等比缩放并居中处理黑边，适配模拟器和不同手机比例。
- **配置层**：`AppSettings` 使用 SharedPreferences 保存任务、三个词条槽、阈值、书籍策略和全局点击按住时长。

## 当前任务

### 能力锻造

三个属性槽分别配置目标类别、目标词条、备选词条、阈值、金银书策略和同类型保留策略。OCR 识别到达标结果后自动选择并保存；无效、未解锁或手动跳过的槽位按配置处理。

### 清理日常（beta）

要求从首页或关卡页开始，依次处理训练室、仗魂、探索、欧特、答题和每日商店免费石。每个操作之间等待 3 秒，并在每个子任务开始时确认目标页面。该任务仍可能因游戏页面变化或未完整测试的流程而中断。

## 权限与运行

首次运行需要启用“自动操作”无障碍服务；Android 13 及以上还需要通知权限。启动任务时系统会请求 MediaProjection 屏幕捕获授权。应用会尝试将 CraveSaga 切换到前台。

全局“点击按住时长”默认 `500ms`，范围 `100–1500ms`，每档 `50ms`，所有自动操作共用该设置。

## 开发环境

- JDK 17
- Android SDK 36
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.2.21
- 最低 Android API 26（Android 8.0），兼容 Android 9 模拟器

## 构建

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
```

正式包输出到 `app/build/outputs/apk/release/app-release.apk`。签名信息放在本地 `keystore.properties`，该文件不会提交到仓库。

## 安全边界

项目不包含 Root、Shizuku、远程脚本、远程 OCR、游戏进程注入或设备指纹收集。反编译目录、构建目录、临时截图、签名材料和本机配置均由 `.gitignore` 排除。

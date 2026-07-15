# Tueku! 拉火车扑克牌：原生 Android 版

此子工程使用 Kotlin 和 Android SDK 实现，不依赖 Kivy、Python 或 Buildozer。

功能：3/4 人新游戏、AI 自动出牌、同点数配对收回中心牌堆、淘汰与胜负判断，以及用 Android `SharedPreferences` 保存和继续游戏。界面由原生 Canvas 绘制，无需额外图片资源。

使用 Android Studio 打开 `android-native`，安装 Android SDK 35 与 JDK 17 后即可运行；调试 APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。发布前请修改 `applicationId` 并配置签名。

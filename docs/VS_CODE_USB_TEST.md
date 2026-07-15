# 在 VS Code 中通过 USB 真机测试

## 已配置的开发环境

- JDK 17：`D:\Professional software\jdk17`
- Android SDK：`C:\Users\zhangyu2024\AppData\Local\Android\Sdk`
- SDK 组件：Platform Tools 37.0.0、Android Platform 35、Build Tools 35.0.0
- Gradle Wrapper：8.7（匹配 Android Gradle Plugin 8.6.1）
- VS Code 扩展：Kotlin、Java、Gradle for Java、Android Gradle Tools

修改用户环境变量后，需要完全退出并重新打开 VS Code，新的集成终端才能读取更新后的 `JAVA_HOME`、`ANDROID_HOME` 和 `PATH`。

## 手机准备

1. 手机系统须为 Android 6.0（API 23）或更高版本。
2. 在“关于手机”中连续点击“版本号”约 7 次，开启开发者选项。
3. 在开发者选项中开启“USB 调试”。部分品牌还需开启“USB 安装”或“通过 USB 验证应用”。
4. 使用支持数据传输的 USB 线连接电脑，USB 用途选择“文件传输”。
5. 保持手机解锁；首次连接时，在手机上允许这台电脑的 RSA 调试授权。

## 先验证设备

在 VS Code 中按 `Ctrl+Shift+P`，运行 `Tasks: Run Task`，选择 `Android: 检查 USB 设备`。

正常结果中，设备序列号后应显示 `device`：

```text
List of devices attached
XXXXXXXX    device product:... model:...
```

- `unauthorized`：解锁手机并接受 RSA 授权，然后重新执行任务。
- `offline`：重新插拔 USB，执行 `adb kill-server` 后再执行 `adb start-server`。
- 列表为空：更换数据线/USB 接口，确认 USB 模式，并安装手机品牌提供的 Windows USB 驱动。

## 构建、安装和启动

最直接的方式：按 `Ctrl+Shift+P`，运行 `Tasks: Run Task`，选择 `Android: USB 安装并启动`。它会依次构建 Debug APK、安装到当前唯一的 USB 设备，然后启动 `MainActivity`。

只构建 APK时可按 `Ctrl+Shift+B`。输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以打开左侧 Android 面板：

1. 选择 Gradle Root 为当前项目，Module 为 `app`，Variant 为 `debug`。
2. 用设备选择器选中 USB 手机。
3. 点击 Install；配置已设为安装后自动启动。
4. 运行 `Android: Logcat` 查看日志；崩溃时筛选包名 `com.example.tuoshouji` 或级别 Error。

## 等价的终端命令

```powershell
adb devices -l
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
adb shell am start -n com.example.tuoshouji/.MainActivity
```

若同时连接了多个设备，先从 `adb devices` 取得序列号，并在 ADB 命令中加入 `-s <序列号>`；或者直接使用 Android 面板的设备选择器。

## 建议的首轮测试

1. 冷启动游戏，确认横屏、字体、背景图和所有音频资源能加载。
2. 分别开始 2、3、4 人游戏，完成出牌、AI 回合、配对回收、淘汰和胜负判定。
3. 游戏中切到后台后返回，确认状态和音频行为正常。
4. 强制结束应用并重新打开，验证 `SharedPreferences` 的继续游戏功能。
5. 锁屏/解锁、拔插 USB、来电或音频焦点变化后再次检查。
6. 测试过程中保持 Logcat 打开，重点检查 `FATAL EXCEPTION`、资源找不到和音频解码错误。


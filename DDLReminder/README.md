# DDL 清单（DDL Reminder）

一个精简的 Android 原生 To Do List，使用 Kotlin、Jetpack Compose、Room、MVVM、StateFlow 和 Material 3。

## 功能

- 多清单管理：新建、重命名、删除；删除清单级联删除任务并取消提醒。
- 任务管理：新建、编辑、删除；DDL 可为空。
- DDL 提醒：截止前 24 小时、12 小时各一次高优先级 Heads-up 通知。
- 通知点击可打开任务所属清单页面。
- 通知渠道 `IMPORTANCE_HIGH`，通知 `PRIORITY_HIGH` + `CATEGORY_REMINDER`，不使用悬浮窗权限。
- 已完成任务灰色显示并保留 24 小时，之后由 UI 过滤 + WorkManager 清理。
- 未完成任务按 DDL 升序排序，无 DDL 的任务排最后。
- 倒计时格式：`距离截止时间还有XXdayXXhourXXmin`，每分钟刷新。
- 颜色档位：> 2 天绿色；1~2 天橙色；< 1 天或已截止红色；无 DDL 灰色。
- 完成/删除任务、删除清单时取消提醒。
- BOOT_COMPLETED、应用更新、时间/时区变化后重新调度未来提醒。
- Android 13+ 通知权限请求；Android 12+ 精确闹钟权限检查与降级提示。

## 构建

要求：

- JDK 17 或 21
- Android SDK Platform 35
- Android Build Tools 36.0.0（项目当前指定版本）
- Gradle 8.9（项目包含 Wrapper）

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew assembleDebug
```

Windows:

```powershell
$env:JAVA_HOME="C:\path\to\jdk-21"
$env:ANDROID_HOME="C:\path\to\Android\Sdk"
.\gradlew.bat assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

仓库提供可直接安装的 Debug 签名 APK：`../DDL-Reminder-v1.0.apk`（工作区根目录）。

Gradle Wrapper 默认使用华为云镜像下载 Gradle 8.9；如需官方源，可修改 `gradle/wrapper/gradle-wrapper.properties`。

若在其他电脑构建，请确保已安装 SDK Platform 35 与 Build Tools 36.0.0；也可以将 `app/build.gradle.kts` 中的 `buildToolsVersion` 改为本机已安装版本。

## 权限说明

Manifest 声明：

- `POST_NOTIFICATIONS`：Android 13+ 通知权限，首次启动请求。
- `SCHEDULE_EXACT_ALARM`：Android 12/12L 精确闹钟特殊权限。
- `USE_EXACT_ALARM`：Android 13+ 精确闹钟权限，安装时授予。
- `RECEIVE_BOOT_COMPLETED`：重启后恢复提醒。
- `VIBRATE`、`WAKE_LOCK`。

没有声明 `SYSTEM_ALERT_WINDOW`，提醒完全使用系统通知横幅实现。

如果设备或系统限制精确闹钟，应用会自动降级为 `setAndAllowWhileIdle` 的非精确调度，并在首页/任务页提示用户，不会崩溃。精确闹钟设置入口为系统“闹钟和提醒”页面。

## 提醒规则

- 仅 DDL 非空且未完成的任务安排提醒。
- 创建或编辑任务时，只调度未来尚未到达的提醒点；已过去的时间点不补发。
- 通知 ID 由任务 id 和提醒节点组合派生。
- 每个提醒节点有独立的 `remind24Sent` / `remind12Sent` 状态，先原子抢占再发送，避免重复通知。
- 完成任务立即取消提醒；取消完成会重置提醒状态并重新调度未来提醒。
- 通知权限被拒绝时不会标记提醒为已发送，以便用户授权后未来的提醒仍能正常工作；已过去的时间点仍不补发。
- 应用被系统强制停止后，Android 会清除闹钟，应用需要重新打开才能恢复调度；重启/更新时若应用可收到广播则会自动恢复。

## 工程结构

```text
app/src/main/java/com/example/ddlreminder/
├── MainActivity.kt              # 权限、通知点击入口、Compose 宿主
├── TodoApplication.kt           # 通知渠道、周期清理
├── data/                        # Room 实体、DAO、数据库、Repository
├── reminder/                    # AlarmManager、BroadcastReceiver、通知
├── ui/                          # Compose 页面、对话框、ViewModel、主题
├── util/                        # 时间格式、排序规则
└── work/                        # 24 小时完成项清理 Worker
```

## 测试

```bash
./gradlew testDebugUnitTest
```

单元测试覆盖倒计时格式、三档颜色边界、DDL 排序、无 DDL 排最后、完成项 24 小时过滤、通知 ID 唯一性。

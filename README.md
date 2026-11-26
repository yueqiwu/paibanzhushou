# 班表小历（Android 原生版）

> 应用商店上架名：**班表小历**（原名「排班助手」，商店内已有同名应用被占用而改名；包名一直保持 `com.bilu.paibanzhushou`，老用户可原地升级）。

给轮班工作者用的排班日历：设置一次「排班周期 + 锚点日期」，之后每一天的班次都会自动排好；
任意年份都能翻，多个人一起管，还能一键对比同事这个月都上什么班。

Kotlin + Jetpack Compose 实现，无第三方运行时依赖——持久化用 SharedPreferences，网络用 HttpURLConnection，
JSON 用 org.json，农历/节气/节假日全部本地算。

- 包名 / applicationId：`com.bilu.paibanzhushou`
- 当前发布版本：**versionName 1.0.35，versionCode 135**
- minSdk 24（Android 7.0），targetSdk 37，Compose Material3

## 功能

- **周期排班**：任意天数的班次周期 + 锚点日期，之后按顺序无限循环，日历自动铺满
- **不限年份**：往前翻、往后翻到任何年份、任何月份，班次都能正常显示
- **小屏一屏显示**：顶部标题卡与排班设置卡合并成一张，日历行高按真实可用高度实测（不再靠经验估算），5.5 英寸小屏也能一屏看完整个月，日期、农历、班次胶囊不重叠
- **多人独立配置**：每个人一套班次、周期、锚点；长按人员名称可拖拽排序，点 × 删除
- **整页左右滑动切人**：在首页任意位置右滑 / 左滑切到上一位 / 下一位；人员标签行、班次图例行内左右滑只横向滚动自己的内容（班次再多也不会被裁掉）、不切人，日期网格保留左右滑翻月
- **单日微调**：临时调班只改一天，单指指定当天班次，或者清除这一天的设置，周期本身不受影响
- **周末 / 节假日自动休息**：指定某些班次落在周六日或法定节假日时自动改休息
- **农历 · 节气 · 节日**：每个格子里有农历日期、传统节日、二十四节气、公历节日；法定节假日标「假」，调休上班标「班」
- **同事排班对比表**：一张表看完所有人的整月班次，左右滑动看全员，上下滑动看整月，今天一行自动高亮并滚动到眼前
- **班次闹钟**：勾选要提醒的班次、给每个班次单独设时间，写入系统精确闹钟；到点全屏响铃页 + 循环铃声 + 震动 + 关闭 / 5 分钟后提醒；改排班、重启手机都会自动重写；权限缺失时逐项标出去处，机型不允许自动跳转时弹说明框并给出手动设置路径
- **闹钟列表**：看清未来 60 天每天会不会响，可单独取消某一天（改排班、开机都不会把它加回来）
- **桌面小组件**：4×2 大卡（7 天横排）、2×1 小卡（4 上 3 下）、2×1 今日班次卡（所有人员今天的班次，一人一列，最多并排 5 人，超出显示 +N；人少时字与胶囊自动放大），换人、改排班后自动刷新
- **从旧版导入**：旧版（uni-app 版）点「把数据转过去」→ 深链跳转确认导入；跳转被系统拦掉时走「排班设置 → 展开 → 从剪贴板导入」。导入前会列出对方人员与锚点日期，导入时整份覆盖并留一份快照，页面底部可「恢复导入前的数据」；引导只在原生版还没数据时出现，点「稍后」不再重复弹
- **本地保存，不上传**：排班数据只在你手机里，不注册、不登录、不联网也能看（除节假日标记外无需网络）；仅每天首次打开时匿名上报一次随机安装标识 + 日期 + 版本号，用于统计使用人数，不含任何排班或个人信息

## 下载 / 官网

| | |
| --- | --- |
| 下载 APK | <https://qipaclub.top/paibanzhushou/banbiaoxiaoli-1.0.35.apk>（8,806,779 字节）｜备用：<https://github.com/yueqiwu/paibanzhushou/releases/download/v1.0.35/banbiaoxiaoli-1.0.35.apk> |
| 官网（自有域名） | <https://qipaclub.top/paibanzhushou/> |
| 官网（GitHub Pages） | <https://yueqiwu.github.io/paibanzhushou/> |
| 源码 | <https://github.com/yueqiwu/paibanzhushou> |
| 版本 / Release | v1.0.35（versionCode 135，正式签名） |
| 包校验 | SHA-256 `807CB0EBC3E981FBD17BB255098C2E48F80C88A3BCCD4BD6D6D483EC77766F1E` |

官网源码就是本仓库的 `docs/` 目录（纯静态 `index.html` + `style.css` + `img/`，全部相对路径），
可直接整目录拷到任意静态站点的子路径下，例如 `qipaclub.top/paibanzhushou/`。

## 代码结构

```
app/src/main/java/com/bilu/paibanzhushou/
├─ MainActivity.kt             // 沉浸式状态栏 + setContent { PaibanzhushouTheme { AppRoot() } }
├─ PaibanApp.kt                // Application：初始化渠道、通知、闹钟重写
├─ core/
│  ├─ Dates.kt                 // 日期解析/格式化/加减/相差天数/月首月末（ThreadLocal GregorianCalendar）
│  ├─ Schedule.kt              // 周期排班求解：shouldAutoRest / resolveDayBan / buildSchedule / toMap
│  ├─ BanColors.kt             // 自定义班次名 → 颜色（含与旧实现一致的 32 位哈希行为）
│  └─ Lunar.kt / LunarData.kt  // 1900–2100 农历、节气、传统节日与公历节日
├─ data/
│  ├─ Store.kt                 // SharedPreferences 读写（键名 paiban_people_v1 等，升级不丢数据）
│  ├─ People.kt                // Person 模型 + 规范化 + 旧版本数据迁移
│  ├─ AlarmSettings.kt         // 闹钟配置：勾选班次、每班次时间、铃声、写入开关
│  ├─ HolidayRepo.kt           // 节假日 / 调休数据源（多源合并 + 带版本号的缓存）
│  ├─ Migrate.kt               // 接收旧版（uni-app）迁移数据：解码/校验、覆盖前快照、撤销、剪贴板兜底
│  └─ Updater.kt               // 应用内更新：多源测速、下载、校验大小后拉起安装
├─ alarm/
│  ├─ ShiftAlarm.kt            // 把排班翻译成系统精确闹钟（未来 60 天）
│  ├─ AlarmReceiver.kt         // 到点触发：提醒通知 + 全屏响铃
│  ├─ AlarmRingService.kt      // 前台服务：放 TYPE_ALARM 铃声 + 震动（不依赖全屏页）
│  ├─ AlarmRingActivity.kt     // 响铃页：关闭 / 5 分钟后提醒
│  ├─ BootReceiver.kt          // 开机、改时间后重写闹钟
│  └─ AlarmNotifications.kt    // 通知渠道与文案
├─ ui/
│  ├─ AppRoot.kt               // 页面路由（首页 ↔ 同事排班表 ↔ 闹钟提醒）、toast、字号锁定
│  ├─ IndexState.kt            // 首页全部业务状态与操作
│  ├─ IndexScreen.kt           // 首页：背景光斑/点阵、Hero、排班设置卡、彩花
│  ├─ CalendarSection.kt       // 月历：农历/节气/节日、打点、折展、水印、左右滑翻月、单日覆盖
│  ├─ MonthPicker.kt           // 系统原生日历/表盘入口 + 铃声选择弹层
│  ├─ Dialogs.kt               // 应用风格确认框
│  ├─ DragSort.kt              // 长按拖拽排序（人员、周期班次互换）
│  ├─ PersonSwipe.kt           // 整页左右滑动切人
│  ├─ MemberTableScreen.kt     // 同事排班对比表
│  ├─ AlarmSettingsScreen.kt   // 闹钟提醒设置页
│  ├─ AlarmListScreen.kt       // 待响闹钟列表
│  ├─ Backdrop.kt              // 首页背景绘制 + 预烘焙「整屏背景 + 高斯模糊」位图（进程内缓存）
│  ├─ GlassCard.kt             // 真实毛玻璃卡片：按卡片坐标裁切预模糊背景
│  ├─ Ui.kt                    // 等比换算、配色常量、玻璃卡/按钮/输入框等基础组件
│  └─ theme/                   // 静态配色（不使用 Material You 动态取色）
├─ widget/                     // 桌面小组件：4×2 大卡、2×1 小卡、2×1 今日班次卡、数据桥
└─ wx/                         // 可选：跳转微信小程序（需微信开放平台完成开发者资质认证）
```

## 实现要点

- **等比换算**：以 750 为基准宽度做等比缩放（`scale = screenWidthDp / 750`，即 750 单位 = 屏宽），
  并锁定 `fontScale = 1f`，不受系统字体大小影响，布局在任何字号下都不走形。
- **日历行高**：`clamp(屏高 × 0.08, 54, 66)dp`，6 行展开 / 1 行折叠（只显示选中日所在周，周日开头）；
  800dp 高的机器首屏一屏放下，更矮的机器整体滚动。
- **日历左右滑动翻月**：用**虚拟无限 Pager**（页索引 ↔ 月/周偏移，只算新出现的那一页），
  不采用「上一/当前/下一」三页 + 落位后瞬回中间的写法（每次落位都要重算三页网格，快滑会掉帧）。
- **选中态**：整格 `#3c9cff` 圆角矩形（12）+ 白字，顶/底小标签同步变蓝；今天格显示「今日」。
- **农历文字**：`#333`、字号 ×0.8（用字号缩放代替 `scale(0.8)`，避免每格一个渲染层）。
- **农历/节气/节日算法**：`utcDays()` 用 Hinnant 公式（`mp = (m + 9) % 12`，三月为岁首）；
  节气索引按**公历月**取表；公历节日用公历月日做键；`sTermInfo` 的月份序列按 1/2/1/2 交替。
- **毛玻璃（真实背景模糊）**：Android 没有 backdrop blur API，而首页背景是固定的（不随内容滚动），
  所以启动时**只烘焙一次**：`Backdrop.kt` 把「整屏渐变 + 点阵 + 光斑」画进位图 → 降采样 1/3 →
  软件 box blur ×3（近似高斯，纯 Kotlin，不依赖 RenderEffect）→ 进程内缓存。
  卡片绘制时按自己的根坐标 `drawImage` 裁出对应区域，等价于 backdrop-filter。
  卡片内再叠白膜 + 1 描边 + 阴影；白膜透明度 hero/设置卡 0.62、日历卡 0.66（真实模糊要透出来）。
  API 31 以下同样可用（软件模糊），无需回退。
- **图例胶囊**：统一高 46（内容 34 + 上下 6）、文字显式 `lineHeight = 34`，「假 / 班」方块 30 且行高 20 居中，
  色点后留 8 间隙，解决行高不一致导致的胶囊不等高、方块字形偏下。
- **日期 / 月份 / 闹钟时间选择**：全部走系统原生 `android.app.DatePickerDialog` / `TimePickerDialog`。
  用 `ContextThemeWrapper` + `applyOverrideConfiguration` 固定简体中文；
  注意不能直接用 `createConfigurationContext`——拿不到 Activity 的 window token，
  会抛 `WindowManager$BadTokenException: Unable to add window -- token null is not valid`。
  月份选择没有原生「只选年月」模式，点目标月任意一天再确定即可，回调统一取当月 1 号。
- **节假日数据**：主源 `https://timor.tech/api/holiday/year/{year}`（`holiday:true` = 放假「假」、
  `false` = 调休补班「班」），备源 `https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/{year}.json`（多镜像合并）。
  主源对某些年份会返回 `{"code":0,"holiday":{}}`，这种情况按失败处理继续回落到备源；缓存带 `ver`，旧缓存自动重拉。
- **闹钟**：按日期写 `setAlarmClock` 精确闹钟（未来 60 天），声音由前台服务 `AlarmRingService` 自己以
  `USAGE_ALARM` 播放——全屏页可能被系统的「全屏通知」权限拦下，声音不能依赖它。

## 性能（90Hz 真机实测）

首版滑日历明显发涩，定位到几类问题后逐一修掉（协议：暖机 10 次后左右交替快滑 8 次，`dumpsys gfxinfo`）：

| 问题 | 修法 |
| --- | --- |
| 空闲时仍在满帧重绘：背景的点阵/光斑用 `rememberInfiniteTransition` 循环动画，每帧重画整屏（空闲 2 秒内渲染 164 帧，≈82fps） | 背景动画去掉（幅度极小、肉眼无差别），整屏背景**预渲染成位图**贴图 → 空闲 **0 帧** |
| 每个日期函数都 `Calendar.getInstance()`（单次 30~60µs），一页 42 格 × 多次调用 → 单帧 20~40ms | `Dates` 改 `ThreadLocal<GregorianCalendar>` 复用；节气表缓存、`utcDays` 纯算术、农历文字加 `LruCache` |
| 三页横向分页容器每次落位重算整页；单元格每帧重算农历/颜色 | 虚拟无限 Pager（只算新出现的那一页）+ 单格静态数据按「月/周」进程内缓存 |
| 毛玻璃：每张卡挂 `Modifier.blur`（RenderEffect），3 块 1272×2800 / 13.6MB 的模糊缓冲，每帧 ~2ms GPU | 整屏背景 + 模糊预烘焙成一张位图，卡片按坐标贴图 |
| 农历文字每格一个 `graphicsLayer`（一页 42 个渲染层） | 用字号 ×0.8 代替 `scale(0.8)`，去掉逐格图层 |

改造前后（8 次快滑 / 一次一个月份）：

| 指标 | 初版 | 当前 |
| --- | --- | --- |
| 50 分位 / 90 分位 / 99 分位 | 11 / 14 / 57 ms | 10 / 11 / 13~15 ms |
| 掉帧 | 2.2%（99 分位 57ms 尖刺） | 0.5~2.7% |
| Slow UI thread | 6 | 1~6 |
| GPU 50/90/95/99 | 8 / 9 / 10 / 14 ms | 5 / 6 / 7 / 7 ms |
| 空闲帧数 | 82fps 持续重绘 | 0 |

> 该机刷新率 90Hz，预算仅 11.1ms，所以「12ms 的帧」本来就已超时；现在 90 分位压到 11ms、99 分位 13~15ms。
> 真机实测毛玻璃：卡片内亮度 std 29.7、边缘能量 12.9，卡片外同背景 73.6 / 30.0（细节被显著抹平 = 模糊生效）。

## 构建

```powershell
$env:JAVA_HOME='E:\androidStudio\jbr'

.\gradlew.bat assembleDebug     # 调试包：app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat assembleRelease   # 正式包：app\build\outputs\apk\release\app-release.apk

# 校验签名（v2 方案，证书 CN=WYQ）
& '$env:JAVA_HOME\bin\apksigner.bat' verify --print-certs app\build\outputs\apk\release\app-release.apk
```

### 正式包签名

签名信息放在仓库外的两个文件里（均已加入 `.gitignore`，不进版本库）：

- `keystore/paibanzhushou.jks`：PKCS12，alias `paibanzhushou`，口令 `Paiban@2026`，有效期 10950 天，证书 SHA-256 指纹
  `7E:38:0A:33:DF:C6:A0:CC:1D:A7:4E:F0:E3:4A:82:43:CB:5F:27:46:83:92:B9:FD:6E:40:36:AD:BE:8B:43:11`。
- `keystore.properties`：`storeFile=keystore/paibanzhushou.jks` / `storePassword=...` / `keyAlias=paibanzhushou` / `keyPassword=...`，
  由 `app/build.gradle.kts` 顶部的 `Properties` 读取后注入 `signingConfigs.release`。

> **正式包关闭了 R8 优化**（`optimization { enable = false }`）。开启 AGP 的 `packageScope` 优化后，启动会崩在
> `androidx.startup.InitializationProvider` → `ProcessLifecycleInitializer`，报
> `java.lang.IllegalAccessError: Class kotlin.collections.f extended by class jg is inaccessible`。稳定优先，保持不混淆。

> 换手机测试正式包时注意：release 与 debug 签名不同，`adb install -r` 会静默失败（版本号不变），
> 需先 `adb uninstall com.bilu.paibanzhushou`。

## 开发辅助

- `tools/find_ui.py`：`adb shell uiautomator dump` 后按正则打印控件文字与点击坐标（实机调试定位用）。
- `tools/gen_icons.py`：按 `res/drawable/logo.png` 生成各密度启动图标（含圆形图标）。
- `tools/bt.py`：官网部署（宝塔 API 上传页面 / 让服务器侧下载 APK / 轮询文件大小 / 删除旧包）。
- `shots/`：开发与回归过程中的实机截图。

## 已知限制

- **全屏响铃页**依赖系统的「全屏通知 / 后台弹出界面」权限，未开启时锁屏只弹一条通知（铃声不受影响，由前台服务播放）。
- 部分厂商 ROM 对精确闹钟有额外限制（自启动、电池白名单），需要在系统设置里给本应用放行。
- 跳转微信小程序需要微信开放平台完成**开发者资质认证**，个人主体未认证时不可用，该入口在代码中保留但未启用。

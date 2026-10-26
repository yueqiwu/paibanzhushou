# 班表小历（Android 原生版）

> 应用商店上架名：**班表小历**（原名「排班助手」，因商店内已有同名应用被占用而改名；包名仍为 `com.bilu.paibanzhushou`，老用户可原地升级）。

由 uniapp 项目 `D:\code\paiban_uniapp`（Vue3 + uvue/uni-app，版本 1.0.3 / 102）**1:1 移植**为 Android 原生 App，
使用 Kotlin + Jetpack Compose 重写，UI 尺寸、配色、文案、交互与业务规则均对准源项目；**wgt 热更新相关代码已丢弃**。

- 包名 / applicationId：`com.bilu.paibanzhushou`
- 当前发布版本：**versionName 1.0.5，versionCode 105**（初始移植版本对齐源 manifest.json 的 1.0.3 / 102；1.0.4 起日历不再限制前后一年）
- minSdk 24（Android 7.0），targetSdk 37，Compose Material3
- 无第三方运行时依赖：持久化用 SharedPreferences，网络用 HttpURLConnection，JSON 用 org.json

## 下载 / 官网

| | |
| --- | --- |
| 下载 APK | <https://qipaclub.top/paibanzhushou/banbiaoxiaoli-1.0.5.apk>（8,575,654 字节）｜备用：<https://github.com/yueqiwu/paibanzhushou/releases/download/v1.0.5/banbiaoxiaoli-1.0.5.apk> |
| 官网（GitHub Pages） | <https://yueqiwu.github.io/paibanzhushou/> |
| 官网（自有域名） | <https://qipaclub.top/paibanzhushou/>（宝塔站点 `c:/wwwroot/qipaclub.top/paibanzhushou`，APK 也在该目录，官网直链下载） |
| 源码 | <https://github.com/yueqiwu/paibanzhushou> |
| 版本 / Release | v1.0.5（versionCode 105，正式签名） |
| 包校验 | SHA-256 `aa158af138f5a4c0a2047ca937b9d4fea9a4482cd37de7f30806032b797c1538` |

官网源码就是本仓库的 `docs/` 目录（纯静态 `index.html` + `style.css` + `img/`，全部相对路径），
可直接整目录拷到任意静态站点的子路径下，例如 `qipaclub.top/paibanzhushou/`。

## 目录结构

```
app/src/main/java/com/bilu/paibanzhushou/
├─ MainActivity.kt          // 沉浸式状态栏 + setContent { PaibanzhushouTheme { AppRoot() } }
├─ core/
│  ├─ Dates.kt              // 对应 utils/date.js（parseYmd/formatYmd/addDays/addMonths/diffDays/mod…）
│  ├─ Schedule.kt           // 对应 utils/schedule.js（shouldAutoRest/resolveDayBan/buildSchedule/toMap）
│  ├─ BanColors.kt          // 对应 utils/ban-color.js（含 JS 32 位溢出哈希行为）
│  ├─ Lunar.kt / LunarData.kt // 对应 wu-calendar 内置的 1900-2100 农历/节气库
├─ data/
│  ├─ Store.kt              // 对应 utils/storage.js（键名完全一致：paiban_people_v1 等）
│  ├─ People.kt             // 对应 utils/people-store.js（Person 模型 + 规范化 + 迁移）
│  └─ HolidayRepo.kt        // 节假日/调班数据源，等价 loadHolidayAndBanList + uni.request
└─ ui/
   ├─ AppRoot.kt            // 页面路由（首页 ↔ 同事排班表）、toast、rpx 换算、字体缩放锁定
   ├─ IndexState.kt         // 对应 pages/index/index.vue 的 data/computed/methods 全量逻辑
   ├─ IndexScreen.kt        // 首页 UI：背景光斑/点阵、Hero、排班设置卡、彩花
   ├─ CalendarSection.kt    // 对应 components/schedule-calendar.vue + 自绘 wu-calendar（月历/折叠/水印/打点）
   ├─ MonthPicker.kt        // uni picker 风格的滚轮年/月/日选择器
   ├─ Dialogs.kt            // uni.showModal 风格确认框
   ├─ DragSort.kt           // 对应 mixins/long-press-sortable.js（长按 300ms 拖拽排序/互换）
   ├─ MemberTableScreen.kt  // 对应 pages/index/member_table.vue
   ├─ Backdrop.kt           // 首页背景绘制 + 预烘焙的「整屏背景 + 高斯模糊」位图（进程内缓存）
   ├─ GlassCard.kt          // 真实毛玻璃卡片：直接贴预模糊背景位图（按卡片坐标裁切）
   ├─ Ui.kt                 // rpx→dp 换算、配色常量、玻璃卡/按钮/输入框等基础组件
   └─ theme/                // 自定义静态配色（不使用 Material You 动态取色）
```

## 关键换算与还原点

- **rpx**：`Rpx.scale = screenWidthDp / 750`，即 750rpx = 屏宽；同时锁定 `fontScale = 1f`（uni 的 rpx 不受系统字体大小影响）。
- **日历行高**：源 `wu-calendar` 的 `itemHeight = 70`（单位是 px≈dp），故行高固定 **70dp**，展开 6 行 = 420dp，折叠 1 行 = 70dp（只显示选中日所在周，周日开头）。
- **日历左右滑动切换**：源用 `swiper`（`slideSwitchMode` 默认 `horizontal`、`circular`、`duration 500`）承载「上一 / 当前 / 下一」三页。移植**没有**照抄「三页 + 落位后瞬回中间页」（那样每次落位都要重算三页网格，快滑会掉帧），而是用**虚拟无限 Pager**：页索引 ↔ 月 / 周偏移（`pageOfMonth` / `monthOfPage`），页面内容只由页索引决定，落位后只把「正在浏览」的月 / 周写回 state；左右箭头走 `animateScrollToPage`。展开态滑一次 = 一个月，折叠态滑一次 = 一周（`PagerSnapDistance.atMost(1)`，与源 `swiper` 一次只翻一页一致），**两者都只切换「正在浏览」的月份 / 周，选中日一动不动**：选中日只在「点日期格」「点今天」「跨天」时变化，所以翻月时那张蓝底格子要么还停在原处（如 10-26），要么根本不在本月就不显示 —— 不会像源 `updateWeeks()` 那样跳到 1 号，也不会跟着月份一起漂。折叠态另用一个 `calendarWeekMs`（浏览锚点，与选中日解耦）决定显示哪一周，展 / 折切换时两个视图互相对齐；相邻页网格不参与选中高亮（避免「幽灵选中」）。
- **选中态**：整格 `#3c9cff` 圆角矩形（12rpx）+ 白字，顶/底小标签同步变蓝，与 `actMultipleStyle` 一致。
- **农历文字**：源 `App.vue` 全局类写的是 `#f1f1f1`，但 `wu-calendar-item.vue` 里的 scoped 规则 `.wu-calendar-item__weeks-lunar-text{font-size:24rpx;color:#333}` 带 `[data-v]`、特异性更高，实际渲染为 **#333**（真机像素取样确认）。原生按 `#333`（选中格为白字）、`24rpx × 0.8`、`width 16rpx` + 右偏 `8rpx` 实现 —— 汉字因宽度不足逐字竖排，即源版右上角那列小字。文字优先级与 `dayText()` 一致：今天 → 「今日」，其次公历节日、节气，再是「初一 → 月份名」，最后是农历日。
- **农历/节气/节日算法**（`core/Lunar.kt`）：按 `wu-calendar/calendar.js` 逐条对齐，修掉移植时 4 处错误 —— ① `utcDays()` 的 Hinnant 公式 `mp` 应为 `(m + 9) % 12`（三月为岁首，写成 `(m+11)%12` 会让公历整体偏移、农历全错）；② 节气索引必须用**公历月** `getTerm(y, m*2-1|m*2)`，原先误用农历月；③ 公历节日查表键为 `m-d`（农历枚举之外的第二轮查表），原先误用农历数字；④ `sTermInfo` 每个 5 位十六进制块解码成 6 位十进制后按 1/2/1/2 拆 4 个节气日（第 4 个曾只取 1 位，霜降被读成 3 号）。例：2026-10-01 国庆 / 10-08 寒露 / 10-10 九月 / 10-18 重阳节 / 10-23 霜降。
- **毛玻璃（真实背景模糊）**：uni 的 `.card` 用 `backdrop-filter: blur(14px)`，Android/Compose 没有 backdrop blur API。
  首页背景是固定的（不随内容滚动），模糊结果也就静态，所以启动时**只烘焙一次**：`Backdrop.kt` 把「整屏渐变 + 点阵 + 光斑」画进位图 → 降采样 1/3 → 软件 box blur ×3（近似高斯，纯 Kotlin，不依赖 RenderEffect）→ 进程内缓存（`GlassBackdropCache`）。卡片绘制时按自己的根坐标`drawImage` 裁出对应区域，等价于真 backdrop blur，且**每帧几乎零成本**。
  卡片内再叠一层白膜 + 1rpx 白描边 + 阴影，与源样式一致；白膜透明度 **hero/设置卡 0.62、日历卡 0.66**（源为 0.88/0.78，为让真实模糊可见而调低）。
  API 31 以下同样可用（软件模糊），无需回退；同事排班表页背景是纯色，仍用普通半透明白卡。
  实测（OnePlus PLR110 / Android 17）：卡片内亮度 std 29.7、边缘能量 12.9，卡片外同背景 73.6 / 30.0（细节被显著抹平 = 模糊生效）。
- **图例（节假日 / 调班）**：统一胶囊高度 `46rpx`（内容 `34rpx` + 上下 `6rpx`）、文字显式 `lineHeight = 34rpx`，「假 / 班」方块 `30rpx` 且文字行高 `20rpx` 居中，色点后留 `8rpx` 间隙 —— 修掉 Material 默认行高导致的胶囊不等高、方块字形偏下。363dp 屏上第一行 5 个班次、第二行「节假日 / 调班」，与源 732rpx > 702rpx 的换行结果一致。
- **节假日数据源**：主源 `https://timor.tech/api/holiday/year/{year}`（`holiday:true` = 放假「假」、`false` = 调休补班「班」），备源 `https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/{year}.json`（gcore 镜像兜底）；一次拉「今天所在年份 ±1 年」合并，缓存键 `paiban_holiday_ban_v1` 与字段格式不变（同事排班表共用），同一天不重复请求，全部源失败则保留旧缓存。
- **弹层/提示**：toast 与 `uni.showToast` 同款居中深色小条；确认框与 `uni.showModal` 同款（标题居中 + 分隔线 + 取消/确定）；日期/月份选择为 uni picker 同款滚轮。

## 性能（90Hz 真机实测）

首版滑日历明显发涩，定位到三类问题后逐一修掉（协议：暖机 10 次后左右交替快滑 8 次，`dumpsys gfxinfo`）：

| 问题 | 修法 |
| --- | --- |
| 空闲时仍在满帧重绘：背景的点阵/光斑用 `rememberInfiniteTransition` 循环动画，每帧重画整屏（空闲 2 秒内渲染 164 帧，≈82fps，GPU 被长期占满，滑动抢不到预算） | 背景动画去掉（幅度极小、肉眼无差别），并把整屏背景**预渲染成位图**贴图 → 空闲 **0 帧** |
| 每个日期工具函数都 `Calendar.getInstance()`（单次 30~60µs），一页 42 格 × 多次调用 → 单帧 20~40ms | `Dates` 改 `ThreadLocal<GregorianCalendar>` 复用；`Lunar` 节气表缓存、`utcDays` 纯算术替代 UTC Calendar、农历文字加 `LruCache` |
| 三页 swiper 每次落位重算整页；单元格每帧重算农历/颜色 | 虚拟无限 Pager（只算新出现的那一页）+ 单格静态数据按「月/周」进程内缓存 |
| 毛玻璃：每张卡挂 `Modifier.blur`（RenderEffect），3 块 1272×2800 / 13.6MB 的模糊缓冲，每帧 ~2ms GPU | 整屏背景 + 模糊预烘焙成一张位图，卡片按坐标贴图（见上节） |
| 农历文字每格一个 `graphicsLayer`（一页 42 个渲染层） | 用字号 ×0.8 代替 `scale(0.8)`，去掉逐格图层 |

改造前后（8 次快滑 / 一次一个月份）：

| 指标 | 改造前 | 改造后 |
| --- | --- | --- |
| 50 分位 / 90 分位 / 99 分位 | 11 / 14 / 57 ms | 10 / 11 / 13~15 ms |
| 掉帧 | 2.2%（99 分位 57ms 尖刺） | 0.5~2.7% |
| Slow UI thread | 6 | 1~6 |
| GPU 50/90/95/99 | 8 / 9 / 10 / 14 ms | 5 / 6 / 7 / 7 ms |
| 空闲帧数 | 82fps 持续重绘 | 0 |

> 该机刷新率 90Hz，预算仅 11.1ms，所以「12ms 的帧」本来就已超时；现在 90 分位压到 11ms、99 分位 13~15ms。

## 功能对照（均已实机验证）

| 功能 | 源项目 | 原生实现 |
| --- | --- | --- |
| 多人配置（新增/删除/改名/拖拽排序） | index.vue | IndexState + DraggableTag |
| 班次管理（新增/删除、自定义班次哈希取色） | index.vue + ban-color.js | IndexState + BanColors |
| 排班周期（点选班次 / 长按拖拽互换 / 增删天） | index.vue | IndexScreen CycleTags |
| 锚点日期、周末/节假日自动休息 | index.vue + schedule.js | Dates + Schedule |
| 保存并生成（彩花动效） | index.vue | IndexState.triggerConfetti + ConfettiOverlay |
| 月历（农历、左右滑动翻页、翻月、选月、折叠、今天、单日覆盖） | wu-calendar + ban-picker | CalendarSection + BanPickerSheet |
| 同事排班对比（月份切换/选择月份、横向滚动、吸顶、今日高亮并自动滚动） | member_table.vue | MemberTableScreen（表头与每行共享同一个 `ScrollState` + `horizontalScroll`，人员多于一屏可左滑，姓名不截断） |
| 节假日数据（每日拉取一次并缓存） | loadHolidayAndBanList | HolidayRepo |
| 跨天刷新、进后台补写盘 | onShow / onHide | AppRoot 生命周期回调 |

## 有意省略（按需求）

1. **wgt 热更新**：`utils/wgt-update.js`、`App.vue` 里的 `checkWgtUpdate()`、首页的「版本号 / 检查更新」行整体移除。
2. **H5 专有元素**：顶部「点击下载 APP」按钮、备案号链接（原生 App 无意义）。
3. 源项目 `uni_modules` 里的 wu-icon / wu-safe-bottom / wu-ui-tools 等未被日历实际使用的库未移植。

## 数据迁移（从手机上的 uniapp 版导入）

uniapp 版包名 `com.paibanzhushou.wyq`（release 包、不可调试、设备无 root），外部存储没有 storage 数据，WebView 也不向无障碍暴露内容、devtools socket 不是标准 HTTP —— 内部存储读不出来。因此改为**从界面读取配置再写入原生存储**：

1. 逐个人员读出「人员名称 / 班次管理 / 锚点日期 / 排班周期 / 自动休息」（配置卡本身就是持久化字段）；
2. `tools/mk_prefs.py` 按原生存储格式（`paiban_people_v1`、`PAIBAN_OPTIONS_KEY` 等）生成 `shots/paiban_store.xml`；
3. debug 包可 `run-as`，直接把 XML 写进 `shared_prefs` 并重启（`tools/import_uniapp_data.ps1` 一键完成）。

导入结果：**5 人**（吴悦琦 / 顾焱焱 / 姚启晨 / 王一丹 / 罗鋆烨）、班次表 `白班 中班 值班 休息 大夜班`、各自锚点与周期（罗鋆烨是 12 天循环 + 大夜班 + 周末/节假日「白班」自动休息，王一丹节假日「中班」自动休息）。
校验：2026-10 与 2026-11 的日历、同事排班表逐格与 uniapp 版一致（含 10-03 王一丹因节假日休息显示「休息」这类边界）。

> 注意：uniapp 版若用过「单日覆盖」，配置卡里看不到，需要逐格比对才能取到；本次比对未发现覆盖日。

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

- `keystore/paibanzhushou.jks`：PKCS12，alias `paibanzhushou`，口令 `Paiban@2026`，有效期 10950 天，SHA-256 证书指纹
  `7E:38:0A:33:DF:C6:A0:CC:1D:A7:4E:F0:E3:4A:82:43:CB:5F:27:46:83:92:B9:FD:6E:40:36:AD:BE:8B:43:11`。
- `keystore.properties`：`storeFile=keystore/paibanzhushou.jks` / `storePassword=...` / `keyAlias=paibanzhushou` / `keyPassword=...`，
  由 `app/build.gradle.kts` 顶部的 `Properties` 读取后注入 `signingConfigs.release`。

> **正式包关闭了 R8 优化**（`optimization { enable = false }`）。开启 AGP 的 `packageScope` 优化后，1.0.4 会在启动时崩在
> `androidx.startup.InitializationProvider` → `ProcessLifecycleInitializer`，报
> `java.lang.IllegalAccessError: Class kotlin.collections.f extended by class jg is inaccessible`。稳定优先，保持不混淆（包体 8.58 MB）。

> 换手机测试正式包时注意：release 与旧 debug 签名不同，`adb install -r` 会静默失败（版本号不变），需先 `adb uninstall com.bilu.paibanzhushou`。

## 开发辅助

- `tools/find_ui.py`：`adb shell uiautomator dump` 后按正则打印控件文字与点击坐标（实机调试定位用）。
- `tools/mk_prefs.py` + `tools/import_uniapp_data.ps1`：从 uniapp 版迁移数据的生成器与写入脚本（见「数据迁移」）。
- `tools/gen_icons.py`：按 `res/drawable/logo.png` 生成各密度启动图标（含圆形图标）。
- `shots/`：移植过程中的实机截图对照。

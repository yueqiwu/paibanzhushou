package com.bilu.paibanzhushou.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.bilu.paibanzhushou.core.BanColors
import com.bilu.paibanzhushou.core.Dates
import com.bilu.paibanzhushou.core.Schedule
import com.bilu.paibanzhushou.core.SelectedEntry
import com.bilu.paibanzhushou.alarm.AlarmNotifications
import com.bilu.paibanzhushou.alarm.ShiftAlarm
import com.bilu.paibanzhushou.data.AlarmConfig
import com.bilu.paibanzhushou.data.AlarmStore
import com.bilu.paibanzhushou.data.HolidayRepo
import com.bilu.paibanzhushou.data.PeopleStore
import com.bilu.paibanzhushou.data.Person
import com.bilu.paibanzhushou.data.ReleaseInfo
import com.bilu.paibanzhushou.data.Updater
import com.bilu.paibanzhushou.data.Store
import com.bilu.paibanzhushou.widget.ScheduleWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.random.Random

/** 底部班次选择弹层的状态 */
data class PickerState(
    val visible: Boolean = false,
    val mode: String = "",
    val index: Int = -1,
    val dateStr: String = "",
    val title: String = "",
    val current: String = "",
    val allowClear: Boolean = false
)

/** 闹钟相关的一项系统权限/设置 */
data class AlarmPermission(
    val id: String,
    val title: String,
    val desc: String,
    val ok: Boolean,
    /** 无法自动检测的项：只做引导，不计入「还有 N 项没开启」，也不点红点 */
    val hintOnly: Boolean = false
)

/** 确认弹窗 */
data class ConfirmState(
    val title: String,
    val content: String,
    val onConfirm: () -> Unit
)

/** 一片彩花 */
data class ConfettiPiece(
    val dx: Float,
    val dy: Float,
    val rot: Float,
    val delay: Int,
    val dur: Int,
    val size: Float,
    val thick: Float,
    val color: Color
)

/**
 * 首页状态与业务逻辑，1:1 移植自 pages/index/index.vue。
 */
class IndexState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val toast: (String) -> Unit
) {
    companion object {
        const val BAN_OPTIONS_KEY = "PAIBAN_OPTIONS_KEY"
        const val BAN_OPTIONS_VERSION_KEY = "paiban_options_version_v1"
        const val BAN_OPTIONS_VERSION = 2
        const val PICKER_MODE_CYCLE = "cycle"
        const val PICKER_MODE_DAY = "day"

        // 闹钟权限项 id（见 alarmPermissions）
        const val PERM_NOTIF = "notif"
        const val PERM_EXACT = "exact"
        const val PERM_OVERLAY = "overlay"
        const val PERM_FSI = "fsi"
        const val PERM_BATTERY = "battery"
        const val PERM_AUTOSTART = "autostart"

        /**
         * 切人后把「重写闹钟 + 刷新桌面小组件」推迟到收尾动画之后（收尾 260ms）再跑。
         * 这两件事都在主线程做几十次 Binder / RemoteViews，和动画抢同一帧时弱机会掉帧闪一下。
         */
        const val SWITCH_SYNC_DELAY_MS = 360L
    }

    val dragController = DragSortController()

    // ---------- 基础数据 ----------
    var banOptions by mutableStateOf(Schedule.DEFAULT_BANS)
        private set
    var newBanOption by mutableStateOf("")
    var people by mutableStateOf<List<Person>>(emptyList())
        private set
    var activePersonId by mutableStateOf("")
        private set
    var activePersonName by mutableStateOf("")
    var configOpen by mutableStateOf(false)
        private set

    // ---------- 闹钟 ----------
    var alarmConfig by mutableStateOf(AlarmConfig())
        private set

    /** 最近一次同步到系统里的闹钟数量（给设置页文案用） */
    var alarmCount by mutableIntStateOf(0)
        private set

    /** 闹钟权限告警（null = 一切正常）。设置页据此显示红色提示条 */
    var alarmWarn by mutableStateOf<String?>(null)

    /** 权限状态的刷新计数：系统设置里改完回来要重新读一遍 */
    var permissionTick by mutableIntStateOf(0)
        private set

    var anchorDate by mutableStateOf(Dates.formatYmd(Dates.todayStart()))
    var cycleBans by mutableStateOf(listOf("白班", "中班", "休息", "休息"))
    var overrides by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var weekendRestBans by mutableStateOf<List<String>>(emptyList())
    var holidayRestBans by mutableStateOf<List<String>>(emptyList())

    var holiday by mutableStateOf<List<String>>(emptyList())
        private set
    var banList by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * 排班数据版本号：任何会影响排班结果的改动（周期 / 锚点 / 单日覆盖 / 自动休息 / 节假日）
     * 都会 +1，日历页据此丢弃缓存、按需重算自己那几个月的窗口。
     */
    var scheduleVersion by mutableIntStateOf(0)
        private set

    var todayStr by mutableStateOf(Dates.formatYmd(Dates.todayStart()))
        private set

    /** 今日班次。单独按「今天这一天」算，与日历当前翻到哪一年无关 */
    var todayShift by mutableStateOf("-")
        private set

    /** 日历副标题。不再限制前后一年，任意年份都能翻 */
    var rangeText by mutableStateOf("不限年份，任意月份都能查看")
        private set

    /** 按「月」缓存的排班窗口（key = 版本号#月首），滑动时同一页只算一次 */
    private val windowCache = HashMap<String, Map<String, SelectedEntry>>()

    /** 非当前人员（左侧/右侧预览页）的窗口缓存，key = 版本号#人员 id#月首 */
    private val personWindowCache = HashMap<String, Map<String, SelectedEntry>>()

    // 日历
    var calendarDateMs by mutableStateOf(Dates.todayStart())
        private set
    var calendarMonthMs by mutableStateOf(Dates.firstOfMonth(Dates.todayStart()))
        private set

    /**
     * 折叠态（单周）正在浏览的日期。翻周只移动它，不动 [calendarDateMs]，
     * 所以「浏览」永远不会改变选中日。
     */
    var calendarWeekMs by mutableStateOf(Dates.todayStart())
        private set
    var foldOpen by mutableStateOf(true)

    // 弹层
    var picker by mutableStateOf(PickerState())
        private set
    var confirm by mutableStateOf<ConfirmState?>(null)
    var nameFocusRequest by mutableIntStateOf(0)
        private set
    var scrollToTopRequest by mutableIntStateOf(0)
        private set

    // 彩花
    var confettiVisible by mutableStateOf(false)
        private set
    var confettiPieces by mutableStateOf<List<ConfettiPiece>>(emptyList())
        private set
    var confettiKey by mutableIntStateOf(0)
        private set

    private var saveJob: Job? = null
    /** 切人后的延迟同步（闹钟 + 小组件），连续快切只保留最后一次 */
    private var switchSyncJob: Job? = null
    private var confettiJob: Job? = null
    private var remoteJob: Job? = null

    // ---------- 计算属性 ----------
    val restBanChoices: List<String>
        get() = cycleBans.filter { it.isNotEmpty() && it != Schedule.REST_BAN }.distinct()

    val peopleNameList: List<String>
        get() = people.map { it.name }

    val activePersonIndex: Int
        get() {
            val idx = people.indexOfFirst { it.id == activePersonId }
            return if (idx < 0) 0 else idx
        }

    /** 闹钟设置作用于哪个人员（独立于首页当前人员） */
    val alarmPersonId: String
        get() = alarmConfig.personId ?: activePersonId

    val todayShiftText: String
        get() = todayShift

    val heroSubtitle: String
        get() {
            val name = activePersonName.ifEmpty {
                peopleNameList.getOrElse(activePersonIndex) { "" }
            }
            return name + " · 今日 " + todayShiftText
        }

    // ---------- 初始化 ----------
    fun init() {
        loadBanOptions()
        loadAlarmConfig()
        loadPeople()
        loadHolidayAndBanList()
        refreshAlarmWarn()
        todayStr = Dates.formatYmd(Dates.todayStart())
        calendarDateMs = Dates.todayStart()
        calendarMonthMs = Dates.firstOfMonth(Dates.todayStart())
        calendarWeekMs = Dates.todayStart()
        generateSchedule()
        // 启动时静默查一次更新：有新版才弹，没有不打扰
        checkUpdate(silent = true)
    }

    /** 等价 onShow：跨天后刷新（从系统设置返回时也会走到这里，权限状态顺带重新读） */
    fun onShow() {
        refreshPermissions()
        val now = Dates.formatYmd(Dates.todayStart())
        if (now != todayStr) {
            todayStr = now
            calendarDateMs = Dates.todayStart()
            calendarMonthMs = Dates.firstOfMonth(Dates.todayStart())
            calendarWeekMs = Dates.todayStart()
            generateSchedule()
        } else if (alarmConfig.enabled) {
            // 回到首页 / 切回前台时补写一次系统闹钟：
            // 闹钟页里改完时间直接退出、或者某次写入被系统拒绝，都在这里按最新设置补齐。
            // 手动在闹钟列表里取消过的日期有 SKIP 记录，不会被重新加回来。
            resyncAlarms()
        }
    }

    /** 等价 onHide / onUnload */
    fun flush() {
        if (saveJob != null) {
            saveJob?.cancel()
            saveJob = null
            savePeople()
        }
        // 切人后还没同步闹钟/小组件就退到后台：这里补一次，
        // 否则会出现「首页显示的是 A，闹钟/桌面卡片还是 B」
        if (switchSyncJob != null) {
            switchSyncJob?.cancel()
            switchSyncJob = null
            if (alarmConfig.enabled) resyncAlarms()
            ScheduleWidgetProvider.refresh(context)
        }
    }

    // ---------- 节假日数据 ----------
    private fun applyHolidayAndBanListCache(): Long {
        val cache = HolidayRepo.loadCache()
        holiday = cache.holiday
        banList = cache.banList
        return cache.fetchedAt
    }

    fun loadHolidayAndBanList() {
        val fetchedAt = applyHolidayAndBanListCache()
        if (Dates.isSameDay(fetchedAt, System.currentTimeMillis())) return
        refreshHolidayAndBanListFromRemote()
    }

    fun refreshHolidayAndBanListFromRemote() {
        if (remoteJob?.isActive == true) return
        remoteJob = scope.launch {
            val cache = withContext(Dispatchers.IO) { HolidayRepo.fetchRemote() } ?: return@launch
            HolidayRepo.saveCache(cache)
            holiday = cache.holiday
            banList = cache.banList
            generateSchedule()
        }
    }

    // ---------- 人员 ----------
    fun loadPeople() {
        people = PeopleStore.loadPeople(anchorDate, cycleBans)
        val savedActiveId = PeopleStore.loadActivePersonId()
        val match = people.firstOrNull { it.id == savedActiveId }
        activePersonId = match?.id ?: people.firstOrNull()?.id ?: ""
        applyActivePersonToForm()
        // 老数据（1.0.22 之前）没有「闹钟作用于哪个人员」，补一次成当前人员；
        // 之后闹钟页再怎么选人都和首页当前人员各管各的。
        if (alarmConfig.personId == null && activePersonId.isNotEmpty()) {
            alarmConfig = alarmConfig.copy(personId = activePersonId)
            AlarmStore.save(alarmConfig)
        }
    }

    fun getActivePerson(): Person? = people.firstOrNull { it.id == activePersonId } ?: people.firstOrNull()

    private fun defaultPerson(name: String, anchor: String, cycle: List<String>): Person {
        val p = Person(
            id = PeopleStore.createPersonId(),
            name = name,
            anchorDate = anchor,
            cycleBans = cycle,
            overrides = emptyMap(),
            weekendRestBans = emptyList(),
            holidayRestBans = emptyList()
        )
        return PeopleStore.normalize(p, anchorDate, cycleBans)
    }

    fun applyActivePersonToForm() {
        val p = getActivePerson() ?: return
        activePersonId = p.id
        activePersonName = p.name
        anchorDate = p.anchorDate
        cycleBans = p.cycleBans
        overrides = p.overrides
        weekendRestBans = p.weekendRestBans
        holidayRestBans = p.holidayRestBans
    }

    fun persistFormToActivePerson() {
        val idx = people.indexOfFirst { it.id == activePersonId }
        if (idx < 0) return
        val current = people[idx]
        val next = current.copy(
            name = activePersonName.ifEmpty { current.name },
            anchorDate = anchorDate,
            cycleBans = cycleBans,
            overrides = overrides,
            weekendRestBans = weekendRestBans,
            holidayRestBans = holidayRestBans
        )
        val list = people.toMutableList()
        list[idx] = next
        people = list
    }

    fun savePeople() {
        PeopleStore.savePeople(people)
        PeopleStore.saveActivePersonId(activePersonId)
    }

    /**
     * 立即落盘，跳过 [scheduleSave] 的 200ms 防抖。
     *
     * 排班一变就重写闹钟的路径要留一份立刻可见的磁盘数据：开机补写（BootReceiver）、
     * 关掉再打开开关时的 [ShiftAlarm.resyncActive] 都是从磁盘读排班的。
     */
    fun savePeopleNow() {
        saveJob?.cancel()
        saveJob = null
        savePeople()
    }

    fun scheduleSave(delayMs: Long = 200L) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(delayMs)
            savePeople()
            saveJob = null
        }
    }

    fun onActivePersonNameBlur() {
        val idx = people.indexOfFirst { it.id == activePersonId }
        if (idx >= 0) {
            val current = people[idx]
            val name = activePersonName.ifEmpty { current.name }
            val list = people.toMutableList()
            list[idx] = current.copy(name = name)
            people = list
            activePersonName = name
        }
        scheduleSave()
    }

    fun setActivePerson(id: String) {
        if (id.isEmpty() || id == activePersonId) return
        // 切人只换「首页看谁」：周期、锚点、班次都没变，没必要走 generateSchedule 的完整流程。
        // 之前的完整流程会在这一帧同步重写几十个系统闹钟并构建小组件 RemoteViews，
        // 和 260ms 收尾动画抢主线程，弱机上就是「切人时日历抖一下 / 闪一下」。
        persistFormToActivePerson()
        activePersonId = id
        applyActivePersonToForm()
        // 立即落盘（activePersonId 也在里面），并发版与小组件随后要按新人员算
        savePeopleNow()
        scheduleVersion++
        windowCache.clear()
        personWindowCache.clear()
        refreshTodayShift()
        // 闹钟与桌面小组件延到动画结束后再同步：闹钟只在「跟随当前人员」时才受影响，
        // 但一次重写要 cancel + setAlarmClock 几十个 PendingIntent，放动画里必掉帧
        scheduleSwitchSync()
    }

    /**
     * 切人后的延迟同步：重写闹钟 + 刷新桌面小组件。
     * 连续快速切人时 cancel 掉上一次，只跑最后一次，避免重复做几十次 Binder 调用。
     */
    private fun scheduleSwitchSync() {
        switchSyncJob?.cancel()
        switchSyncJob = scope.launch {
            delay(SWITCH_SYNC_DELAY_MS)
            // 排班一变，闹钟对应的工作日也变了，跟着重写（开关没开就什么都不做）
            if (alarmConfig.enabled) resyncAlarms()
            // 桌面小组件显示的是同一份排班，顺手刷一下
            ScheduleWidgetProvider.refresh(context)
            switchSyncJob = null
        }
    }

    /** 消费一次「聚焦人员名称」请求，避免页面重新进入时重复触发 */
    fun consumeNameFocus() {
        if (nameFocusRequest > 0) nameFocusRequest = 0
    }

    fun addPerson() {
        val baseName = "同事" + (people.size + 1)
        val person = defaultPerson(baseName, Dates.formatYmd(Dates.todayStart()), cycleBans)
        people = people + person
        activePersonId = person.id
        applyActivePersonToForm()
        nameFocusRequest++
        scheduleSave()
        generateSchedule()
    }

    fun removePerson(id: String) {
        if (people.size <= 1) {
            toast("至少要保留一个人员")
            return
        }
        val target = people.firstOrNull { it.id == id } ?: return
        confirm = ConfirmState(
            title = "提示",
            content = "确定要删除“" + target.name + "”吗？该人员的排班配置会一并删除。",
            onConfirm = {
                val next = people.filter { it.id != id }
                people = next
                if (activePersonId == id) {
                    activePersonId = next.firstOrNull()?.id ?: ""
                    applyActivePersonToForm()
                }
                scheduleSave()
                generateSchedule()
            }
        )
    }

    fun toggleConfig() {
        configOpen = !configOpen
    }

    // ---------- 排班生成 ----------
    /**
     * 排班不再预生成「前后一年」，改为「翻到哪个月就算哪个月」：
     * 这里只负责把表单写回人员、落盘，并让日历丢弃旧窗口；具体每天的班次由
     * [windowedSelected] 按需算，因此 1970~2100 甚至更远的年份都能正常显示。
     */
    fun generateSchedule() {
        persistFormToActivePerson()
        // 立即落盘：紧接着的 resyncAlarms / 桌面小组件都要按新排班算
        savePeopleNow()
        scheduleVersion++
        windowCache.clear()
        personWindowCache.clear()
        refreshTodayShift()
        // 排班一变，闹钟对应的工作日也变了，跟着重写（开关没开就什么都不做）
        if (alarmConfig.enabled) resyncAlarms()
        // 桌面小组件显示的是同一份排班，顺手刷一下
        ScheduleWidgetProvider.refresh(context)
        // 单日覆盖不再按一年裁剪：设到几年后也要保留
    }

    /** 今日班次单独算一天，避免「翻到 2030 年时首页副标题变成 -」 */
    private fun refreshTodayShift() {
        val todayMs = Dates.todayStart()
        todayShift = Schedule.buildSchedule(
            cycleBans = cycleBans,
            anchorDate = anchorDate,
            startMs = todayMs,
            endMs = todayMs,
            holiday = holiday,
            banList = banList,
            overrides = overrides,
            weekendRestBans = weekendRestBans,
            holidayRestBans = holidayRestBans,
            fallbackAnchorMs = todayMs
        ).firstOrNull()?.info ?: "-"
    }

    /** 只把可见月份 ±2 个月的数据喂给日历（当前选中月） */
    fun windowedSelected(): Map<String, SelectedEntry> = windowedSelected(calendarMonthMs)

    /**
     * 以指定月份为中心算出「该月 ±2 个月」的班次（日历每一页只关心自己那一页的窗口）。
     * 结果按 [scheduleVersion] 缓存，滑动时不会重复计算。
     */
    fun windowedSelected(monthMs: Long): Map<String, SelectedEntry> {
        val version = scheduleVersion
        val monthFirst = Dates.firstOfMonth(monthMs)
        val key = version.toString() + "#" + monthFirst
        windowCache[key]?.let { return it }

        val startMs = Dates.addMonths(monthFirst, -2)
        val endMs = Dates.monthEndMs(Dates.addMonths(monthFirst, 2))
        val map = Schedule.toMap(
            Schedule.buildSchedule(
                cycleBans = cycleBans,
                anchorDate = anchorDate,
                startMs = startMs,
                endMs = endMs,
                holiday = holiday,
                banList = banList,
                overrides = overrides,
                weekendRestBans = weekendRestBans,
                holidayRestBans = holidayRestBans,
                fallbackAnchorMs = monthFirst
            )
        )
        // 翻很多年时缓存会一直长，简单粗暴地整块丢弃即可（重算一页只要百来天，可忽略）
        if (windowCache.size > 120) windowCache.clear()
        windowCache[key] = map
        return map
    }

    /**
     * 左右滑动切换人员时，预览页要用「另一个人员」的班次数据。
     * [person] 为空或就是当前人员时走原来的 [windowedSelected]（用表单态，包含还没保存的编辑）。
     */
    fun windowedEntries(person: Person?, monthMs: Long): Map<String, SelectedEntry> {
        if (person == null || person.id == activePersonId) return windowedSelected(monthMs)
        return windowedSelectedFor(person, monthMs)
    }

    /** 指定人员在某月 ±2 个月窗口内的班次（结果按版本号 + 人员 + 月份缓存） */
    private fun windowedSelectedFor(person: Person, monthMs: Long): Map<String, SelectedEntry> {
        val monthFirst = Dates.firstOfMonth(monthMs)
        val key = scheduleVersion.toString() + "#" + person.id + "#" + monthFirst
        personWindowCache[key]?.let { return it }

        val startMs = Dates.addMonths(monthFirst, -2)
        val endMs = Dates.monthEndMs(Dates.addMonths(monthFirst, 2))
        val map = Schedule.toMap(
            Schedule.buildSchedule(
                cycleBans = person.cycleBans,
                anchorDate = person.anchorDate,
                startMs = startMs,
                endMs = endMs,
                holiday = holiday,
                banList = banList,
                overrides = person.overrides,
                weekendRestBans = person.weekendRestBans,
                holidayRestBans = person.holidayRestBans,
                fallbackAnchorMs = monthFirst
            )
        )
        if (personWindowCache.size > 60) personWindowCache.clear()
        personWindowCache[key] = map
        return map
    }

    fun onGenerateTap() {
        triggerConfetti()
        generateSchedule()
    }

    // ---------- 彩花 ----------
    fun triggerConfetti() {
        val colors = listOf(
            Color(0xFFFF4D6D), Color(0xFFFFD166), Color(0xFF06D6A0),
            Color(0xFF4A90E2), Color(0xFF9C27B0), Color(0xFFFF9F1C)
        )
        val pieces = ArrayList<ConfettiPiece>(18)
        for (i in 0 until 18) {
            val r1 = Random.nextFloat()
            val r2 = Random.nextFloat()
            val r3 = Random.nextFloat()
            val r4 = Random.nextFloat()
            val r5 = Random.nextFloat()
            val r6 = Random.nextFloat()
            pieces.add(
                ConfettiPiece(
                    dx = r1 * 520f - 260f,
                    dy = -r2 * 520f - 120f,
                    rot = r3 * 720f - 360f,
                    delay = (r4 * 90f).toInt(),
                    dur = (520f + r5 * 420f).toInt(),
                    size = 12f + r6 * 14f,
                    thick = 8f + Random.nextFloat() * 10f,
                    color = colors[i % colors.size]
                )
            )
        }
        confettiPieces = pieces
        confettiKey++
        confettiVisible = true
        confettiJob?.cancel()
        confettiJob = scope.launch {
            delay(980)
            confettiVisible = false
        }
    }

    // ---------- 周期 / 单日 ----------
    fun onAnchorDateChange(value: String) {
        if (value.isEmpty()) return
        anchorDate = value
        generateSchedule()
    }

    fun openCyclePicker(index: Int) {
        picker = PickerState(
            visible = true,
            mode = PICKER_MODE_CYCLE,
            index = index,
            title = "第 " + (index + 1) + " 天",
            current = cycleBans.getOrElse(index) { "" },
            allowClear = false
        )
    }

    fun removeCycleDayAt(index: Int) {
        if (cycleBans.size <= 1) {
            toast("至少要保留一天")
            return
        }
        val list = cycleBans.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        cycleBans = list
        generateSchedule()
    }

    fun addCycleDay() {
        cycleBans = cycleBans + Schedule.REST_BAN
        generateSchedule()
    }

    fun toggleRestBan(kind: String, ban: String) {
        if (kind == "holiday") {
            holidayRestBans = if (holidayRestBans.contains(ban)) holidayRestBans - ban else holidayRestBans + ban
        } else {
            weekendRestBans = if (weekendRestBans.contains(ban)) weekendRestBans - ban else weekendRestBans + ban
        }
        generateSchedule()
    }

    fun onSelectDate(dateStr: String) {
        val ms = Dates.parseYmd(dateStr) ?: return
        calendarDateMs = ms
        calendarWeekMs = ms
        // 源版点击日期后日历以该日期所在月重排（wu-calendar choiceDate → cale._getWeek(fullDate)）
        calendarMonthMs = Dates.firstOfMonth(ms)
        picker = PickerState(
            visible = true,
            mode = PICKER_MODE_DAY,
            index = -1,
            dateStr = dateStr,
            title = dateStr,
            current = overrides[dateStr] ?: "",
            allowClear = true
        )
    }

    fun closePicker() {
        picker = PickerState()
    }

    fun onPickerSelect(ban: String) {
        val p = picker
        closePicker()
        if (p.mode == PICKER_MODE_CYCLE) {
            val list = cycleBans.toMutableList()
            if (p.index in list.indices) {
                list[p.index] = ban
                cycleBans = list
                generateSchedule()
            }
        } else if (p.mode == PICKER_MODE_DAY) {
            overrides = overrides + (p.dateStr to ban)
            generateSchedule()
        }
    }

    fun onPickerClear() {
        val p = picker
        closePicker()
        if (p.dateStr.isEmpty()) return
        overrides = overrides - p.dateStr
        generateSchedule()
    }

    // ---------- 拖拽 ----------
    fun onDrop(kind: String, from: Int, to: Int) {
        if (kind == DragKind.PEOPLE) {
            if (from !in people.indices || to !in people.indices) return
            val list = people.toMutableList()
            val item = list.removeAt(from)
            list.add(to, item)
            people = list
            scheduleSave()
        } else if (kind == DragKind.CYCLE) {
            if (from !in cycleBans.indices || to !in cycleBans.indices) return
            val list = cycleBans.toMutableList()
            val tmp = list[from]
            list[from] = list[to]
            list[to] = tmp
            cycleBans = list
            generateSchedule()
        }
    }

    fun onDragTap(kind: String, index: Int) {
        if (kind == DragKind.CYCLE) openCyclePicker(index)
    }

    // ---------- 班次管理 ----------
    fun loadBanOptions() {
        val raw = Store.readStringOrNull(BAN_OPTIONS_KEY)
        val saved = raw?.let { text ->
            runCatching {
                val arr = JSONArray(text)
                val list = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "")
                    if (s.isNotEmpty()) list.add(s)
                }
                list
            }.getOrNull()
        }
        if (!saved.isNullOrEmpty()) {
            val version = Store.readRaw(BAN_OPTIONS_VERSION_KEY, "0").toIntOrNull() ?: 1
            if (version < BAN_OPTIONS_VERSION) {
                val merged = Schedule.DEFAULT_BANS + saved.filter { it !in Schedule.DEFAULT_BANS }
                banOptions = merged
                Store.writeRaw(BAN_OPTIONS_VERSION_KEY, BAN_OPTIONS_VERSION.toString())
                saveBanOptions()
            } else {
                banOptions = saved
            }
        } else {
            banOptions = Schedule.DEFAULT_BANS
            Store.writeRaw(BAN_OPTIONS_VERSION_KEY, BAN_OPTIONS_VERSION.toString())
            saveBanOptions()
        }
    }

    fun saveBanOptions() {
        Store.writeRaw(BAN_OPTIONS_KEY, JSONArray(banOptions).toString())
    }

    fun addBanOption() {
        val text = newBanOption.trim()
        if (text.isEmpty()) return
        if (banOptions.contains(text)) {
            toast("班次已存在")
            return
        }
        banOptions = banOptions + text
        newBanOption = ""
        saveBanOptions()
    }

    private fun collectBanUsage(ban: String): Pair<Int, String> {
        val cycleDays = cycleBans.count { it == ban }
        val overrideDays = overrides.values.count { it == ban }
        val restSettings = weekendRestBans.count { it == ban } + holidayRestBans.count { it == ban }
        val parts = ArrayList<String>(3)
        if (cycleDays > 0) parts.add("排班周期里的 " + cycleDays + " 天")
        if (overrideDays > 0) parts.add(overrideDays.toString() + " 个单日设置")
        if (restSettings > 0) parts.add("自动休息设定")
        val text = if (parts.isNotEmpty()) parts.joinToString("、") + "正在使用它，删除后会一并改为休息。" else ""
        return (cycleDays + overrideDays + restSettings) to text
    }

    fun removeBanOption(index: Int) {
        val ban = banOptions.getOrNull(index) ?: return
        if (ban.isEmpty() || Schedule.PROTECTED_BANS.contains(ban)) return
        val (total, usageText) = collectBanUsage(ban)
        val content = if (total > 0) {
            "确定要删除“" + ban + "”吗？" + usageText
        } else {
            "确定要删除“" + ban + "”吗？"
        }
        confirm = ConfirmState("提示", content) { applyBanRemoval(ban) }
    }

    private fun applyBanRemoval(ban: String) {
        banOptions = banOptions.filter { it != ban }
        saveBanOptions()

        var nextCycle = cycleBans.map { if (it == ban) Schedule.REST_BAN else it }
        if (nextCycle.isEmpty()) nextCycle = listOf(Schedule.REST_BAN)
        cycleBans = nextCycle

        overrides = overrides.filterValues { it != ban }
        weekendRestBans = weekendRestBans.filter { it != ban }
        holidayRestBans = holidayRestBans.filter { it != ban }
        if (alarmConfig.bans.contains(ban) || alarmConfig.times.containsKey(ban)) {
            alarmConfig = alarmConfig.copy(
                bans = alarmConfig.bans.filter { it != ban },
                times = alarmConfig.times.filterKeys { it != ban }
            )
            AlarmStore.save(alarmConfig)
        }

        generateSchedule()
        toast("已删除该班次")
    }

    // ---------- 闹钟 ----------
    fun loadAlarmConfig() {
        var config = AlarmStore.load()
        if (config.bans.isEmpty()) {
            // 默认只给「值班」设闹钟（班次里没有值班就留空）
            config = config.copy(bans = listOf("值班").filter { banOptions.contains(it) })
        }
        alarmConfig = config
        // 开关是关的，但系统里还残留着上次写的闹钟（比如中途重装 / 清了数据）→ 清掉
        if (!config.enabled) ShiftAlarm.cancelAll(context)
    }

    fun toggleAlarmBan(ban: String) {
        if (ban.isEmpty() || ban == Schedule.REST_BAN) return
        val next = if (alarmConfig.bans.contains(ban)) {
            alarmConfig.bans - ban
        } else {
            alarmConfig.bans + ban
        }
        alarmConfig = alarmConfig.copy(bans = next)
        AlarmStore.save(alarmConfig)
        if (alarmConfig.enabled) resyncAlarms()
    }

    /** 只改某个班次的响铃时间（各班次互不影响） */
    fun setAlarmTimeFor(ban: String, hour: Int, minute: Int) {
        if (ban.isEmpty()) return
        val minutes = hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
        alarmConfig = alarmConfig.copy(times = alarmConfig.times + (ban to minutes))
        AlarmStore.save(alarmConfig)
        if (alarmConfig.enabled) resyncAlarms()
    }

    /** 换「闹钟作用于哪个人员」：只影响闹钟，不切首页当前人员 */
    fun setAlarmPerson(id: String) {
        if (id.isEmpty() || id == alarmConfig.personId) return
        alarmConfig = alarmConfig.copy(personId = id)
        AlarmStore.save(alarmConfig)
        if (alarmConfig.enabled) resyncAlarms()
    }

    /** 闹铃音：null 跟随系统；AlarmConfig.RINGTONE_SILENT 只震动；其它为铃声 Uri。响铃时才读取，不用重写闹钟。 */
    fun setAlarmRingtone(value: String?) {
        alarmConfig = alarmConfig.copy(ringtone = value)
        AlarmStore.save(alarmConfig)
    }

    fun setAlarmEnabled(enabled: Boolean) {
        alarmConfig = alarmConfig.copy(enabled = enabled)
        AlarmStore.save(alarmConfig)
        if (!enabled) {
            ShiftAlarm.cancelAll(context)
            alarmCount = 0
            refreshAlarmWarn()
            toast("已关闭班次闹钟")
            return
        }
        if (!ShiftAlarm.canScheduleExact(context)) {
            alarmCount = 0
            toast("请在系统设置里允许「闹钟和提醒」")
            requestExactAlarmPermission()
            return
        }
        // 没有「后台弹出界面」时，后台/锁屏拉起响铃页会被系统拦掉（铃声照响，只是不弹页面）
        if (!com.bilu.paibanzhushou.alarm.AlarmNotifications.canDrawOverlays(context)) {
            toast("请打开「后台弹出界面」，否则锁屏时不会弹出响铃页")
            requestOverlayPermission()
        }
        // Android 14+：没有「全屏通知」权限时，闹钟到点只能弹通知、不会亮屏响铃
        if (!com.bilu.paibanzhushou.alarm.AlarmNotifications.canUseFullScreen(context)) {
            toast("请允许「全屏通知」，否则闹钟可能只弹通知")
            requestFullScreenPermission()
        }
        val count = resyncAlarms()
        refreshAlarmWarn()
        toast(
            when {
                count > 0 -> "已写入 " + count + " 个闹钟"
                alarmConfig.bans.isEmpty() -> "先选一个要提醒的班次"
                else -> "未来 " + alarmConfig.horizonDays + " 天里没有这些班次"
            }
        )
    }

    /** 跳系统「闹钟和提醒」授权页；没有这个页面就退到应用设置 */
    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val direct = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        runCatching { context.startActivity(direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    /** 跳系统「后台弹出界面 / 显示在其他应用上层」开关页 */
    private fun requestOverlayPermission() {
        val direct = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        runCatching { context.startActivity(direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    /** 跳系统「全屏通知」开关页（Android 14+ 才有） */
    private fun requestFullScreenPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val direct = Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:" + context.packageName)
        )
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        runCatching { context.startActivity(direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    /** 跳系统「声音与振动」设置：闹钟铃声统一由系统设置决定，App 里只做入口 */
    fun openSystemSoundSettings() {
        val direct = Intent(Settings.ACTION_SOUND_SETTINGS)
        val alarm = Intent(Settings.ACTION_SETTINGS)
        val app = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        for (i in listOf(direct, alarm, app)) {
            val ok = runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return
        }
    }

    /**
     * 检查「闹钟真的能不能响」：通知权限 + Android 14+ 的全屏通知。
     * 缺任何一个，到点都只会静默或只弹一条通知，所以页面上要显式提示。
     */
    fun refreshAlarmWarn() {
        alarmWarn = when {
            !alarmConfig.enabled -> null
            !AlarmNotifications.canPost(context) -> "缺少通知权限，闹钟到点不会响"
            !AlarmNotifications.canDrawOverlays(context) ->
                "没开「后台弹出界面」，锁屏或后台时不会弹出响铃页（铃声不受影响）"
            !AlarmNotifications.canUseFullScreen(context) -> "没开「全屏通知」，锁屏或后台时闹钟只会弹一条通知"
            else -> null
        }
    }

    /**
     * 闹钟需要的系统权限一览（页面里逐条显示，缺的可以一键跳设置）。
     * 读 [permissionTick] 是为了让 Compose 在 tick 变化后重新取一遍真实状态。
     */
    val alarmPermissions: List<AlarmPermission>
        get() {
            permissionTick
            return listOf(
                AlarmPermission(
                    id = PERM_NOTIF,
                    title = "通知权限",
                    desc = "闹钟到点靠通知提醒，没授权会只剩铃声、锁屏看不到提示",
                    ok = AlarmNotifications.canPost(context)
                ),
                AlarmPermission(
                    id = PERM_EXACT,
                    title = "闹钟和提醒（精确闹钟）",
                    desc = "没有它系统会延后触发，闹钟可能晚几分钟才响",
                    ok = ShiftAlarm.canScheduleExact(context)
                ),
                AlarmPermission(
                    id = PERM_OVERLAY,
                    title = "后台弹出界面（悬浮窗）",
                    desc = "允许后，App 在后台时会弹出全屏响铃页；铃声不需要它",
                    ok = AlarmNotifications.canDrawOverlays(context)
                ),
                AlarmPermission(
                    id = PERM_FSI,
                    title = "全屏通知",
                    desc = "锁屏时自动亮屏显示响铃页（部分机型叫「锁屏显示」）",
                    ok = AlarmNotifications.canUseFullScreen(context)
                ),
                AlarmPermission(
                    id = PERM_BATTERY,
                    title = "电池优化白名单",
                    desc = "不加白名单，熄屏或清后台后系统可能冻住 App，闹钟到点不响",
                    ok = ShiftAlarm.isIgnoringBatteryOptimizations(context)
                ),
                AlarmPermission(
                    id = PERM_AUTOSTART,
                    title = "自启动 / 后台运行",
                    desc = "荣耀、华为、OPPO、vivo、小米等机型还要在系统设置里允许「自启动」「后台运行」，" +
                        "否则划掉后台后闹钟不响。此项无法自动检测，请手动确认已允许",
                    ok = false,
                    hintOnly = true
                ),
            )
        }

    /** 刷新权限状态（从系统设置返回时调用） */
    fun refreshPermissions() {
        permissionTick++
        refreshAlarmWarn()
    }

    /** 跳到某一项权限对应的系统设置页 */
    fun openAlarmPermission(id: String) {
        val pkg = Uri.parse("package:" + context.packageName)
        val app = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        val candidates: List<Intent> = when (id) {
            PERM_NOTIF -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                app
            )
            PERM_EXACT -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
                } else null,
                app
            )
            PERM_OVERLAY -> listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
                app
            )
            PERM_FSI -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
                } else null,
                app
            )
            PERM_BATTERY -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
                } else null,
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                app
            )
            // 国产 ROM 的「自启动 / 后台运行」没有统一入口，逐个试各家的设置页，都打不开就落到应用详情
            PERM_AUTOSTART -> autostartIntents() + app
            else -> listOf(app)
        }
        for (i in candidates) {
            val ok = runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return
        }
    }

    /** 各家 ROM 的「自启动 / 后台运行」管理页（打不开就抛异常，由外层 runCatching 兜住） */
    private fun autostartIntents(): List<Intent> {
        val vendors = listOf(
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity"
        )
        return vendors.map { (p, c) ->
            Intent().apply {
                component = ComponentName(p, c)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /** 去补权限：优先「全屏通知」，否则应用通知设置 */
    fun openAlarmPermissionSettings() {
        val fsi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
        } else null
        val overlay = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )
        val notif = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        val app = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        val attempts = when {
            !AlarmNotifications.canDrawOverlays(context) -> listOfNotNull(overlay, fsi, notif, app)
            AlarmNotifications.canUseFullScreen(context) -> listOf(notif, app)
            else -> listOfNotNull(fsi, notif, app)
        }
        for (i in attempts) {
            val ok = runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return
        }
    }

    /**
     * 按当前人员和设置重写系统闹钟；返回写入数量。
     *
     * 这里必须优先用内存里的 [people]：磁盘写入有 200ms 防抖（[scheduleSave]），
     * 单日把某天改成「休息」之后马上重写闹钟时，从磁盘读到的还是改之前的排班，
     * 那天原本「值班」的旧闹钟就撤不掉 —— 表现为「已经改成休息了，闹钟还在响」。
     * 内存里的这份 people 是 [persistFormToActivePerson] 刚写完的，才是当前排班。
     */
    fun resyncAlarms(): Int {
        val id = alarmConfig.personId ?: activePersonId
        val person = people.firstOrNull { it.id == id } ?: ShiftAlarm.alarmPerson(alarmConfig)
        val count = ShiftAlarm.sync(context, person, alarmConfig)
        alarmCount = count
        android.util.Log.i(
            "BootAlarm",
            "resync person=" + (person?.name ?: "null") + " bans=" + alarmConfig.bans +
                " times=" + alarmConfig.times + " -> " + count
        )
        return count
    }

    // ---------- 应用内更新 ----------
    var updateInfo by mutableStateOf<ReleaseInfo?>(null); private set
    var updateChecking by mutableStateOf(false); private set
    /** -1 = 未下载，0..100 = 下载进度 */
    var updateProgress by mutableStateOf(-1); private set
    private var updateJob: Job? = null

    fun currentVersion(): String = Updater.currentVersion(context)

    /** [silent] 为 true 时（启动自动检查）没有新版本不会打扰用户 */
    fun checkUpdate(silent: Boolean = true) {
        if (updateChecking) return
        updateChecking = true
        updateJob = scope.launch {
            val latest = withContext(Dispatchers.IO) { Updater.fetchLatest(context) }
            val cur = Updater.currentVersion(context)
            updateChecking = false
            if (latest != null && Updater.isNewer(latest.version, cur)) {
                updateProgress = -1
                updateInfo = latest
            } else if (!silent) {
                toast(if (latest == null) "检查更新失败，请检查网络" else "已经是最新版本（$cur）")
            }
        }
    }

    fun dismissUpdate() {
        updateInfo = null
        updateProgress = -1
    }

    fun downloadUpdate() {
        val info = updateInfo ?: return
        if (updateProgress in 0..99) return
        updateProgress = 0
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                Updater.download(context, info) { p -> if (p >= 0) updateProgress = p }
            }
            if (file == null) {
                updateProgress = -1
                toast("下载失败，请检查网络后重试")
                return@launch
            }
            updateProgress = 100
            if (!Updater.install(context, file)) {
                toast("请允许「安装未知应用」后再点一次更新")
            }
        }
    }

    fun banColor(ban: String): String = BanColors.get(ban)

    // ---------- 日历交互 ----------
    fun onCellTap(entryDate: String) {
        onSelectDate(entryDate)
    }

    fun prevMonth() {
        step(-1)
    }

    fun nextMonth() {
        step(1)
    }

    /**
     * 左右切换：**只换正在浏览的月份 / 周，绝不改动选中日**。
     * 选中日只在「点日期格」「今天」「跨天」时变化，所以翻月时高亮要么仍在本月那一格，
     * 要么根本不在本月（就不显示），不会跳到 1 号、也不会跟着月份一起漂。
     */
    private fun step(delta: Int) {
        if (!foldOpen) {
            val target = Dates.addDays(calendarWeekMs, delta * 7)
            calendarWeekMs = target
            calendarMonthMs = Dates.firstOfMonth(target)
            return
        }
        calendarMonthMs = Dates.addMonths(calendarMonthMs, delta)
    }

    fun jumpToMonth(year: Int, month1: Int) {
        val monthFirst = Dates.firstOfMonth(year, month1)
        if (monthFirst == calendarMonthMs) return
        calendarMonthMs = monthFirst
        if (!foldOpen) calendarWeekMs = monthFirst
    }

    /** 浏览用：只移动日历显示的月份，不动选中日（日历滑动落位后回写） */
    fun browseToMonth(monthMs: Long) {
        val first = Dates.firstOfMonth(monthMs)
        if (first != calendarMonthMs) calendarMonthMs = first
    }

    /** 浏览用：折叠态移动到某一周，不动选中日（日历滑动落位后回写） */
    fun browseToWeek(weekMs: Long) {
        val day = Dates.startOfDay(weekMs)
        if (day != calendarWeekMs) calendarWeekMs = day
        browseToMonth(day)
    }

    fun backToToday() {
        val today = Dates.todayStart()
        calendarDateMs = today
        calendarMonthMs = Dates.firstOfMonth(today)
        calendarWeekMs = today
    }

    /**
     * 展 / 折切换时把另一个视图对齐到当前视野：
     * 折叠 → 展开：显示当前浏览周所在月；
     * 展开 → 折叠：显示选中日所在周；若选中日不在当前月（正在翻月浏览），显示当前月第一周。
     */
    fun toggleFold() {
        foldOpen = !foldOpen
        if (foldOpen) {
            calendarMonthMs = Dates.firstOfMonth(calendarWeekMs)
        } else {
            val selectedInView = Dates.firstOfMonth(calendarDateMs) == calendarMonthMs
            calendarWeekMs = if (selectedInView) calendarDateMs else calendarMonthMs
        }
    }

    fun requestScrollToTop() {
        scrollToTopRequest++
    }

    fun toastMsg(msg: String) = toast(msg)
}

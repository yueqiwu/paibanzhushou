package com.bilu.paibanzhushou.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.bilu.paibanzhushou.core.BanColors
import com.bilu.paibanzhushou.core.Dates
import com.bilu.paibanzhushou.core.Schedule
import com.bilu.paibanzhushou.core.SelectedEntry
import com.bilu.paibanzhushou.data.HolidayRepo
import com.bilu.paibanzhushou.data.PeopleStore
import com.bilu.paibanzhushou.data.Person
import com.bilu.paibanzhushou.data.Store
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
    private val scope: CoroutineScope,
    private val toast: (String) -> Unit
) {
    companion object {
        const val BAN_OPTIONS_KEY = "PAIBAN_OPTIONS_KEY"
        const val BAN_OPTIONS_VERSION_KEY = "paiban_options_version_v1"
        const val BAN_OPTIONS_VERSION = 2
        const val PICKER_MODE_CYCLE = "cycle"
        const val PICKER_MODE_DAY = "day"
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
        loadPeople()
        loadHolidayAndBanList()
        todayStr = Dates.formatYmd(Dates.todayStart())
        calendarDateMs = Dates.todayStart()
        calendarMonthMs = Dates.firstOfMonth(Dates.todayStart())
        calendarWeekMs = Dates.todayStart()
        generateSchedule()
    }

    /** 等价 onShow：跨天后刷新 */
    fun onShow() {
        val now = Dates.formatYmd(Dates.todayStart())
        if (now != todayStr) {
            todayStr = now
            calendarDateMs = Dates.todayStart()
            calendarMonthMs = Dates.firstOfMonth(Dates.todayStart())
            calendarWeekMs = Dates.todayStart()
            generateSchedule()
        }
    }

    /** 等价 onHide / onUnload */
    fun flush() {
        if (saveJob != null) {
            saveJob?.cancel()
            saveJob = null
            savePeople()
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
        persistFormToActivePerson()
        scheduleSave()
        activePersonId = id
        applyActivePersonToForm()
        generateSchedule()
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
        scheduleSave()
        scheduleVersion++
        windowCache.clear()
        refreshTodayShift()
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

        generateSchedule()
        toast("已删除该班次")
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

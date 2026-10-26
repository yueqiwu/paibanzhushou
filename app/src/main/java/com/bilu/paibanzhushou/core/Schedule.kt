package com.bilu.paibanzhushou.core

/** 单日排班条目，对应 utils/schedule.js 里 buildSchedule 的输出 */
data class SelectedEntry(
    val date: String,
    val info: String,
    val infoColor: String,
    val topInfo: String,
    val topInfoColor: String
)

/**
 * 排班生成逻辑，1:1 移植自 utils/schedule.js。
 */
object Schedule {
    const val REST_BAN = "休息"

    val DEFAULT_BANS: List<String> = listOf("白班", "中班", "值班", "夜班", "休息")
    val PROTECTED_BANS: List<String> = listOf("休息")

    fun shouldAutoRest(
        ban: String,
        isWeekend: Boolean,
        isHoliday: Boolean,
        weekendRestBans: List<String>,
        holidayRestBans: List<String>
    ): Boolean {
        if (ban.isEmpty() || ban == REST_BAN) return false
        return (isHoliday && holidayRestBans.contains(ban)) || (isWeekend && weekendRestBans.contains(ban))
    }

    fun buildSchedule(
        cycleBans: List<String>,
        anchorDate: String,
        startMs: Long?,
        endMs: Long?,
        holiday: List<String>,
        banList: List<String>,
        overrides: Map<String, String>,
        weekendRestBans: List<String>,
        holidayRestBans: List<String>,
        fallbackAnchorMs: Long?
    ): List<SelectedEntry> {
        val cycle = cycleBans.filter { it.isNotEmpty() }
        val start: Long = startMs ?: return emptyList()
        val end: Long = endMs ?: return emptyList()
        if (cycle.isEmpty() || end < start) return emptyList()

        val cycleLen = cycle.size.toLong()
        val anchorMs: Long = Dates.parseYmd(anchorDate) ?: fallbackAnchorMs ?: start
        val holidaySet = holiday.toHashSet()
        val banSet = banList.toHashSet()

        val out = ArrayList<SelectedEntry>()
        var t = start
        while (t <= end) {
            val dateStr = Dates.formatYmd(t)
            val dayOffset = Dates.diffDays(anchorMs, t)
            val cycleIndex = Dates.mod(dayOffset, cycleLen).toInt()
            val dow = Dates.dayOfWeek(t)
            val isWeekend = dow == 0 || dow == 6
            val isHoliday = holidaySet.contains(dateStr)

            val override = overrides[dateStr]
            val ban = if (!override.isNullOrEmpty()) {
                override
            } else {
                val base = cycle.getOrElse(cycleIndex) { REST_BAN }
                if (shouldAutoRest(base, isWeekend, isHoliday, weekendRestBans, holidayRestBans)) REST_BAN else base
            }

            val topInfo: String
            val topInfoColor: String
            when {
                isHoliday -> {
                    topInfo = "假"
                    topInfoColor = "#f00"
                }
                banSet.contains(dateStr) -> {
                    topInfo = "班"
                    topInfoColor = "#ff9800"
                }
                else -> {
                    topInfo = ""
                    topInfoColor = ""
                }
            }
            out.add(SelectedEntry(dateStr, ban, BanColors.get(ban), topInfo, topInfoColor))
            t = Dates.addDays(t, 1)
        }
        return out
    }

    fun toMap(list: List<SelectedEntry>): Map<String, SelectedEntry> = list.associateBy { it.date }
}

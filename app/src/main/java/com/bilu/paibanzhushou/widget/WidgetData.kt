package com.bilu.paibanzhushou.widget

import android.content.Context
import com.bilu.paibanzhushou.core.Dates
import com.bilu.paibanzhushou.core.Schedule
import com.bilu.paibanzhushou.data.HolidayRepo
import com.bilu.paibanzhushou.data.PeopleStore
import java.util.Calendar

/**
 * 小组件共用的数据：当前人员的「今天起 7 天」。
 *
 * 4×2 大卡（ScheduleWidgetProvider）和 2×1 小卡（ScheduleCompactWidgetProvider）
 * 都从这里取数，保证两张卡永远显示同一份排班。
 */
object WidgetData {

    data class Day(
        val ymd: String,
        /** 1..31 */
        val dayOfMonth: Int,
        /** 0=周日 … 6=周六 */
        val weekday: Int,
        val ban: String
    )

    data class Week(
        val personName: String,
        val monthLabel: String,
        val days: List<Day>
    )

    /** 「全员今日班次」卡里的一列：一个人的姓名 + 今天上什么班 */
    data class TodayPerson(val name: String, val ban: String)

    /** 「全员今日班次」卡的数据：日期标题 + 每人一列 */
    data class TodayBoard(val dateLabel: String, val people: List<TodayPerson>)

    private val WEEK = arrayOf("日", "一", "二", "三", "四", "五", "六")

    fun weekdayText(weekday: Int): String = "周" + WEEK[weekday.coerceIn(0, 6)]

    fun load(context: Context): Week {
        val today = Dates.todayStart()
        val todayStr = Dates.formatYmd(today)
        val people = runCatching {
            PeopleStore.loadPeople(todayStr, listOf("白班", "中班", "休息", "休息"))
        }.getOrDefault(emptyList())
        val activeId = runCatching { PeopleStore.loadActivePersonId() }.getOrNull()
        val person = people.firstOrNull { it.id == activeId } ?: people.firstOrNull()

        val dates = (0..6).map { Dates.formatYmd(Dates.addDays(today, it)) }
        val monthLabel = (todayStr.substring(5, 7).toIntOrNull() ?: 1).toString() + "月"

        if (person == null) {
            return Week(
                personName = "还没有人员",
                monthLabel = monthLabel,
                days = dates.map { Day(it, dayNumber(it), dayOfWeek(it), "") }
            )
        }

        val cache = runCatching { HolidayRepo.loadCache() }.getOrNull()
        val list = runCatching {
            Schedule.buildSchedule(
                cycleBans = person.cycleBans,
                anchorDate = person.anchorDate,
                startMs = today,
                endMs = Dates.addDays(today, 6),
                holiday = cache?.holiday ?: emptyList(),
                banList = cache?.banList ?: emptyList(),
                overrides = person.overrides,
                weekendRestBans = person.weekendRestBans,
                holidayRestBans = person.holidayRestBans,
                fallbackAnchorMs = today
            )
        }.getOrDefault(emptyList())

        return Week(
            personName = person.name,
            monthLabel = monthLabel,
            days = dates.mapIndexed { i, d ->
                Day(d, dayNumber(d), dayOfWeek(d), list.getOrNull(i)?.info.orEmpty())
            }
        )
    }

    /**
     * 所有人员的「今天」：一人一次 [Schedule.buildSchedule]（startMs = endMs = 今天）。
     *
     * 顺序沿用 PeopleStore 里的顺序（= 首页人员标签的顺序），不按当前人员重排，
     * 这样切人时卡片的列序不会跟着跳。
     */
    fun loadToday(context: Context): TodayBoard {
        val today = Dates.todayStart()
        val todayStr = Dates.formatYmd(today)
        val people = runCatching {
            PeopleStore.loadPeople(todayStr, listOf("白班", "中班", "休息", "休息"))
        }.getOrDefault(emptyList())
        val cache = runCatching { HolidayRepo.loadCache() }.getOrNull()

        val columns = people.map { person ->
            val ban = runCatching {
                Schedule.buildSchedule(
                    cycleBans = person.cycleBans,
                    anchorDate = person.anchorDate,
                    startMs = today,
                    endMs = today,
                    holiday = cache?.holiday ?: emptyList(),
                    banList = cache?.banList ?: emptyList(),
                    overrides = person.overrides,
                    weekendRestBans = person.weekendRestBans,
                    holidayRestBans = person.holidayRestBans,
                    fallbackAnchorMs = today
                )
            }.getOrNull()?.firstOrNull()?.info.orEmpty()
            TodayPerson(person.name, ban)
        }

        return TodayBoard(dateLabel(todayStr), columns)
    }

    /** 2026-11-17 → 「11月17日 周二」 */
    private fun dateLabel(ymd: String): String {
        val parts = ymd.split("-")
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val dom = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return "${month}月${dom}日 " + weekdayText(dayOfWeek(ymd))
    }

    private fun dayOfWeek(date: String): Int {
        val parts = date.split("-")
        val cal = Calendar.getInstance()
        cal.set(
            parts.getOrNull(0)?.toIntOrNull() ?: 2000,
            (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1,
            parts.getOrNull(2)?.toIntOrNull() ?: 1
        )
        return cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    private fun dayNumber(date: String): Int =
        date.substringAfterLast("-").toIntOrNull() ?: 0
}

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

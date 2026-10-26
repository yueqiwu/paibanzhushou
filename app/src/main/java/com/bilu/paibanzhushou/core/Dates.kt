package com.bilu.paibanzhushou.core

import java.util.Calendar
import java.util.GregorianCalendar

/**
 * 日期工具，语义与 utils/date.js 保持一致（本地时区、零点对齐）。
 *
 * 性能注意：这里每个函数都复用同一个 ThreadLocal GregorianCalendar。老实现每次调用都
 * Calendar.getInstance()（要克隆 TimeZone、初始化字段），单个约 30~60µs；而日历一页要算
 * 42 个格子 × 多个字段，一次翻页就是几百次调用（真机实测单帧卡 20~40ms）。复用后
 * 单次降到 1~3µs，语义完全不变（仍是本地时区的公历）。
 */
object Dates {
    private val YMD = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    private val TL = ThreadLocal.withInitial { GregorianCalendar() }

    private inline fun <T> withCal(ms: Long, block: (Calendar) -> T): T {
        val c = TL.get()!!
        // 时区被系统改过时同步一次（getDefault 是缓存值，开销可忽略）
        val tz = java.util.TimeZone.getDefault()
        if (c.timeZone != tz) c.timeZone = tz
        c.timeInMillis = ms
        return block(c)
    }

    fun pad2(n: Int): String = if (n < 10) "0" + n else n.toString()

    fun startOfDay(ms: Long): Long = withCal(ms) { c ->
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis
    }

    fun todayStart(): Long = startOfDay(System.currentTimeMillis())

    fun parseYmd(str: String?): Long? {
        if (str == null || !YMD.matches(str)) return null
        val y = str.substring(0, 4).toIntOrNull() ?: return null
        val mo = str.substring(5, 7).toIntOrNull() ?: return null
        val d = str.substring(8, 10).toIntOrNull() ?: return null
        if (y == 0 || mo < 1 || mo > 12 || d < 1 || d > 31) return null
        val c = TL.get()!!
        val tz = java.util.TimeZone.getDefault()
        if (c.timeZone != tz) c.timeZone = tz
        c.clear()
        c.set(y, mo - 1, d, 0, 0, 0)
        return c.timeInMillis
    }

    fun formatYmd(ms: Long): String = withCal(ms) { c ->
        c.get(Calendar.YEAR).toString() + "-" + pad2(c.get(Calendar.MONTH) + 1) + "-" + pad2(c.get(Calendar.DAY_OF_MONTH))
    }

    fun year(ms: Long): Int = withCal(ms) { it.get(Calendar.YEAR) }
    fun month(ms: Long): Int = withCal(ms) { it.get(Calendar.MONTH) + 1 }
    fun day(ms: Long): Int = withCal(ms) { it.get(Calendar.DAY_OF_MONTH) }

    /** 0 = 周日 */
    fun dayOfWeek(ms: Long): Int = withCal(ms) { it.get(Calendar.DAY_OF_WEEK) - 1 }

    fun weekdayCn(ms: Long): String = "日一二三四五六".substring(dayOfWeek(ms), dayOfWeek(ms) + 1)

    fun addDays(ms: Long, n: Int): Long = withCal(ms) { c ->
        c.add(Calendar.DATE, n)
        c.timeInMillis
    }

    fun addYears(ms: Long, n: Int): Long = withCal(ms) { c ->
        c.add(Calendar.YEAR, n)
        c.timeInMillis
    }

    /** 等价于 JS: new Date(y, m + months, 1) */
    fun addMonths(ms: Long, n: Int): Long = withCal(ms) { c ->
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH)
        c.clear()
        c.set(y, m + n, 1, 0, 0, 0)
        c.timeInMillis
    }

    /** 等价于 JS: new Date(y, m + 1, 0)，即当月最后一天 */
    fun monthEndMs(ms: Long): Long = withCal(ms) { c ->
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH)
        c.clear()
        c.set(y, m + 1, 0, 0, 0, 0)
        c.timeInMillis
    }

    fun firstOfMonth(ms: Long): Long = addMonths(ms, 0)

    fun firstOfMonth(year: Int, month1: Int): Long {
        val c = TL.get()!!
        val tz = java.util.TimeZone.getDefault()
        if (c.timeZone != tz) c.timeZone = tz
        c.clear()
        c.set(year, month1 - 1, 1, 0, 0, 0)
        return c.timeInMillis
    }

    fun daysInMonth(year: Int, month1: Int): Int {
        val c = TL.get()!!
        val tz = java.util.TimeZone.getDefault()
        if (c.timeZone != tz) c.timeZone = tz
        c.clear()
        c.set(year, month1, 0, 0, 0, 0)
        return c.get(Calendar.DAY_OF_MONTH)
    }

    fun diffDays(from: Long, to: Long): Long = Math.round((to - from) / 86400000.0)

    fun mod(n: Long, m: Long): Long = ((n % m) + m) % m

    fun isSameDay(a: Long, b: Long): Boolean = a != 0L && b != 0L && formatYmd(a) == formatYmd(b)

    fun monthsBetween(from: Long, to: Long): Int {
        val ay = year(from)
        val am = month(from)
        val by = year(to)
        val bm = month(to)
        return (by - ay) * 12 + (bm - am)
    }
}

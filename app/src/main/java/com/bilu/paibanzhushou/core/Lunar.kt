package com.bilu.paibanzhushou.core

import java.util.Calendar
import java.util.TimeZone

/** 农历信息 */
data class LunarInfo(
    val lYear: Int,
    val lMonth: Int,
    val lDay: Int,
    val isLeap: Boolean,
    val monthCn: String,
    val dayCn: String,
    val isTerm: Boolean,
    val term: String,
    val festival: String
)

/**
 * 农历换算，移植自 wu-calendar/calendar.js（1900-2100）。
 */
object Lunar {
    private val SOLAR_TERM = arrayOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
        "立夏", "小满", "芒种", "夏至", "小暑", "大暑", "立秋", "处暑",
        "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    )

    private val N_STR1 = "日一二三四五六七八九十"
    private val N_STR2 = "初十廿卅"
    private val N_STR3 = "正二三四五六七八九十冬腊"

    private val FESTIVALS: Map<String, String> = linkedMapOf(
        "1-1" to "元旦",
        "2-14" to "情人节",
        "3-8" to "妇女节",
        "3-12" to "植树节",
        "4-1" to "愚人节",
        "5-1" to "劳动节",
        "5-4" to "青年节",
        "5-12" to "护士节",
        "6-1" to "儿童节",
        "8-1" to "建军节",
        "9-10" to "教师节",
        "10-1" to "国庆",
        "11-1" to "万圣节",
        "12-24" to "圣诞节",
        "正月初一" to "春节",
        "二月初二" to "龙抬头",
        "五月初五" to "端午节",
        "七月初七" to "七夕节",
        "七月十五" to "中元节",
        "八月十五" to "中秋节",
        "九月初九" to "重阳节",
        "腊月初八" to "腊八节",
        "腊月廿三" to "小年",
        "腊月三十" to "除夕"
    )

    private fun leapMonth(y: Int): Int = LunarData.lunarInfo[y - 1900] and 0xf

    private fun leapDays(y: Int): Int {
        if (leapMonth(y) != 0) {
            return if (LunarData.lunarInfo[y - 1900] and 0x10000 != 0) 30 else 29
        }
        return 0
    }

    private fun monthDays(y: Int, m: Int): Int =
        if (LunarData.lunarInfo[y - 1900] and (0x10000 shr m) != 0) 30 else 29

    private fun lYearDays(y: Int): Int {
        var sum = 348
        var i = 0x8000
        while (i > 0x8) {
            if (LunarData.lunarInfo[y - 1900] and i != 0) sum += 1
            i = i shr 1
        }
        return sum + leapDays(y)
    }

    private fun toChinaMonth(m: Int): String {
        if (m < 1 || m > 12) return ""
        return N_STR3.substring(m - 1, m) + "月"
    }

    private fun toChinaDay(d: Int): String = when (d) {
        10 -> "初十"
        20 -> "二十"
        30 -> "三十"
        else -> {
            if (d < 1 || d > 30) "" else
                N_STR2.substring(d / 10, d / 10 + 1) + N_STR1.substring(d % 10, d % 10 + 1)
        }
    }

    /** 每年 24 节气的「日」，解析一次后缓存 */
    private val TERM_DAYS = HashMap<Int, IntArray>()

    private fun termDaysOf(y: Int): IntArray {
        TERM_DAYS[y]?.let { return it }
        val table = LunarData.sTermInfo[y - 1900]
        val days = IntArray(24)
        var k = 0
        for (g in 0 until 6) {
            val chunk = table.substring(g * 5, g * 5 + 5)
            // 每个 5 位十六进制块 → 6 位十进制，按 1/2/1/2 拆成 4 个节气日
            // （最后一个是两位，之前按 1 位取，导致霜降这种 23 日只取到末位 3）
            val dec = Integer.parseInt(chunk, 16).toString()
            days[k++] = dec.substring(0, 1).toIntOrNull() ?: -1
            days[k++] = dec.substring(1, 3).toIntOrNull() ?: -1
            days[k++] = dec.substring(3, 4).toIntOrNull() ?: -1
            days[k++] = if (dec.length >= 6) dec.substring(4, 6).toIntOrNull() ?: -1 else -1
        }
        TERM_DAYS[y] = days
        return days
    }

    private fun getTerm(y: Int, n: Int): Int {
        if (y < 1900 || y > 2100) return -1
        if (n < 1 || n > 24) return -1
        return termDaysOf(y)[n - 1]
    }

    /** 公历日期 → 距 1970-01-01 的天数（Howard Hinnant 的 civil_from_days 逆运算，纯算术、无 Calendar） */
    private fun utcDays(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        // Hinnant 公式里三月是「一年之始」：mp = (m + 9) % 12（m=3 → 0）。
        // 之前写成 (m + 11) % 12 会让公历日期整体偏移，农历日期与节气全部错位。
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    fun solar2lunar(y: Int, m: Int, d: Int): LunarInfo {
        if (y < 1900 || y > 2100) return LunarInfo(y, 1, 1, false, "", "", false, "", "")

        var offset = (utcDays(y, m, d) - utcDays(1900, 1, 31)).toDouble()
        var i = 1900
        var temp = 0
        while (i < 2101 && offset > 0) {
            temp = lYearDays(i)
            offset -= temp
            i++
        }
        if (offset < 0) {
            offset += temp
            i--
        }
        val lYear = i
        val leap = leapMonth(lYear)
        var isLeap = false

        i = 1
        while (i < 13 && offset > 0) {
            if (leap > 0 && i == leap + 1 && !isLeap) {
                i -= 1
                isLeap = true
                temp = leapDays(lYear)
            } else {
                temp = monthDays(lYear, i)
            }
            if (isLeap && i == leap + 1) isLeap = false
            offset -= temp
            i += 1
        }

        if (offset == 0.0 && leap > 0 && i == leap + 1) {
            if (isLeap) {
                isLeap = false
            } else {
                isLeap = true
                i -= 1
            }
        }
        if (offset < 0) {
            offset += temp
            i -= 1
        }
        val lMonth = i
        val lDay = offset.toInt() + 1

        val monthCn = toChinaMonth(lMonth)
        val dayCn = toChinaDay(lDay)

        // 节日查表（与 calendar.js 一致）：先查农历「月日」汉字（春节/中秋…），再查公历「m-d」（国庆/教师节…）
        val festival = FESTIVALS[monthCn + dayCn] ?: FESTIVALS[m.toString() + "-" + d] ?: ""

        // 节气：当月两个节气由**公历月份**决定（calendar.js 用 m*2-1 / m*2），
        // 之前用农历月份去索引，导致「秋分」跑到 10-23、「教师节」跑到 10-07 这类错位
        var isTerm = false
        var term = ""
        val firstNode = getTerm(y, m * 2 - 1)
        val secondNode = getTerm(y, m * 2)
        if (firstNode == d) {
            isTerm = true
            term = SOLAR_TERM[m * 2 - 2]
        }
        if (secondNode == d) {
            isTerm = true
            term = SOLAR_TERM[m * 2 - 1]
        }

        return LunarInfo(
            lYear = lYear,
            lMonth = lMonth,
            lDay = lDay,
            isLeap = isLeap,
            monthCn = if (isLeap) "闰" + monthCn else monthCn,
            dayCn = dayCn,
            isTerm = isTerm,
            term = term,
            festival = festival
        )
    }

    /** dateStr("yyyy-MM-dd") → 格子农历文字 的缓存：翻页来回滑时不重复换算 */
    private val LABEL_CACHE = object : android.util.LruCache<String, String>(2048) {}

    fun labelFor(y: Int, m: Int, d: Int): String {
        val key = Dates.pad2(y) + "-" + Dates.pad2(m) + "-" + Dates.pad2(d)
        LABEL_CACHE.get(key)?.let { return it }
        val text = label(solar2lunar(y, m, d))
        LABEL_CACHE.put(key, text)
        return text
    }

    /** 日历格子里显示的农历文字（isDay 由调用方处理为「今天」） */
    fun label(info: LunarInfo): String {
        if (info.festival.isNotEmpty()) return info.festival
        if (info.isTerm) return info.term
        if (info.dayCn == "初一") return info.monthCn
        return info.dayCn
    }
}

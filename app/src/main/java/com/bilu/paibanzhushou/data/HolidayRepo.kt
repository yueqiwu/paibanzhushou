package com.bilu.paibanzhushou.data

import com.bilu.paibanzhushou.core.Dates
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 节假日 / 调班数据源（公开现成接口，不再依赖自建 JSON）。
 *
 * 主源： https://timor.tech/api/holiday/year/{year}  —— 中国法定节假日 + 调休补班，按年返回，免费无需 key
 *        返回 {"code":0,"holiday":{"10-01":{"holiday":true,"name":"国庆节","date":"2026-10-01"}, ...}}
 *        holiday=true  → 放假（日历上的「假」）
 *        holiday=false → 调休补班（日历上的「班」）
 * 备源： https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/{year}.json （国务院公告整理，静态 JSON）
 *        返回 {"year":2026,"days":[{"name":"国庆节","date":"2026-10-01","isOffDay":true}, ...]}
 *
 * 排班表范围是「今天 ±1 年」，因此一次拉取 3 个年份并合并；结果写进本地缓存（每天只拉一次）。
 */
object HolidayRepo {
    const val CACHE_KEY = "paiban_holiday_ban_v1"

    private const val TIMOR_YEAR = "https://timor.tech/api/holiday/year/"
    private val HOLIDAY_CN_YEAR = listOf(
        "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/",
        "https://gcore.jsdelivr.net/gh/NateScarlet/holiday-cn@master/"
    )

    data class Cache(val holiday: List<String>, val banList: List<String>, val fetchedAt: Long)

    private fun JSONArray?.toListSafe(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) {
            out.add(optString(i, ""))
        }
        return out
    }

    fun loadCache(): Cache {
        val raw = Store.readStringOrNull(CACHE_KEY) ?: return Cache(emptyList(), emptyList(), 0L)
        return runCatching {
            val o = JSONObject(raw)
            Cache(
                holiday = o.optJSONArray("holiday").toListSafe(),
                banList = (o.optJSONArray("banList") ?: o.optJSONArray("banlist")).toListSafe(),
                fetchedAt = o.optLong("fetchedAt", 0L)
            )
        }.getOrElse { Cache(emptyList(), emptyList(), 0L) }
    }

    fun saveCache(cache: Cache) {
        val o = JSONObject()
        o.put("holiday", JSONArray(cache.holiday))
        o.put("banList", JSONArray(cache.banList))
        o.put("savedAt", System.currentTimeMillis())
        o.put("fetchedAt", cache.fetchedAt)
        Store.writeRaw(CACHE_KEY, o.toString())
    }

    /** 拉取「今天 ±1 年」的节假日与调班；全部失败时返回 null（保留旧缓存） */
    fun fetchRemote(): Cache? {
        val year = Dates.year(Dates.todayStart())
        val holiday = LinkedHashSet<String>()
        val banList = LinkedHashSet<String>()
        var anySuccess = false
        for (y in listOf(year - 1, year, year + 1)) {
            val days = fetchTimor(y) ?: fetchHolidayCn(y)
            if (days == null) continue
            anySuccess = true
            days.first.forEach { holiday.add(it) }
            days.second.forEach { banList.add(it) }
        }
        if (!anySuccess || (holiday.isEmpty() && banList.isEmpty())) return null
        return Cache(holiday.sorted(), banList.sorted(), Dates.todayStart())
    }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "paibanzhushou-android/1.0")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (t: Throwable) {
            }
        }
    }

    /** 主源：timor.tech 按年查询 */
    private fun fetchTimor(year: Int): Pair<List<String>, List<String>>? {
        val text = httpGet(TIMOR_YEAR + year) ?: return null
        return runCatching {
            val map = JSONObject(text).optJSONObject("holiday") ?: return null
            val holiday = ArrayList<String>()
            val banList = ArrayList<String>()
            val keys = map.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = map.optJSONObject(key) ?: continue
                val date = item.optString("date").ifEmpty { year.toString() + "-" + key }
                if (item.optBoolean("holiday", false)) holiday.add(date) else banList.add(date)
            }
            holiday to banList
        }.getOrNull()
    }

    /** 备源：holiday-cn 静态 JSON（含 jsDelivr 的两个镜像域名） */
    private fun fetchHolidayCn(year: Int): Pair<List<String>, List<String>>? {
        for (base in HOLIDAY_CN_YEAR) {
            val text = httpGet(base + year + ".json") ?: continue
            val parsed = runCatching {
                val days = JSONObject(text).optJSONArray("days") ?: return@runCatching null
                val holiday = ArrayList<String>()
                val banList = ArrayList<String>()
                for (i in 0 until days.length()) {
                    val item = days.optJSONObject(i) ?: continue
                    val date = item.optString("date")
                    if (date.isEmpty()) continue
                    if (item.optBoolean("isOffDay", false)) holiday.add(date) else banList.add(date)
                }
                holiday to banList
            }.getOrNull()
            if (parsed != null && (parsed.first.isNotEmpty() || parsed.second.isNotEmpty())) return parsed
        }
        return null
    }
}

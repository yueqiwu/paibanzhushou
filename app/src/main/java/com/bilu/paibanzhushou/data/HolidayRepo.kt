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

    /**
     * 缓存结构版本。
     * 2 = 修掉了「某年接口返回空对象却当作成功、导致备源从不生效」的 bug；
     * 老缓存里没有 ver 字段，升级后视为过期并立刻重拉一次，不用等第二天。
     */
    private const val CACHE_VER = 2

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
            // 旧结构：数据先照用，但把 fetchedAt 归零，交给上层立刻重拉
            val fresh = o.optInt("ver", 1) >= CACHE_VER
            Cache(
                holiday = o.optJSONArray("holiday").toListSafe(),
                banList = (o.optJSONArray("banList") ?: o.optJSONArray("banlist")).toListSafe(),
                fetchedAt = if (fresh) o.optLong("fetchedAt", 0L) else 0L
            )
        }.getOrElse { Cache(emptyList(), emptyList(), 0L) }
    }

    fun saveCache(cache: Cache) {
        val o = JSONObject()
        o.put("ver", CACHE_VER)
        o.put("holiday", JSONArray(cache.holiday))
        o.put("banList", JSONArray(cache.banList))
        o.put("savedAt", System.currentTimeMillis())
        o.put("fetchedAt", cache.fetchedAt)
        Store.writeRaw(CACHE_KEY, o.toString())
    }

    /**
     * 拉取「今天 ±1 年」的节假日与调班；全部失败时返回 null（保留旧缓存）。
     *
     * 注意：某一年「拉到了但内容为空」和「没拉到」必须区别对待。
     * timor.tech 对还没公布安排的年份会返回 {"code":0,"holiday":{}}，
     * 这种空结果不能当作成功，否则会导致备源 holiday-cn 永远不被尝试
     * （2027 年就踩过这个坑：接口已更新，App 却仍显示不出「假/班」）。
     */
    fun fetchRemote(): Cache? {
        val year = Dates.year(Dates.todayStart())
        val years = listOf(year - 1, year, year + 1)
        val fetched = LinkedHashMap<Int, Pair<List<String>, List<String>>>()
        for (y in years) {
            val days = fetchTimor(y) ?: fetchHolidayCn(y)
            if (days == null || (days.first.isEmpty() && days.second.isEmpty())) continue
            fetched[y] = days
        }
        if (fetched.isEmpty()) return null

        val holiday = LinkedHashSet<String>()
        val banList = LinkedHashSet<String>()

        // 没拉到的年份先沿用旧缓存，避免某一年接口挂了就把那一年的数据清空
        val missing = years.toSet() - fetched.keys
        if (missing.isNotEmpty()) {
            val old = loadCache()
            old.holiday.filterTo(holiday) { yearOf(it) in missing }
            old.banList.filterTo(banList) { yearOf(it) in missing }
        }
        fetched.values.forEach { (h, b) ->
            holiday.addAll(h)
            banList.addAll(b)
        }

        if (holiday.isEmpty() && banList.isEmpty()) return null
        return Cache(holiday.sorted(), banList.sorted(), Dates.todayStart())
    }

    private fun yearOf(dateStr: String): Int? = dateStr.take(4).toIntOrNull()

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
        val result: Pair<List<String>, List<String>>? = runCatching {
            val map = JSONObject(text).optJSONObject("holiday") ?: return@runCatching null
            val holiday = ArrayList<String>()
            val banList = ArrayList<String>()
            val keys = map.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = map.optJSONObject(key) ?: continue
                val date = item.optString("date").ifEmpty { year.toString() + "-" + key }
                if (item.optBoolean("holiday", false)) holiday.add(date) else banList.add(date)
            }
            // 空对象（该年份安排还没公布）= 失败，交给备源，不能当成功返回空列表
            if (holiday.isEmpty() && banList.isEmpty()) null else holiday to banList
        }.getOrNull()
        return result
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

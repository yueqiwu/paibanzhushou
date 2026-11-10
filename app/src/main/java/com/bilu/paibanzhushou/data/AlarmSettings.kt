package com.bilu.paibanzhushou.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 闹钟提醒设置：给哪些班次设闹铃、几点响、是否已开启。
 *
 * 与排班数据解耦：班次时间属于「班次」本身，和人员无关，
 * 所以整份设置全局存一份（键 paiban_alarm_v1）。
 */
data class AlarmConfig(
    val enabled: Boolean = false,
    val bans: List<String> = emptyList(),
    val hour: Int = 8,
    val minute: Int = 0,
    val horizonDays: Int = 60
) {
    val timeText: String
        get() = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
}

object AlarmStore {
    const val KEY = "paiban_alarm_v1"

    fun load(): AlarmConfig {
        val raw = Store.readStringOrNull(KEY) ?: return AlarmConfig()
        return try {
            val o = JSONObject(raw)
            val bans = ArrayList<String>()
            val arr = o.optJSONArray("bans")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "")
                    if (s.isNotEmpty()) bans.add(s)
                }
            }
            AlarmConfig(
                enabled = o.optBoolean("enabled", false),
                bans = bans,
                hour = o.optInt("hour", 8).coerceIn(0, 23),
                minute = o.optInt("minute", 0).coerceIn(0, 59),
                horizonDays = o.optInt("horizonDays", 60).coerceIn(1, 365)
            )
        } catch (t: Throwable) {
            AlarmConfig()
        }
    }

    fun save(config: AlarmConfig) {
        try {
            val o = JSONObject()
            o.put("enabled", config.enabled)
            o.put("bans", JSONArray(config.bans))
            o.put("hour", config.hour)
            o.put("minute", config.minute)
            o.put("horizonDays", config.horizonDays)
            Store.writeRaw(KEY, o.toString())
        } catch (t: Throwable) {
        }
    }
}

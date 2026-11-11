package com.bilu.paibanzhushou.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
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
    val horizonDays: Int = 60,
    /** 闹铃音：null = 跟随系统默认闹钟铃声；[RINGTONE_SILENT] = 只震动；其它为铃声 Uri 字符串 */
    val ringtone: String? = null
) {
    val timeText: String
        get() = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)

    companion object {
        const val RINGTONE_SILENT = "silent"
    }
}

/** 可选闹铃音：value 为 null 表示跟随系统，[AlarmConfig.RINGTONE_SILENT] 表示只震动。 */
data class RingtoneOption(val value: String?, val title: String)

object AlarmRingtones {
    fun list(context: Context): List<RingtoneOption> {
        val out = ArrayList<RingtoneOption>()
        out.add(RingtoneOption(null, "跟随系统默认铃音"))
        out.add(RingtoneOption(AlarmConfig.RINGTONE_SILENT, "静音（只震动）"))
        val rm = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val cursor = runCatching { rm.cursor }.getOrNull() ?: return out
        cursor.use {
            while (it.moveToNext()) {
                val title = runCatching {
                    it.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                }.getOrNull() ?: continue
                val uri = runCatching { rm.getRingtoneUri(it.position)?.toString() }.getOrNull()
                    ?: continue
                if (out.any { o -> o.value == uri }) continue
                out.add(RingtoneOption(uri, title))
            }
        }
        return out
    }

    fun label(context: Context, value: String?): String = when (value) {
        null -> "系统默认"
        AlarmConfig.RINGTONE_SILENT -> "静音（只震动）"
        else -> runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(value))?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "自定义铃音"
    }
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
                horizonDays = o.optInt("horizonDays", 60).coerceIn(1, 365),
                ringtone = o.optString("ringtone", "").ifEmpty { null }
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
            if (config.ringtone != null) o.put("ringtone", config.ringtone)
            Store.writeRaw(KEY, o.toString())
        } catch (t: Throwable) {
        }
    }
}

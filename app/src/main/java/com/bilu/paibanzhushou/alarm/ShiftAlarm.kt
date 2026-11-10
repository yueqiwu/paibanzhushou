package com.bilu.paibanzhushou.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import android.content.Context
import android.content.Intent
import com.bilu.paibanzhushou.MainActivity
import com.bilu.paibanzhushou.core.Dates
import com.bilu.paibanzhushou.core.Schedule
import com.bilu.paibanzhushou.data.AlarmConfig
import com.bilu.paibanzhushou.data.HolidayRepo
import com.bilu.paibanzhushou.data.Person
import com.bilu.paibanzhushou.data.Store
import org.json.JSONArray
import java.util.Calendar

/**
 * 把排班里的某些班次写成「系统闹钟」。
 *
 * 说明：手机自带时钟 App 的闹钟列表只有「几点 + 星期几重复」两种维度，
 * 而排班是按日期轮换的（周期长度不一定是 7 的倍数），没法映射成固定的星期几，
 * 所以这里用 [AlarmManager.setAlarmClock] —— 它在系统层面就是「闹钟」
 * （状态栏闹钟图标、免打扰例外、精确到分钟），到点由本应用弹全屏提醒并响闹铃音。
 */
object ShiftAlarm {
    const val ACTION_FIRE = "com.bilu.paibanzhushou.action.SHIFT_ALARM"
    const val EXTRA_DATE = "alarm_date"
    const val EXTRA_BAN = "alarm_ban"

    private const val WRITTEN_KEY = "paiban_alarm_written_v1"

    /** 某个人员在未来 [AlarmConfig.horizonDays] 天里，命中目标班次的日子（date 升序） */
    fun targetDays(person: Person, config: AlarmConfig, todayMs: Long = Dates.todayStart()): List<Pair<String, String>> {
        if (config.bans.isEmpty()) return emptyList()
        val cache = HolidayRepo.loadCache()
        val endMs = Dates.addDays(todayMs, config.horizonDays)
        val list = Schedule.buildSchedule(
            cycleBans = person.cycleBans,
            anchorDate = person.anchorDate,
            startMs = todayMs,
            endMs = endMs,
            holiday = cache.holiday,
            banList = cache.banList,
            overrides = person.overrides,
            weekendRestBans = person.weekendRestBans,
            holidayRestBans = person.holidayRestBans,
            fallbackAnchorMs = todayMs
        )
        return list.filter { it.info in config.bans }.map { it.date to it.info }
    }

    /** 是否已获得「闹钟和提醒」精确闹钟权限（API 31+ 需要） */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    /** 同步：写新的、撤旧的。返回真正写进系统的闹钟数量 */
    fun sync(context: Context, person: Person?, config: AlarmConfig): Int {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return 0
        if (person == null || !config.enabled || config.bans.isEmpty()) {
            cancelAll(context)
            return 0
        }

        val now = System.currentTimeMillis()
        val desired = LinkedHashMap<String, Pair<String, String>>()
        targetDays(person, config).forEach { (date, ban) ->
            val trigger = triggerAt(date, config.hour, config.minute)
            if (trigger > now) desired[key(date, ban)] = date to ban
        }

        // 撤销已经不需要的（改班次 / 改时间 / 关掉开关 / 换了人员）
        writtenKeys(context).forEach { k ->
            if (!desired.containsKey(k)) {
                val parts = k.split("#")
                if (parts.size == 2) cancelOne(context, parts[0], parts[1])
            }
        }

        val showIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setAlarmClock 在缺「闹钟和提醒」权限时会抛 SecurityException，这里按实际成功数记账，
        // 免得出现「设置里显示已写入 N 个闹钟，系统里其实一个都没有」的假象。
        val written = ArrayList<String>(desired.size)
        android.util.Log.i("BootAlarm", "sync desired=" + desired.size + " exact=" + am.canScheduleExactAlarms())
        desired.forEach { (k, p) ->
            val (date, ban) = p
            val pi = pendingIntent(context, date, ban)
            val ok = runCatching {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt(date, config.hour, config.minute), showIntent), pi)
            }.isSuccess
            if (ok) written.add(k)
        }
        Store.writeRaw(WRITTEN_KEY, JSONArray(written).toString())
        return written.size
    }

    /** 关掉开关：撤销本应用写过的全部闹钟 */
    fun cancelAll(context: Context) {
        writtenKeys(context).forEach { k ->
            val parts = k.split("#")
            if (parts.size == 2) cancelOne(context, parts[0], parts[1])
        }
        Store.writeRaw(WRITTEN_KEY, "[]")
    }

    /** 闹钟响过之后，把它从「已写」清单里去掉（下次同步会补新日期的） */
    fun markFired(context: Context, date: String, ban: String) {
        val rest = writtenKeys(context).filter { it != key(date, ban) }
        Store.writeRaw(WRITTEN_KEY, JSONArray(rest).toString())
    }

    // ---------- 内部 ----------

    private fun key(date: String, ban: String) = date + "#" + ban

    private fun requestCode(date: String, ban: String) = (date + "#" + ban).hashCode()

    private fun pendingIntent(context: Context, date: String, ban: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_DATE, date)
            putExtra(EXTRA_BAN, ban)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(date, ban), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelOne(context: Context, date: String, ban: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(pendingIntent(context, date, ban)) }
    }

    private fun writtenKeys(context: Context): List<String> {
        val raw = Store.readStringOrNull(WRITTEN_KEY) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotEmpty()) out.add(s)
            }
            out
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun triggerAt(date: String, hour: Int, minute: Int): Long {
        val dayMs = Dates.parseYmd(date) ?: return 0L
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayMs
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

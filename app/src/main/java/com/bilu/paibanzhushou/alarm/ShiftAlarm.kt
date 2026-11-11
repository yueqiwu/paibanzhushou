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
import com.bilu.paibanzhushou.data.AlarmStore
import com.bilu.paibanzhushou.data.HolidayRepo
import com.bilu.paibanzhushou.data.PeopleStore
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
/** 一条已写入系统的闹钟 */
data class AlarmEntry(val date: String, val ban: String, val triggerAt: Long)

object ShiftAlarm {
    const val ACTION_FIRE = "com.bilu.paibanzhushou.action.SHIFT_ALARM"
    const val EXTRA_DATE = "alarm_date"
    const val EXTRA_BAN = "alarm_ban"
    const val EXTRA_FROM_ALARM_CLOCK = "alarm_from_clock"
    private const val SNOOZE_RC = 8801

    private const val WRITTEN_KEY = "paiban_alarm_written_v1"
    /** 用户手动取消过的单条闹钟（date#ban）：重写窗口时跳过，免得又被加回来 */
    private const val SKIP_KEY = "paiban_alarm_skipped_v1"

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
        val today = Dates.formatYmd(Dates.todayStart())
        // 手动取消过的条目只对「还没到点的那天」有意义，过期的顺手清掉，避免无限堆积
        val skip = skippedKeys(context).filter { it.substringBefore('#') >= today }.toMutableSet()
        Store.writeRaw(SKIP_KEY, JSONArray(skip.toList()).toString())

        val desired = LinkedHashMap<String, Pair<String, String>>()
        targetDays(person, config).forEach { (date, ban) ->
            val trigger = triggerAt(date, config.hour, config.minute)
            if (trigger > now && !skip.contains(key(date, ban))) desired[key(date, ban)] = date to ban
        }

        // 撤销已经不需要的（改班次 / 改时间 / 关掉开关 / 换了人员）
        writtenKeys(context).forEach { k ->
            if (!desired.containsKey(k)) {
                val parts = k.split("#")
                if (parts.size == 2) cancelOne(context, parts[0], parts[1])
            }
        }

        // setAlarmClock 在缺「闹钟和提醒」权限时会抛 SecurityException，这里按实际成功数记账，
        // 免得出现「设置里显示已写入 N 个闹钟，系统里其实一个都没有」的假象。
        val written = ArrayList<String>(desired.size)
        android.util.Log.i("BootAlarm", "sync desired=" + desired.size + " exact=" + am.canScheduleExactAlarms())
        desired.forEach { (k, p) ->
            val (date, ban) = p
            val pi = pendingIntent(context, date, ban)
            // 每条闹钟用各自的 showIntent（requestCode 不同），避免 ROM 把「同一条闹钟」合并
            val showIntent = showIntent(context, date, ban)
            val ok = runCatching {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt(date, config.hour, config.minute), showIntent), pi)
            }.isSuccess
            if (ok) written.add(k)
        }
        Store.writeRaw(WRITTEN_KEY, JSONArray(written).toString())
        // 常驻前台服务：ColorOS 会把「后台闲置」应用的精确闹钟整体延后约 3 天，
        // 前台服务让本应用不进入闲置状态，闹钟才能准点触发。
        if (written.isNotEmpty()) AlarmKeepAliveService.start(context, written.size)
        else AlarmKeepAliveService.stop(context)
        return written.size
    }

    /**
     * 按「当前设置 + 当前人员」直接重写一遍窗口。
     *
     * 用处：闹钟每次响过之后顺手把 60 天窗口往后补一段。这样只要闹钟还在照常触发，
     * 窗口就会一直往后滚，不会因为「两个月没打开过 App」而断掉。
     */
    fun resyncActive(context: Context): Int {
        val config = AlarmStore.load()
        if (!config.enabled) return 0
        val people = runCatching {
            PeopleStore.loadPeople(
                Dates.formatYmd(Dates.todayStart()),
                listOf("白班", "中班", "休息", "休息")
            )
        }.getOrNull() ?: return -1
        val activeId = PeopleStore.loadActivePersonId()
        val person = people.firstOrNull { it.id == activeId } ?: people.firstOrNull() ?: return -1
        return sync(context, person, config)
    }

    /** 已写入的闹钟（按触发时间升序）：date / ban / 触发毫秒 */
    fun writtenEntries(context: Context, config: AlarmConfig): List<AlarmEntry> {
        return writtenKeys(context).mapNotNull { k ->
            val parts = k.split("#")
            if (parts.size != 2) null
            else AlarmEntry(parts[0], parts[1], triggerAt(parts[0], config.hour, config.minute))
        }.sortedBy { it.triggerAt }
    }

    /** 手动取消过的闹钟（date#ban，按日期升序），给闹钟列表页的「已取消」区用 */
    fun skippedEntries(context: Context): List<String> = skippedKeys(context).sorted()

    /** 某天某班次的闹钟开着吗（闹钟列表页用来显示「已取消 / 已加入」用不到，留着给恢复逻辑判断） */
    fun isSkipped(context: Context, date: String, ban: String): Boolean =
        skippedKeys(context).contains(key(date, ban))

    /**
     * 只取消某一天的闹钟：撤掉系统里的那一条，并且记下「用户主动跳过」，
     * 之后重写窗口（改排班、开机、每次响铃后续写）都不会再把它加回来。
     */
    fun cancelSingle(context: Context, date: String, ban: String) {
        cancelOne(context, date, ban)
        val rest = writtenKeys(context).filter { it != key(date, ban) }
        Store.writeRaw(WRITTEN_KEY, JSONArray(rest).toString())
        val skip = skippedKeys(context).toMutableSet().apply { add(key(date, ban)) }
        Store.writeRaw(SKIP_KEY, JSONArray(skip.toList()).toString())
        if (rest.isEmpty()) AlarmKeepAliveService.stop(context)
    }

    /** 撤销「手动取消」，让它回到正常排班闹钟里 */
    fun restoreSingle(context: Context, date: String, ban: String) {
        val skip = skippedKeys(context).toMutableSet().apply { remove(key(date, ban)) }
        Store.writeRaw(SKIP_KEY, JSONArray(skip.toList()).toString())
        runCatching { resyncActive(context) }
    }

    /** 关掉开关：撤销本应用写过的全部闹钟（含「稍后提醒」用的临时闹钟） */
    fun cancelAll(context: Context) {
        writtenKeys(context).forEach { k ->
            val parts = k.split("#")
            if (parts.size == 2) cancelOne(context, parts[0], parts[1])
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (am != null) runCatching { am.cancel(snoozeIntent(context, "", "")) }
        Store.writeRaw(WRITTEN_KEY, "[]")
        AlarmKeepAliveService.stop(context)
    }

    /**
     * 「稍后提醒」：把这条班次再排 [minutes] 分钟后的一个闹钟。
     * 固定 requestCode，所以同一个班次重复点稍后只会覆盖上一条。
     */
    fun scheduleSnooze(context: Context, date: String, ban: String, minutes: Int = 5) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val trigger = System.currentTimeMillis() + minutes.coerceIn(1, 60) * 60_000L
        val showIntent = showIntent(context, date, ban)
        val pi = snoozeIntent(context, date, ban)
        runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showIntent), pi)
        }.onFailure { android.util.Log.w("AlarmRing", "snooze failed", it) }
    }

    private fun snoozeIntent(context: Context, date: String, ban: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE + "/snooze"
            putExtra(EXTRA_DATE, date)
            putExtra(EXTRA_BAN, ban)
            putExtra(EXTRA_FROM_ALARM_CLOCK, true)
        }
        return PendingIntent.getBroadcast(
            context, SNOOZE_RC, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 闹钟响过之后，把它从「已写」清单里去掉（下次同步会补新日期的） */
    fun markFired(context: Context, date: String, ban: String) {
        val rest = writtenKeys(context).filter { it != key(date, ban) }
        Store.writeRaw(WRITTEN_KEY, JSONArray(rest).toString())
    }

    // ---------- 内部 ----------

    private fun key(date: String, ban: String) = date + "#" + ban

    private fun requestCode(date: String, ban: String) = (date + "#" + ban).hashCode()

    /**
     * 每条闹钟用自己的 action（tag）：ColorOS 的 OplusAlarmAdjustment 会按 tag 把「同一个应用
     * 的一批闹钟」当成一组做延后（实测把最近的闹钟推后了约 3 天），逐条区分后不再被合并。
     */
    private fun actionFor(date: String, ban: String) = ACTION_FIRE + "/" + date + "#" + ban

    private fun showIntent(context: Context, date: String, ban: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode(date, ban), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingIntent(context: Context, date: String, ban: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = actionFor(date, ban)
            putExtra(EXTRA_DATE, date)
            putExtra(EXTRA_BAN, ban)
            putExtra(EXTRA_FROM_ALARM_CLOCK, true)
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

    private fun skippedKeys(context: Context): List<String> {
        val raw = Store.readStringOrNull(SKIP_KEY) ?: return emptyList()
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
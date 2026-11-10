package com.bilu.paibanzhushou.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bilu.paibanzhushou.core.Dates
import com.bilu.paibanzhushou.data.AlarmStore
import com.bilu.paibanzhushou.data.PeopleStore

/**
 * 重启后 AlarmManager 里的闹钟会被系统清空，这里按当前设置重写一遍。
 *
 * 坑：开机阶段应用私有存储可能还读不到（用户未解锁 / 凭据加密存储未挂载），
 * 此时 [AlarmStore.load] 会拿到默认值（enabled=false），补写就会静默失败。
 * 所以「读不到设置」时用 [AlarmManager.setAndAllowWhileIdle] 隔一会儿再来一次
 * （25s 想一次，仍失败就 90s 再想一次），这样不依赖进程还活着。
 */
class BootReceiver : BroadcastReceiver() {
    private val bootActions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        "android.intent.action.QUICKBOOT_POWERON",
        Intent.ACTION_USER_UNLOCKED,
        Intent.ACTION_MY_PACKAGE_REPLACED
    )

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_RETRY && action !in bootActions) return
        val app = context.applicationContext
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 0)
        val ok = attemptSync(app)
        Log.i("BootAlarm", "action=" + action + " attempt=" + attempt + " ok=" + ok)
        if (ok || action == ACTION_RETRY && attempt >= 1) return
        scheduleRetry(app, attempt + 1, if (attempt == 0) 25_000L else 90_000L)
    }

    /** 读设置并补写闹钟；返回 true 表示设置已可读（该做的都做了） */
    private fun attemptSync(context: Context): Boolean {
        val config = AlarmStore.load()
        if (!config.enabled) return false
        val people = runCatching {
            PeopleStore.loadPeople(
                Dates.formatYmd(Dates.todayStart()),
                listOf("白班", "中班", "休息", "休息")
            )
        }.getOrNull() ?: return false
        val activeId = PeopleStore.loadActivePersonId()
        val person = people.firstOrNull { it.id == activeId } ?: people.firstOrNull() ?: return false
        val n = runCatching { ShiftAlarm.sync(context, person, config) }
            .onFailure { Log.w("BootAlarm", "sync failed", it) }
            .getOrDefault(-1)
        Log.i("BootAlarm", "sync n=" + n + " people=" + people.size)
        return true
    }

    private fun scheduleRetry(context: Context, attempt: Int, delayMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, BootReceiver::class.java).apply {
            action = ACTION_RETRY
            putExtra(EXTRA_ATTEMPT, attempt)
        }
        val pi = PendingIntent.getBroadcast(
            context, 9000 + attempt, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi)
        }
        Log.i("BootAlarm", "retry scheduled attempt=" + attempt + " in " + delayMs + "ms")
    }

    companion object {
        const val ACTION_RETRY = "com.bilu.paibanzhushou.action.ALARM_RESYNC"
        private const val EXTRA_ATTEMPT = "attempt"
    }
}

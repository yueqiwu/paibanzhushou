package com.bilu.paibanzhushou.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** 班次闹钟到点：弹全屏响铃页（通知 + 直接拉起双保险），并把这条从「已写」清单里划掉 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra(ShiftAlarm.EXTRA_DATE) ?: return
        val ban = intent.getStringExtra(ShiftAlarm.EXTRA_BAN) ?: return
        Log.i("AlarmRing", "fire date=" + date + " ban=" + ban + " fromAlarm=" + intent.getBooleanExtra(ShiftAlarm.EXTRA_FROM_ALARM_CLOCK, false))
        runCatching { ShiftAlarm.markFired(context, date, ban) }
        // ① 声音：前台服务自己放闹铃音。锁屏/后台时由系统拦掉的是「启动界面」，
        //    拦不到前台服务，所以这条路径不受全屏通知权限影响。
        AlarmRingService.start(context, date, ban)
        // ② 通知（带 fullScreenIntent）：全屏通知可用时顺带亮屏显示响铃页
        AlarmNotifications.showRinging(context, date, ban)
        // ③ 双保险：部分 ROM 不弹全屏 intent，这里再直接起一次（后台启动会被拒，忽略即可）
        runCatching {
            val fire = AlarmRingActivity.intent(context, date, ban)
            context.startActivity(fire)
        }.onFailure { Log.w("AlarmRing", "startActivity fallback failed: " + it.message) }
    }
}

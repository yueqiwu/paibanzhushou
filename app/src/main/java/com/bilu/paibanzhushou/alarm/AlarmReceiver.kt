package com.bilu.paibanzhushou.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 班次闹钟到点：弹全屏提醒，并把这条从「已写」清单里划掉 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra(ShiftAlarm.EXTRA_DATE) ?: return
        val ban = intent.getStringExtra(ShiftAlarm.EXTRA_BAN) ?: return
        runCatching { ShiftAlarm.markFired(context, date, ban) }
        AlarmNotifications.show(context, date, ban)
    }
}

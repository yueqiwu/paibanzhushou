package com.bilu.paibanzhushou.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import com.bilu.paibanzhushou.MainActivity

/**
 * 常驻前台服务：只要开着「班次闹钟」，就让进程一直活着。
 *
 * 为什么需要：ColorOS / ColorOS 系的省电策略会把「后台闲置超过几分钟」的应用的
 * 精确闹钟整体推后（实测 setAlarmClock 与 setExactAndAllowWhileIdle 都会被
 * OplusAlarmAdjustment 加上约 3 天的 adjustment，到点完全不响）。
 * 前台服务让本应用不再处于「后台闲置」状态，AlarmManager 的闹钟才会准点触发；
 * 顺带也保证到点由本应用弹全屏响铃页时进程还在。
 */
class AlarmKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra(EXTRA_COUNT, 0) ?: 0
        runCatching { startForeground(NOTIF_ID, build(this, count)) }
            .onFailure { Log.w("AlarmRing", "startForeground failed: " + it.message) }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { stopForeground(true) }
    }

    companion object {
        private const val CHANNEL_ID = "alarm_keepalive"
        private const val NOTIF_ID = 9101
        const val EXTRA_COUNT = "alarm_count"

        fun start(context: Context, count: Int) {
            val intent = Intent(context, AlarmKeepAliveService::class.java).putExtra(EXTRA_COUNT, count)
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.w("AlarmRing", "start FGS failed: " + it.message) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AlarmKeepAliveService::class.java)) }
        }

        fun update(context: Context, count: Int) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            runCatching { nm.notify(NOTIF_ID, build(context, count)) }
        }

        private fun build(context: Context, count: Int): Notification {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "班次闹钟常驻", NotificationManager.IMPORTANCE_LOW).apply {
                            description = "开着班次闹钟时保持运行，保证到点能响"
                            setShowBadge(false)
                        }
                    )
                }
            }
            val content = PendingIntent.getActivity(
                context, 9102, Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (count > 0) "已排好 " + count + " 个闹钟，到点会响" else "保持运行中"
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("班次闹钟已开启")
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(content)
                .build()
        }
    }
}

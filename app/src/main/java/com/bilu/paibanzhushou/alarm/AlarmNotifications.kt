package com.bilu.paibanzhushou.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bilu.paibanzhushou.MainActivity

/**
 * 班次闹钟到点后的通知：高优先级 + 全屏 intent（拉起 [AlarmRingActivity]）+ 关闭/稍后提醒按钮。
 *
 * 注意：响铃本身由 [AlarmRingActivity] 用 MediaPlayer 循环播放（USAGE_ALARM），
 * 这里的通道故意不放声音，避免「通知响一声 + 页面循环响」两路声音叠在一起。
 * 如果系统没允许全屏通知、页面起不来，用户仍会看到这条常驻通知并可以点按钮处理。
 */
object AlarmNotifications {
    private const val CHANNEL_ID = "shift_alarm_ring_v1"
    private const val LEGACY_CHANNEL_ID = "shift_alarm"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(CHANNEL_ID, "班次闹钟（响铃）", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "排到需要上班的班次时全屏响铃提醒"
                setSound(null, attrs)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** 是否有通知权限（没有就不发，避免系统静默丢） */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 系统是否允许本应用使用「全屏通知」（Android 14+ 需要用户/系统授予） */
    fun canUseFullScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return nm.canUseFullScreenIntent()
    }

    fun showRinging(context: Context, date: String, ban: String) {
        if (!canPost(context)) return
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val fire = AlarmRingActivity.intent(context, date, ban)
        val fullPi = PendingIntent.getActivity(
            context, requestCode(date, ban, 0), fire,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissPi = PendingIntent.getActivity(
            context, requestCode(date, ban, 1),
            AlarmRingActivity.intent(context, date, ban, AlarmRingActivity.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getActivity(
            context, requestCode(date, ban, 2),
            AlarmRingActivity.intent(context, date, ban, AlarmRingActivity.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openApp = PendingIntent.getActivity(
            context, requestCode(date, ban, 3),
            android.content.Intent(context, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = date + " 排到「" + ban + "」，记得按时到岗"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("班次闹钟 · " + ban)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setContentIntent(fullPi)
            .setFullScreenIntent(fullPi, true)
            .setDeleteIntent(openApp)
            .addAction(0, "关闭闹钟", dismissPi)
            .addAction(0, "5 分钟后提醒", snoozePi)
        runCatching { nm.notify(notificationId(date, ban), builder.build()) }
    }

    fun cancel(context: Context, date: String, ban: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { nm.cancel(notificationId(date, ban)) }
        runCatching { nm.cancel(LEGACY_CHANNEL_ID.hashCode()) }
    }

    private fun notificationId(date: String, ban: String) = (date + "#" + ban).hashCode()

    private fun requestCode(date: String, ban: String, slot: Int) =
        (date + "#" + ban + "#" + slot).hashCode()
}

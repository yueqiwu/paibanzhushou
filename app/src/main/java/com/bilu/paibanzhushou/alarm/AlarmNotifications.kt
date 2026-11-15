package com.bilu.paibanzhushou.alarm

import android.Manifest
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bilu.paibanzhushou.MainActivity

/**
 * 班次闹钟到点后的通知：高优先级 + 全屏 intent（拉起 [AlarmRingActivity]）+ 关闭/稍后提醒按钮。
 *
 * 1.0.9 修复「到点只弹一条通知、不响铃」：
 * - 老通道 shift_alarm_ring_v1 建的时候被系统降成了 IMPORTANCE_DEFAULT（曾用 setSilent(true)），
 *   而全屏 intent 只在通道重要性 >= HIGH 时才会被系统执行，于是锁屏/后台只会看到一条通知。
 *   这里改用新通道 v2，并显式把通道重要性拉到 HIGH，同时不再 setSilent。
 * - 通道本身带闹铃音轨（USAGE_ALARM），所以就算系统没允许「全屏通知」，
 *   通知也会按闹铃音量响一声 + 震动，而不是静默一条。
 */
object AlarmNotifications {
    private const val CHANNEL_ID = "shift_alarm_ring_v2"
    /** 给手表镜像用的通知通道：必须「会响/会震」，静默通知 OPPO 的通知桥不会转给手表 */
    private const val CHANNEL_ID_WATCH = "shift_alarm_watch_v1"
    /**
     * 循环提醒手表用的通道（1.0.13）。
     *
     * 手表只会在「通知有新的一次提醒」时震一下，所以要靠反复更新同一条通知来制造连续震动。
     * 这条通道不能带声音，否则手机每 12 秒会跟着响一声；只保留震动 + 高重要性。
     */
    private const val CHANNEL_ID_WATCH_LOOP = "shift_alarm_watch_v2"
    private val LEGACY_CHANNEL_IDS = listOf("shift_alarm_ring_v1", "shift_alarm")

    private fun ringtoneUri(): android.net.Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        LEGACY_CHANNEL_IDS.forEach { runCatching { nm.deleteNotificationChannel(it) } }

        val existing = runCatching { nm.getNotificationChannel(CHANNEL_ID) }.getOrNull()
        if (existing != null && existing.importance >= NotificationManager.IMPORTANCE_HIGH) return
        // 存在但被降过级（用户改过 / 系统降级）→ 删掉重建，恢复 HIGH
        if (existing != null) runCatching { nm.deleteNotificationChannel(CHANNEL_ID) }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "班次闹钟（响铃）",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "排到需要上班的班次时全屏响铃提醒"
            // 声音由 AlarmRingService 的 MediaPlayer 负责（界面可能被系统拦掉，服务不会），
            // 这里留空避免两条音轨叠在一起变成回声；震动保留，作为额外的触觉提示。
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 500, 700, 500, 1200)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    private fun ensureWatchChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = runCatching { nm.getNotificationChannel(CHANNEL_ID_WATCH) }.getOrNull()
        if (existing != null && existing.importance >= NotificationManager.IMPORTANCE_HIGH) return
        if (existing != null) runCatching { nm.deleteNotificationChannel(CHANNEL_ID_WATCH) }
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID_WATCH,
            "班次闹钟（手表提醒）",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "手表的提醒卡片：划掉它等于关闭闹钟"
            setSound(sound, attrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 500, 700, 500, 1200)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    /**
     * 循环震动用的通道：静音 + 高重要性 + 震动波形。
     *
     * importanc 必须是 HIGH：手表的通知桥/系统省电策略只转高优先级通知。
     */
    private fun ensureWatchLoopChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = runCatching { nm.getNotificationChannel(CHANNEL_ID_WATCH_LOOP) }.getOrNull()
        if (existing != null && existing.importance >= NotificationManager.IMPORTANCE_HIGH) return
        if (existing != null) runCatching { nm.deleteNotificationChannel(CHANNEL_ID_WATCH_LOOP) }
        val channel = NotificationChannel(
            CHANNEL_ID_WATCH_LOOP,
            "班次闹钟（手表连续提醒）",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "响铃期间循环提醒手表，让它持续震动；本身不发声"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 1200)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    /** 是否有通知权限（没有就不发，避免系统静默丢） */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 系统是否允许本应用使用「全屏通知」（Android 14+）。
     *
     * 两个来源任一为「允许」即可：ColorOS 的开关是 uid 级 appop（查询 package 级仍是 default，
     * 但 [NotificationManager.canUseFullScreenIntent] 返回 true），纯 AOSP 上则要看 appop。
     */
    fun canUseFullScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null && runCatching { nm.canUseFullScreenIntent() }.getOrDefault(false)) return true
        val am = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return true
        val mode = runCatching {
            am.unsafeCheckOpNoThrow(
                // AppOpsManager.OPSTR_USE_FULL_SCREEN_INTENT（预览版 SDK 里没有这个常量）
                "android:use_full_screen_intent",
                Process.myUid(),
                context.packageName
            )
        }.getOrNull() ?: return true
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 是否有「后台弹出界面」权限（SYSTEM_ALERT_WINDOW）。
     *
     * 有它才会被 Android 视为「可以后台启动 Activity」的例外，锁屏/后台时才能真正弹出响铃页；
     * 没有的话 startActivity 会被系统以 BAL_BLOCK 拒掉（铃声仍由前台服务播放，不受影响）。
     */
    fun canDrawOverlays(context: Context): Boolean =
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    fun showRinging(context: Context, date: String, ban: String) {
        if (!canPost(context)) return
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val fire = AlarmRingActivity.intent(context, date, ban)
        val fullPi = PendingIntent.getActivity(
            context, requestCode(date, ban, 0), fire,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 关闭/稍后都走 Service：手表或通知栏点一下就能直接停铃，不会闪一下界面
        val dismissPi = PendingIntent.getService(
            context, requestCode(date, ban, 1),
            ringCommand(context, AlarmRingService.ACTION_DISMISS, date, ban),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getService(
            context, requestCode(date, ban, 2),
            ringCommand(context, AlarmRingService.ACTION_SNOOZE, date, ban),
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
            .setOnlyAlertOnce(false)
            .setContentIntent(fullPi)
            .setFullScreenIntent(fullPi, true)
            // 划掉通知（手表上也是同样的动作）就等于关闭闹钟
            .setDeleteIntent(dismissPi)
            .addAction(0, "关闭闹钟", dismissPi)
            .addAction(0, "5 分钟后提醒", snoozePi)
            // 手表（Wear OS 及多数蓝牙手表的通知同步）优先读 WearableExtender 里的动作
            .extend(
                NotificationCompat.WearableExtender()
                    .addAction(NotificationCompat.Action(0, "关闭闹钟", dismissPi))
                    .addAction(NotificationCompat.Action(0, "5 分钟后提醒", snoozePi))
            )
        runCatching { nm.notify(notificationId(date, ban), builder.build()) }

        // 第二条：专门给手表镜像用的「普通通知」。
        // ColorOS 手表（OPPO Watch）通过 com.heytap.health 的 NotificationListenerService 同步通知，
        // 这类桥接通常会过滤掉 ONGOING（常驻/前台服务）通知，所以第一条在手表上根本不会出现。
        // 这里再发一条不常驻、可划掉的同类通知：手表上能看到、能划掉；划掉 = deleteIntent = 关闭闹钟。
        ensureWatchChannel(context)
        val mirror = buildMirror(context, date, ban, text, onlyAlertOnce = true, channel = CHANNEL_ID_WATCH)
        runCatching { nm.notify(notificationId(date, ban) + 1, mirror) }
    }

    /**
     * 手表镜像通知。
     *
     * [onlyAlertOnce] = false 时，同一条通知每 notify 一次就会被当作「新提醒」重新提醒一次，
     * 手表的通知桥会跟着震一次 —— 循环响应用它来制造「持续震动」。
     */
    private fun buildMirror(
        context: Context,
        date: String,
        ban: String,
        text: String,
        onlyAlertOnce: Boolean,
        channel: String
    ): Notification {
        val fullPi = PendingIntent.getActivity(
            context, requestCode(date, ban, 0),
            AlarmRingActivity.intent(context, date, ban),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissPi = PendingIntent.getService(
            context, requestCode(date, ban, 1),
            ringCommand(context, AlarmRingService.ACTION_DISMISS, date, ban),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getService(
            context, requestCode(date, ban, 2),
            ringCommand(context, AlarmRingService.ACTION_SNOOZE, date, ban),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("班次闹钟 · " + ban)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(onlyAlertOnce)
            .setContentIntent(fullPi)
            .setDeleteIntent(dismissPi)
            .addAction(0, "关闭闹钟", dismissPi)
            .addAction(0, "5 分钟后提醒", snoozePi)
            .build()
    }

    /**
     * 响铃期间周期性刷新镜像通知，让手表反复震动。
     *
     * 手表端只会在通知「有新提醒」时震一次，所以这里每次都用不同的正文（已响铃时长），
     * 并且 onlyAlertOnce=false，保证每次刷新都会再震一次。
     */
    fun updateWatchPing(context: Context, date: String, ban: String, elapsedSeconds: Int) {
        if (!canPost(context)) return
        ensureWatchLoopChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val mm = elapsedSeconds / 60
        val ss = elapsedSeconds % 60
        val elapsed = (if (mm > 0) "" + mm + " 分 " else "") + ss + " 秒"
        val text = "已响铃 " + elapsed + " · " + date + " 排到「" + ban + "」"
        // 发两条（同文案、不同 id）：手表的通知桥一条只震一下，两条连着下发震感更强，
        // 配合 2 秒的刷新间隔，体感接近「一直在震」。
        val n = buildMirror(context, date, ban, text, onlyAlertOnce = false, channel = CHANNEL_ID_WATCH_LOOP)
        runCatching { nm.notify(notificationId(date, ban) + 1, n) }
        runCatching { nm.notify(notificationId(date, ban) + 2, n) }
    }

    private fun ringCommand(context: Context, action: String, date: String, ban: String): android.content.Intent {
        return android.content.Intent(context, AlarmRingService::class.java).apply {
            this.action = action
            putExtra(ShiftAlarm.EXTRA_DATE, date)
            putExtra(ShiftAlarm.EXTRA_BAN, ban)
        }
    }

    fun cancel(context: Context, date: String, ban: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { nm.cancel(notificationId(date, ban)) }
        runCatching { nm.cancel(notificationId(date, ban) + 1) }
        runCatching { nm.cancel(notificationId(date, ban) + 2) }
    }

    private fun notificationId(date: String, ban: String) = (date + "#" + ban).hashCode()

    private fun requestCode(date: String, ban: String, slot: Int) =
        (date + "#" + ban + "#" + slot).hashCode()
}
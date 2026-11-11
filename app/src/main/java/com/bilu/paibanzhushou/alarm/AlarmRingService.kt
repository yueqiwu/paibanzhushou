package com.bilu.paibanzhushou.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 到点响铃的前台服务。
 *
 * 为什么不能用「全屏通知 + 响铃页」来负责发声：
 * Android 14+ 起，全屏 intent 需要用户授予 USE_FULL_SCREEN_INTENT，没授予时系统会以
 * BAL_BLOCK 直接拒掉这次 Activity 启动（实测日志：
 * "Background activity launch blocked ... result code=102"），
 * 响铃页根本没起来，于是「后台 / 锁屏不响」。
 *
 * 声音不能依赖界面，所以这里用前台服务自己放闹铃音 + 震动：
 * 前台服务（尤其是精确闹钟广播里拉起的）不受后台启动 Activity 的限制，
 * 锁屏、息屏、应用在后台都能响，关闭按钮也从这条常驻通知上点。
 */
class AlarmRingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var dateText = ""
    private var banText = ""
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { finishRing() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching { startForeground(NOTIF_ID, buildNotification()) }
            .onFailure { Log.w(TAG, "startForeground failed: " + it.message) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DISMISS) {
            finishRing()
            return START_NOT_STICKY
        }
        if (action == ACTION_SNOOZE) {
            val d = intent.getStringExtra(ShiftAlarm.EXTRA_DATE) ?: dateText
            val b = intent.getStringExtra(ShiftAlarm.EXTRA_BAN) ?: banText
            if (d.isNotEmpty() && b.isNotEmpty()) {
                runCatching { ShiftAlarm.scheduleSnooze(this, d, b, 5) }
            }
            finishRing()
            return START_NOT_STICKY
        }

        intent?.getStringExtra(ShiftAlarm.EXTRA_DATE)?.let { if (it.isNotEmpty()) dateText = it }
        intent?.getStringExtra(ShiftAlarm.EXTRA_BAN)?.let { if (it.isNotEmpty()) banText = it }
        runCatching { startForeground(NOTIF_ID, buildNotification()) }
        startRinging()
        handler.removeCallbacks(autoStop)
        // 兜底：5 分钟后自动停，避免没人处理一直响
        handler.postDelayed(autoStop, 5 * 60 * 1000L)
        Log.i(TAG, "ringing date=" + dateText + " ban=" + banText)
        return START_NOT_STICKY
    }

    private fun startRinging() {
        if (player != null) return
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .build()
                focusRequest = req
                runCatching { am.requestAudioFocus(req) }
            }
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "play failed: " + it.message) }
        startVibrating()
    }

    private fun startVibrating() {
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = v
            val pattern = longArrayOf(0, 700, 500, 700, 500, 1200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
        }
    }

    private fun stopRinging() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            }
        }
        focusRequest = null
    }

    /** 停止响铃、撤销通知、结束服务 */
    private fun finishRing() {
        handler.removeCallbacks(autoStop)
        stopRinging()
        if (dateText.isNotEmpty() && banText.isNotEmpty()) {
            runCatching { AlarmNotifications.cancel(this, dateText, banText) }
        }
        runCatching { stopForeground(true) }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        stopRinging()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "班次闹钟响铃中", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "响铃时显示，提供关闭/稍后提醒"
                        setShowBadge(false)
                        setSound(null, null)
                    }
                )
            }
        }
        val open = PendingIntent.getActivity(
            this, 9201, AlarmRingActivity.intent(this, dateText, banText),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getService(
            this, 9202, commandIntent(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getService(
            this, 9203, commandIntent(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("班次闹钟 · " + banText)
            .setContentText(dateText + " 排到「" + banText + "」，正在响铃")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "关闭闹钟", dismiss)
            .addAction(0, "5 分钟后提醒", snooze)
            .build()
    }

    private fun commandIntent(action: String): Intent {
        return Intent(this, AlarmRingService::class.java).apply {
            this.action = action
            putExtra(ShiftAlarm.EXTRA_DATE, dateText)
            putExtra(ShiftAlarm.EXTRA_BAN, banText)
        }
    }

    companion object {
        private const val TAG = "AlarmRing"
        private const val CHANNEL_ID = "alarm_ringing"
        private const val NOTIF_ID = 9103
        const val ACTION_DISMISS = "com.bilu.paibanzhushou.action.RING_DISMISS"
        const val ACTION_SNOOZE = "com.bilu.paibanzhushou.action.RING_SNOOZE"

        /**
         * 开始响铃。用 startForegroundService，到点广播来自 setAlarmClock，
         * 属于系统允许「后台启动前台服务」的场景；失败时只记日志，由通知兜底。
         */
        fun start(context: Context, date: String, ban: String) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                putExtra(ShiftAlarm.EXTRA_DATE, date)
                putExtra(ShiftAlarm.EXTRA_BAN, ban)
            }
            val ok = runCatching { context.startForegroundService(intent) }.isSuccess
            if (!ok) Log.w(TAG, "start ring FGS failed")
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AlarmRingService::class.java)) }
        }
    }
}

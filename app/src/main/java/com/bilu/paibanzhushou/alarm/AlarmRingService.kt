package com.bilu.paibanzhushou.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
    private var ringStartedAt = 0L
    private var screenWatcherOn = false
    private var overlayView: View? = null

    /** 起响铃页（有悬浮窗权限时才可能成功，见 [showBalOverlay]） */
    private val startRingPage = Runnable {
        val ok = runCatching {
            startActivity(
                AlarmRingActivity.intent(this, dateText, banText)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        Log.i(TAG, "start ring page: " + (ok.isSuccess) + " " + (ok.exceptionOrNull()?.message ?: ""))
    }
    private val hideOverlay = Runnable { hideBalOverlay() }

    /**
     * 电源键 = 停铃。
     *
     * 应用收不到 KEYCODE_POWER（系统自己消费），所以用屏幕状态广播模拟「按一下电源键」：
     * 响铃期间只要屏幕状态发生切换（息屏→亮屏、亮屏→息屏），就当作机主按了电源键，停掉铃声。
     * 开响后 3 秒内忽略，避免全屏响铃页自己亮屏（setTurnScreenOn）把自己关掉。
     */
    private val screenWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action != Intent.ACTION_SCREEN_ON && action != Intent.ACTION_SCREEN_OFF) return
            if (System.currentTimeMillis() - ringStartedAt < 3000L) return
            // 亮屏一律当作机主按了电源键（锁屏时只有按电源键才会亮屏）。
            // 息屏只有在响铃页正显示时才算「按电源键」——锁屏时系统自己也会发 SCREEN_OFF，
            // 那种不能算，否则闹钟刚响就被自己关掉了。
            // 注意不能用 activity 的「当前是否可见」：熄屏时系统会先让页面 onPause，
            // 那时可见性已变 false，判定就失效了。这里用「本次响铃期间是否曾经弹出过页面」。
            if (action == Intent.ACTION_SCREEN_OFF && !pageShown) return
            Log.i(TAG, "screen " + action + " -> dismiss alarm")
            finishRing()
        }
    }

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
        ringStartedAt = System.currentTimeMillis()
        active = true
        pageShown = false
        runCatching { startForeground(NOTIF_ID, buildNotification()) }
        startRinging()
        registerScreenWatcher()
        // 「后台弹出界面」只有权限还不够：Android 15+ 要求调用时确实叠着一个属于本应用的窗口，
        // 否则 startActivity 仍被 BAL_BLOCK 拦掉。这里先挂一个 1px 透明悬浮窗，再起响铃页。
        showBalOverlay()
        // 锁屏时不去起响铃页：ColorOS 会拦截「锁屏上弹界面」，还会把屏幕熄掉、顺带触发停铃，
        // 得不偿失。锁屏下靠铃声 + 通知（含手表动作），按电源键亮屏即停铃。
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (keyguard?.isKeyguardLocked == true) {
            Log.i(TAG, "keyguard locked, ring page skipped")
            handler.postDelayed(hideOverlay, 1500)
        } else {
            handler.postDelayed(startRingPage, 300)
            handler.postDelayed(hideOverlay, 4000)
        }
        handler.removeCallbacks(autoStop)
        // 兜底：5 分钟后自动停，避免没人处理一直响
        handler.postDelayed(autoStop, 5 * 60 * 1000L)
        Log.i(TAG, "ringing date=" + dateText + " ban=" + banText)
        return START_NOT_STICKY
    }

    /**
     * 挂一个 1px 透明悬浮窗。
     *
     * Android 15 起，「后台启动 Activity」对 SYSTEM_ALERT_WINDOW 的豁免要求
     * 「当前确实有本应用的非应用窗口可见」（BAL 日志里的 realCallingUidHasNonAppVisibleWindow），
     * 光有权限不够。响铃页起来之后 4 秒就撤掉，不影响用户。
     */
    private fun showBalOverlay() {
        if (overlayView != null) return
        if (!runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false)) {
            Log.w(TAG, "no overlay permission, ring page may be blocked")
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val v = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        val lp = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        runCatching { wm.addView(v, lp); overlayView = v }
            .onFailure { Log.w(TAG, "add overlay failed: " + it.message) }
    }

    private fun hideBalOverlay() {
        val v = overlayView ?: return
        overlayView = null
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        runCatching { wm.removeViewImmediate(v) }
    }

    private fun registerScreenWatcher() {
        if (screenWatcherOn) return
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenWatcher, f)
            screenWatcherOn = true
        }.onFailure { Log.w(TAG, "register screen watcher failed: " + it.message) }
    }

    private fun unregisterScreenWatcher() {
        if (!screenWatcherOn) return
        runCatching { unregisterReceiver(screenWatcher) }
        screenWatcherOn = false
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
        active = false
        handler.removeCallbacks(autoStop)
        handler.removeCallbacks(startRingPage)
        handler.removeCallbacks(hideOverlay)
        hideBalOverlay()
        unregisterScreenWatcher()
        stopRinging()
        if (dateText.isNotEmpty() && banText.isNotEmpty()) {
            runCatching { AlarmNotifications.cancel(this, dateText, banText) }
        }
        runCatching { stopForeground(true) }
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        handler.removeCallbacks(autoStop)
        handler.removeCallbacks(startRingPage)
        handler.removeCallbacks(hideOverlay)
        hideBalOverlay()
        unregisterScreenWatcher()
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
        /** 是否正在响铃；响铃页靠它判断「是不是已经被别人关掉了」 */
        @Volatile var active = false
            private set

        /** 本次响铃期间是否弹出过响铃页（由 [AlarmRingActivity] 的 onResume 置位） */
        @Volatile var pageShown = false
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

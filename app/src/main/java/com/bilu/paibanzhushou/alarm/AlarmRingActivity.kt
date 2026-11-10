package com.bilu.paibanzhushou.alarm

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilu.paibanzhushou.core.Dates
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闹钟到点后的全屏响铃页。
 *
 * 以前只发一条通知（响一声就结束），用户反馈「只是通知、不会响铃」，
 * 所以这里做成真正的闹钟：全屏亮起 + 循环闹铃音 + 震动，必须点「关闭」才停。
 *
 * 进入方式：
 * 1) 通知的全屏 intent（AlarmNotifications 里 setFullScreenIntent），锁屏/息屏也能拉起；
 * 2) AlarmReceiver 直接 startActivity 兜底。
 */
class AlarmRingActivity : ComponentActivity() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null

    private var dateText by mutableStateOf("")
    private var banText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

        handleIntent(intent)
        startRinging()

        setContent {
            AlarmRingScreen(
                date = dateText,
                ban = banText,
                onDismiss = { stopAndFinish() },
                onSnooze = { snoozeAndFinish() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val action = intent.getStringExtra(EXTRA_ACTION)
        when (action) {
            ACTION_DISMISS -> stopAndFinish()
            ACTION_SNOOZE -> snoozeAndFinish()
            else -> {
                handleIntent(intent)
                startRinging()
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        dateText = intent?.getStringExtra(ShiftAlarm.EXTRA_DATE) ?: Dates.formatYmd(Dates.todayStart())
        banText = intent?.getStringExtra(ShiftAlarm.EXTRA_BAN) ?: "班次"
    }

    // ---------- 响铃 ----------

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
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            am.ringerMode // 不强制改铃声模式，但 USAGE_ALARM 在静音/免打扰下仍会响（系统允许闹钟）
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlarmRingActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { android.util.Log.w("AlarmRing", "play failed", it) }
        startVibrating()
        // 兜底：5 分钟后自动停，避免无人处理一直响
        window.decorView.postDelayed({ if (!isFinishing) stopAndFinish() }, 5 * 60 * 1000L)
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
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
        focusRequest = null
    }

    private fun stopAndFinish() {
        stopRinging()
        AlarmNotifications.cancel(this, dateText, banText)
        finish()
    }

    private fun snoozeAndFinish() {
        val minutes = intent?.getIntExtra(EXTRA_SNOOZE_MIN, 5) ?: 5
        ShiftAlarm.scheduleSnooze(this, dateText, banText, minutes)
        stopAndFinish()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACTION = "alarm_action"
        const val ACTION_DISMISS = "com.bilu.paibanzhushou.action.ALARM_DISMISS"
        const val ACTION_SNOOZE = "com.bilu.paibanzhushou.action.ALARM_SNOOZE"
        const val EXTRA_SNOOZE_MIN = "alarm_snooze_min"

        fun intent(context: Context, date: String, ban: String, action: String? = null, snoozeMinutes: Int = 5): Intent {
            return Intent(context, AlarmRingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(ShiftAlarm.EXTRA_DATE, date)
                putExtra(ShiftAlarm.EXTRA_BAN, ban)
                if (action != null) putExtra(EXTRA_ACTION, action)
                putExtra(EXTRA_SNOOZE_MIN, snoozeMinutes)
            }
        }
    }
}

@Composable
private fun AlarmRingScreen(
    date: String,
    ban: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    var now by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.US).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1020), Color(0xFF16233A), Color(0xFF0B1020))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "班次闹钟",
                color = Color(0xFF6AC695),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = now,
                color = Color.White,
                fontSize = 84.sp,
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = date,
                color = Color(0xCCFFFFFF),
                fontSize = 18.sp
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = "今天排到「" + ban + "」",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(64.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2))
            ) {
                Text("关闭闹钟", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x1FFFFFFF),
                    contentColor = Color.White
                )
            ) {
                Text("5 分钟后提醒", fontSize = 20.sp)
            }
        }
    }
}

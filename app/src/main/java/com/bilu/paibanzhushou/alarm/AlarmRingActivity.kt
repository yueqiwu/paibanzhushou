package com.bilu.paibanzhushou.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
 * 声音不在这里放：Android 14+ 没授予「全屏通知」时，后台起 Activity 会被系统以 BAL_BLOCK
 * 直接拒掉，界面根本不会出现。所以铃声交由 [AlarmRingService] 播放，本页只负责显示和按钮，
 * 用户点「关闭 / 稍后提醒」后再去把服务停掉。
 */
class AlarmRingActivity : ComponentActivity() {

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
        // 通知上的「关闭闹钟 / 5 分钟后提醒」也会拉起本页并带上动作，这里直接照做，不显示界面
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_DISMISS -> {
                dismissAll()
                return
            }
            ACTION_SNOOZE -> {
                snoozeAndFinish()
                return
            }
        }

        showScreen()
    }

    private fun showScreen() {
        setContent {
            // 服务一停（按电源键 / 通知里关闭 / 5 分钟超时），本页自动关掉，不会停在响铃页
            LaunchedEffect(Unit) {
                val startedAt = System.currentTimeMillis()
                var seenActive = false
                while (true) {
                    delay(500)
                    if (AlarmRingService.active) {
                        seenActive = true
                    } else if (seenActive || System.currentTimeMillis() - startedAt > 8000L) {
                        finish()
                        break
                    }
                }
            }
            AlarmRingScreen(
                date = dateText,
                ban = banText,
                onDismiss = { dismissAll() },
                onSnooze = { snoozeAndFinish() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmRingService.pageShown = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_DISMISS -> dismissAll()
            ACTION_SNOOZE -> snoozeAndFinish()
            else -> {
                handleIntent(intent)
                showScreen()
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        dateText = intent?.getStringExtra(ShiftAlarm.EXTRA_DATE) ?: Dates.formatYmd(Dates.todayStart())
        banText = intent?.getStringExtra(ShiftAlarm.EXTRA_BAN) ?: "班次"
    }

    private fun dismissAll() {
        AlarmRingService.stop(this)
        AlarmNotifications.cancel(this, dateText, banText)
        finish()
    }

    private fun snoozeAndFinish() {
        val minutes = intent?.getIntExtra(EXTRA_SNOOZE_MIN, 5) ?: 5
        ShiftAlarm.scheduleSnooze(this, dateText, banText, minutes)
        dismissAll()
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

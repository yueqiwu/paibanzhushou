package com.bilu.paibanzhushou.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.Configuration
import android.app.TimePickerDialog
import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bilu.paibanzhushou.core.Dates
import java.util.Calendar
import java.util.Locale

/**
 * 系统原生弹窗的按钮/月份名跟随 App 语言（App 只有简体中文，不跟随系统英文）。
 *
 * 注意必须用 ContextThemeWrapper 包住 Activity 的 context：WindowManager 会委托回 Activity，
 * 窗口 token 才有效；直接用 createConfigurationContext 会得到没有 token 的 context，
 * Dialog.show() 会抛 BadTokenException。
 */
private fun pickerContext(context: Context): Context {
    return runCatching {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.SIMPLIFIED_CHINESE)
        object : ContextThemeWrapper(context, 0) {
            init {
                applyOverrideConfiguration(config)
            }
        }
    }.getOrDefault(context)
}

/** 完整日期选择：系统原生的 DatePickerDialog（Material 日历样式）。 */
@Composable
fun DatePickerLauncher(
    show: Boolean,
    initialMs: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    if (!show) return
    val context = pickerContext(LocalContext.current)
    val cal = remember(show, initialMs) {
        Calendar.getInstance().apply { timeInMillis = initialMs }
    }
    DisposableEffect(show, initialMs) {
        val dialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                val ms = Dates.parseYmd(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
                    ?: initialMs
                onPick(ms)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.setOnCancelListener { onDismiss() }
        dialog.show()
        onDispose { runCatching { dialog.dismiss() } }
    }
}

/**
 * 只选年月：仍是系统 DatePickerDialog。原生日历样式没有「只选年月」模式，
 * 点这个月的任意一天再点确定即可，回调里统一取当月 1 号。
 */
@Composable
fun MonthPickerLauncher(
    show: Boolean,
    initialMs: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    if (!show) return
    val context = pickerContext(LocalContext.current)
    val cal = remember(show, initialMs) {
        Calendar.getInstance().apply { timeInMillis = initialMs }
    }
    DisposableEffect(show, initialMs) {
        val dialog = DatePickerDialog(
            context,
            { _, year, month, _ -> onPick(Dates.firstOfMonth(year, month + 1)) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.setTitle("选择月份")
        dialog.setOnCancelListener { onDismiss() }
        dialog.show()
        onDispose { runCatching { dialog.dismiss() } }
    }
}

/** 只选时刻：系统原生的 TimePickerDialog（24 小时制）。 */
@Composable
fun AlarmTimePickerSheet(
    show: Boolean,
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onPick: (Int, Int) -> Unit
) {
    if (!show) return
    val context = pickerContext(LocalContext.current)
    DisposableEffect(show, hour, minute) {
        val dialog = TimePickerDialog(
            context,
            { _, h, m -> onPick(h, m) },
            hour.coerceIn(0, 23),
            minute.coerceIn(0, 59),
            true
        )
        dialog.setOnCancelListener { onDismiss() }
        dialog.show()
        onDispose { runCatching { dialog.dismiss() } }
    }
}


/** 选闹铃音：底部浮层列出系统闹钟铃声，点一下试听，确定后保存。 */
@Composable
fun RingtonePickerSheet(
    show: Boolean,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    val options = remember(show) { com.bilu.paibanzhushou.data.AlarmRingtones.list(context) }
    var draft by remember(show) { mutableStateOf(current) }
    var preview by remember(show) { mutableStateOf<android.media.Ringtone?>(null) }

    fun stopPreview() {
        runCatching { preview?.stop() }
        preview = null
    }

    fun play(value: String?) {
        stopPreview()
        if (value == null || value == com.bilu.paibanzhushou.data.AlarmConfig.RINGTONE_SILENT) return
        runCatching {
            val r = android.media.RingtoneManager.getRingtone(context, android.net.Uri.parse(value))
            r?.audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            r?.play()
            preview = r
        }
    }

    DisposableEffect(show) {
        onDispose { runCatching { preview?.stop() } }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x59112034))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    stopPreview()
                    onDismiss()
                }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Color.White,
                    RoundedCornerShape(topStart = Rpx.dp(28), topEnd = Rpx.dp(28))
                )
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(Rpx.dp(88)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.weight(1f).fillMaxSize().clickable {
                        stopPreview()
                        onDismiss()
                    },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        "取消",
                        fontSize = Rpx.sp(30),
                        color = Color(0xFF333333),
                        modifier = Modifier.padding(start = Rpx.dp(30))
                    )
                }
                Text(
                    "闹钟铃声",
                    fontSize = Rpx.sp(30),
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF111111)
                )
                Box(
                    Modifier.weight(1f).fillMaxSize().clickable {
                        stopPreview()
                        onPick(draft)
                    },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        "确定",
                        fontSize = Rpx.sp(30),
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF007AFF),
                        modifier = Modifier.padding(end = Rpx.dp(30))
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(Rpx.dp(1)).background(Color(0xFFEBEBEB)))
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(Rpx.dp(620))
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { opt ->
                    val active = opt.value == draft
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = opt.value
                                play(opt.value)
                            }
                            .padding(horizontal = Rpx.dp(30), vertical = Rpx.dp(22)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = opt.title,
                            fontSize = Rpx.sp(28),
                            color = if (active) Color(0xFF007AFF) else Color(0xFF222222),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (active) {
                            Text("✓", fontSize = Rpx.sp(30), color = Color(0xFF007AFF))
                        }
                    }
                }
            }
        }
    }
}

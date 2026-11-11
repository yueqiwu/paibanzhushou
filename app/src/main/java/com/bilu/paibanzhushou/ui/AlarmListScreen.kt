package com.bilu.paibanzhushou.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.bilu.paibanzhushou.alarm.AlarmEntry
import com.bilu.paibanzhushou.alarm.ShiftAlarm
import com.bilu.paibanzhushou.core.Dates
import java.util.Calendar

/**
 * 闹钟列表页：把「自动写入系统闹钟」写进去的那些条目逐条列出来，允许单独取消某一天。
 *
 * 为什么需要它：自动写入是「未来 N 天里所有命中班次的日子」，用户临时某天不上班时
 * 只能整开关关掉；这里给到单条粒度，取消后会被记进 skipped 名单，后续重写窗口也不会再加回来。
 */
@Composable
fun AlarmListScreen(state: IndexState, onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val config = state.alarmConfig
    val entries: List<AlarmEntry> = remember(tick, config.enabled, config.hour, config.minute) {
        if (!config.enabled) emptyList() else ShiftAlarm.writtenEntries(context, config)
    }
    val skipped: List<String> = remember(tick, config.enabled) { ShiftAlarm.skippedEntries(context) }
    val todayStr = Dates.formatYmd(Dates.todayStart())
    val upcoming = entries.filter { it.date >= todayStr }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.pageBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Rpx.dp(40))
    ) {
        // 顶部：返回 + 标题（和同事排班表一致的处理）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Rpx.dp(16), end = Rpx.dp(24), top = Rpx.dp(10), bottom = Rpx.dp(8))
        ) {
            Text(
                text = "‹",
                fontSize = Rpx.sp(56),
                color = C.textPrimary,
                modifier = Modifier
                    .size(Rpx.dp(72))
                    .clickable { onBack() }
                    .padding(start = Rpx.dp(14)),
            )
            Spacer(Modifier.width(Rpx.dp(8)))
            Text(
                text = "闹钟列表",
                fontSize = Rpx.sp(34),
                fontWeight = FontWeight.SemiBold,
                color = C.textPrimary
            )
        }

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(16)),
            radius = Rpx.dp(24),
            contentPadding = PaddingValues(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(22), bottom = Rpx.dp(22))
        ) {
            Text(
                text = "共 " + upcoming.size + " 个待响闹钟",
                fontSize = Rpx.sp(28),
                fontWeight = FontWeight.SemiBold,
                color = C.textPrimary
            )
            Text(
                text = "每天 " + pad2(config.hour) + ":" + pad2(config.minute) +
                    " 响铃；取消某一天后，改排班、开机都不会把它加回来",
                fontSize = Rpx.sp(21),
                color = C.hint,
                lineHeight = Rpx.sp(32),
                modifier = Modifier.padding(top = Rpx.dp(8))
            )
        }

        if (upcoming.isEmpty()) {
            Text(
                text = if (config.enabled) "未来 " + config.horizonDays + " 天里没有选中班次的日子" else "闹钟开关没打开",
                fontSize = Rpx.sp(23),
                color = C.hint,
                modifier = Modifier.padding(start = Rpx.dp(30), top = Rpx.dp(26))
            )
        } else {
            SectionLabel("即将响铃", topMargin = Rpx.dp(26))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Rpx.dp(24))
                    .glassCard(radius = Rpx.dp(20), bgAlpha = 0.88f)
                    .padding(vertical = Rpx.dp(6))
            ) {
                upcoming.forEachIndexed { index, e ->
                    AlarmRow(
                        entry = e,
                        color = hexColor(state.banColor(e.ban)),
                        actionText = "取消",
                        actionColor = Color(0xFFE53935),
                        onAction = {
                            ShiftAlarm.cancelSingle(context, e.date, e.ban)
                            state.resyncAlarms()
                            tick++
                            state.toastMsg("已取消 " + e.date + " 的闹钟")
                        }
                    )
                    if (index != upcoming.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = Rpx.dp(20))
                                .height(Rpx.dp(1))
                                .background(Color(0x0F000000))
                        )
                    }
                }
            }
        }

        if (skipped.isNotEmpty()) {
            SectionLabel("已取消", topMargin = Rpx.dp(26))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Rpx.dp(24))
                    .glassCard(radius = Rpx.dp(20), bgAlpha = 0.88f)
                    .padding(vertical = Rpx.dp(6))
            ) {
                skipped.forEachIndexed { index, key ->
                    val parts = key.split("#")
                    val date = parts.getOrElse(0) { "" }
                    val ban = parts.getOrElse(1) { "" }
                    AlarmRow(
                        entry = AlarmEntry(date, ban, 0L),
                        color = hexColor(state.banColor(ban)),
                        actionText = "恢复",
                        actionColor = C.blue,
                        onAction = {
                            ShiftAlarm.restoreSingle(context, date, ban)
                            state.resyncAlarms()
                            tick++
                            state.toastMsg("已恢复 " + date + " 的闹钟")
                        }
                    )
                    if (index != skipped.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = Rpx.dp(20))
                                .height(Rpx.dp(1))
                                .background(Color(0x0F000000))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(
    entry: AlarmEntry,
    color: Color,
    actionText: String,
    actionColor: Color,
    onAction: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Rpx.dp(20), vertical = Rpx.dp(18))
    ) {
        Box(
            Modifier
                .size(Rpx.dp(18))
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(Rpx.dp(16)))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.date + "  " + weekdayOf(entry.date),
                fontSize = Rpx.sp(26),
                fontWeight = FontWeight.Medium,
                color = C.textPrimary
            )
            Text(
                text = if (entry.triggerAt > 0L) clockOf(entry.triggerAt) + " · " + entry.ban else entry.ban,
                fontSize = Rpx.sp(21),
                color = C.hint,
                modifier = Modifier.padding(top = Rpx.dp(4))
            )
        }
        Text(
            text = actionText,
            fontSize = Rpx.sp(23),
            color = actionColor,
            modifier = Modifier
                .clickable { onAction() }
                .padding(horizontal = Rpx.dp(14), vertical = Rpx.dp(8))
        )
    }
}

private fun pad2(v: Int): String = if (v < 10) "0" + v else "" + v

private fun weekdayOf(date: String): String {
    val ms = Dates.parseYmd(date) ?: return ""
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    val names = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    return names[cal.get(Calendar.DAY_OF_WEEK) - 1]
}

private fun clockOf(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return pad2(cal.get(Calendar.HOUR_OF_DAY)) + ":" + pad2(cal.get(Calendar.MINUTE))
}

package com.bilu.paibanzhushou.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bilu.paibanzhushou.core.Dates
import java.util.Calendar
import java.util.Locale

/** 滚轮行高（uni picker 的每行高度） */
private val WHEEL_ITEM_H: Dp = Rpx.dp(88)

/** 日期网格每行高度 */
private val DAY_CELL_H: Dp = Rpx.dp(74)

/** 完整日期选择（等价 uni picker mode="date"） */
@Composable
fun DatePickerLauncher(
    show: Boolean,
    initialMs: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    WheelPickerDialog(show, initialMs, monthOnly = false, onDismiss = onDismiss, onPick = onPick)
}

/** 只选年月（等价 uni picker mode="date" fields="month"） */
@Composable
fun MonthPickerLauncher(
    show: Boolean,
    initialMs: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    WheelPickerDialog(show, initialMs, monthOnly = true, onDismiss = onDismiss, onPick = onPick)
}

@Composable
private fun WheelPickerDialog(
    show: Boolean,
    initialMs: Long,
    monthOnly: Boolean,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    if (!show) return
    val start = remember { Calendar.getInstance().apply { timeInMillis = initialMs } }
    var year by remember { mutableIntStateOf(start.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(start.get(Calendar.MONTH) + 1) }
    var day by remember { mutableIntStateOf(start.get(Calendar.DAY_OF_MONTH)) }
    var quick by remember { mutableStateOf(monthOnly) }
    var quickYear by remember { mutableIntStateOf(start.get(Calendar.YEAR)) }

    fun stepMonth(delta: Int) {
        var y = year
        var m = month + delta
        if (m < 1) { m = 12; y -= 1 }
        if (m > 12) { m = 1; y += 1 }
        if (y in 1970..2100) { year = y; month = m }
    }

    fun pickToday() {
        val c = Calendar.getInstance()
        year = c.get(Calendar.YEAR)
        month = c.get(Calendar.MONTH) + 1
        day = c.get(Calendar.DAY_OF_MONTH)
    }

    val dayCount = Dates.daysInMonth(year, month)
    val firstDow = remember(year, month) {
        Calendar.getInstance().apply {
            set(year, month - 1, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.get(Calendar.DAY_OF_WEEK) - 1
    }
    val todayCal = remember { Calendar.getInstance() }
    val todayY = todayCal.get(Calendar.YEAR)
    val todayM = todayCal.get(Calendar.MONTH) + 1
    val todayD = todayCal.get(Calendar.DAY_OF_MONTH)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.White)
                    .navigationBarsPadding()
            ) {
                // 顶部工具栏：取消 / 标题 / 确定
                Row(
                    Modifier.fillMaxWidth().height(Rpx.dp(88)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.weight(1f).fillMaxSize().clickable { onDismiss() },
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
                        if (monthOnly) "选择月份" else "选择日期",
                        fontSize = Rpx.sp(30),
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111111)
                    )
                    Box(
                        Modifier.weight(1f).fillMaxSize().clickable {
                            val ms = if (monthOnly) {
                                Dates.firstOfMonth(year, month)
                            } else {
                                Dates.parseYmd(String.format(Locale.US, "%04d-%02d-%02d", year, month, day))
                                    ?: initialMs
                            }
                            onPick(ms)
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

                // 年月切换条：‹ 2026年1月 › ＋ 今天
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(Rpx.dp(78))
                        .padding(horizontal = Rpx.dp(14)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArrowButton("‹") {
                        if (quick) {
                            if (quickYear > 1970) quickYear -= 1
                        } else {
                            stepMonth(-1)
                        }
                    }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = !monthOnly) {
                            quick = !quick
                            if (quick) quickYear = year
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (quick) quickYear.toString() + "年" else year.toString() + "年 " + month + "月",
                            fontSize = Rpx.sp(32),
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF111111)
                        )
                    }
                    ArrowButton("›") {
                        if (quick) {
                            if (quickYear < 2100) quickYear += 1
                        } else {
                            stepMonth(1)
                        }
                    }
                    if (!monthOnly) {
                        Box(
                            Modifier.height(Rpx.dp(56))
                                .clip(RoundedCornerShape(Rpx.dp(28)))
                                .background(Color(0xFFF2F5FA))
                                .clickable { pickToday() }
                                .padding(horizontal = Rpx.dp(20)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("今天", fontSize = Rpx.sp(26), color = Color(0xFF4A90E2))
                        }
                    }
                }

                if (quick) {
                    // 年 / 月 快速选择
                    Column(Modifier.fillMaxWidth().padding(horizontal = Rpx.dp(24), vertical = Rpx.dp(4))) {
                        for (r in 0 until 4) {
                            Row(Modifier.fillMaxWidth()) {
                                for (c in 0 until 3) {
                                    val m = r * 3 + c + 1
                                    val active = m == month && quickYear == year
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .padding(Rpx.dp(6))
                                            .height(Rpx.dp(76))
                                            .clip(RoundedCornerShape(Rpx.dp(16)))
                                            .background(if (active) Color(0xFFEAF2FE) else Color(0xFFF5F7FA))
                                            .clickable {
                                                year = quickYear
                                                month = m
                                                if (monthOnly) {
                                                    onPick(Dates.firstOfMonth(year, month))
                                                } else {
                                                    quick = false
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            m.toString() + "月",
                                            fontSize = Rpx.sp(30),
                                            color = if (active) Color(0xFF4A90E2) else Color(0xFF333333),
                                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 星期标题
                    Row(Modifier.fillMaxWidth().padding(horizontal = Rpx.dp(24)).height(Rpx.dp(44))) {
                        listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Text(w, fontSize = Rpx.sp(24), color = Color(0xFF9AA3B2))
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = Rpx.dp(24))) {
                        for (row in 0 until ((firstDow + dayCount + 6) / 7)) {
                            Row(Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val d = row * 7 + col - firstDow + 1
                                    if (d in 1..dayCount) {
                                        val selected = d == day
                                        val isToday = year == todayY && month == todayM && d == todayD
                                        Box(
                                            Modifier.weight(1f).height(DAY_CELL_H),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(Rpx.dp(62))
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (selected) Color(0xFF4A90E2) else Color.Transparent
                                                    )
                                                    .then(
                                                        if (isToday && !selected) {
                                                            Modifier.border(Rpx.dp(2), Color(0xFF4A90E2), CircleShape)
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .clickable { day = d },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    d.toString(),
                                                    fontSize = Rpx.sp(30),
                                                    fontWeight = if (selected || isToday) FontWeight.Medium else FontWeight.Normal,
                                                    color = when {
                                                        selected -> Color.White
                                                        isToday -> Color(0xFF4A90E2)
                                                        else -> Color(0xFF111111)
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        Box(Modifier.weight(1f).height(DAY_CELL_H))
                                    }
                                }
                            }
                        }
                    }
                    // 已选结果，点确定前先看一眼
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Rpx.dp(30), vertical = Rpx.dp(8)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("已选", fontSize = Rpx.sp(26), color = Color(0xFF9AA3B2))
                        Text(
                            String.format(Locale.US, "%04d-%02d-%02d", year, month, day),
                            fontSize = Rpx.sp(30),
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF111111),
                            modifier = Modifier.padding(start = Rpx.dp(10))
                        )
                    }
                }
            }
        }
    }
}

/** 日期弹层里的小箭头按钮 */
@Composable
private fun ArrowButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Rpx.dp(64))
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = Rpx.sp(40), color = Color(0xFF333333))
    }
}

/** 只选时刻（小时 + 分钟），用于班次闹铃时间。
 *  用应用内浮层而不是 Dialog：部分机型上 Dialog 里的滚轮一拖动就会被判成
 *  「点到外面」而直接关掉，导致时间根本没法改。 */
@Composable
fun AlarmTimePickerSheet(
    show: Boolean,
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onPick: (Int, Int) -> Unit
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 6 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 6 }
    ) {
        var h by remember { mutableIntStateOf(hour.coerceIn(0, 23)) }
        var m by remember { mutableIntStateOf(minute.coerceIn(0, 59)) }
        val hours = remember { (0..23).toList() }
        val minutes = remember { (0..59).toList() }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x59112034))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
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
                        Modifier.weight(1f).fillMaxSize().clickable { onDismiss() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            "取消",
                            fontSize = Rpx.sp(30),
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(start = Rpx.dp(30))
                        )
                    }
                    Box(
                        Modifier.weight(1f).fillMaxSize().clickable { onPick(h, m) },
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
                Box(Modifier.fillMaxWidth().height(WHEEL_ITEM_H * 5)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                        WheelColumn(
                            values = hours.map { String.format(java.util.Locale.US, "%02d", it) + "时" },
                            selectedIndex = h,
                            onSelected = { h = hours[it] },
                            modifier = Modifier.weight(1f)
                        )
                        WheelColumn(
                            values = minutes.map { String.format(java.util.Locale.US, "%02d", it) + "分" },
                            selectedIndex = m,
                            onSelected = { m = minutes[it] },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(WHEEL_ITEM_H)
                            .background(Color(0x0A000000))
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(Rpx.dp(1))
                            .offset(y = 0.dp - WHEEL_ITEM_H / 2)
                            .background(Color(0xFFE9E9E9))
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(Rpx.dp(1))
                            .offset(y = WHEEL_ITEM_H / 2)
                            .background(Color(0xFFE9E9E9))
                    )
                }
            }
        }
    }
}

/**
 * 单列滚轮：等行高 + 吸附滚动，停稳后回调当前居中项。
 * 用 LazyColumn 的 contentPadding 把首项推到中间来实现「居中选中」。
 */
@Composable
private fun WheelColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (values.size - 1).coerceAtLeast(0))
    )
    val fling = rememberSnapFlingBehavior(state)

    LaunchedEffect(state, values.size) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val idx = state.firstVisibleItemIndex.coerceIn(0, (values.size - 1).coerceAtLeast(0))
                if (idx != selectedIndex) onSelected(idx)
            }
        }
    }
    LaunchedEffect(selectedIndex, values.size) {
        if (!state.isScrollInProgress && state.firstVisibleItemIndex != selectedIndex) {
            state.scrollToItem(selectedIndex.coerceIn(0, (values.size - 1).coerceAtLeast(0)))
        }
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = WHEEL_ITEM_H * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(values) { index, value ->
            Box(
                Modifier.fillMaxWidth().height(WHEEL_ITEM_H),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    fontSize = Rpx.sp(32),
                    color = if (index == selectedIndex) Color(0xFF111111) else Color(0xFF9AA3B2)
                )
            }
        }
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

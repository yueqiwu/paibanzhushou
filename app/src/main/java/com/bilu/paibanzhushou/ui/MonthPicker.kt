package com.bilu.paibanzhushou.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bilu.paibanzhushou.core.Dates
import java.util.Calendar

/** 滚轮行高（uni picker 的每行高度） */
private val WHEEL_ITEM_H: Dp = Rpx.dp(88)

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
    val cal = remember(initialMs) { Calendar.getInstance().apply { timeInMillis = initialMs } }
    var year by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(cal.get(Calendar.MONTH) + 1) }
    var day by remember { mutableIntStateOf(cal.get(Calendar.DAY_OF_MONTH)) }
    val years = remember { (1970..2100).toList() }
    val dayCount = Dates.daysInMonth(year, month)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            // 关键：让弹窗窗口铺满整屏（否则窗口被系统栏内缩，面板底边到不了屏幕底部）
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
                // 顶部工具栏：取消 / 确定（与 uni picker 一致，中间留空）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(Rpx.dp(88)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { onDismiss() },
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
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                val ms = if (monthOnly) {
                                    Dates.firstOfMonth(year, month)
                                } else {
                                    Dates.parseYmd(String.format("%04d-%02d-%02d", year, month, day))
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

                Box(Modifier.fillMaxWidth().height(WHEEL_ITEM_H * 5)) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        WheelColumn(
                            values = years.map { it.toString() + "年" },
                            selectedIndex = years.indexOf(year).coerceAtLeast(0),
                            onSelected = { year = years[it] },
                            modifier = Modifier.weight(1f)
                        )
                        WheelColumn(
                            values = (1..12).map { it.toString() + "月" },
                            selectedIndex = month - 1,
                            onSelected = { month = it + 1 },
                            modifier = Modifier.weight(1f)
                        )
                        if (!monthOnly) {
                            WheelColumn(
                                values = (1..dayCount).map { it.toString() + "日" },
                                selectedIndex = (day - 1).coerceIn(0, dayCount - 1),
                                onSelected = { day = it + 1 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    // 中间选中行：浅灰选中带 + 上下的细分隔线（对齐 uni picker）
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
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color(0xF2FFFFFF), Color(0x00FFFFFF))
                                    )
                                )
                        )
                        Box(Modifier.fillMaxWidth().height(WHEEL_ITEM_H))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color(0x00FFFFFF), Color(0xF2FFFFFF))
                                    )
                                )
                        )
                    }
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

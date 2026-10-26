package com.bilu.paibanzhushou.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bilu.paibanzhushou.core.Dates
import com.bilu.paibanzhushou.core.Lunar
import com.bilu.paibanzhushou.core.SelectedEntry
import com.bilu.paibanzhushou.core.Schedule
import kotlinx.coroutines.launch

private val CAL_THEME = Color(0xFF3C9CFF)
private val WEEK_NAMES = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 日历区块：标题 + 图例 + 月历卡片，1:1 移植 components/schedule-calendar.vue。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarSection(state: IndexState, onOpenMemberTable: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(26))) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("排班日历", fontSize = Rpx.sp(34), fontWeight = FontWeight.SemiBold, color = C.textPrimary)
            PillButton(
                text = "对比同事排班",
                height = Rpx.dp(64),
                radius = Rpx.dp(16),
                fontSize = Rpx.sp(24),
                textColor = C.blue,
                bgColor = Color(0xD9FFFFFF),
                borderColor = Color(0x664A90E2),
                onClick = onOpenMemberTable
            )
        }
        Text(
            text = state.rangeText,
            fontSize = Rpx.sp(24),
            color = Color(0x940B1020),
            modifier = Modifier.padding(top = Rpx.dp(8))
        )
        // 图例
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = Rpx.dp(16)),
            horizontalArrangement = Arrangement.spacedBy(Rpx.dp(12)),
            verticalArrangement = Arrangement.spacedBy(Rpx.dp(12))
        ) {
            state.banOptions.forEach { ban ->
                LegendDotItem(name = ban, color = hexColor(state.banColor(ban)))
            }
            LegendTagItem(tag = "假", tagColor = Color(0xFFFF0000), name = "节假日")
            LegendTagItem(tag = "班", tagColor = Color(0xFFFF9800), name = "调班")
        }
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(16)),
        radius = Rpx.dp(28),
        bgAlpha = 0.66f,
        contentPadding = PaddingValues(Rpx.dp(12))
    ) {
        CalendarGrid(state)
    }
}

/**
 * 图例胶囊：色点款与标签款共用同一容器与行高，保证「白班/中班…」与「假 节假日 / 班 调班」垂直对齐。
 * 源样式：padding 6rpx 16rpx、圆角 999rpx、rgba(255,255,255,.7) 底 + 1rpx rgba(0,0,0,.04) 边。
 */
@Composable
private fun LegendPill(content: @Composable RowScope.() -> Unit) {
    Row(
        // 内容行高 34rpx + 上下 padding 6rpx，两款胶囊高度完全一致
        Modifier
            .height(Rpx.dp(46))
            .background(Color(0xB3FFFFFF), RoundedCornerShape(999.dp))
            .border(Rpx.dp(1), Color(0x0A000000), RoundedCornerShape(999.dp))
            .padding(horizontal = Rpx.dp(16)),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun LegendDotItem(name: String, color: Color) {
    LegendPill {
        Box(
            Modifier
                .size(Rpx.dp(16))
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(Rpx.dp(8)))
        LegendText(name)
    }
}

@Composable
private fun LegendTagItem(tag: String, tagColor: Color, name: String) {
    LegendPill {
        Box(
            Modifier
                .height(Rpx.dp(30))
                .background(tagColor, RoundedCornerShape(Rpx.dp(8)))
                .padding(horizontal = Rpx.dp(10)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tag,
                fontSize = Rpx.sp(20),
                lineHeight = Rpx.sp(20),
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.width(Rpx.dp(8)))
        LegendText(name)
    }
}

@Composable
private fun LegendText(name: String) {
    Text(
        text = name,
        fontSize = Rpx.sp(24),
        // 显式行高，避免 Material 默认行高（24sp）撑高胶囊导致「假 / 班」胶囊与色点胶囊不齐
        lineHeight = Rpx.sp(34),
        color = Color(0xB80B1020)
    )
}

/**
 * 月历本体，对应 uni_modules/wu-calendar。
 */
/** 日历一行高度（源 wu-calendar itemHeight = 70px，1:1 用 dp） */
private val CAL_ROW_HEIGHT = 70.dp

/** 虚拟无限 Pager：中间页 + 总页数（页索引保持在数千量级，避免超长偏移的精度问题） */
private const val PAGER_MIDDLE = 6000
private const val PAGER_COUNT = 12001

@Composable
private fun CalendarGrid(state: IndexState) {
    var showMonthPicker by remember { mutableStateOf(false) }
    val monthFirst = state.calendarMonthMs
    val targetHeight = if (state.foldOpen) CAL_ROW_HEIGHT * 6 else CAL_ROW_HEIGHT
    val containerHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(200, easing = LinearEasing),
        label = "calHeight"
    )

    Column(Modifier.fillMaxWidth()) {
        // 展开 / 折叠会把「一页」的含义从月换成周：用 key 重建 pager，基准页直接落在当前视野上
        key(state.foldOpen) {
            CalendarPager(
                state = state,
                monthFirst = monthFirst,
                containerHeight = containerHeight,
                onOpenMonthPicker = { showMonthPicker = true }
            )
        }
        // 折叠
        Box(
            Modifier
                .fillMaxWidth()
                .height(Rpx.dp(40))
                .clickable { state.toggleFold() },
            contentAlignment = Alignment.Center
        ) {
            Chevron(
                dir = if (state.foldOpen) ChevronDir.UP else ChevronDir.DOWN,
                color = Color(0xFF333333),
                size = Rpx.dp(18)
            )
        }
    }

    MonthPickerLauncher(
        show = showMonthPicker,
        initialMs = monthFirst,
        onDismiss = { showMonthPicker = false },
        onPick = { ms ->
            showMonthPicker = false
            state.jumpToMonth(Dates.year(ms), Dates.month(ms))
        }
    )
}

/**
 * 日历头 + 网格。
 *
 * 源版 wu-calendar 用 swiper「三页 + 落位后瞬回中间页」，每次落位都要重算三页 42 格，
 * 连续快滑时会明显掉帧、快滑也吃不到惯性。这里换成虚拟无限 Pager（页索引 <-> 月 / 周偏移）：
 * 页面内容只由页索引决定，落位后仅把「正在浏览的月 / 周」写回 state（标题、数据窗口用），
 * 不再有瞬回与整页重算，连续滑动也就顺了。
 */
@Composable
private fun CalendarPager(
    state: IndexState,
    monthFirst: Long,
    containerHeight: Dp,
    onOpenMonthPicker: () -> Unit
) {
    // true = 整月，false = 单周（沿开源版 foldOpen 语义）
    val expandMonth = state.foldOpen
    // 基准周期：进入当前模式时正在浏览的月 / 周，pager 的中间页就是它
    val baseMonth = remember { Dates.firstOfMonth(monthFirst) }
    val baseWeekStart = remember {
        val anchor = state.calendarWeekMs
        Dates.addDays(anchor, -Dates.dayOfWeek(anchor))
    }

    fun monthOfPage(page: Int): Long = Dates.addMonths(baseMonth, page - PAGER_MIDDLE)
    fun weekStartOfPage(page: Int): Long = Dates.addDays(baseWeekStart, (page - PAGER_MIDDLE) * 7)
    fun pageOfMonth(ms: Long): Int = PAGER_MIDDLE + Dates.monthsBetween(baseMonth, Dates.firstOfMonth(ms))
    fun pageOfWeek(weekStart: Long): Int =
        PAGER_MIDDLE + (Dates.diffDays(baseWeekStart, Dates.addDays(weekStart, -Dates.dayOfWeek(weekStart))) / 7).toInt()

    val pagerState = rememberPagerState(initialPage = PAGER_MIDDLE) { PAGER_COUNT }
    val scope = rememberCoroutineScope()

    // 视野 -> pager：点「今天」、选月份、点日期格改的都是 state，这里把 pager 移过去
    val browsePeriodMs = if (expandMonth) {
        Dates.firstOfMonth(state.calendarMonthMs)
    } else {
        Dates.addDays(state.calendarWeekMs, -Dates.dayOfWeek(state.calendarWeekMs))
    }
    LaunchedEffect(browsePeriodMs, expandMonth) {
        val target = if (expandMonth) pageOfMonth(browsePeriodMs) else pageOfWeek(browsePeriodMs)
        if (target != pagerState.settledPage) pagerState.scrollToPage(target)
    }
    // pager -> 视野：落位后写回 state（绝不碰选中日）
    LaunchedEffect(pagerState.settledPage, expandMonth) {
        val page = pagerState.settledPage
        if (expandMonth) state.browseToMonth(monthOfPage(page)) else state.browseToWeek(weekStartOfPage(page))
    }

    val titleYear = Dates.year(monthFirst)
    val titleMonth = Dates.month(monthFirst)
    val todayStr = state.todayStr
    val selectedMs = Dates.startOfDay(state.calendarDateMs)
    val selectedStr = Dates.formatYmd(selectedMs)

    Column(Modifier.fillMaxWidth()) {
        // 头部
        Box(Modifier.fillMaxWidth().height(Rpx.dp(100))) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.width(Rpx.dp(100)).height(Rpx.dp(100)).clickable {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1, animationSpec = tween(300))
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Chevron(dir = ChevronDir.LEFT, color = Color(0xFF555555), size = Rpx.dp(18))
                }
                Box(
                    Modifier
                        .width(Rpx.dp(200))
                        .height(Rpx.dp(100))
                        .clickable { onOpenMonthPicker() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = titleYear.toString() + "年" + titleMonth.toString() + "月",
                        fontSize = Rpx.sp(32),
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center
                    )
                }
                Box(
                    Modifier.width(Rpx.dp(100)).height(Rpx.dp(100)).clickable {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = tween(300))
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Chevron(dir = ChevronDir.RIGHT, color = Color(0xFF555555), size = Rpx.dp(18))
                }
            }
            // 今天
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .height(Rpx.dp(50))
                    .background(
                        Color(0xFFF1F1F1),
                        RoundedCornerShape(topStart = Rpx.dp(50), bottomStart = Rpx.dp(50))
                    )
                    .clickable { state.backToToday() }
                    .padding(start = Rpx.dp(20), end = Rpx.dp(10)),
                contentAlignment = Alignment.Center
            ) {
                Text("今天", fontSize = Rpx.sp(24), color = Color(0xFF333333))
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(Rpx.dp(2))
                    .background(Color(0xFFEDEDED))
            )
        }

        // 星期行
        Row(Modifier.fillMaxWidth().padding(horizontal = Rpx.dp(8))) {
            WEEK_NAMES.forEach { name ->
                Box(
                    Modifier.weight(1f).height(Rpx.dp(90)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name, fontSize = Rpx.sp(28), color = Color(0xFF333333))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(Rpx.dp(2)).background(Color(0xFFF5F5F5)))

        // 日期网格
        Box(
            Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .clipToBounds()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                // 与源版 swiper 一致：一次滑动只翻一个月/一周（快滑也不会连翻好几个月）
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1)
                )
            ) { page ->
                val pageWeekStart = if (expandMonth) 0L else weekStartOfPage(page)
                val pageMonth = if (expandMonth) monthOfPage(page) else Dates.firstOfMonth(pageWeekStart)
                val pageData = if (expandMonth) monthGridPage(pageMonth) else weekGridPage(pageWeekStart)
                // 数据窗口按本页所在月取：只算本页 ±2 个月，滑到哪一年都能算，
                // 用 scheduleVersion 做 key，配置一变就整页重算
                val entries = remember(pageMonth, state.scheduleVersion) {
                    state.windowedSelected(pageMonth)
                }
                // 高亮只画在「选中日归属」的那一页：相邻月网格里恰好带上选中日时不出现幽灵选中
                val selectedOnPage = if (expandMonth) {
                    pageMonth == Dates.firstOfMonth(selectedMs)
                } else {
                    Dates.diffDays(pageWeekStart, selectedMs) in 0L..6L
                }
                Box(Modifier.fillMaxSize()) {
                    if (expandMonth) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pageData.monthNumber.toString(),
                                fontSize = Rpx.sp(400),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF999999).copy(alpha = 0.1f)
                            )
                        }
                    }
                    Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                        pageData.rows.forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = Rpx.dp(8))) {
                                row.forEach { cell ->
                                    CalendarCell(
                                        day = cell.day,
                                        dateStr = cell.dateStr,
                                        lunarText = cell.lunar,
                                        isToday = cell.dateStr == todayStr,
                                        selected = selectedOnPage && cell.dateStr == selectedStr,
                                        entry = entries[cell.dateStr],
                                        modifier = Modifier.weight(1f).height(CAL_ROW_HEIGHT),
                                        onClick = { state.onCellTap(cell.dateStr) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单格的静态数据（与选中/班次无关），一页只算一次并全局缓存 */
private class CellBase(
    val day: Int,
    val dateStr: String,
    val lunar: String,
    val timeMs: Long
)

/** 日历单页数据，对应源 wu-calendar-block 的一份 weeks 数据 */
private class CalendarPage(
    val monthNumber: Int,
    val rows: List<List<CellBase>>
)

/**
 * 格子静态数据的进程内缓存：滚动时同一月份会被反复组合，
 * 日期/农历只算一次，回头再滑也不用重算。
 */
private val CELL_CACHE = object : android.util.LruCache<String, CalendarPage>(48) {}

/** 整月 42 格（周日开头），对应源 wu-calendar 的 weeks */
private fun monthGridPage(monthMs: Long): CalendarPage {
    val first = Dates.firstOfMonth(monthMs)
    val key = "m" + Dates.formatYmd(first)
    CELL_CACHE.get(key)?.let { return it }
    val gridStart = Dates.addDays(first, -Dates.dayOfWeek(first))
    val page = CalendarPage(
        monthNumber = Dates.month(first),
        rows = (0 until 6).map { row ->
            (0 until 7).map { col -> cellBase(Dates.addDays(gridStart, row * 7 + col)) }
        }
    )
    CELL_CACHE.put(key, page)
    return page
}

/** 折叠态单周，对应源 wu-calendar 单周视图 */
private fun weekGridPage(weekStart: Long): CalendarPage {
    val key = "w" + Dates.formatYmd(weekStart)
    CELL_CACHE.get(key)?.let { return it }
    val page = CalendarPage(
        monthNumber = Dates.month(Dates.firstOfMonth(weekStart)),
        rows = listOf((0 until 7).map { cellBase(Dates.addDays(weekStart, it)) })
    )
    CELL_CACHE.put(key, page)
    return page
}

private fun cellBase(ms: Long): CellBase {
    val y = Dates.year(ms)
    val m = Dates.month(ms)
    val d = Dates.day(ms)
    return CellBase(
        day = d,
        dateStr = Dates.formatYmd(ms),
        lunar = Lunar.labelFor(y, m, d),
        timeMs = ms
    )
}

@Composable
private fun CalendarCell(
    day: Int,
    dateStr: String,
    lunarText: String,
    isToday: Boolean,
    selected: Boolean,
    entry: SelectedEntry?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // 源样式：外框高 itemHeight(70px)，内框高 itemHeight - defaultMargin(8px)，上下各留 4px
    val boxHeight = 70.dp - Rpx.dp(8)
    val blockShape = RoundedCornerShape(Rpx.dp(12))
    val tagShape = RoundedCornerShape(Rpx.dp(8))

    Box(modifier.clickable { onClick() }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(boxHeight)
                .background(if (selected) CAL_THEME else Color.Transparent, blockShape),
            contentAlignment = Alignment.Center
        ) {
            // 日期数字（唯一参与流式布局的元素，垂直居中）
            Text(
                text = day.toString(),
                fontSize = Rpx.sp(28),
                lineHeight = Rpx.sp(32),
                color = if (selected) Color.White else if (isToday) CAL_THEME else Color(0xFF333333)
            )
            // 农历文字：绝对右 8rpx、垂直居中、宽度 16rpx、字号 24rpx、颜色 #333
            // 注意颜色：App.vue 里的 .mine-calendar-lunar-text 虽然写了 #f1f1f1，但组件内
            // scoped 规则（.wu-calendar-item__weeks-lunar-text{color:#333}，带 [data-v] 属性
            // 选择器，特异性更高）胜出，真机取色确认为 #333333，所以这里用 #333。
            // 源版用 transform: scale(.8)。这里直接把字号乘 0.8，省掉每格一个 graphicsLayer
            // （一页 42 格 → 42 个渲染层，滑动时是实打实的开销；字形差异肉眼不可见）
            Text(
                // 源的 dayText()：isDay 时返回 todayText（i18n「今日」），否则才是节日/节气/农历
                text = if (isToday) "今日" else lunarText,
                fontSize = Rpx.sp(19.2),
                lineHeight = Rpx.sp(20.8),
                color = if (selected) Color.White else Color(0xFF333333),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -Rpx.dp(8))
                    .width(Rpx.dp(16))
            )
            val top = entry?.topInfo.orEmpty()
            if (top.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = Rpx.dp(4))
                        .background(if (selected) CAL_THEME else hexColor(entry?.topInfoColor), tagShape)
                        .padding(horizontal = Rpx.dp(4), vertical = Rpx.dp(4)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = top,
                        fontSize = Rpx.sp(20),
                        lineHeight = Rpx.sp(20),
                        color = Color.White
                    )
                }
            }
            val info = entry?.info.orEmpty()
            if (info.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -Rpx.dp(4))
                        .background(if (selected) CAL_THEME else hexColor(entry?.infoColor), tagShape)
                        .padding(horizontal = Rpx.dp(4), vertical = Rpx.dp(4)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = info,
                        fontSize = Rpx.sp(20),
                        lineHeight = Rpx.sp(20),
                        color = Color.White
                    )
                }
            }
        }
    }
}

private enum class ChevronDir { LEFT, RIGHT, UP, DOWN }

/** 折线箭头 */
@Composable
private fun Chevron(dir: ChevronDir, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            when (dir) {
                ChevronDir.LEFT -> {
                    moveTo(w * 0.68f, 0f)
                    lineTo(w * 0.32f, h / 2f)
                    lineTo(w * 0.68f, h)
                }
                ChevronDir.RIGHT -> {
                    moveTo(w * 0.32f, 0f)
                    lineTo(w * 0.68f, h / 2f)
                    lineTo(w * 0.32f, h)
                }
                ChevronDir.UP -> {
                    moveTo(0f, h * 0.66f)
                    lineTo(w / 2f, h * 0.32f)
                    lineTo(w, h * 0.66f)
                }
                ChevronDir.DOWN -> {
                    moveTo(0f, h * 0.34f)
                    lineTo(w / 2f, h * 0.68f)
                    lineTo(w, h * 0.34f)
                }
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = w * 0.14f, cap = StrokeCap.Round)
        )
    }
}

/* ---------------- 班次选择底部弹层 ---------------- */

@Composable
fun BanPickerSheet(state: IndexState) {
    val picker = state.picker
    AnimatedVisibility(
        visible = picker.visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 6 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 6 }
    ) {
        val screenH = LocalConfiguration.current.screenHeightDp.dp
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x59112034))
                    .clickable { state.closePicker() }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = screenH * 0.78f)
                    .background(
                        Color.White,
                        RoundedCornerShape(topStart = Rpx.dp(28), topEnd = Rpx.dp(28))
                    )
                    .padding(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(24), bottom = Rpx.dp(40))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Rpx.dp(16)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = picker.title,
                        fontSize = Rpx.sp(30),
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111111)
                    )
                    Text(
                        text = "✕",
                        fontSize = Rpx.sp(28),
                        color = C.textSub,
                        modifier = Modifier
                            .clickable { state.closePicker() }
                            .padding(horizontal = Rpx.dp(8))
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenH * 0.52f)
                        .verticalScroll(rememberScrollState())
                ) {
                    state.banOptions.forEach { ban ->
                        val active = ban == picker.current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = Rpx.dp(12))
                                .clip(RoundedCornerShape(Rpx.dp(18)))
                                .then(
                                    if (active) Modifier.background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(Color(0x2E6AC695), Color(0x294A90E2))
                                        )
                                    ) else Modifier.background(C.fieldBg)
                                )
                                .border(
                                    Rpx.dp(1),
                                    if (active) Color(0x664A90E2) else Color(0x0A000000),
                                    RoundedCornerShape(Rpx.dp(18))
                                )
                                .clickable { state.onPickerSelect(ban) }
                                .padding(horizontal = Rpx.dp(20), vertical = Rpx.dp(24)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(Rpx.dp(18))
                                    .background(hexColor(state.banColor(ban)), CircleShape)
                            )
                            Text(
                                text = ban,
                                fontSize = Rpx.sp(28),
                                color = Color(0xFF333333),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = Rpx.dp(16))
                            )
                            if (active) {
                                Text(
                                    text = "✓",
                                    fontSize = Rpx.sp(28),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2B6CB0)
                                )
                            }
                        }
                    }
                }
                if (picker.allowClear) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Rpx.dp(12))
                            .background(Color(0x14E53935), RoundedCornerShape(Rpx.dp(18)))
                            .clickable { state.onPickerClear() }
                            .padding(vertical = Rpx.dp(24)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("清除单日设置", fontSize = Rpx.sp(28), color = Color(0xFFE53935))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Rpx.dp(12))
                        .background(Color(0x0A000000), RoundedCornerShape(Rpx.dp(18)))
                        .clickable { state.closePicker() }
                        .padding(vertical = Rpx.dp(24)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", fontSize = Rpx.sp(28), color = Color(0xFF5B6B7F))
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

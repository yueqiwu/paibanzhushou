package com.bilu.paibanzhushou.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilu.paibanzhushou.core.Dates
import com.bilu.paibanzhushou.core.Schedule
import com.bilu.paibanzhushou.core.SelectedEntry
import com.bilu.paibanzhushou.data.HolidayRepo
import com.bilu.paibanzhushou.data.PeopleStore
import com.bilu.paibanzhushou.data.Person
import com.bilu.paibanzhushou.data.Store
import kotlin.math.roundToInt

/** 上次查看的月份（与 uniapp 版本同一个键） */
private const val MEMBER_MONTH_KEY = "paiban_member_table_month_v1"

private val MONTH_KEY_REGEX = Regex("^\\d{4}-\\d{2}$")

/** 当月的一天（对应 member_table.vue 的 daysInMonth 元素） */
private data class MemberDay(
    val date: String,
    val weekday: String,
    val topInfo: String,
    val topInfoColor: String,
    val isToday: Boolean
)

/**
 * 同事排班对比页状态，1:1 移植 pages/index/member_table.vue。
 * 所有字段与页面 data 一一对应。
 */
private class MemberTableState {
    var people by mutableStateOf<List<Person>>(emptyList())
    var holiday by mutableStateOf<List<String>>(emptyList())
    var banList by mutableStateOf<List<String>>(emptyList())
    var currentMonth by mutableStateOf("")
    var daysInMonth by mutableStateOf<List<MemberDay>>(emptyList())
    var personShifts by mutableStateOf<Map<String, Map<String, SelectedEntry>>>(emptyMap())

    val currentMonthText: String
        get() {
            if (currentMonth.length < 7) return ""
            return currentMonth.substring(0, 4) + "年" + currentMonth.substring(5, 7) + "月"
        }

    val currentMonthStartMs: Long
        get() {
            if (!MONTH_KEY_REGEX.matches(currentMonth)) return Dates.todayStart()
            val y = currentMonth.substring(0, 4).toIntOrNull() ?: return Dates.todayStart()
            val m = currentMonth.substring(5, 7).toIntOrNull() ?: return Dates.todayStart()
            if (m < 1 || m > 12) return Dates.todayStart()
            return Dates.firstOfMonth(y, m)
        }

    fun init() {
        loadPeople()
        loadHolidayAndBanList()
        initCurrentMonth()
        generateMonthData()
    }

    fun loadPeople() {
        // 与首页同一份人员存储：周期、锚点、单日覆盖、自动休息都要带上
        people = PeopleStore.loadPeople(
            Dates.formatYmd(Dates.todayStart()),
            listOf("白班", "中班", "休息", "休息")
        )
    }

    fun loadHolidayAndBanList() {
        val cache = HolidayRepo.loadCache()
        holiday = cache.holiday
        banList = cache.banList
    }

    fun initCurrentMonth() {
        val saved = Store.readRaw(MEMBER_MONTH_KEY, "")
        if (MONTH_KEY_REGEX.matches(saved)) {
            currentMonth = saved
            return
        }
        currentMonth = Dates.formatYmd(Dates.todayStart()).substring(0, 7)
    }

    fun generateMonthData() {
        if (currentMonth.length < 7) return
        Store.writeRaw(MEMBER_MONTH_KEY, currentMonth)
        val y = currentMonth.substring(0, 4).toIntOrNull() ?: return
        val m = currentMonth.substring(5, 7).toIntOrNull() ?: return
        if (m < 1 || m > 12) return
        val startMs = Dates.firstOfMonth(y, m)
        val endMs = Dates.monthEndMs(startMs)

        val holidaySet = holiday.toHashSet()
        val banSet = banList.toHashSet()
        val todayStr = Dates.formatYmd(Dates.todayStart())

        val days = ArrayList<MemberDay>()
        var t = startMs
        while (t <= endMs) {
            val dateStr = Dates.formatYmd(t)
            var topInfo = ""
            var topInfoColor = ""
            if (holidaySet.contains(dateStr)) {
                topInfo = "假"
                topInfoColor = "#f00"
            } else if (banSet.contains(dateStr)) {
                topInfo = "班"
                topInfoColor = "#ff9800"
            }
            days.add(
                MemberDay(
                    date = dateStr,
                    weekday = Dates.weekdayCn(t),
                    topInfo = topInfo,
                    topInfoColor = topInfoColor,
                    isToday = dateStr == todayStr
                )
            )
            t = Dates.addDays(t, 1)
        }
        daysInMonth = days
        generatePersonShifts()
    }

    fun generatePersonShifts() {
        if (daysInMonth.isEmpty()) {
            personShifts = emptyMap()
            return
        }
        val startMs = Dates.parseYmd(daysInMonth.first().date)
        val endMs = Dates.parseYmd(daysInMonth.last().date)
        val out = LinkedHashMap<String, Map<String, SelectedEntry>>()
        people.forEach { person ->
            out[person.id] = if (startMs == null || endMs == null) {
                emptyMap()
            } else {
                val selected = Schedule.buildSchedule(
                    cycleBans = person.cycleBans,
                    anchorDate = person.anchorDate,
                    startMs = startMs,
                    endMs = endMs,
                    holiday = holiday,
                    banList = banList,
                    overrides = person.overrides,
                    weekendRestBans = person.weekendRestBans,
                    holidayRestBans = person.holidayRestBans,
                    fallbackAnchorMs = startMs
                )
                Schedule.toMap(selected)
            }
        }
        personShifts = out
    }

    fun shiftOf(personId: String, date: String): SelectedEntry? = personShifts[personId]?.get(date)

    fun shiftColor(personId: String, date: String): String {
        val c = shiftOf(personId, date)?.infoColor ?: ""
        return if (c.isEmpty()) "#f0f0f0" else c
    }

    fun shiftText(personId: String, date: String): String = shiftOf(personId, date)?.info ?: ""

    fun prevMonth() {
        if (currentMonth.length < 7) return
        var year = currentMonth.substring(0, 4).toIntOrNull() ?: return
        var month = currentMonth.substring(5, 7).toIntOrNull() ?: return
        month -= 1
        if (month < 1) {
            month = 12
            year -= 1
        }
        currentMonth = year.toString() + "-" + Dates.pad2(month)
        generateMonthData()
    }

    fun nextMonth() {
        if (currentMonth.length < 7) return
        var year = currentMonth.substring(0, 4).toIntOrNull() ?: return
        var month = currentMonth.substring(5, 7).toIntOrNull() ?: return
        month += 1
        if (month > 12) {
            month = 1
            year += 1
        }
        currentMonth = year.toString() + "-" + Dates.pad2(month)
        generateMonthData()
    }

    /** picker mode=date fields=month 的回调 */
    fun onMonthChange(ms: Long) {
        currentMonth = Dates.formatYmd(ms).substring(0, 7)
        generateMonthData()
    }

    fun banColor(ban: String): String = com.bilu.paibanzhushou.core.BanColors.get(ban)
}

/**
 * 同事排班对比页（1:1 还原 pages/index/member_table.vue）。
 */
@Composable
fun MemberTableScreen(onBack: () -> Unit) {
    val state = remember { MemberTableState().apply { init() } }
    val listState = rememberLazyListState()

    val todayIndex = state.daysInMonth.indexOfFirst { it.isToday }
    LaunchedEffect(state.currentMonth, state.daysInMonth) {
        if (state.daysInMonth.isNotEmpty()) {
            val target = if (todayIndex >= 0) todayIndex else 0
            // 首帧行高尚未确定，滚动位置会有偏差：测量完成后再对齐一次
            listState.scrollToItem(target)
            withFrameNanos { }
            listState.scrollToItem(target)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.pageBg)
    ) {
        MemberTopBar(onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(Rpx.dp(24))
        ) {
            MemberHeaderCard(state)
            Spacer(Modifier.height(Rpx.dp(24)))
            MemberTableCard(
                state = state,
                listState = listState,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 原生导航栏（uni 默认导航栏：44dp 高、#f3fffb 底、黑色居中标题、左侧返回） */
@Composable
private fun MemberTopBar(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.pageBg)
            .statusBarsPadding()
            .height(Rpx.dp(88)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(Rpx.dp(88))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(Rpx.dp(40))) {
                val w = size.width
                val path = Path().apply {
                    moveTo(w * 0.64f, w * 0.2f)
                    lineTo(w * 0.34f, w * 0.5f)
                    lineTo(w * 0.64f, w * 0.8f)
                }
                drawPath(
                    path = path,
                    color = Color(0xFF333333),
                    style = Stroke(
                        width = w * 0.1f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        Text(
            text = "同事排班表",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = Rpx.sp(34),
            color = Color(0xFF000000),
            maxLines = 1
        )
        Spacer(Modifier.size(Rpx.dp(88)))
    }
}

/** 头部卡片：标题 + 月份切换 */
@Composable
private fun MemberHeaderCard(state: MemberTableState) {
    var pickerOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(
                radius = Rpx.dp(20),
                bgAlpha = 0.78f,
                borderAlpha = 0.8f,
                elevation = Rpx.dp(18)
            )
            .padding(Rpx.dp(24))
    ) {
        Text(
            text = "同事排班对比",
            fontSize = Rpx.sp(36),
            fontWeight = FontWeight.SemiBold,
            color = C.textPrimary
        )
        Spacer(Modifier.height(Rpx.dp(20)))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton(
                text = "上个月",
                height = Rpx.dp(64),
                radius = Rpx.dp(8),
                fontSize = Rpx.sp(24),
                textColor = Color(0xFF111111),
                bgColor = Color(0x0F000000)
            ) { state.prevMonth() }
            Spacer(Modifier.width(Rpx.dp(20)))
            Text(
                text = state.currentMonthText,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = Rpx.sp(28),
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333),
                maxLines = 1
            )
            Spacer(Modifier.width(Rpx.dp(20)))
            PillButton(
                text = "下个月",
                height = Rpx.dp(64),
                radius = Rpx.dp(8),
                fontSize = Rpx.sp(24),
                textColor = Color(0xFF111111),
                bgColor = Color(0x0F000000)
            ) { state.nextMonth() }
            Spacer(Modifier.width(Rpx.dp(20)))
            MemberPrimaryButton(text = "选择月份") { pickerOpen = true }
        }
    }
    MonthPickerLauncher(
        show = pickerOpen,
        initialMs = state.currentMonthStartMs,
        onDismiss = { pickerOpen = false },
        onPick = { ms ->
            pickerOpen = false
            state.onMonthChange(ms)
        }
    )
}

/** 表格卡片：表头吸顶 + 日期列吸左 + 双向滚动 */
@Composable
private fun MemberTableCard(state: MemberTableState, listState: LazyListState, modifier: Modifier = Modifier) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(modifier)
            .glassCard(
                radius = Rpx.dp(20),
                bgAlpha = 0.78f,
                borderAlpha = 0.8f,
                elevation = Rpx.dp(18)
            )
            .padding(Rpx.dp(24))
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentWidth = maxWidth
            val dateWidth = Rpx.dp(190)
            val memberMinWidth = Rpx.dp(120)
            val count = state.people.size
            // 源版 .header-cell { min-width:120rpx; white-space:nowrap } + table 自动布局：
            // 列宽取「最小宽 / 姓名实测宽 / 均分」三者最大，超出视口即横向滚动
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val namesNeed: Dp = if (count <= 0) 0.dp else {
                val widestPx = state.people.maxOfOrNull { person ->
                    textMeasurer.measure(
                        text = AnnotatedString(person.name),
                        style = TextStyle(
                            fontSize = Rpx.sp(26),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp
                        ),
                        maxLines = 1
                    ).size.width
                } ?: 0
                // 32rpx = 左右各 16rpx 内边距；再留 8rpx 富余，避免字距误差导致姓名被省略号截断
                with(density) { widestPx.toDp() } + Rpx.dp(40)
            }
            val memberWidth = if (count <= 0) {
                Rpx.dp(120)
            } else {
                maxOf(memberMinWidth, namesNeed, (contentWidth - dateWidth) / count.toFloat())
            }
            // 横向滚动：表头与每一行共用同一个 ScrollState（源版是一个横向 scroll-view，
            // 表头与内容一起横移），这样 5 个人员都能滑到，且不会因为「Row 被父级最大宽度
            // 截断」导致姓名被压成省略号
            val hScroll = rememberScrollState()

            Column(Modifier.fillMaxSize()) {
                TableHeaderRow(state, dateWidth, memberWidth, count, hScroll)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(state.daysInMonth) { index, day ->
                        TableDataRow(
                            state = state,
                            day = day,
                            striped = index % 2 == 0,
                            dateWidth = dateWidth,
                            memberWidth = memberWidth,
                            hScroll = hScroll
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeaderRow(
    state: MemberTableState,
    dateWidth: Dp,
    memberWidth: Dp,
    count: Int,
    hScroll: ScrollState
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.95f))
            .bottomBorder(color = Color(0x1A000000), thickness = Rpx.dp(2))
    ) {
        Box(
            Modifier
                .width(dateWidth)
                .background(Color.White.copy(alpha = 0.9f))
                .rightBorder()
                .padding(horizontal = Rpx.dp(16), vertical = Rpx.dp(20)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "日期",
                fontSize = Rpx.sp(26),
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111),
                maxLines = 1
            )
        }
        Box(
            Modifier
                .weight(1f)
                .clipToBounds()
        ) {
            // horizontalScroll 会给子项「无限宽」约束，列宽才能真正取到 memberWidth
            Row(Modifier.horizontalScroll(hScroll)) {
                state.people.forEach { person ->
                    Box(
                        Modifier
                            .width(memberWidth)
                            .rightBorder()
                            .padding(horizontal = Rpx.dp(16), vertical = Rpx.dp(20)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = person.name,
                            fontSize = Rpx.sp(26),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                            color = Color(0xFF111111),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableDataRow(
    state: MemberTableState,
    day: MemberDay,
    striped: Boolean,
    dateWidth: Dp,
    memberWidth: Dp,
    hScroll: ScrollState
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (striped) Color(0x05000000) else Color.Transparent)
            .bottomBorder(color = Color(0x14000000))
    ) {
        MemberDateCell(day = day, width = dateWidth)
        Box(
            Modifier
                .weight(1f)
                .clipToBounds()
        ) {
            Row(Modifier.horizontalScroll(hScroll)) {
                state.people.forEach { person ->
                    MemberShiftCell(
                        text = state.shiftText(person.id, day.date),
                        colorHex = state.shiftColor(person.id, day.date),
                        width = memberWidth
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberDateCell(day: MemberDay, width: Dp) {
    val shadowPx = with(LocalDensity.current) { 2.dp.toPx() }
    Column(
        Modifier
            .width(width)
            .background(Color.White.copy(alpha = 0.9f))
            .rightBorder()
            .padding(horizontal = Rpx.dp(16), vertical = Rpx.dp(20))
    ) {
        Text(
            text = day.date,
            fontSize = Rpx.sp(28),
            fontWeight = if (day.isToday) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (day.isToday) Color(0xFFFF3B30) else Color(0xFF111111),
            maxLines = 1,
            softWrap = false,
            style = if (day.isToday) {
                TextStyle(shadow = Shadow(color = Color(0x4DFF3B30), offset = Offset(0f, shadowPx), blurRadius = shadowPx * 2f))
            } else {
                TextStyle.Default
            }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = day.weekday,
                fontSize = Rpx.sp(22),
                color = Color(0xFF888888),
                maxLines = 1
            )
            if (day.topInfo.isNotEmpty()) {
                Spacer(Modifier.width(Rpx.dp(10)))
                Text(
                    text = day.topInfo,
                    fontSize = Rpx.sp(20),
                    fontWeight = FontWeight.Bold,
                    color = hexColor(day.topInfoColor),
                    maxLines = 1
                )
            }
            if (day.isToday) {
                Spacer(Modifier.width(Rpx.dp(10)))
                Text(
                    text = "今",
                    fontSize = Rpx.sp(20),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF0000),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MemberShiftCell(text: String, colorHex: String, width: Dp) {
    Box(
        Modifier
            .width(width)
            .rightBorder()
            .padding(horizontal = Rpx.dp(16), vertical = Rpx.dp(20)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Rpx.dp(60))
                .background(hexColor(colorHex), RoundedCornerShape(Rpx.dp(8)))
                .padding(horizontal = Rpx.dp(4), vertical = Rpx.dp(8)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = Rpx.sp(24),
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 渐变 + 流光主按钮（member_table.vue 的 .btn--primary） */
@Composable
private fun MemberPrimaryButton(text: String, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "memberPrimary")
    val gradientPosition by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientPosition"
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val shape = RoundedCornerShape(Rpx.dp(8))
    Box(
        Modifier
            .height(Rpx.dp(64))
            .clip(shape)
            .drawBehind {
                val w = size.width
                val h = size.height
                // background-size: 200% 200% + background-position 0% → 100% 50%
                val shift = gradientPosition * w
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF6AC695), Color(0xFF4A90E2), Color(0xFF9C27B0)),
                        start = Offset(-shift, -0.5f * h),
                        end = Offset(-shift + 2f * w, 1.5f * h)
                    ),
                    size = Size(w, h)
                )
                // ::after 的斜向流光
                val bandWidth = w * 0.4f
                val left = w * (-0.4f + 1.76f * shimmer)
                val alpha = if (shimmer < 0.5f) {
                    0.2f + 0.35f * (shimmer / 0.5f)
                } else {
                    0.55f - 0.35f * ((shimmer - 0.5f) / 0.5f)
                }
                rotate(degrees = -16f, pivot = Offset(w / 2f, h / 2f)) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.White, Color.Transparent),
                            startX = left,
                            endX = left + bandWidth
                        ),
                        topLeft = Offset(left, -h),
                        size = Size(bandWidth, h * 3f),
                        alpha = alpha
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Rpx.dp(18)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = Rpx.sp(24),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private fun Modifier.rightBorder(color: Color = Color(0x1A000000)): Modifier = this.drawBehind {
    val strokeWidth = Rpx.dp(1).toPx()
    drawRect(
        color = color,
        topLeft = Offset(size.width - strokeWidth, 0f),
        size = Size(strokeWidth, size.height)
    )
}

private fun Modifier.bottomBorder(color: Color, thickness: Dp = Rpx.dp(1)): Modifier = this.drawBehind {
    val strokeWidth = thickness.toPx()
    drawRect(
        color = color,
        topLeft = Offset(0f, size.height - strokeWidth),
        size = Size(size.width, strokeWidth)
    )
}

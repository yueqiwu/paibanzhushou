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
                beyondViewportPageCount = 1
            ) { page ->
                val pageWeekStart = if (expandMonth) 0L else weekStartOfPage(page)
                val pageMonth = if (expandMonth) monthOfPage(page) else Dates.firstOfMonth(pageWeekStart)
                val pageData = if (expandMonth) monthGridPage(pageMonth) else weekGridPage(pageWeekStart)
                // 数据窗口按本页所在月取，避免落位时整页重算
                val entries = state.windowedSelected(pageMonth)
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
                                row.forEach { ms ->
                                    val dateStr = Dates.formatYmd(ms)
                                    CalendarCell(
                                        day = Dates.day(ms),
                                        dateStr = dateStr,
                                        isToday = dateStr == todayStr,
                                        selected = selectedOnPage && dateStr == selectedStr,
                                        entry = entries[dateStr],
                                        modifier = Modifier.weight(1f).height(CAL_ROW_HEIGHT),
                                        onClick = { state.onCellTap(dateStr) }
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

/** 日历单页数据，对应源 wu-calendar-block 的一份 weeks 数据 */
private class CalendarPage(val monthNumber: Int, val rows: List<List<Long>>)

/** 整月 42 格（周日开头），对应源 wu-calendar 的 weeks */
private fun monthGridPage(monthMs: Long): CalendarPage {
    val first = Dates.firstOfMonth(monthMs)
    val gridStart = Dates.addDays(first, -Dates.dayOfWeek(first))
    return CalendarPage(
        monthNumber = Dates.month(first),
        rows = (0 until 6).map { row -> (0 until 7).map { col -> Dates.addDays(gridStart, row * 7 + col) } }
    )
}

/** 折叠态单周，对应源 wu-calendar 单周视图 */
private fun weekGridPage(weekStart: Long): CalendarPage =
    CalendarPage(
        monthNumber = Dates.month(Dates.firstOfMonth(weekStart)),
        rows = listOf((0 until 7).map { Dates.addDays(weekStart, it) })
    )

package com.bilu.paibanzhushou.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.focus.FocusRequester
import androidx.core.content.ContextCompat
import com.bilu.paibanzhushou.R
import com.bilu.paibanzhushou.core.Dates
import com.bilu.paibanzhushou.core.Schedule
import com.bilu.paibanzhushou.data.Backup
import com.bilu.paibanzhushou.data.Migrate
import com.bilu.paibanzhushou.data.Person
import android.Manifest
import android.content.Intent
import android.net.Uri
import kotlin.math.roundToInt

/**
 * 首页，1:1 移植自 pages/index/index.vue。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndexScreen(
    state: IndexState,
    onOpenMemberTable: () -> Unit,
    onOpenAlarmSettings: () -> Unit,
    onImportLegacy: () -> Unit
) {
    val scrollState = rememberScrollState()
    var confettiOrigin by remember { mutableStateOf(Offset.Zero) }
    val swipeScope = rememberCoroutineScope()
    val swipe = remember { PersonSwipeState(swipeScope) }
    var calendarBounds by remember { mutableStateOf<Rect?>(null) }
    // 人员标签行（含「+ 新增」按钮）的屏幕范围：这一带要留给标签栏自己横滑，不能触发整页切人
    var tabsBounds by remember { mutableStateOf<Rect?>(null) }
    // 班次图例行的范围：班次多时这一行要能左右滑，同样不能触发切人
    var legendBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val dragging = state.dragController.dragging
    val currentPerson = state.people.firstOrNull { it.id == state.activePersonId }

    BoxWithConstraints(Modifier.fillMaxSize().background(C.pageBg)) {
        val widthPx = with(density) { maxWidth.toPx() }
        var headerHeight by remember { mutableStateOf(0.dp) }
        var footerHeight by remember { mutableStateOf(0.dp) }
        // 日历可用的净高度：整屏减状态栏、顶部 8rpx、头部卡、页脚、导航栏。
        // 5.5 英寸（约 640dp 高）机器上按屏幕高减常量估算的 chrome 会偏小，行高压不进一屏，改为实测。
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val availableForCalendar =
            (maxHeight - statusTop - Rpx.dp(8) - headerHeight - footerHeight - navBottom)
                .coerceAtLeast(0.dp)
        PageBackground()

        Column(
            Modifier
                .fillMaxSize()
                // 整页左右滑动切人：挂在最外层，日历区域与其它横向滚动区在 handle 内部被排除
                .personSwipeSwitch(
                    state = state,
                    swipe = swipe,
                    widthPx = { widthPx },
                    calendarBounds = { calendarBounds },
                    tabsBounds = { tabsBounds },
                    legendBounds = { legendBounds },
                    // 注意：这里必须读实时状态。canSwipe 是组合期的普通 val，被手势协程捕获后
                    // 会永远停留在首帧（首次安装只有 1 个人员 → false），导致新增人员后滑不动。
                    enabled = {
                        !state.dragController.dragging &&
                            !state.picker.visible &&
                            state.people.size > 1
                    },
                    onSwitch = { person -> state.setActivePerson(person.id) }
                )
                .verticalScroll(scrollState, enabled = !dragging && !swipe.swiping)
                .imePadding()
        ) {
            // 源版从页面顶部留 100rpx（含状态栏）；这里改成「状态栏高度 + 8rpx」，把省下来的
            // 竖屏空间让给日历，让首页不滚动也能看到完整月历
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(Rpx.dp(8)))

            Column(Modifier.fillMaxWidth()) {
                HeaderCard(
                    state = state,
                    swipe = swipe,
                    widthPx = widthPx,
                    onOpenAlarmSettings = onOpenAlarmSettings,
                    onImportLegacy = onImportLegacy,
                    onTabsBounds = { tabsBounds = it },
                    onConfettiOrigin = { confettiOrigin = it },
                    onHeight = { headerHeight = it }
                )
            }
            SwipeableCalendar(
                state = state,
                swipe = swipe,
                currentPerson = currentPerson,
                widthPx = widthPx,
                onOpenMemberTable = onOpenMemberTable,
                onGridBounds = { calendarBounds = it },
                onLegendBounds = { legendBounds = it },
                availableHeight = availableForCalendar
            )
            CopyrightFooter(
                state = state,
                onImportLegacy = onImportLegacy,
                modifier = Modifier.onGloballyPositioned {
                    footerHeight = with(density) { it.size.height.toDp() }
                }
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        DragOverlay(state)
        ConfettiOverlay(confettiOrigin, state.confettiPieces, state.confettiKey, state.confettiVisible)
        BanPickerSheet(state)
        state.confirm?.let { c ->
            ConfirmDialog(c) { state.confirm = null }
        }
    }
}

/**
 * 日历区块（标题 + 图例 + 月历）整体跟着手指横移，并在旁边摆一张「相邻人员」的日历预览。
 *
 * 只有这一块会动（对齐源版 .person-swipe-track），标题卡与设置卡固定不动；
 * 预览页 interactive = false（不建 pager、不响应点击），拖动时几乎不增加绘制成本。
 */
@Composable
private fun SwipeableCalendar(
    state: IndexState,
    swipe: PersonSwipeState,
    currentPerson: Person?,
    widthPx: Float,
    onOpenMemberTable: () -> Unit,
    onGridBounds: (Rect) -> Unit,
    onLegendBounds: (Rect) -> Unit,
    availableHeight: Dp? = null
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // 只做位移，不做 alpha / scale 渐变：收尾时 offset 归零、内容同时换成新人员，
                // 此时 alpha 若还要从 0.88 补回 1，那一帧就是明显的「闪一下」（部分机型更明显）
                .graphicsLayer { translationX = swipe.offset }
        ) {
            // CalendarSection 会发出两个并列的布局节点（标题列 + 日历卡），这里必须用 Column 包一层，
            // 直接塞进 Box 会让标题和图例被日历卡盖住
            Column(Modifier.fillMaxWidth()) {
                CalendarSection(
                    state = state,
                    onOpenMemberTable = onOpenMemberTable,
                    person = currentPerson,
                    interactive = true,
                    // 只有月历卡本身吃掉横滑（翻月）；标题、月份栏交给外层切人，
                    // 图例自己横向滚动（班次一多就溢出）
                    onGridBounds = onGridBounds,
                    onLegendBounds = onLegendBounds,
                    availableHeight = availableHeight
                )
            }
        }
        val peek = swipe.peekPerson
        if (peek != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = swipe.offset + swipe.peekSign * widthPx }
            ) {
                Column(Modifier.fillMaxWidth()) {
                    CalendarSection(
                        state = state,
                        onOpenMemberTable = {},
                        person = peek,
                        interactive = false,
                        availableHeight = availableHeight
                    )
                }
            }
        }
    }
}

/* ---------------- 背景 ---------------- */

@Composable
private fun PageBackground() {
    Box(Modifier.fillMaxSize()) {
        // 渐变底 + 白点阵 + 模糊色块：与毛玻璃卡片共用同一份绘制实现（ui/Backdrop.kt），
        // 这样卡片内重绘的背景才能和屏幕背景严丝合缝地对齐。
        // 背景静态绘制（不跑循环动效）：源版 .page::before 的 sparkleMove / .blob 的 floaty 在浏览器里
        // 只是 GPU 合成层的位移，这里若每帧重绘整屏渐变+点阵+色块，会持续吃满 GPU（真机实测 idle 也 90fps、
        // GPU 6~9ms/帧），并且让上层毛玻璃卡片的模糊层每帧重新模糊，滑动时直接掉帧。
        // 视觉上点阵漂移/色块呼吸幅度极小，静态化后肉眼无差别。
        // 预渲染成一张位图后每帧只做一次纹理贴图：整屏渐变 + 三层点阵 + 三个径向色块若每帧重绘，
        // 真机实测要多吃约 2ms GPU（90Hz 下预算只有 11.1ms），贴图后几乎为 0。
        val density = LocalDensity.current
        val cfg = LocalConfiguration.current
        val wPx = with(density) { cfg.screenWidthDp.dp.roundToPx() }
        val hPx = with(density) { cfg.screenHeightDp.dp.roundToPx() }
        val bgBitmap = remember(wPx, hPx) { renderBackdropBitmap(wPx, hPx, density) }
        Image(
            bitmap = bgBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        // 背景 logo
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = Rpx.dp(40), y = Rpx.dp(80))
                .size(Rpx.dp(260))
                .alpha(0.06f)
                .rotate(-12f),
            contentScale = ContentScale.Crop
        )
    }
}


/** 把整屏背景一次性画进位图（尺寸 = 屏幕像素），供 PageBackground 每帧直接贴图 */
private fun renderBackdropBitmap(w: Int, h: Int, density: Density): ImageBitmap {
    val bitmap = ImageBitmap(w, h)
    val canvas = GfxCanvas(bitmap)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(w.toFloat(), h.toFloat())
    ) {
        drawBackdropScreen(
            screenSize = Size(w.toFloat(), h.toFloat()),
            translation = Offset.Zero,
            dotShiftPx = 0f,
            dotAlpha = 0.55f,
            blobScale = 1f
        )
    }
    return bitmap
}

/* ---------------- 顶部标题卡 ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderCard(
    state: IndexState,
    swipe: PersonSwipeState,
    widthPx: Float,
    onOpenAlarmSettings: () -> Unit,
    onImportLegacy: () -> Unit,
    onTabsBounds: (Rect) -> Unit,
    onConfettiOrigin: (Offset) -> Unit,
    onHeight: (Dp) -> Unit
) {
    val density = LocalDensity.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onHeight(with(density) { it.size.height.toDp() }) }
            .padding(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(16)),
        radius = Rpx.dp(24),
        bgAlpha = 0.62f,
        contentPadding = PaddingValues(
            start = Rpx.dp(22),
            end = Rpx.dp(22),
            top = Rpx.dp(14),
            bottom = Rpx.dp(10)
        )
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Box(
            Modifier
                .size(Rpx.dp(64))
                .background(Brush.linearGradient(listOf(C.green, C.blue)), RoundedCornerShape(Rpx.dp(20)))
                .padding(Rpx.dp(5)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Rpx.dp(15))),
                contentScale = ContentScale.Crop
            )
        }
        Column(Modifier.padding(start = Rpx.dp(18)).weight(1f)) {
            Text(
                text = "班表小历",
                fontSize = Rpx.sp(30),
                fontWeight = FontWeight.Bold,
                color = C.textPrimary,
                letterSpacing = Rpx.sp(1),
                lineHeight = Rpx.sp(34)
            )
            Text(
                text = state.heroSubtitle,
                fontSize = Rpx.sp(22),
                color = C.textSub,
                lineHeight = Rpx.sp(26),
                modifier = Modifier.padding(top = Rpx.dp(2))
            )
        }
        AlarmTopButton(state = state, onClick = onOpenAlarmSettings)
        }
        // 排班设置：标题行可点，展开后才是班次/周期/自动休息/闹钟配置
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Rpx.dp(6))
                .clickable { state.toggleConfig() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "排班设置",
                fontSize = Rpx.sp(30),
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111)
            )
            if (state.people.size > 1) {
                Text(
                    text = "长按人员名称可拖拽排序",
                    fontSize = Rpx.sp(20),
                    color = C.hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Rpx.dp(16), end = Rpx.dp(16))
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box(
                Modifier
                    .background(Color(0x144A90E2), RoundedCornerShape(999.dp))
                    .border(Rpx.dp(1), Color(0x334A90E2), RoundedCornerShape(999.dp))
                    .clickable { state.toggleConfig() }
                    .padding(horizontal = Rpx.dp(16), vertical = Rpx.dp(6))
            ) {
                Text(
                    text = if (state.configOpen) "收起 ▲" else "展开 ▼",
                    fontSize = Rpx.sp(22),
                    color = C.blue
                )
            }
        }
        // 人员标签行（整页切人的手势在这里让位给横向滚动）
        PeopleTabs(state, swipe, widthPx, onTabsBounds)
        androidx.compose.animation.AnimatedVisibility(
            visible = state.configOpen,
            enter = expandVertically(tween(280)) + fadeIn(tween(280)),
            exit = shrinkVertically(tween(280)) + fadeOut(tween(200))
        ) {
            Column(Modifier.fillMaxWidth()) {
                ConfigBody(state, onConfettiOrigin, onImportLegacy)
            }
        }
    }
}

/* ---------------- 顶部闹钟入口 ---------------- */

/**
 * 顶部栏右侧的闹钟按钮：闹钟相关的全部设置与权限都收进了这个入口（AlarmSettingsScreen）。
 * 播放态用蓝底 + 蓝图标，关闭态用灰底 + 灰图标；有未授予的权限时右上角一个红点。
 */
@Composable
private fun AlarmTopButton(state: IndexState, onClick: () -> Unit) {
    val enabled = state.alarmConfig.enabled
    // hintOnly 的项（自启动/后台运行）无法自动检测，不计入红点
    val missing = state.alarmPermissions.count { !it.ok && !it.hintOnly }
    val fg = if (enabled) C.blue else Color(0xFF5B6B7F)
    Box(
        Modifier
            .size(Rpx.dp(60))
            .clip(RoundedCornerShape(Rpx.dp(18)))
            .background(if (enabled) Color(0x1A4A90E2) else Color(0x0F000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(Rpx.dp(32))) {
            val stroke = size.minDimension * 0.085f
            val cy = size.height * 0.56f
            val r = size.minDimension * 0.30f
            // 表盘
            drawCircle(
                color = fg,
                radius = r,
                center = center.copy(y = cy),
                style = Stroke(width = stroke)
            )
            // 两只铃铛
            drawLine(
                color = fg,
                start = Offset(size.width * 0.18f, size.height * 0.30f),
                end = Offset(size.width * 0.30f, size.height * 0.20f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = fg,
                start = Offset(size.width * 0.82f, size.height * 0.30f),
                end = Offset(size.width * 0.70f, size.height * 0.20f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            // 指针
            drawLine(
                color = fg,
                start = Offset(center.x, cy),
                end = Offset(center.x, cy - r * 0.52f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = fg,
                start = Offset(center.x, cy),
                end = Offset(center.x + r * 0.46f, cy + r * 0.12f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            // 底座
            drawLine(
                color = fg,
                start = Offset(size.width * 0.32f, size.height * 0.93f),
                end = Offset(size.width * 0.68f, size.height * 0.93f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        if (missing > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = Rpx.dp(4), end = Rpx.dp(4))
                    .size(Rpx.dp(14))
                    .background(Color(0xFFE53935), CircleShape)
                    .border(Rpx.dp(2), Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun PeopleTabs(
    state: IndexState,
    swipe: PersonSwipeState,
    widthPx: Float,
    onTabsBounds: (Rect) -> Unit
) {
    val controller = state.dragController
    val lazyState = rememberLazyListState()
    // 拖动切人的进度：当前人员的选中态随手指淡出、目标人员同步淡入，
    // 手指还没松开，上面这排 tab 就已经在跟着走了。
    val progress = if (swipe.swiping && swipe.peekPerson != null) {
        (kotlin.math.abs(swipe.offset) / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    } else 0f
    // 切人/新增/删除后，只有在选中的 tab 被挡在屏幕外时才把它滚进来（并居中）。
    // 已经完整可见就一动不动：以前无条件 animateScrollToItem + 居中，每次切完整排 tab 都会自己滚一下。
    LaunchedEffect(state.activePersonId, state.people.size) {
        val index = state.people.indexOfFirst { it.id == state.activePersonId }
        if (index < 0) return@LaunchedEffect
        val info = lazyState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = visible != null &&
            visible.offset >= info.viewportStartOffset &&
            visible.offset + visible.size <= info.viewportEndOffset
        if (fullyVisible) return@LaunchedEffect
        runCatching {
            lazyState.scrollToItem(index)
            val now = lazyState.layoutInfo
            val item = now.visibleItemsInfo.firstOrNull { it.index == index } ?: return@runCatching
            val viewport = now.viewportEndOffset - now.viewportStartOffset
            val delta = item.offset - ((viewport - item.size) / 2)
            if (kotlin.math.abs(delta) > 1) lazyState.scrollBy(delta.toFloat())
        }
    }
    androidx.compose.foundation.lazy.LazyRow(
        state = lazyState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Rpx.dp(10))
            // 把整排标签（含右侧「+ 新增」）的位置报给外层手势：按在这里只横滑标签，不切人
            .onGloballyPositioned { onTabsBounds(it.boundsInRoot()) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(state.people, key = { _, p -> p.id }) { index, person ->
            val weight = when (person.id) {
                swipe.peekPerson?.id -> progress
                state.activePersonId -> 1f - progress
                else -> 0f
            }.coerceIn(0f, 1f)
            val active = weight > 0.5f
            val isSource = controller.dragging && controller.kind == DragKind.PEOPLE && controller.fromIndex == index
            val pressScale by animateFloatAsState(
                targetValue = if (controller.isPressing(DragKind.PEOPLE, index)) 0.94f else 1f,
                animationSpec = tween(120),
                label = "peoplePress"
            )
            DraggableTag(
                controller = controller,
                kind = DragKind.PEOPLE,
                index = index,
                label = person.name,
                subLabel = "",
                accent = Color.Transparent,
                count = state.people.size,
                onTap = { state.setActivePerson(person.id) },
                onDrop = { from, to -> state.onDrop(DragKind.PEOPLE, from, to) },
                modifier = Modifier
                    .padding(end = Rpx.dp(12))
                    .alpha(if (isSource) 0.3f else 1f)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        // 过渡中按进度在白底和浅蓝底之间插值；线性渐变至少要两个颜色，所以走纯色分支
                        .then(
                            if (weight >= 0.999f) {
                                Modifier.background(
                                    Brush.linearGradient(listOf(Color(0x386AC695), Color(0x334A90E2))),
                                    RoundedCornerShape(999.dp)
                                )
                            } else {
                                Modifier.background(
                                    lerp(Color(0xB3FFFFFF), Color(0xFFD9E9F8), weight),
                                    RoundedCornerShape(999.dp)
                                )
                            }
                        )
                        .border(
                            Rpx.dp(1),
                            lerp(Color(0x0F000000), Color(0x594A90E2), weight),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(
                            start = Rpx.dp(24),
                            end = if (state.people.size > 1) Rpx.dp(12) else Rpx.dp(24),
                            top = Rpx.dp(10),
                            bottom = Rpx.dp(10)
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = person.name,
                        fontSize = Rpx.sp(24),
                        color = lerp(Color(0xFF444444), Color(0xFF2B6CB0), weight),
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                    if (state.people.size > 1) {
                        RoundCloseButton(
                            size = Rpx.dp(40),
                            modifier = Modifier.padding(start = Rpx.dp(8)),
                            onTap = { state.removePerson(person.id) }
                        )
                    }
                }
            }
        }
        item {
            Box(
                Modifier
                    .padding(end = Rpx.dp(12))
                    .dashedBorder(Color(0x664A90E2), Rpx.dp(999))
                    .background(Color(0x0F4A90E2), RoundedCornerShape(999.dp))
                    .clickable { state.addPerson() }
                    .padding(horizontal = Rpx.dp(24), vertical = Rpx.dp(10))
            ) {
                Text("+ 新增", fontSize = Rpx.sp(24), color = C.blue)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigBody(
    state: IndexState,
    onConfettiOrigin: (Offset) -> Unit,
    onImportLegacy: () -> Unit
) {
    val context = LocalContext.current
    val nameFocus = remember { FocusRequester() }
    var showAnchorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.nameFocusRequest) {
        if (state.nameFocusRequest > 0) {
            runCatching { nameFocus.requestFocus() }
            state.consumeNameFocus()
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = Rpx.dp(12))) {
        // 人员名称
        Row(
            Modifier.fillMaxWidth().padding(top = Rpx.dp(16)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("人员名称", fontSize = Rpx.sp(26), color = C.textLabel)
            AppTextField(
                value = state.activePersonName,
                onValueChange = { state.activePersonName = it },
                placeholder = "请输入名称",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Rpx.dp(16)),
                textAlign = TextAlign.End,
                fontSize = Rpx.sp(26),
                focusRequester = nameFocus,
                onFocusChanged = { focused ->
                    if (!focused) state.onActivePersonNameBlur()
                },
                onDone = { state.onActivePersonNameBlur() }
            )
        }

        SectionLabel("班次管理", topMargin = Rpx.dp(28), bottomMargin = Rpx.dp(20))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Rpx.dp(12)),
            verticalArrangement = Arrangement.spacedBy(Rpx.dp(12))
        ) {
            state.banOptions.forEachIndexed { index, ban ->
                Row(
                    Modifier
                        .background(C.fieldBg, RoundedCornerShape(999.dp))
                        .border(Rpx.dp(1), Color(0x0D000000), RoundedCornerShape(999.dp))
                        .padding(start = Rpx.dp(20), end = if (ban !in com.bilu.paibanzhushou.core.Schedule.PROTECTED_BANS) Rpx.dp(8) else Rpx.dp(20), top = Rpx.dp(8), bottom = Rpx.dp(8)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ban, fontSize = Rpx.sp(24), color = Color(0xFF333333))
                    if (ban !in com.bilu.paibanzhushou.core.Schedule.PROTECTED_BANS) {
                        RoundCloseButton(
                            size = Rpx.dp(40),
                            modifier = Modifier.padding(start = Rpx.dp(8)),
                            onTap = { state.removeBanOption(index) }
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = Rpx.dp(16)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTextField(
                value = state.newBanOption,
                onValueChange = { state.newBanOption = it },
                placeholder = "新增班次",
                modifier = Modifier.weight(1f),
                maxLength = 4,
                onDone = { state.addBanOption() }
            )
            GradientButton(
                text = "添加",
                modifier = Modifier.padding(start = Rpx.dp(16)),
                height = Rpx.dp(64),
                radius = Rpx.dp(16),
                fontSize = Rpx.sp(24),
                onClick = { state.addBanOption() }
            )
        }

        SectionLabel("排班周期", topMargin = Rpx.dp(28))

        Row(
            Modifier.fillMaxWidth().padding(top = Rpx.dp(16)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("锚点日期", fontSize = Rpx.sp(26), color = C.textLabel)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier
                        .background(C.fieldBg, RoundedCornerShape(Rpx.dp(16)))
                        .border(Rpx.dp(1), Color(0x0A000000), RoundedCornerShape(Rpx.dp(16)))
                        .clickable { showAnchorPicker = true }
                        .padding(horizontal = Rpx.dp(20))
                        .height(Rpx.dp(64)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.anchorDate, fontSize = Rpx.sp(26), color = Color(0xFF111111))
                }
            }
        }

        CycleTags(state)

        Text(
            text = "第1天对应锚点日期，之后按顺序循环；长按拖到另一个班次上可互换位置，点 × 删除该天",
            fontSize = Rpx.sp(22),
            color = C.hint,
            lineHeight = Rpx.sp(34),
            modifier = Modifier.padding(top = Rpx.dp(10))
        )

        if (state.restBanChoices.isNotEmpty()) {
            SectionLabel("自动休息", topMargin = Rpx.dp(28))
            RestSettingRow(
                label = "周末休息",
                choices = state.restBanChoices,
                selected = state.weekendRestBans,
                activeColor = Color(0xFF43A047),
                onToggle = { state.toggleRestBan("weekend", it) }
            )
            RestSettingRow(
                label = "节假日休息",
                choices = state.restBanChoices,
                selected = state.holidayRestBans,
                activeColor = Color(0xFFF57C00),
                onToggle = { state.toggleRestBan("holiday", it) }
            )
            Text(
                text = "所选班次落在周六/周日（或节假日）时自动改为休息",
                fontSize = Rpx.sp(22),
                color = C.hint,
                lineHeight = Rpx.sp(34),
                modifier = Modifier.padding(top = Rpx.dp(10))
            )
        }

        // 保存并生成
        Row(
            Modifier.fillMaxWidth().padding(top = Rpx.dp(26)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.onGloballyPositioned { onConfettiOrigin(it.boundsInRoot().center) }
            ) {
                GradientButton(
                    text = "保存并生成",
                    height = Rpx.dp(64),
                    radius = Rpx.dp(16),
                    fontSize = Rpx.sp(24),
                    onClick = { state.onGenerateTap() }
                )
            }
        }

        // 数据搬运：导出（备份 / 换机）与从旧版导入，两个入口并排
        Row(
            Modifier.fillMaxWidth().padding(top = Rpx.dp(20)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "导出数据 ›",
                fontSize = Rpx.sp(22),
                color = C.blue,
                modifier = Modifier
                    .clickable {
                        val export = Backup.build()
                        if (export == null) {
                            state.toastMsg("还没有可导出的数据")
                        } else if (Backup.copyToClipboard(context, export.link)) {
                            state.toastMsg(
                                "已复制备份数据（" + export.people + " 位人员），" +
                                    "到新手机点「从剪贴板导入旧版数据」即可恢复"
                            )
                        } else {
                            state.toastMsg("复制失败，请重试")
                        }
                    }
                    .padding(vertical = Rpx.dp(2))
            )
            // 旧版数据导入入口：首启引导点过「稍后」之后，用户仍能在这里找回来
            Text(
                text = "从剪贴板导入旧版数据 ›",
                fontSize = Rpx.sp(22),
                color = C.blue,
                modifier = Modifier
                    .clickable { onImportLegacy() }
                    .padding(vertical = Rpx.dp(2))
            )
        }
        Text(
            text = "「导出数据」把人员、班次、周期、单日调整和闹钟设置复制到剪贴板；" +
                "换机时在新手机点「从剪贴板导入旧版数据」，导入前会自动备份当前数据",
            fontSize = Rpx.sp(19),
            color = C.hint,
            lineHeight = Rpx.sp(28),
            modifier = Modifier.padding(top = Rpx.dp(6))
        )
    }

    DatePickerLauncher(
        show = showAnchorPicker,
        initialMs = Dates.parseYmd(state.anchorDate) ?: Dates.todayStart(),
        onDismiss = { showAnchorPicker = false },
        onPick = { ms ->
            showAnchorPicker = false
            state.onAnchorDateChange(Dates.formatYmd(ms))
        }
    )
}

@Composable
internal fun SectionLabel(text: String, topMargin: androidx.compose.ui.unit.Dp, bottomMargin: androidx.compose.ui.unit.Dp = 0.dp) {
    Row(
        Modifier.padding(top = topMargin, bottom = bottomMargin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(Rpx.dp(6))
                .height(Rpx.dp(24))
                .background(Brush.verticalGradient(listOf(C.green, C.blue)), RoundedCornerShape(999.dp))
        )
        Text(
            text = text,
            fontSize = Rpx.sp(24),
            fontWeight = FontWeight.SemiBold,
            color = C.textSection,
            modifier = Modifier.padding(start = Rpx.dp(10))
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleTags(state: IndexState) {
    val controller = state.dragController
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = Rpx.dp(14)),
        horizontalArrangement = Arrangement.spacedBy(Rpx.dp(12)),
        verticalArrangement = Arrangement.spacedBy(Rpx.dp(12))
    ) {
        state.cycleBans.forEachIndexed { index, ban ->
            val isSource = controller.dragging && controller.kind == DragKind.CYCLE && controller.fromIndex == index
            val isTarget = controller.dragging && controller.kind == DragKind.CYCLE && controller.toIndex == index && controller.fromIndex != index
            val pressScale by animateFloatAsState(
                targetValue = if (controller.isPressing(DragKind.CYCLE, index)) 0.94f else 1f,
                animationSpec = tween(120),
                label = "cyclePress"
            )
            DraggableTag(
                controller = controller,
                kind = DragKind.CYCLE,
                index = index,
                label = ban,
                subLabel = (index + 1).toString(),
                accent = hexColor(state.banColor(ban)),
                count = state.cycleBans.size,
                onTap = { state.openCyclePicker(index) },
                onDrop = { from, to -> state.onDrop(DragKind.CYCLE, from, to) },
                modifier = Modifier
                    .alpha(if (isSource) 0.3f else 1f)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
            ) {
                Row(
                    Modifier
                        .background(C.fieldBg, RoundedCornerShape(999.dp))
                        .border(
                            Rpx.dp(1),
                            if (isTarget) Color(0x994A90E2) else Color(0x0D000000),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(start = Rpx.dp(20), end = Rpx.dp(10), top = Rpx.dp(8), bottom = Rpx.dp(8)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(Rpx.dp(14))
                            .background(hexColor(state.banColor(ban)), CircleShape)
                    )
                    Text(
                        text = (index + 1).toString(),
                        fontSize = Rpx.sp(22),
                        color = Color(0xFFA5AEBC),
                        modifier = Modifier.padding(start = Rpx.dp(8), end = Rpx.dp(6))
                    )
                    Text(ban, fontSize = Rpx.sp(24), color = Color(0xFF333333))
                    RoundCloseButton(
                        size = Rpx.dp(40),
                        modifier = Modifier.padding(start = Rpx.dp(8)),
                        onTap = { state.removeCycleDayAt(index) }
                    )
                }
            }
        }
        Box(
            Modifier
                .dashedBorder(Color(0x664A90E2), Rpx.dp(999))
                .background(Color(0x0F4A90E2), RoundedCornerShape(999.dp))
                .clickable { state.addCycleDay() }
                .padding(horizontal = Rpx.dp(18), vertical = Rpx.dp(8))
        ) {
            Text("+", fontSize = Rpx.sp(28), color = C.blue)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestSettingRow(
    label: String,
    choices: List<String>,
    selected: List<String>,
    activeColor: Color,
    onToggle: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = Rpx.dp(14))) {
        Text(
            text = label,
            fontSize = Rpx.sp(24),
            color = C.textSub,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(Rpx.dp(120)).padding(top = Rpx.dp(10))
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Rpx.dp(12)),
            verticalArrangement = Arrangement.spacedBy(Rpx.dp(12))
        ) {
            choices.forEach { ban ->
                val active = selected.contains(ban)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) activeColor else C.fieldBg)
                        .border(Rpx.dp(1), if (active) activeColor else Color(0x0D000000), RoundedCornerShape(999.dp))
                        .clickable { onToggle(ban) }
                        .padding(horizontal = Rpx.dp(20), vertical = Rpx.dp(8))
                ) {
                    Text(
                        text = ban,
                        fontSize = Rpx.sp(24),
                        color = if (active) Color.White else Color(0xFF333333)
                    )
                }
            }
        }
    }
}


/* ---------------- 版权 ---------------- */

/** 官网：App 内留一个入口，能看到更新说明、也能直接把下载页发给别人 */
private const val WEBSITE_URL = "https://qipaclub.top/paibanzhushou/"

/** 用系统浏览器打开官网；个别精简 ROM 没有浏览器时给提示，而不是点了没反应 */
private fun openWebsite(state: IndexState, context: android.content.Context) {
    val ok = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!ok) state.toastMsg("没有找到可用的浏览器")
}

@Composable
private fun CopyrightFooter(
    state: IndexState,
    onImportLegacy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 导入过旧版数据时留了个撤销口：误点导入还能把导入前的数据恢复回来。
    // 只在新导入后的 24 小时内提示一次（同一份快照不重复提示），用户可以点「关闭」永久收起。
    var showRestoreHint by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    LaunchedEffect(state.scheduleVersion) {
        val show = Migrate.shouldShowRestoreHint()
        showRestoreHint = show
        if (show) Migrate.markRestoreHintShown()
    }
    // 版权与版本合成一行，省掉原先三行文字占的高度（开屏要看完整月历）
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(10))
    ) {
        // 更新卡片不参与「页脚实测高度」：它出现时只是页面变长、往下滚就能看到，
        // 不会再把日历的可用高度算小、把月历压窄。
        UpdateSection(state)
        Column(
            modifier
                .fillMaxWidth()
                .padding(top = Rpx.dp(14), bottom = Rpx.dp(8))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Rpx.dp(6)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "仅供个人使用 · © 2026 WYQ",
                    fontSize = Rpx.sp(19),
                    color = Color(0x590B1020),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "v" + state.currentVersion() + " · " + if (state.updateChecking) "检查中…" else "检查更新",
                    fontSize = Rpx.sp(19),
                    color = Color(0xCC4A90E2),
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = Rpx.dp(12))
                        .clip(RoundedCornerShape(Rpx.dp(8)))
                        .clickable { state.checkUpdate(silent = false) }
                        .padding(horizontal = Rpx.dp(4), vertical = Rpx.dp(2))
                )
            }
            if (showRestoreHint) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Rpx.dp(6)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "误导入？恢复导入前的数据",
                        fontSize = Rpx.sp(19),
                        color = Color(0xCCE5534B),
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Rpx.dp(8)))
                            .clickable { confirmRestore = true }
                            .padding(horizontal = Rpx.dp(4), vertical = Rpx.dp(2))
                    )
                    Text(
                        text = "关闭",
                        fontSize = Rpx.sp(19),
                        color = Color(0x660B1020),
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = Rpx.dp(12))
                            .clip(RoundedCornerShape(Rpx.dp(8)))
                            .clickable {
                                Migrate.dismissRestoreHint()
                                showRestoreHint = false
                            }
                            .padding(horizontal = Rpx.dp(4), vertical = Rpx.dp(2))
                    )
                }
            }
            // 迁移入口常驻在页脚一次，首启引导被关掉之后也找得回来
            if (!Migrate.alreadyMigrated()) {
                Text(
                    text = "从旧版导入数据",
                    fontSize = Rpx.sp(19),
                    color = Color(0xCC4A90E2),
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = Rpx.dp(6))
                        .clip(RoundedCornerShape(Rpx.dp(8)))
                        .clickable { onImportLegacy() }
                        .padding(horizontal = Rpx.dp(4), vertical = Rpx.dp(2))
                )
            }
            // 官网入口：换机、看更新说明、把下载页发给朋友都用得上
            Text(
                text = "官网 ›",
                fontSize = Rpx.sp(19),
                color = Color(0xCC4A90E2),
                maxLines = 1,
                modifier = Modifier
                    .padding(top = Rpx.dp(6))
                    .clip(RoundedCornerShape(Rpx.dp(8)))
                    .clickable { openWebsite(state, context) }
                    .padding(horizontal = Rpx.dp(4), vertical = Rpx.dp(2))
            )
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("恢复导入前的数据") },
            text = { Text("把排班恢复成上次导入旧版数据之前的模样，导入进来的内容会被替换掉。只保留最近一次的撤销点。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        val result = Migrate.restoreBackup()
                        showRestoreHint = false
                        if (result.ok) state.reloadAfterMigrate()
                        state.toastMsg(result.message)
                    }
                ) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("取消") }
            }
        )
    }
}

/* ---------------- 版本 / 检查更新 ---------------- */

/** Release notes 是 markdown，卡片里转成纯文本：标题去掉 #、列表换成 ·，内容完整不截断 */
private fun plainNotes(raw: String): String {
    return raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val body = when {
                line.startsWith("#") -> line.trimStart('#').trim()
                line.startsWith("- ") || line.startsWith("* ") -> "· " + line.substring(2).trim()
                else -> line.trimStart('>').trim()
            }
            // 去掉 markdown 的加粗 / 行内代码标记，卡片里当纯文本显示
            body.replace("**", "").replace("__", "").replace("`", "")
        }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

@Composable
private fun UpdateSection(state: IndexState) {
    val info = state.updateInfo
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (info == null) {
            // 无新版本时不占位，版本与「检查更新」在下方版权行里
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Rpx.dp(20)))
                    .background(Color(0xF2FFFFFF))
                    .padding(Rpx.dp(18))
            ) {
                Text(
                    text = "发现新版本 v" + info.version,
                    fontSize = Rpx.sp(24),
                    fontWeight = FontWeight.SemiBold,
                    color = C.textPrimary
                )
                val notes = remember(info.notes) { plainNotes(info.notes) }
                if (notes.isNotBlank()) {
                    // 更新说明只留前几行，够看懂改了什么即可（完整的在 GitHub Release 里）
                    Text(
                        text = notes,
                        fontSize = Rpx.sp(20),
                        lineHeight = Rpx.sp(32),
                        color = C.textSub,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Rpx.dp(6))
                    )
                }
                val p = state.updateProgress
                if (p in 0..99) {
                    Text(
                        text = "正在下载 $p%",
                        fontSize = Rpx.sp(21),
                        color = C.blue,
                        modifier = Modifier.padding(top = Rpx.dp(10))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Rpx.dp(6))
                            .height(Rpx.dp(6))
                            .clip(RoundedCornerShape(Rpx.dp(3)))
                            .background(Color(0x14000000))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((p.coerceIn(0, 100)) / 100f)
                                .height(Rpx.dp(6))
                                .clip(RoundedCornerShape(Rpx.dp(3)))
                                .background(C.blue)
                        )
                    }
                } else {
                    Row(
                        Modifier.padding(top = Rpx.dp(12)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(Rpx.dp(999)))
                                .background(C.blue)
                                .clickable { state.downloadUpdate() }
                                .padding(horizontal = Rpx.dp(22), vertical = Rpx.dp(10))
                        ) {
                            Text(
                                text = "下载并安装",
                                fontSize = Rpx.sp(22),
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "稍后",
                            fontSize = Rpx.sp(22),
                            color = C.textSub,
                            modifier = Modifier
                                .padding(start = Rpx.dp(18))
                                .clip(RoundedCornerShape(Rpx.dp(8)))
                                .clickable { state.dismissUpdate() }
                                .padding(horizontal = Rpx.dp(6), vertical = Rpx.dp(6))
                        )
                    }
                }
            }
        }
    }
}

/* ---------------- 拖拽浮层 ---------------- */

@Composable
private fun DragOverlay(state: IndexState) {
    val controller = state.dragController
    if (!controller.dragging) return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (controller.dragPos.x - controller.floatSize.width / 2f).roundToInt(),
                        (controller.dragPos.y - controller.floatSize.height / 2f).roundToInt()
                    )
                }
                .graphicsLayer {
                    scaleX = 1.06f
                    scaleY = 1.06f
                }
                .onGloballyPositioned { controller.floatSize = it.size }
        ) {
            val floatShape = RoundedCornerShape(999.dp)
            if (controller.kind == DragKind.PEOPLE) {
                Box(
                    Modifier
                        .shadow(Rpx.dp(8), floatShape)
                        .background(Color.White, floatShape)
                        .border(Rpx.dp(1), Color(0x804A90E2), floatShape)
                        .padding(horizontal = Rpx.dp(24), vertical = Rpx.dp(10))
                ) {
                    Text(controller.label, fontSize = Rpx.sp(24), color = Color(0xFF444444), maxLines = 1)
                }
            } else {
                Row(
                    Modifier
                        .shadow(Rpx.dp(8), floatShape)
                        .background(Color.White, floatShape)
                        .border(Rpx.dp(1), Color(0x804A90E2), floatShape)
                        .padding(horizontal = Rpx.dp(20), vertical = Rpx.dp(8)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(Rpx.dp(14)).background(controller.labelColor, CircleShape))
                    Text(controller.subLabel, fontSize = Rpx.sp(22), color = Color(0xFFA5AEBC), modifier = Modifier.padding(start = Rpx.dp(8), end = Rpx.dp(6)))
                    Text(controller.label, fontSize = Rpx.sp(24), color = Color(0xFF333333), maxLines = 1)
                }
            }
        }
    }
}

/* ---------------- 彩花 ---------------- */

@Composable
private fun ConfettiOverlay(origin: Offset, pieces: List<ConfettiPiece>, burstKey: Int, visible: Boolean) {
    if (!visible || pieces.isEmpty()) return
    val anim = remember(burstKey) { Animatable(0f) }
    LaunchedEffect(burstKey) {
        anim.snapTo(0f)
        anim.animateTo(1f, animationSpec = tween(1000, easing = LinearEasing))
    }
    val cubic = remember { CubicBezierEasing(0.15f, 0.78f, 0.2f, 1f) }
    Canvas(Modifier.fillMaxSize()) {
        val elapsed = anim.value * 1000f
        // 扩散光环 620ms
        val ringP = (elapsed / 620f).coerceIn(0f, 1f)
        if (ringP < 1f) {
            val base = Rpx.dp(10).toPx()
            val r = base * (0.2f + 6.0f * ringP)
            drawCircle(
                color = Color.White.copy(alpha = 0.95f * (1f - ringP)),
                radius = r,
                center = origin,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        pieces.forEach { piece ->
            val local = ((elapsed - piece.delay) / piece.dur)
            if (local <= 0f || local >= 1f) return@forEach
            val eased = cubic.transform(local)
            val k = Rpx.scale * density
            val pos = origin + Offset(piece.dx * k, piece.dy * k) * eased
            val w = piece.size * k
            val h = piece.thick * k
            rotate(degrees = piece.rot * eased, pivot = pos) {
                drawRoundRect(
                    color = piece.color.copy(alpha = 1f - local),
                    topLeft = Offset(pos.x - w / 2f, pos.y - h / 2f),
                    size = Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f, w / 2f)
                )
            }
        }
    }
}

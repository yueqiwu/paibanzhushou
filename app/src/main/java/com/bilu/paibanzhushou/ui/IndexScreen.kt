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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.focus.FocusRequester
import com.bilu.paibanzhushou.R
import com.bilu.paibanzhushou.core.Dates
import kotlin.math.roundToInt

/**
 * 首页，1:1 移植自 pages/index/index.vue。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndexScreen(state: IndexState, onOpenMemberTable: () -> Unit) {
    val scrollState = rememberScrollState()
    var confettiOrigin by remember { mutableStateOf(Offset.Zero) }
    val dragging = state.dragController.dragging

    Box(Modifier.fillMaxSize().background(C.pageBg)) {
        PageBackground()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = !dragging)
                .imePadding()
        ) {
            // 源版 APP 分支：<view class="header-block" style="padding-top:100rpx;">（从页面顶部起算，含状态栏）
            Spacer(Modifier.height(Rpx.dp(100)))

            HeroCard(state)
            ConfigCard(state, onConfettiOrigin = { confettiOrigin = it })
            CalendarSection(state = state, onOpenMemberTable = onOpenMemberTable)
            CopyrightFooter()
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

@Composable
private fun HeroCard(state: IndexState) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(26)),
        radius = Rpx.dp(28),
        bgAlpha = 0.62f,
        contentPadding = PaddingValues(horizontal = Rpx.dp(28), vertical = Rpx.dp(24))
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Box(
            Modifier
                .size(Rpx.dp(88))
                .background(Brush.linearGradient(listOf(C.green, C.blue)), RoundedCornerShape(Rpx.dp(26)))
                .padding(Rpx.dp(6)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Rpx.dp(20))),
                contentScale = ContentScale.Crop
            )
        }
        Column(Modifier.padding(start = Rpx.dp(18))) {
            Text(
                text = "排班助手",
                fontSize = Rpx.sp(34),
                fontWeight = FontWeight.Bold,
                color = C.textPrimary,
                letterSpacing = Rpx.sp(1)
            )
            Text(
                text = state.heroSubtitle,
                fontSize = Rpx.sp(24),
                color = C.textSub,
                modifier = Modifier.padding(top = Rpx.dp(6))
            )
        }
        }
    }
}

/* ---------------- 排班设置卡片 ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigCard(state: IndexState, onConfettiOrigin: (Offset) -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(18)),
        radius = Rpx.dp(28),
        bgAlpha = 0.62f,
        contentPadding = PaddingValues(
            start = Rpx.dp(24),
            end = Rpx.dp(24),
            top = Rpx.dp(22),
            bottom = Rpx.dp(20)
        )
    ) {
        // header
        Row(
            Modifier.fillMaxWidth().clickable { state.toggleConfig() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("排班设置", fontSize = Rpx.sp(30), fontWeight = FontWeight.SemiBold, color = Color(0xFF111111))
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

        PeopleTabs(state)

        androidx.compose.animation.AnimatedVisibility(
            visible = state.configOpen,
            enter = expandVertically(tween(280)) + fadeIn(tween(280)),
            exit = shrinkVertically(tween(280)) + fadeOut(tween(200))
        ) {
            Column(Modifier.fillMaxWidth()) {
                ConfigBody(state, onConfettiOrigin)
            }
        }
    }
}

@Composable
private fun PeopleTabs(state: IndexState) {
    val controller = state.dragController
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = Rpx.dp(14)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.people.forEachIndexed { index, person ->
            val active = person.id == state.activePersonId
            val isSource = controller.dragging && controller.kind == DragKind.PEOPLE && controller.fromIndex == index
            val isTarget = controller.dragging && controller.kind == DragKind.PEOPLE && controller.toIndex == index && controller.fromIndex != index
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
                        .then(
                            if (active) Modifier.background(
                                Brush.linearGradient(listOf(Color(0x386AC695), Color(0x334A90E2)))
                            ) else Modifier.background(Color(0xB3FFFFFF))
                        )
                        .border(
                            Rpx.dp(1),
                            if (active) Color(0x594A90E2) else Color(0x0F000000),
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
                        color = if (active) Color(0xFF2B6CB0) else Color(0xFF444444),
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
        Box(
            Modifier
                .dashedBorder(Color(0x664A90E2), Rpx.dp(999))
                .background(Color(0x0F4A90E2), RoundedCornerShape(999.dp))
                .clickable { state.addPerson() }
                .padding(horizontal = Rpx.dp(24), vertical = Rpx.dp(10))
        ) {
            Text("+ 新增", fontSize = Rpx.sp(24), color = C.blue)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigBody(state: IndexState, onConfettiOrigin: (Offset) -> Unit) {
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
private fun SectionLabel(text: String, topMargin: androidx.compose.ui.unit.Dp, bottomMargin: androidx.compose.ui.unit.Dp = 0.dp) {
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

@Composable
private fun CopyrightFooter() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(36), bottom = Rpx.dp(20)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "该软件仅供个人使用，请勿商用。",
            fontSize = Rpx.sp(22),
            color = Color(0x610B1020),
            lineHeight = Rpx.sp(38)
        )
        Text(
            text = "Copyright © 2026 WYQ 版权所有。",
            fontSize = Rpx.sp(22),
            color = Color(0x610B1020),
            lineHeight = Rpx.sp(38),
            modifier = Modifier.padding(top = Rpx.dp(6))
        )
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

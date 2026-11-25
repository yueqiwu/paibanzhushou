package com.bilu.paibanzhushou.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bilu.paibanzhushou.data.Person
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * 整页左右滑动切换排班人员的状态（对齐源项目 mixins/person-swipe.js 的手感）：
 *
 * - 跟手阶段 offset 1:1 跟随手指，超过屏宽 92% 后按 0.12 阻尼；
 * - 抬手够阈值（屏宽 12%，夹在 44~96dp）或「260ms 内快滑 32dp」→ 260ms 补满整屏，
 *   补满瞬间把相邻人员设为当前人员、同时把 offset 归零（同一帧完成，肉眼看不到跳变）；
 * - 不满足则 220ms 回弹。
 *
 * 只有日历区块跟着 offset 平移（对齐源版 .person-swipe-track），标题卡与设置卡固定不动。
 */
@Stable
class PersonSwipeState(private val scope: CoroutineScope) {
    private val settleEasing = CubicBezierEasing(0.22f, 0.68f, 0.32f, 1f)

    /** 日历区块的水平位移（px，负值 = 往左拖，下一位从右边进来） */
    var offset by mutableFloatStateOf(0f)
        private set

    /** 拖动过程中「露出来」的那位人员；null = 没在拖动 */
    var peekPerson by mutableStateOf<Person?>(null)
        private set

    /** 相邻人员在 offset 的哪一侧：+1 = 右边（下一位），-1 = 左边（上一位） */
    var peekSign by mutableIntStateOf(1)
        private set

    /** 是否正在横向拖动（用于临时关掉页面竖向滚动） */
    var swiping by mutableStateOf(false)

    private var job: Job? = null

    fun prepare(person: Person, sign: Int) {
        job?.cancel()
        peekPerson = person
        peekSign = sign
        swiping = true
    }

    /** 手指跟手：直接改 offset，不跑动画 */
    fun dragTo(value: Float) {
        job?.cancel()
        offset = value
    }

    fun cancel() {
        job?.cancel()
        offset = 0f
        peekPerson = null
        swiping = false
    }

    /**
     * 抬手结算：够了就补满整屏后换人，不够就回弹。
     *
     * 换人的时机很关键：补满后目标页已经停在正中、主层整块滑出屏幕（看不见），
     * 这时才 [onSwitch]，主层就能「在屏幕外」把新人员的日历重组好；等两帧再归零，
     * 屏幕上从预览页交回主层时两边内容已经完全一致。
     * 若像以前那样在同一帧里 offset 归零 + 换人，荣耀等机型上主层偶尔会晚一帧重组，
     * 正中就会闪一帧旧人员的班次标签。
     */
    fun release(enough: Boolean, width: Float, onSwitch: (Person) -> Unit) {
        val target = peekPerson
        val sign = peekSign
        job?.cancel()
        job = scope.launch {
            if (enough) {
                animate(offset, -sign * width, animationSpec = tween(260, easing = settleEasing)) { v, _ ->
                    offset = v
                }
                if (target != null) {
                    onSwitch(target)
                    // 等两帧让主层组合/布局完成；加超时是防止退到后台时等不到帧、swiping 卡在 true
                    withTimeoutOrNull(240L) {
                        withFrameNanos { }
                        withFrameNanos { }
                    }
                }
                offset = 0f
                peekPerson = null
                swiping = false
            } else {
                animate(offset, 0f, animationSpec = tween(220)) { v, _ ->
                    offset = v
                }
                offset = 0f
                peekPerson = null
                swiping = false
            }
        }
    }
}

/**
 * 挂在首页根节点上的「左右滑动切人」手势。
 *
 * 命中排除：
 * - 按在日历区域内 → 交给日历自己的翻月手势；
 * - 按在人员标签行内 → 交给标签栏自己的横向滚动（否则想滑标签就会切人）；
 * - 起手点被别的组件消费了（按钮、人员标签的横向滚动等）→ 直接放行；
 * - 竖向位移先超过横向 → 交给页面竖向滚动。
 */
@Composable
fun Modifier.personSwipeSwitch(
    state: IndexState,
    swipe: PersonSwipeState,
    widthPx: () -> Float,
    calendarBounds: () -> Rect?,
    tabsBounds: () -> Rect?,
    /** 班次图例那一行：班次多到一行放不下时要左右滑，这一带不能触发整页切人 */
    legendBounds: (() -> Rect?)? = null,
    enabled: () -> Boolean,
    onSwitch: (Person) -> Unit
): Modifier {
    // pointerInput(Unit) 的手势协程只启动一次，闭包会一直沿用首次组合时捕获的 lambda。
    // 首启只有 1 个人员（canSwipe = false），若直接捕获就会永远读不到「后来新增了人员」，
    // 表现为添加人员后无法左右滑动、重启（重新组合）才恢复 —— 所以这里必须用
    // rememberUpdatedState 把每帧最新的回调转给手势协程。
    val currentEnabled = rememberUpdatedState(enabled)
    val currentBounds = rememberUpdatedState(calendarBounds)
    val currentTabsBounds = rememberUpdatedState(tabsBounds)
    val currentLegendBounds = rememberUpdatedState(legendBounds)
    val currentWidth = rememberUpdatedState(widthPx)
    val currentOnSwitch = rememberUpdatedState(onSwitch)

    return pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    val thresholdMin = 44.dp.toPx()
    val thresholdMax = 96.dp.toPx()
    val flingDistance = 32.dp.toPx()

    fun neighbourOf(sign: Int): Person? {
        val list = state.people
        if (list.size <= 1) return null
        val current = list.indexOfFirst { it.id == state.activePersonId }
        if (current < 0) return null
        val target = ((current + sign) % list.size + list.size) % list.size
        return list.getOrNull(target)
    }

    awaitEachGesture {
        // 走 Initial 阶段：抢在按钮 / 标签的 clickable 之前判断方向，
        // 否则按月栏那种 clickable 会先把 down 消费掉，滑动就永远起不来。
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!currentEnabled.value()) return@awaitEachGesture
        val bounds = currentBounds.value()
        if (bounds != null && bounds.contains(down.position)) return@awaitEachGesture
        // 人员标签行要留给它自己的横向滚动：手指落在标签上时绝不切人
        val tabs = currentTabsBounds.value()
        if (tabs != null && tabs.contains(down.position)) return@awaitEachGesture
        // 班次图例行同理：班次一多就会横向溢出，得让它自己的 horizontalScroll 接管
        val legend = currentLegendBounds.value?.invoke()
        if (legend != null && legend.contains(down.position)) return@awaitEachGesture
        val width = (currentWidth.value().takeIf { it > 1f } ?: size.width.toFloat())
        if (width <= 1f) return@awaitEachGesture
        val threshold = (width * 0.12f).coerceIn(thresholdMin, thresholdMax)
        val edge = width * 0.92f

        var dx = 0f
        var decided = false
        var tracking = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            dx = change.position.x - down.position.x
            val dy = change.position.y - down.position.y

            if (!decided) {
                if (abs(dx) < slop && abs(dy) < slop) {
                    // 还没起手：抬手了就是个点击
                    if (!change.pressed) return@awaitEachGesture
                    continue
                }
                if (abs(dy) >= abs(dx)) return@awaitEachGesture // 竖向：交给页面滚动
                val sign = if (dx < 0f) 1 else -1
                val target = neighbourOf(sign) ?: return@awaitEachGesture
                swipe.prepare(target, sign)
                decided = true
                tracking = true
            }

            if (tracking) {
                change.consume()
                val sign = if (dx < 0f) -1 else 1
                val raw = abs(dx)
                val damped = if (raw <= edge) raw else edge + (raw - edge) * 0.12f
                swipe.dragTo(sign * damped)
                if (!change.pressed) break
            }
        }

        if (!tracking) return@awaitEachGesture

        val elapsed = android.os.SystemClock.uptimeMillis() - down.uptimeMillis
        val distance = abs(dx)
        val enough = distance >= threshold ||
            (elapsed <= 260L && distance >= flingDistance)
        val switchTo = currentOnSwitch.value
        swipe.release(enough, width) { person -> switchTo(person) }
    }
    }
}

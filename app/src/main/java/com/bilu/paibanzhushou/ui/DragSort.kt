package com.bilu.paibanzhushou.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.withTimeoutOrNull

/** 拖拽种类 */
object DragKind {
    const val PEOPLE = "people"
    const val CYCLE = "cycle"
}

/**
 * 长按拖拽控制器，等价于 mixins/long-press-sortable.js 的状态部分。
 */
class DragSortController {
    var kind by mutableStateOf("")
    var fromIndex by mutableStateOf(-1)
    var toIndex by mutableStateOf(-1)
    var dragging by mutableStateOf(false)
    var label by mutableStateOf("")
    var subLabel by mutableStateOf("")
    var labelColor by mutableStateOf(Color.Transparent)
    var dragPos by mutableStateOf(Offset.Zero)   // 浮层中心（根坐标）
    var floatSize by mutableStateOf(IntSize.Zero)
    var pressingKey by mutableStateOf("")        // 长按计时中的标签（"kind#index"），用于按压反馈

    fun isPressing(kind: String, index: Int) = pressingKey == (kind + "#" + index)

    private val rects = HashMap<String, Rect>()

    private fun key(kind: String, index: Int) = kind + "#" + index

    fun record(kind: String, index: Int, rect: Rect) {
        rects[key(kind, index)] = rect
    }

    fun nearest(kind: String, point: Offset, count: Int): Int {
        var best = -1
        var bestDist = Double.MAX_VALUE
        for (i in 0 until count) {
            val r = rects[key(kind, i)] ?: continue
            val cx = r.left + r.width / 2f
            val cy = r.top + r.height / 2f
            val dx = (cx - point.x).toDouble()
            val dy = (cy - point.y).toDouble()
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        if (best < 0) return -1
        return best.coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun cancel() {
        dragging = false
        kind = ""
        fromIndex = -1
        toIndex = -1
    }
}

private const val LONG_PRESS_MS = 300L

/**
 * 长按拖拽的标签容器，等价于 mixins/long-press-sortable.js 的手势部分。
 */
@Composable
fun DraggableTag(
    controller: DragSortController,
    kind: String,
    index: Int,
    label: String,
    subLabel: String,
    accent: Color,
    count: Int,
    onTap: () -> Unit,
    onDrop: (from: Int, to: Int) -> Unit,
    onDragStateChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val posRef = remember { object { var pos: Offset = Offset.Zero } }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                posRef.pos = coords.positionInRoot()
                controller.record(kind, index, coords.boundsInRoot())
            }
            .pointerInput(kind, index, count) {
                val threshold = 8f * density
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.isConsumed) return@awaitEachGesture
                    controller.pressingKey = kind + "#" + index

                    var state = 0 // 0 等待 1 抬起(点击) 2 判定滚动 3 被子控件消费
                    withTimeoutOrNull(LONG_PRESS_MS) {
                        while (state == 0) {
                            val ev = awaitPointerEvent()
                            val change: PointerInputChange? = ev.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                state = 2
                                break
                            }
                            if (change.isConsumed) {
                                state = 3
                                break
                            }
                            if (change.changedToUpIgnoreConsumed()) {
                                state = 1
                                break
                            }
                            if ((change.position - down.position).getDistance() > threshold) {
                                state = 2
                                break
                            }
                        }
                    }

                    if (state == 1) {
                        controller.pressingKey = ""
                        down.consume()
                        onTap()
                        return@awaitEachGesture
                    }
                    if (state != 0) {
                        controller.pressingKey = ""
                        return@awaitEachGesture
                    }

                    controller.pressingKey = ""
                    controller.kind = kind
                    controller.fromIndex = index
                    controller.toIndex = index
                    controller.label = label
                    controller.subLabel = subLabel
                    controller.labelColor = accent
                    controller.dragPos = posRef.pos + down.position
                    controller.dragging = true
                    onDragStateChange(true)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    var endByUp = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val change = ev.changes.firstOrNull { it.id == down.id }
                        if (change == null) break
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            endByUp = true
                            break
                        }
                        val delta = change.positionChange()
                        if (delta != Offset.Zero) {
                            controller.dragPos += delta
                            controller.toIndex = controller.nearest(kind, controller.dragPos, count)
                            change.consume()
                        }
                    }

                    val from = controller.fromIndex
                    val to = controller.toIndex
                    controller.dragging = false
                    controller.kind = ""
                    controller.fromIndex = -1
                    controller.toIndex = -1
                    onDragStateChange(false)
                    if (endByUp && from >= 0 && to >= 0 && to != from) onDrop(from, to)
                }
            }
    ) {
        content()
    }
}

/** 拖拽浮层容器，内容由调用方按 dragPos 定位 */
@Composable
fun DragFloatingLayer(controller: DragSortController, content: @Composable (DragSortController) -> Unit) {
    if (controller.dragging) {
        Box(
            modifier = Modifier.onGloballyPositioned { coords ->
                controller.floatSize = coords.size
            }
        ) {
            content(controller)
        }
    }
}

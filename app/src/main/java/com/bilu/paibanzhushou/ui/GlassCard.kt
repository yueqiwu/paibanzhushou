package com.bilu.paibanzhushou.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 真实毛玻璃卡片。
 *
 * 移植自 uni 的 `.card`（rgba(255,255,255,.88) + 1rpx 白边 + backdrop-filter: blur(14px)）。
 * 浏览器有 backdrop-filter，Android 没有对应 API，所以这里用等价做法：
 *   1. 在卡片自己的绘制层里再画一份「整屏背景」（坐标按 -卡片根坐标 平移，与屏幕背景对齐）；
 *   2. 对这个绘制层做真实高斯模糊（API 31+ 走 RenderEffect，低版本自动退化为半透明白卡）；
 *   3. 盖上 rgba(255,255,255,alpha) 白膜 + 1rpx 白边。
 * 由于首页背景是固定的（不随内容滚动），这样得到的就是真正的 backdrop blur。
 */
/** 临时实验开关：关掉真实模糊以验证 GPU 开销归属 */
private const val BLUR_ENABLED = true

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = Rpx.dp(28),
    bgAlpha: Float = 0.72f,
    borderAlpha: Float = 0.9f,
    elevation: Dp = Rpx.dp(8),
    blurRadius: Dp = Rpx.dp(26),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    // Modifier.blur 只在 Android 12(31)+ 真正生效，低版本这里不画模糊层、白膜沿用源版的 0.88
    val realBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && BLUR_ENABLED

    Box(
        modifier
            .onGloballyPositioned { positionInRoot = it.positionInRoot() }
            .shadow(elevation, shape, clip = false, ambientColor = Color(0x14112034), spotColor = Color(0x14112034))
            .clip(shape)
    ) {
        if (realBlur) {
            // 用全局预模糊的整屏背景贴图，而不是在卡片里挂 Modifier.blur：
            // 后者每张卡都持有一块整屏 RenderEffect 缓冲（实测 3 块 13.6MB），
            // 90Hz 下每帧多花约 2ms GPU；贴图后同样是真 backdrop blur，但几乎免费。
            val blurred = remember(screenW, screenH, blurRadius) {
                GlassBackdropCache.get(
                    wPx = screenW.toInt(),
                    hPx = screenH.toInt(),
                    density = density,
                    blurRadiusPx = with(density) { blurRadius.toPx() }
                )
            }
            if (blurred != null) {
                // graphicsLayer：把这张贴图放进独立渲染层，卡片内日历滚动时只做图层合成，
                // 不必每帧重新记录 / 重贴（否则每帧 3 次全卡面积的双线性放大很吃填充率）
                Canvas(Modifier.matchParentSize().graphicsLayer()) {
                    val d = GlassBackdropCache.DOWNSCALE.toFloat()
                    val srcX = (-positionInRoot.x / d).toInt()
                    val srcY = (-positionInRoot.y / d).toInt()
                    val srcW = (size.width / d).toInt().coerceAtLeast(1)
                    val srcH = (size.height / d).toInt().coerceAtLeast(1)
                    drawImage(
                        image = blurred,
                        srcOffset = IntOffset(
                            srcX.coerceIn(0, (blurred.width - srcW).coerceAtLeast(0)),
                            srcY.coerceIn(0, (blurred.height - srcH).coerceAtLeast(0))
                        ),
                        srcSize = IntSize(srcW, srcH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.Low
                    )
                }
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Color.White.copy(alpha = if (realBlur) bgAlpha else bgAlpha.coerceAtLeast(0.88f)))
        )
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
        Box(Modifier.matchParentSize().border(Rpx.dp(1), Color.White.copy(alpha = borderAlpha), shape))
    }
}

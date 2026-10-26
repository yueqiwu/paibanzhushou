package com.bilu.paibanzhushou.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 整屏背景的「纯绘制」实现：135° 渐变底 + 三层白点阵 + 三个模糊色块。
 *
 * 之所以抽成 DrawScope 扩展而不是 Composable，是为了让毛玻璃卡片能在自己的绘制层里
 * 再画一份同样的背景（配合 Modifier.blur 做真实背景模糊，等同 CSS backdrop-filter）。
 *
 * @param screenSize 屏幕尺寸（px）。卡片内复用时必须传屏幕尺寸，坐标才和屏幕背景一致。
 * @param translation 绘制原点偏移（px）。卡片内传 -卡片在根布局中的坐标，模糊副本就与屏幕背景严丝合缝。
 * @param dotShiftPx 点阵整体的纵向浮动（px，源版 sparkleMove 动画）。
 * @param dotAlpha 点阵透明度（源版 0.4 → 0.7 呼吸），内部还会乘 0.28。
 * @param blobScale 色块缩放（源版 floaty 动画 1 → 1.03）。
 */
fun DrawScope.drawBackdropScreen(
    screenSize: Size,
    translation: Offset = Offset.Zero,
    dotShiftPx: Float = 0f,
    dotAlpha: Float = 0.55f,
    blobScale: Float = 1f
) {
    val u = Rpx.scale * density
    // .bg：linear-gradient(135deg, #eefaf4 0%, #eef3fb 45%, #faf0f7 100%)
    drawRect(
        brush = Brush.linearGradient(
            0f to Color(0xFFEEFAF4),
            0.45f to Color(0xFFEEF3FB),
            1f to Color(0xFFFAF0F7),
            start = translation,
            end = translation + Offset(screenSize.width, screenSize.height)
        ),
        topLeft = translation,
        size = screenSize
    )
    // .page::before 白色点阵（background-size 220/260/320rpx 三层叠加）
    drawDotsTile(screenSize, translation, 220f * u, 0.20f, 0.30f, dotAlpha, dotShiftPx)
    drawDotsTile(screenSize, translation, 260f * u, 0.80f, 0.20f, dotAlpha * 0.9f, dotShiftPx)
    drawDotsTile(screenSize, translation, 320f * u, 0.60f, 0.70f, dotAlpha * 0.8f, dotShiftPx)
    // .blob--1/2/3：420/520/460rpx 的径向渐变圆
    drawBlob(
        center = translation + Offset(90f * u, 330f * u),
        radius = 210f * u, color = Color(0xFF6AC695), scale = blobScale
    )
    drawBlob(
        center = translation + Offset(screenSize.width - 80f * u, 640f * u),
        radius = 260f * u, color = Color(0xFF4A90E2), scale = blobScale
    )
    drawBlob(
        center = translation + Offset(290f * u, screenSize.height - 50f * u),
        radius = 230f * u, color = Color(0xFFFFD166), scale = blobScale
    )
}

/**
 * 预渲染「整屏背景 + 高斯模糊」的位图（按 [downscale] 降采样，默认 1/2）。
 *
 * 毛玻璃卡片从这张图上抠自己那一块贴上去，等价 CSS 的 backdrop-filter，
 * 但不再在卡片布局里挂 Modifier.blur（那会让每张卡都持有一块整屏 RenderEffect 缓冲，
 * 90Hz 下每次重绘多花 ~2ms GPU，还占 3 块 13.6MB 的 render target）。
 * 只在 API 31+ 可用，低版本返回 null（调用方退化为半透明白卡）。
 */
fun renderBlurredBackdrop(
    wPx: Int,
    hPx: Int,
    density: Density,
    blurRadiusPx: Float,
    downscale: Int = 2
): ImageBitmap? {
    if (wPx <= 0 || hPx <= 0) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val d = downscale.coerceAtLeast(1)
    val bw = ((wPx + d - 1) / d).coerceAtLeast(1)
    val bh = ((hPx + d - 1) / d).coerceAtLeast(1)

    // 1) 整屏背景（不缩放）
    val src = ImageBitmap(wPx, hPx)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = GfxCanvas(src),
        size = Size(wPx.toFloat(), hPx.toFloat())
    ) {
        drawBackdropScreen(
            screenSize = Size(wPx.toFloat(), hPx.toFloat()),
            translation = Offset.Zero,
            dotShiftPx = 0f,
            dotAlpha = 0.55f,
            blobScale = 1f
        )
    }

    // 2) 降采样 + 软件高斯模糊（3 次 box blur 近似高斯；RenderNode.draw 是 @hide，不可用）
    val small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(small).drawBitmap(
        src.asAndroidBitmap(),
        null,
        Rect(0, 0, bw, bh),
        Paint(Paint.FILTER_BITMAP_FLAG)
    )
    val pixels = IntArray(bw * bh)
    small.getPixels(pixels, 0, bw, 0, 0, bw, bh)
    val radius = (blurRadiusPx / d).toInt().coerceAtLeast(1)
    boxBlurArgb(pixels, bw, bh, radius, passes = 3)
    small.setPixels(pixels, 0, bw, 0, 0, bw, bh)
    return small.asImageBitmap()
}

/** 可分离 box blur（滑动窗口求和），跑 [passes] 次近似高斯。纯 Kotlin，兼容所有 API 版本。 */
private fun boxBlurArgb(pixels: IntArray, w: Int, h: Int, radius: Int, passes: Int) {
    if (radius < 1 || w <= 0 || h <= 0) return
    val tmp = IntArray(pixels.size)
    repeat(passes) {
        blurLines(pixels, tmp, w, h, radius, horizontal = true)
        blurLines(tmp, pixels, w, h, radius, horizontal = false)
    }
}

private fun blurLines(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val len = if (horizontal) w else h
    val lines = if (horizontal) h else w
    val div = radius * 2 + 1
    for (line in 0 until lines) {
        fun idx(i: Int): Int = if (horizontal) line * w + i else i * w + line
        fun clamp(i: Int): Int = if (i < 0) 0 else if (i >= len) len - 1 else i
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val c = src[idx(clamp(i))]
            a += (c ushr 24) and 0xFF
            r += (c ushr 16) and 0xFF
            g += (c ushr 8) and 0xFF
            b += c and 0xFF
        }
        for (i in 0 until len) {
            dst[idx(i)] = ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            val outC = src[idx(clamp(i - radius))]
            val inC = src[idx(clamp(i + radius + 1))]
            a += ((inC ushr 24) and 0xFF) - ((outC ushr 24) and 0xFF)
            r += ((inC ushr 16) and 0xFF) - ((outC ushr 16) and 0xFF)
            g += ((inC ushr 8) and 0xFF) - ((outC ushr 8) and 0xFF)
            b += (inC and 0xFF) - (outC and 0xFF)
        }
    }
}

/** [renderBlurredBackdrop] 的进程内缓存：三张毛玻璃卡共用一张模糊背景 */
object GlassBackdropCache {
    /** 降采样倍数，与 [renderBlurredBackdrop] 的 downscale 参数保持一致 */
    const val DOWNSCALE = 3

    private var key = ""
    private var image: ImageBitmap? = null

    fun get(wPx: Int, hPx: Int, density: Density, blurRadiusPx: Float): ImageBitmap? {
        val k = wPx.toString() + "|" + hPx + "|" + blurRadiusPx.toInt() + "|" + Rpx.scale
        if (k == key) return image
        val rendered = renderBlurredBackdrop(wPx, hPx, density, blurRadiusPx, DOWNSCALE)
        key = k
        image = rendered
        return rendered
    }
}

private fun DrawScope.drawDotsTile(
    screenSize: Size,
    translation: Offset,
    tile: Float,
    fx: Float,
    fy: Float,
    alpha: Float,
    shiftPx: Float
) {
    if (tile <= 0f) return
    val radius = 2.dp.toPx()
    val color = Color.White.copy(alpha = alpha * 0.28f)
    var y = -tile
    while (y < screenSize.height + tile) {
        var x = -tile
        while (x < screenSize.width + tile) {
            drawCircle(color, radius, translation + Offset(x + tile * fx, y + tile * fy + shiftPx))
            x += tile
        }
        y += tile
    }
}

private fun DrawScope.drawBlob(center: Offset, radius: Float, color: Color, scale: Float) {
    val r = radius * scale
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0f)),
            center = center,
            radius = r
        ),
        radius = r,
        center = center
    )
}

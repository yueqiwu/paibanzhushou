package com.bilu.paibanzhushou.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow

/**
 * rpx -> dp 换算（750 设计稿宽度）。
 */
object Rpx {
    var scale: Float = 0.5f

    fun dp(value: Number): Dp = (value.toFloat() * scale).dp
    fun sp(value: Number): TextUnit = (value.toFloat() * scale).sp
}

/** 全局配色（与 uniapp 版本保持一致） */
object C {
    val pageBg = Color(0xFFF3FFFB)
    val textPrimary = Color(0xFF0B1020)
    val textSub = Color(0xFF7A8494)
    val textMuted = Color(0xFF9AA3B2)
    val textLabel = Color(0xFF444444)
    val textSection = Color(0xFF5B6B7F)
    val blue = Color(0xFF4A90E2)
    val green = Color(0xFF6AC695)
    val purple = Color(0xFF9C27B0)
    val yellow = Color(0xFFFFD166)
    val fieldBg = Color(0xFFF5F7FB)
    val cardWhite = Color(0xE0FFFFFF)
    val calendarTheme = Color(0xFF3C9CFF)
    val hint = Color(0xFF9AA3B2)
}

fun hexColor(hex: String?, fallback: Color = Color.Transparent): Color {
    val h = (hex ?: "").trim()
    if (h.isEmpty()) return fallback
    if (h.equals("transparent", ignoreCase = true)) return Color.Transparent
    if (!h.startsWith("#")) return fallback
    return try {
        when (h.length) {
            4 -> {
                val r = h[1].toString().toInt(16) * 17
                val g = h[2].toString().toInt(16) * 17
                val b = h[3].toString().toInt(16) * 17
                Color(0xFF000000.toInt() or (r shl 16) or (g shl 8) or b)
            }
            7 -> Color(0xFF000000.toInt() or h.substring(1).toLong(16).toInt())
            9 -> Color(h.substring(1).toLong(16).toInt())
            else -> fallback
        }
    } catch (t: Throwable) {
        fallback
    }
}

/** 毛玻璃风格卡片（uni 的 .card：半透明白 + 白边 + 柔和阴影） */
fun Modifier.glassCard(
    radius: Dp = Rpx.dp(28),
    bgAlpha: Float = 0.88f,
    borderAlpha: Float = 0.9f,
    elevation: Dp = Rpx.dp(8)
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(elevation, shape, clip = false, ambientColor = Color(0x14112034), spotColor = Color(0x14112034))
        .background(Color.White.copy(alpha = bgAlpha), shape)
        .border(Rpx.dp(1), Color.White.copy(alpha = borderAlpha), shape)
}

/** 虚线圆角边框 */
fun Modifier.dashedBorder(
    color: Color,
    radius: Dp,
    strokeWidth: Dp = Rpx.dp(1),
    dash: Dp = Rpx.dp(8),
    gap: Dp = Rpx.dp(5)
): Modifier = this.drawBehind {
    val path = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                0f, 0f, size.width, size.height,
                CornerRadius(radius.toPx(), radius.toPx())
            )
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()), 0f)
        )
    )
}

/** 渐变主按钮（uni .btn--primary 的 135deg 渐变） */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = Rpx.dp(64),
    radius: Dp = Rpx.dp(16),
    fontSize: TextUnit = Rpx.sp(24),
    fontWeight: FontWeight = FontWeight.Medium,
    colors: List<Color> = listOf(C.blue, C.green),
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = if (pressed) 0.98f else 1f
                scaleY = if (pressed) 0.98f else 1f
            }
            .background(Brush.linearGradient(colors), RoundedCornerShape(radius))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = Rpx.dp(28)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1)
    }
}

/** 通用小药丸按钮（ghost / outline 等） */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = Rpx.dp(64),
    radius: Dp = Rpx.dp(8),
    fontSize: TextUnit = Rpx.sp(24),
    textColor: Color = C.textPrimary,
    bgColor: Color = Color(0x0F000000),
    borderColor: Color? = null,
    fontWeight: FontWeight = FontWeight.Normal,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = if (pressed) 0.98f else 1f
                scaleY = if (pressed) 0.98f else 1f
            }
            .background(bgColor, RoundedCornerShape(radius))
            .then(
                if (borderColor != null) Modifier.border(Rpx.dp(1), borderColor, RoundedCornerShape(radius))
                else Modifier
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = Rpx.dp(18)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 输入框（uni 的 #f5f7fb 底、圆角 16rpx、高 64rpx） */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    fontSize: TextUnit = Rpx.sp(26),
    height: Dp = Rpx.dp(64),
    maxLength: Int = Int.MAX_VALUE,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .height(height)
            .background(C.fieldBg, RoundedCornerShape(Rpx.dp(16)))
            .border(Rpx.dp(1), Color(0x0A000000), RoundedCornerShape(Rpx.dp(16)))
            .padding(horizontal = Rpx.dp(20)),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = { v -> onValueChange(if (v.length > maxLength) v.take(maxLength) else v) },
            singleLine = true,
            textStyle = TextStyle(
                color = C.textPrimary,
                fontSize = fontSize,
                textAlign = textAlign
            ),
            cursorBrush = SolidColor(C.blue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                )
                .then(
                    if (onFocusChanged != null) Modifier.onFocusChanged { onFocusChanged(it.isFocused) }
                    else Modifier
                )
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Color(0xFFB4BCC8),
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 小号圆形关闭按钮（uni 的 × 圆底按钮） */
@Composable
fun RoundCloseButton(
    modifier: Modifier = Modifier,
    size: Dp = Rpx.dp(40),
    fontSize: TextUnit = Rpx.sp(28),
    bg: Color = Color(0x0F000000),
    fg: Color = C.textSection,
    onTap: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .background(if (pressed) Color(0xFFE53935) else bg, RoundedCornerShape(999.dp))
            .clickable(interactionSource = source, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "×",
            color = if (pressed) Color.White else fg,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Rpx.dp(2))
        )
    }
}

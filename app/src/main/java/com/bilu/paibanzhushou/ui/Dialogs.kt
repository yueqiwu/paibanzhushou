package com.bilu.paibanzhushou.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 确认弹窗，1:1 复刻 uni.showModal 的外观：
 * 圆角白卡 + 居中加粗标题 + 居中灰色正文 + 底部一分为二的双按钮（上方与中间各一条分隔线）。
 */
@Composable
fun ConfirmDialog(confirm: ConfirmState, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(0.78f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
            ) {
                Text(
                    text = confirm.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp)
                )
                Text(
                    text = confirm.content,
                    fontSize = 14.sp,
                    color = Color(0xFF808080),
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFEBEBEB))
                )
                Row(Modifier.fillMaxWidth().height(50.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", fontSize = 17.sp, color = Color(0xFF1A1A1A))
                    }
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxSize()
                            .background(Color(0xFFEBEBEB))
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                onDismiss()
                                confirm.onConfirm()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "确定",
                            fontSize = 17.sp,
                            color = Color(0xFF007AFF),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

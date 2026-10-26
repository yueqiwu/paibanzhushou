package com.bilu.paibanzhushou.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

/** uni.showToast 的替代：居中深色小提示，1.5s 自动消失 */
private class ToastHost {
    var text by mutableStateOf<String?>(null)
    var key by mutableIntStateOf(0)

    fun show(message: String) {
        if (message.isEmpty()) return
        text = message
        key += 1
    }

    fun hide() {
        text = null
    }
}

/**
 * 应用根节点：首页 / 同事排班表 两页切换 + 全局 toast。
 * rpx 以屏幕宽度为基准换算（750 设计宽），并锁定字体缩放，保证还原度。
 */
@Composable
fun AppRoot() {
    val configuration = LocalConfiguration.current
    Rpx.scale = configuration.screenWidthDp / 750f

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val toastHost = remember { ToastHost() }
    val indexState = remember { IndexState(scope, toastHost::show) }
    var route by remember { mutableStateOf("index") }

    LaunchedEffect(Unit) { indexState.init() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // 源版 onShow：跨天后刷新「今日」
                Lifecycle.Event.ON_RESUME -> indexState.onShow()
                // 源版 onHide：进后台前把去抖中的改动补写盘
                Lifecycle.Event.ON_PAUSE -> indexState.flush()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(toastHost.key) {
        val currentKey = toastHost.key
        if (toastHost.text != null) {
            delay(1500)
            if (toastHost.key == currentKey) toastHost.hide()
        }
    }

    // 锁定 fontScale：uni 的 rpx 不受系统字体大小影响
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Box(Modifier.fillMaxSize().background(C.pageBg)) {
            if (route == "member") {
                MemberTableScreen(onBack = {
                    indexState.onShow()
                    route = "index"
                })
                BackHandler {
                    indexState.onShow()
                    route = "index"
                }
            } else {
                IndexScreen(
                    state = indexState,
                    onOpenMemberTable = {
                        indexState.flush()
                        route = "member"
                    }
                )
            }

            AnimatedVisibility(
                visible = toastHost.text != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    Modifier
                        .widthIn(max = Rpx.dp(520))
                        .background(Color(0xB3111111), RoundedCornerShape(Rpx.dp(10)))
                        .padding(horizontal = Rpx.dp(30), vertical = Rpx.dp(22))
                ) {
                    Text(
                        text = toastHost.text ?: "",
                        color = Color.White,
                        fontSize = Rpx.sp(28),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

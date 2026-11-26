package com.bilu.paibanzhushou.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bilu.paibanzhushou.data.Migrate
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toastHost = remember { ToastHost() }
    val indexState = remember { IndexState(context.applicationContext, scope, toastHost::show) }
    var route by remember { mutableStateOf("index") }

    // 旧版迁移：深链带来的数据包（等用户确认后写入）
    // 存成 JSON 字符串：转屏/进程重建后待确认的包还在，深链早已被消费也不会丢
    var migratePayloadJson by rememberSaveable { mutableStateOf<String?>(null) }
    val migratePayload = Migrate.decodePayload(migratePayloadJson)
    // 首启引导：还装着旧版 + 原生版还是白纸 + 没迁移过 + 没点过「稍后」才提示
    var showMigrateGuide by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        indexState.init()
        if (Migrate.pendingLink == null &&
            Migrate.legacyInstalled(context) &&
            Migrate.nativeDataIsEmpty() &&
            !Migrate.alreadyMigrated() &&
            !Migrate.guideDismissed()
        ) {
            showMigrateGuide = true
        }
    }

    // 深链可能在应用已启动时才到（singleTask + onNewIntent），状态一变就弹确认框
    val pendingMigrateLink = Migrate.pendingLink
    LaunchedEffect(pendingMigrateLink) {
        val link = Migrate.consumeLink() ?: return@LaunchedEffect
        val payload = Migrate.parseLink(link)
        if (payload == null) {
            toastHost.show("迁移链接无法识别")
        } else {
            showMigrateGuide = false
            migratePayloadJson = Migrate.encodePayload(payload)
        }
    }

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
            // 页面切换：新页面从右侧滑入并淡入，旧页面往左微移淡出；返回时方向相反
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val dir = if (targetState == "index") -1 else 1
                    (slideInHorizontally(tween(300)) { w -> dir * (w / 3) } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(300)) { w -> -dir * (w / 6) } + fadeOut(tween(200)))
                },
                label = "route"
            ) { current ->
                when (current) {
                    "alarmSettings" -> {
                        AlarmSettingsScreen(
                            state = indexState,
                            onBack = {
                                indexState.onShow()
                                route = "index"
                            },
                            onOpenAlarmList = {
                                indexState.flush()
                                route = "alarms"
                            }
                        )
                        BackHandler {
                            indexState.onShow()
                            route = "index"
                        }
                    }
                    "alarms" -> {
                        AlarmListScreen(state = indexState, onBack = {
                            indexState.onShow()
                            route = "index"
                        })
                        BackHandler {
                            indexState.onShow()
                            route = "index"
                        }
                    }
                    "member" -> {
                        MemberTableScreen(onBack = {
                            indexState.onShow()
                            route = "index"
                        })
                        BackHandler {
                            indexState.onShow()
                            route = "index"
                        }
                    }
                    else -> IndexScreen(
                        state = indexState,
                        onOpenMemberTable = {
                            indexState.flush()
                            route = "member"
                        },
                        onOpenAlarmSettings = {
                            indexState.flush()
                            route = "alarmSettings"
                        }
                    )
                }
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

            // 旧版迁移确认：深链 / 剪贴板解析成功后都走这里
            val payload = migratePayload
            if (payload != null) {
                AlertDialog(
                    onDismissRequest = { migratePayloadJson = null },
                    title = { Text("导入旧版数据") },
                    text = {
                        Text(
                            "检测到 " + Migrate.describe(payload) +
                                "。导入会覆盖当前数据，旧版里的内容不会被删除。",
                            fontSize = Rpx.sp(26)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val result = Migrate.apply(payload)
                                migratePayloadJson = null
                                indexState.reloadAfterMigrate()
                                toastHost.show(result.message)
                            }
                        ) {
                            Text("导入")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { migratePayloadJson = null }) {
                            Text("取消")
                        }
                    }
                )
            }

            // 首启引导：从系统安装器直接打开新版（没带深链）时，告诉用户回旧版点迁移
            if (showMigrateGuide) {
                AlertDialog(
                    onDismissRequest = {
                        Migrate.dismissGuide()
                        showMigrateGuide = false
                    },
                    title = { Text("把旧版数据带过来") },
                    text = {
                        Text(
                            "检测到还装着旧版排班助手。打开旧版，连点最下面版权信息 7 下，" +
                                "选「打开原生版并导入」；也可以先在旧版里复制迁移数据，再点下面的按钮。",
                            fontSize = Rpx.sp(26)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val fromClipboard = Migrate.parseClipboard(context)
                                if (fromClipboard == null) {
                                    toastHost.show("剪贴板里没有迁移数据")
                                } else {
                                    showMigrateGuide = false
                                    migratePayloadJson = Migrate.encodePayload(fromClipboard)
                                }
                            }
                        ) {
                            Text("从剪贴板导入")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                Migrate.dismissGuide()
                                showMigrateGuide = false
                            }
                        ) {
                            Text("稍后")
                        }
                    }
                )
            }
        }
    }
}

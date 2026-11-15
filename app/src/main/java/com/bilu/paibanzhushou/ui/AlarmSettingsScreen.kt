package com.bilu.paibanzhushou.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bilu.paibanzhushou.core.Schedule
import com.bilu.paibanzhushou.data.AlarmRingtones

/**
 * 闹钟提醒设置页：把原先塞在首页「排班设置 → 展开」里的闹钟设置与权限整体搬到这里，
 * 顶部栏右侧的闹钟按钮进入；页面顶部可以选「为谁写闹钟」。
 *
 * 选人员 = 直接切首页当前人员（写闹钟的人就是首页正在看的人，避免两处状态打架），
 * 所以这里切人会和首页联动。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmSettingsScreen(
    state: IndexState,
    onBack: () -> Unit,
    onOpenAlarmList: () -> Unit
) {
    val context = LocalContext.current
    var showAlarmTime by remember { mutableStateOf(false) }
    var showRingtone by remember { mutableStateOf(false) }
    val config = state.alarmConfig
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        state.setAlarmEnabled(true)
        if (!granted) state.toastMsg("没给通知权限，闹钟到点可能弹不出来")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.pageBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Rpx.dp(40))
    ) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Rpx.dp(20), vertical = Rpx.dp(10)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(Rpx.dp(56))
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Chevron(dir = ChevronDir.LEFT, color = Color(0xFF333333), size = Rpx.dp(20))
            }
            Text(
                text = "闹钟提醒",
                fontSize = Rpx.sp(34),
                fontWeight = FontWeight.SemiBold,
                color = C.textPrimary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.size(Rpx.dp(56)))
        }

        // 为谁写闹钟
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Rpx.dp(24)),
            radius = Rpx.dp(24),
            bgAlpha = 0.72f,
            contentPadding = PaddingValues(horizontal = Rpx.dp(22), vertical = Rpx.dp(18))
        ) {
            Text(
                text = "为谁写闹钟",
                fontSize = Rpx.sp(28),
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111)
            )
            Text(
                text = "换人等于切首页当前人员，首页日历会跟着换",
                fontSize = Rpx.sp(21),
                color = C.hint,
                lineHeight = Rpx.sp(30),
                modifier = Modifier.padding(top = Rpx.dp(6))
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = Rpx.dp(14)),
                horizontalArrangement = Arrangement.spacedBy(Rpx.dp(12)),
                verticalArrangement = Arrangement.spacedBy(Rpx.dp(12))
            ) {
                state.people.forEach { person ->
                    val active = person.id == state.activePersonId
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) Color(0x1A4A90E2) else Color(0x0DFFFFFF))
                            .border(
                                Rpx.dp(1),
                                if (active) Color(0x664A90E2) else Color(0x14000000),
                                RoundedCornerShape(999.dp)
                            )
                            .clickable { state.setActivePerson(person.id) }
                            .padding(horizontal = Rpx.dp(22), vertical = Rpx.dp(10)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = person.name,
                            fontSize = Rpx.sp(24),
                            color = if (active) Color(0xFF2B6CB0) else Color(0xFF444444),
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 闹钟设置
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Rpx.dp(24), end = Rpx.dp(24), top = Rpx.dp(16)),
            radius = Rpx.dp(24),
            bgAlpha = 0.72f,
            contentPadding = PaddingValues(horizontal = Rpx.dp(22), vertical = Rpx.dp(16))
        ) {
            SectionLabel("闹钟提醒", topMargin = Rpx.dp(6))

            val choices = state.banOptions.filter { it != Schedule.REST_BAN }
            if (choices.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = Rpx.dp(14)),
                    horizontalArrangement = Arrangement.spacedBy(Rpx.dp(12)),
                    verticalArrangement = Arrangement.spacedBy(Rpx.dp(12))
                ) {
                    choices.forEach { ban ->
                        val active = config.bans.contains(ban)
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) Color(0x1A4A90E2) else C.fieldBg)
                                .border(
                                    Rpx.dp(1),
                                    if (active) Color(0x664A90E2) else Color(0x0D000000),
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable { state.toggleAlarmBan(ban) }
                                .padding(horizontal = Rpx.dp(18), vertical = Rpx.dp(8)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(Rpx.dp(16))
                                    .background(hexColor(state.banColor(ban)), CircleShape)
                            )
                            Text(
                                text = ban,
                                fontSize = Rpx.sp(24),
                                color = if (active) Color(0xFF2B6CB0) else Color(0xFF333333),
                                modifier = Modifier.padding(start = Rpx.dp(10))
                            )
                        }
                    }
                }
            }
            Text(
                text = "选中的班次会在当天按下面的时间响铃",
                fontSize = Rpx.sp(21),
                color = C.hint,
                lineHeight = Rpx.sp(30),
                modifier = Modifier.padding(top = Rpx.dp(10))
            )

            // 响铃时间
            Row(
                Modifier.fillMaxWidth().padding(top = Rpx.dp(18)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("响铃时间", fontSize = Rpx.sp(26), color = C.textLabel)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        Modifier
                            .background(C.fieldBg, RoundedCornerShape(Rpx.dp(16)))
                            .border(Rpx.dp(1), Color(0x0A000000), RoundedCornerShape(Rpx.dp(16)))
                            .clickable { showAlarmTime = true }
                            .padding(horizontal = Rpx.dp(20))
                            .height(Rpx.dp(64)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = config.timeText,
                            fontSize = Rpx.sp(26),
                            color = Color(0xFF111111)
                        )
                    }
                }
            }

            // 闹钟铃声：App 内自选（可跟随系统、也可选手机里任意铃声，或只震动）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Rpx.dp(18))
                    .clip(RoundedCornerShape(Rpx.dp(12)))
                    .clickable { showRingtone = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("闹钟铃声", fontSize = Rpx.sp(26), color = C.textLabel)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        text = AlarmRingtones.label(context, config.ringtone) + " ›",
                        fontSize = Rpx.sp(24),
                        color = C.blue
                    )
                }
            }
            Text(
                text = "点这里换铃声：可以选系统闹钟铃声、手机里的其它铃声，或「静音」只震动",
                fontSize = Rpx.sp(20),
                color = C.hint,
                lineHeight = Rpx.sp(28),
                modifier = Modifier.padding(top = Rpx.dp(6))
            )

            // 开关
            Row(
                Modifier.fillMaxWidth().padding(top = Rpx.dp(18)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "自动写入系统闹钟",
                    fontSize = Rpx.sp(26),
                    color = C.textLabel,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            state.setAlarmEnabled(false)
                        } else if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            state.setAlarmEnabled(true)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = C.blue
                    )
                )
            }
            Text(
                text = if (config.enabled) {
                    "已写入 " + state.alarmCount + " 个闹钟（未来 " + config.horizonDays +
                        " 天）；改排班会自动重写"
                } else {
                    "开启后把未来 " + config.horizonDays + " 天里这些班次的日子写成系统闹钟"
                },
                fontSize = Rpx.sp(21),
                color = C.hint,
                lineHeight = Rpx.sp(32),
                modifier = Modifier.padding(top = Rpx.dp(8))
            )

            if (config.enabled) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Rpx.dp(14))
                        .clip(RoundedCornerShape(Rpx.dp(14)))
                        .background(Color(0x0A4A90E2))
                        .clickable(onClick = onOpenAlarmList)
                        .padding(horizontal = Rpx.dp(18), vertical = Rpx.dp(16))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "查看闹钟列表，可单独取消某一天",
                            fontSize = Rpx.sp(23),
                            color = C.blue,
                            modifier = Modifier.weight(1f)
                        )
                        Text("›", fontSize = Rpx.sp(30), color = C.blue)
                    }
                }
            }

            // 权限
            SectionLabel("闹钟权限", topMargin = Rpx.dp(28))
            Text(
                text = "下面这些开关决定闹钟能不能准点响、能不能亮屏弹出响铃页",
                fontSize = Rpx.sp(21),
                color = C.hint,
                lineHeight = Rpx.sp(30),
                modifier = Modifier.padding(top = Rpx.dp(8))
            )
            val perms = state.alarmPermissions
            val missing = perms.count { !it.ok }
            Text(
                text = if (missing == 0) "全部已开启，闹钟可以正常响" else "还有 " + missing + " 项没开启",
                fontSize = Rpx.sp(22),
                color = if (missing == 0) Color(0xFF43A047) else Color(0xFFE53935),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = Rpx.dp(10))
            )
            perms.forEach { perm ->
                AlarmPermissionRow(perm) { state.openAlarmPermission(perm.id) }
            }
            // 手机侧的勿扰：系统默认把「闹钟」列为优先级例外，App 的响铃走 USAGE_ALARM，一般不会被静音；
            // 若某些机型屏蔽了，需要去系统「勿扰」里允许闹钟
            Text(
                text = "手机勿扰 / 睡眠模式：系统默认把闹钟列为优先级例外，闹钟会照常响；如果你的机型连闹钟一起屏蔽了，" +
                    "请在系统「设置 → 声音与振动 → 勿扰」里把「闹钟」勾上。",
                fontSize = Rpx.sp(20),
                color = C.hint,
                lineHeight = Rpx.sp(30),
                modifier = Modifier.padding(top = Rpx.dp(14))
            )
            // 手表端是独立固件：睡眠模式由手表自己决定要不要静音，App 只能把通知按「闹钟」类别发出去
            Text(
                text = "手表 / 手环：手表开启睡眠模式后会自行屏蔽通知。需要在手表的「睡眠模式 / 勿扰」里把「班表小历」加进允许提醒（或允许闹钟），" +
                    "手机这边的闹钟不受影响。",
                fontSize = Rpx.sp(20),
                color = C.hint,
                lineHeight = Rpx.sp(30),
                modifier = Modifier.padding(top = Rpx.dp(14))
            )
        }

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    AlarmTimePickerSheet(
        show = showAlarmTime,
        hour = config.hour,
        minute = config.minute,
        onDismiss = { showAlarmTime = false },
        onPick = { h, m ->
            showAlarmTime = false
            state.setAlarmTime(h, m)
        }
    )
    RingtonePickerSheet(
        show = showRingtone,
        current = config.ringtone,
        onDismiss = { showRingtone = false },
        onPick = { value ->
            showRingtone = false
            state.setAlarmRingtone(value)
        }
    )
}

/** 一项闹钟权限：标题 + 说明 + 「已开启 / 去设置」 */
@Composable
private fun AlarmPermissionRow(perm: AlarmPermission, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Rpx.dp(12))
            .clip(RoundedCornerShape(Rpx.dp(16)))
            .background(Color(0x08000000))
            .padding(horizontal = Rpx.dp(16), vertical = Rpx.dp(14)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = perm.title,
                fontSize = Rpx.sp(24),
                color = Color(0xFF333333),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = perm.desc,
                fontSize = Rpx.sp(21),
                color = C.hint,
                lineHeight = Rpx.sp(30),
                modifier = Modifier.padding(top = Rpx.dp(4))
            )
        }
        if (perm.ok) {
            Text(
                text = "已开启",
                fontSize = Rpx.sp(22),
                color = Color(0xFF43A047),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = Rpx.dp(12))
            )
        } else {
            Box(
                Modifier
                    .padding(start = Rpx.dp(12))
                    .clip(RoundedCornerShape(999.dp))
                    .background(C.blue)
                    .clickable { onOpen() }
                    .padding(horizontal = Rpx.dp(18), vertical = Rpx.dp(9))
            ) {
                Text("去设置", fontSize = Rpx.sp(22), color = Color.White)
            }
        }
    }
}

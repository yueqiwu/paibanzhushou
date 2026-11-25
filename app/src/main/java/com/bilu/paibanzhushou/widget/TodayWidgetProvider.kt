package com.bilu.paibanzhushou.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.bilu.paibanzhushou.MainActivity
import com.bilu.paibanzhushou.R
import com.bilu.paibanzhushou.core.BanColors

/**
 * 桌面小组件（今日）：2×1 卡片，只回答一件事 ——「现在轮到谁、今天上什么班」。
 *
 * 和另两张卡的区别：
 * - 4×2 大卡（[ScheduleWidgetProvider]）是 7 天日历网格，信息全但要占 4×2；
 * - 2×1 小卡（[ScheduleCompactWidgetProvider]）是今天起 7 天的日号圆圈，看趋势；
 * - 这张（今日）只放今天的班次胶囊 + 明天一句预告，一眼就能看到今天该不该早起。
 *
 * 数据仍走 [WidgetData]（同一个人、同一份排班），班次色用预置 `pill_<hex>`：
 * RemoteViews 的 setBackgroundColor 会把胶囊圆角抹掉，圆角只能靠 drawable。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ScheduleWidgetProvider.ACTION_REFRESH) refreshAll(context)
    }

    companion object {
        /** 班次配色 → 预置胶囊 drawable */
        private val PILLS = mapOf(
            "4A90E2" to R.drawable.pill_4a90e2,
            "6AC695" to R.drawable.pill_6ac695,
            "D81B60" to R.drawable.pill_d81b60,
            "9C27B0" to R.drawable.pill_9c27b0,
            "FFD166" to R.drawable.pill_ffd166,
            "E53935" to R.drawable.pill_e53935,
            "FB8C00" to R.drawable.pill_fb8c00,
            "F9A825" to R.drawable.pill_f9a825,
            "C0CA33" to R.drawable.pill_c0ca33,
            "43A047" to R.drawable.pill_43a047,
            "00897B" to R.drawable.pill_00897b,
            "00ACC1" to R.drawable.pill_00acc1,
            "3949AB" to R.drawable.pill_3949ab,
            "6D4C41" to R.drawable.pill_6d4c41
        )

        private const val MUTED = "#7A8494"

        fun refresh(context: Context) {
            runCatching {
                context.sendBroadcast(
                    Intent(ScheduleWidgetProvider.ACTION_REFRESH).setPackage(context.packageName)
                )
            }
        }

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching {
                mgr.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            }.getOrDefault(IntArray(0))
            ids.forEach { render(context, mgr, it) }
        }

        private fun hexOf(ban: String): String =
            runCatching { BanColors.get(ban) }.getOrDefault("")
                .trim().removePrefix("#").uppercase()

        /** 浅色底（休息的黄、调度的黄绿等）用深色字，其余用白字 */
        private fun textColor(hex: String): Int {
            if (hex.length != 6) return Color.parseColor(MUTED)
            val r = hex.substring(0, 2).toIntOrNull(16) ?: 255
            val g = hex.substring(2, 4).toIntOrNull(16) ?: 255
            val b = hex.substring(4, 6).toIntOrNull(16) ?: 255
            val luma = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            return if (luma > 0.66) Color.parseColor("#4A3B00") else Color.WHITE
        }

        /** 2026-11-17 → 「11月17日 周二」；日期串异常时退回「17日」+ 组件的 weekday */
        private fun dateLabel(day: WidgetData.Day?): String {
            if (day == null) return ""
            val parts = day.ymd.split("-")
            val month = parts.getOrNull(1)?.toIntOrNull()
            val dom = parts.getOrNull(2)?.toIntOrNull() ?: day.dayOfMonth
            val md = if (month == null) "${dom}日" else "${month}月${dom}日"
            return "$md ${WidgetData.weekdayText(day.weekday)}"
        }

        /** 供预览/调试复用：只负责把数据绑进 RemoteViews，不负责下发 */
        internal fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val week = runCatching { WidgetData.load(context) }.getOrNull()
            val today = week?.days?.getOrNull(0)
            val tomorrow = week?.days?.getOrNull(1)
            // 还没有人员时 WidgetData 给的是空班次串，别显示成「一个空的胶囊」
            val hasSchedule = week?.days?.any { it.ban.isNotBlank() } ?: false

            views.setTextViewText(R.id.t_person, week?.personName.orEmpty())
            views.setTextViewText(R.id.t_date, dateLabel(today))

            val ban = today?.ban.orEmpty()
            val hex = if (ban.isBlank()) "" else hexOf(ban)
            views.setTextViewText(R.id.t_ban, ban.ifBlank { "—" })
            views.setInt(
                R.id.t_ban,
                "setBackgroundResource",
                if (hex.isBlank()) R.drawable.pill_none else PILLS[hex] ?: R.drawable.pill_none
            )
            views.setTextColor(R.id.t_ban, textColor(hex))

            val nextBan = tomorrow?.ban.orEmpty()
            val nextText = when {
                !hasSchedule -> ""
                nextBan.isBlank() -> "明天 待定"
                else -> "明天 $nextBan"
            }
            views.setTextViewText(R.id.t_next, nextText)

            val pi = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.t_root, pi)
            return views
        }

        private fun render(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = runCatching { buildViews(context) }.getOrNull() ?: return
            runCatching { mgr.updateAppWidget(widgetId, views) }
        }
    }
}

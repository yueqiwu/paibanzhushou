package com.bilu.paibanzhushou.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.bilu.paibanzhushou.MainActivity
import com.bilu.paibanzhushou.R
import com.bilu.paibanzhushou.core.BanColors

/**
 * 桌面小组件（今日）：2×1 卡片，回答一件事 ——「所有排班人员今天各上什么班」。
 *
 * 和另两张卡的区别：
 * - 4×2 大卡（[ScheduleWidgetProvider]）是某个人的 7 天日历网格，信息全但要占 4×2；
 * - 2×1 小卡（[ScheduleCompactWidgetProvider]）是某个人今天起 7 天的日号圆圈，看趋势；
 * - 这张（今日）一屏放全员：每人一列（班次胶囊 + 姓名），一眼看完今天谁上班谁休息。
 *
 * 数据走 [WidgetData.loadToday]（全员，顺序 = 首页人员标签顺序），班次色用预置 `pill_<hex>`：
 * RemoteViews 的 setBackgroundColor 会把胶囊圆角抹掉，圆角只能靠 drawable。
 * 列是运行时用 [RemoteViews.addView] 塞进 `t_row` 的，最多 [MAX_COLUMNS] 列，多出来折成「+N」。
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

        /** 一屏最多显示几列（2×1 约 170dp 宽、去掉左右内边距剩 150dp，5 列是能看清字的上限） */
        private const val MAX_COLUMNS = 5

        /**
         * 列内胶囊的字号/左右内边距跟着人数缩放：人少时放大显得舒展，5 个人时收到 8.5sp + 2dp，
         * 这样连「大夜班」这种三字班次也能塞进 30dp 的列宽，不会被截成「大夜…」。
         */
        private fun banStyle(count: Int): Pair<Float, Int> = when {
            count <= 2 -> 11f to 8
            count == 3 -> 10.5f to 7
            count == 4 -> 9.5f to 5
            else -> 8.5f to 2
        }

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

        /** 一个人一列：上=班次胶囊（班次色），下=姓名 */
        private fun columnView(
            context: Context,
            name: String,
            ban: String,
            banSize: Float,
            padDp: Int,
            nameSize: Float
        ): RemoteViews {
            val col = RemoteViews(context.packageName, R.layout.widget_today_item)
            val hex = if (ban.isBlank()) "" else hexOf(ban)
            col.setTextViewText(R.id.i_ban, ban.ifBlank { "—" })
            col.setInt(
                R.id.i_ban,
                "setBackgroundResource",
                if (hex.isBlank()) R.drawable.pill_none else PILLS[hex] ?: R.drawable.pill_none
            )
            col.setTextColor(R.id.i_ban, textColor(hex))
            col.setTextViewTextSize(R.id.i_ban, TypedValue.COMPLEX_UNIT_SP, banSize)
            val d = context.resources.displayMetrics.density
            col.setViewPadding(
                R.id.i_ban,
                (padDp * d).toInt(),
                (2 * d).toInt(),
                (padDp * d).toInt(),
                (2 * d).toInt()
            )
            col.setTextViewText(R.id.i_name, name)
            col.setTextViewTextSize(R.id.i_name, TypedValue.COMPLEX_UNIT_SP, nameSize)
            return col
        }

        /** 人太多时的收尾列：灰色「+N」，姓名位留空（只表示还有多少人没显示） */
        private fun moreView(context: Context, more: Int): RemoteViews {
            val col = RemoteViews(context.packageName, R.layout.widget_today_item)
            col.setTextViewText(R.id.i_ban, "+$more")
            col.setInt(R.id.i_ban, "setBackgroundResource", R.drawable.pill_none)
            col.setTextColor(R.id.i_ban, Color.parseColor(MUTED))
            col.setTextViewTextSize(R.id.i_ban, TypedValue.COMPLEX_UNIT_SP, 8.5f)
            col.setTextViewText(R.id.i_name, "")
            return col
        }

        /** 供预览/调试复用：只负责把数据绑进 RemoteViews，不负责下发 */
        internal fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val board = runCatching { WidgetData.loadToday(context) }.getOrNull()
            val people = board?.people.orEmpty()

            views.setTextViewText(R.id.t_date, board?.dateLabel.orEmpty())
            views.removeAllViews(R.id.t_row)

            if (people.isEmpty()) {
                views.setViewVisibility(R.id.t_row, View.GONE)
                views.setViewVisibility(R.id.t_empty, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.t_row, View.VISIBLE)
                views.setViewVisibility(R.id.t_empty, View.GONE)
                val shown = if (people.size <= MAX_COLUMNS) people else people.take(MAX_COLUMNS - 1)
                val (banSize, padDp) = banStyle(people.size)
                val nameSize = if (people.size >= MAX_COLUMNS) 8f else 8.5f
                shown.forEach { p ->
                    views.addView(
                        R.id.t_row,
                        columnView(context, p.name, p.ban, banSize, padDp, nameSize)
                    )
                }
                val hidden = people.size - shown.size
                if (hidden > 0) views.addView(R.id.t_row, moreView(context, hidden))
            }

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

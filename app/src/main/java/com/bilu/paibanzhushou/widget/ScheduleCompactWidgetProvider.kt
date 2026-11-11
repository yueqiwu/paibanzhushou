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
 * 桌面小组件（小号）：2×1 卡片，一行标题 + 7 个日号圆圈。
 *
 * 与 4×2 大卡（[ScheduleWidgetProvider]）相比，这里把「星期 + 日号 + 班次文字」
 * 压成一个圆圈：圆内是日号、圆底是当天的班次色，今天额外套一圈深色描边。
 * 这样 2×1 的窄条也能一眼看完「今天起 7 天上什么班」。
 *
 * 班次色块同样用预置 drawable（RemoteViews 的 setBackgroundColor 会抹掉圆形，
 * 圆角/圆形只能靠 drawable），并按底色明暗自动切换白字/深字保证可读。
 */
class ScheduleCompactWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ScheduleWidgetProvider.ACTION_REFRESH) refreshAll(context)
    }

    companion object {
        private val DATE_IDS = intArrayOf(
            R.id.c_date0, R.id.c_date1, R.id.c_date2, R.id.c_date3,
            R.id.c_date4, R.id.c_date5, R.id.c_date6
        )

        /** 班次配色 → 预置圆形 drawable */
        private val CIRCLES = mapOf(
            "4A90E2" to R.drawable.circle_4a90e2,
            "6AC695" to R.drawable.circle_6ac695,
            "D81B60" to R.drawable.circle_d81b60,
            "9C27B0" to R.drawable.circle_9c27b0,
            "FFD166" to R.drawable.circle_ffd166,
            "E53935" to R.drawable.circle_e53935,
            "FB8C00" to R.drawable.circle_fb8c00,
            "F9A825" to R.drawable.circle_f9a825,
            "C0CA33" to R.drawable.circle_c0ca33,
            "43A047" to R.drawable.circle_43a047,
            "00897B" to R.drawable.circle_00897b,
            "00ACC1" to R.drawable.circle_00acc1,
            "3949AB" to R.drawable.circle_3949ab,
            "6D4C41" to R.drawable.circle_6d4c41
        )

        /** 今天用的「带深色描边」版本，保证保留班次色同时能看出是今天 */
        private val CIRCLES_TODAY = mapOf(
            "4A90E2" to R.drawable.circle_today_4a90e2,
            "6AC695" to R.drawable.circle_today_6ac695,
            "D81B60" to R.drawable.circle_today_d81b60,
            "9C27B0" to R.drawable.circle_today_9c27b0,
            "FFD166" to R.drawable.circle_today_ffd166,
            "E53935" to R.drawable.circle_today_e53935,
            "FB8C00" to R.drawable.circle_today_fb8c00,
            "F9A825" to R.drawable.circle_today_f9a825,
            "C0CA33" to R.drawable.circle_today_c0ca33,
            "43A047" to R.drawable.circle_today_43a047,
            "00897B" to R.drawable.circle_today_00897b,
            "00ACC1" to R.drawable.circle_today_00acc1,
            "3949AB" to R.drawable.circle_today_3949ab,
            "6D4C41" to R.drawable.circle_today_6d4c41
        )

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
                mgr.getAppWidgetIds(ComponentName(context, ScheduleCompactWidgetProvider::class.java))
            }.getOrDefault(IntArray(0))
            ids.forEach { render(context, mgr, it) }
        }

        private fun hexOf(ban: String): String =
            runCatching { BanColors.get(ban) }.getOrDefault("")
                .trim().removePrefix("#").uppercase()

        /** 浅色底（休息的黄、调度的黄绿等）用深色数字，其余用白字 */
        private fun textColor(hex: String): Int {
            if (hex.length != 6) return Color.parseColor("#7A8494")
            val r = hex.substring(0, 2).toIntOrNull(16) ?: 255
            val g = hex.substring(2, 4).toIntOrNull(16) ?: 255
            val b = hex.substring(4, 6).toIntOrNull(16) ?: 255
            val luma = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            return if (luma > 0.66) Color.parseColor("#4A3B00") else Color.WHITE
        }

        /** 供预览/调试复用：只负责把数据绑进 RemoteViews，不负责下发 */
        internal fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_compact)
            val week = runCatching { WidgetData.load(context) }.getOrNull()

            views.setTextViewText(R.id.c_month, week?.monthLabel.orEmpty())
            views.setTextViewText(R.id.c_person, week?.personName.orEmpty())

            for (i in 0..6) {
                val day = week?.days?.getOrNull(i)
                views.setTextViewText(DATE_IDS[i], day?.dayOfMonth?.toString().orEmpty())
                val ban = day?.ban.orEmpty()
                val hex = if (ban.isBlank()) "" else hexOf(ban)
                val bg = if (hex.isBlank()) {
                    R.drawable.circle_none
                } else if (i == 0) {
                    CIRCLES_TODAY[hex] ?: R.drawable.circle_none
                } else {
                    CIRCLES[hex] ?: R.drawable.circle_none
                }
                views.setInt(DATE_IDS[i], "setBackgroundResource", bg)
                views.setTextColor(DATE_IDS[i], textColor(hex))
            }

            val pi = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.c_root, pi)
            return views
        }

        private fun render(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = runCatching { buildViews(context) }.getOrNull() ?: return
            runCatching { mgr.updateAppWidget(widgetId, views) }
        }
    }
}

package com.bilu.paibanzhushou.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import com.bilu.paibanzhushou.MainActivity
import com.bilu.paibanzhushou.R
import com.bilu.paibanzhushou.core.BanColors
import com.bilu.paibanzhushou.core.Dates
import com.bilu.paibanzhushou.core.Schedule
import com.bilu.paibanzhushou.data.HolidayRepo
import com.bilu.paibanzhushou.data.PeopleStore
import java.util.Calendar

/**
 * 桌面小组件：2:1 卡片，横向排 7 天（今天在最左），显示当前人员「今天起 7 天」的班次。
 *
 * 设计说明：
 * - 卡面用与 App 同一套浅色渐变 + 白色描边圆角，像一张「玻璃卡」；
 * - 头部一行：渐变圆点 + 应用名 + 当前月份 + 当前人员；
 * - 7 列：星期 / 日号 / 班次胶囊（圆角药丸，颜色沿用 App 的班次配色）；
 * - 今天那一列固定在最左，带淡蓝圆角底和蓝色文字，和 App 内「选中今天」呼应。
 *
 * 为什么班次色块用「14 个预置圆角 drawable」而不是 setBackgroundColor：
 * RemoteViews 里 setBackgroundColor 会替换掉背景 drawable，圆角就丢了；班次颜色
 * 只会来自 BanColors 里固定的 14 个色值，所以给每个色值预生成一张圆角药丸图，
 * 运行时按色值查 drawable 即可，既保圆角又不增加额外开销。
 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refreshAll(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.bilu.paibanzhushou.action.WIDGET_REFRESH"

        private val DAY_IDS = intArrayOf(
            R.id.w_day0, R.id.w_day1, R.id.w_day2, R.id.w_day3,
            R.id.w_day4, R.id.w_day5, R.id.w_day6
        )
        private val DATE_IDS = intArrayOf(
            R.id.w_date0, R.id.w_date1, R.id.w_date2, R.id.w_date3,
            R.id.w_date4, R.id.w_date5, R.id.w_date6
        )
        private val BAN_IDS = intArrayOf(
            R.id.w_ban0, R.id.w_ban1, R.id.w_ban2, R.id.w_ban3,
            R.id.w_ban4, R.id.w_ban5, R.id.w_ban6
        )
        private val WEEK = arrayOf("日", "一", "二", "三", "四", "五", "六")

        /** 班次配色 → 预置圆角药丸 drawable（BanColors 只可能产出这些色值） */
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

        /** 排班数据变化后调用：给自己发广播，让所有组件刷新 */
        fun refresh(context: Context) {
            runCatching {
                context.sendBroadcast(Intent(ACTION_REFRESH).setPackage(context.packageName))
            }
        }

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching {
                mgr.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
            }.getOrDefault(IntArray(0))
            ids.forEach { render(context, mgr, it) }
        }

        private fun pillFor(ban: String): Int {
            val hex = runCatching { BanColors.get(ban) }.getOrDefault("")
                .trim().removePrefix("#").uppercase()
            return PILLS[hex] ?: R.drawable.pill_none
        }

        private fun dayOfWeek(date: String): Int {
            val parts = date.split("-")
            val cal = Calendar.getInstance()
            cal.set(
                parts.getOrNull(0)?.toIntOrNull() ?: 2000,
                ((parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1),
                parts.getOrNull(2)?.toIntOrNull() ?: 1
            )
            return cal.get(Calendar.DAY_OF_WEEK) - 1
        }

        private fun dayNumber(date: String): String =
            (date.substringAfterLast("-").toIntOrNull() ?: 0).toString()

        /** 供预览/调试复用：只负责把数据绑进 RemoteViews，不负责下发 */
        internal fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)
            val today = Dates.todayStart()
            val todayStr = Dates.formatYmd(today)
            val people = runCatching {
                PeopleStore.loadPeople(todayStr, listOf("白班", "中班", "休息", "休息"))
            }.getOrDefault(emptyList())
            val activeId = runCatching { PeopleStore.loadActivePersonId() }.getOrNull()
            val person = people.firstOrNull { it.id == activeId } ?: people.firstOrNull()

            views.setTextViewText(R.id.w_month, (todayStr.substring(5, 7).toIntOrNull() ?: 1).toString() + "月")

            if (person == null) {
                views.setTextViewText(R.id.w_person, "还没有人员")
                for (i in 0..6) {
                    views.setTextViewText(DAY_IDS[i], if (i == 0) "今天" else "周" + WEEK[dayOfWeek(Dates.formatYmd(Dates.addDays(today, i)))])
                    views.setTextViewText(DATE_IDS[i], dayNumber(Dates.formatYmd(Dates.addDays(today, i))))
                    views.setTextViewText(BAN_IDS[i], "")
                    views.setInt(BAN_IDS[i], "setBackgroundResource", R.drawable.pill_none)
                }
            } else {
                val cache = runCatching { HolidayRepo.loadCache() }.getOrNull()
                val endMs = Dates.addDays(today, 6)
                val list = runCatching {
                    Schedule.buildSchedule(
                        cycleBans = person.cycleBans,
                        anchorDate = person.anchorDate,
                        startMs = today,
                        endMs = endMs,
                        holiday = cache?.holiday ?: emptyList(),
                        banList = cache?.banList ?: emptyList(),
                        overrides = person.overrides,
                        weekendRestBans = person.weekendRestBans,
                        holidayRestBans = person.holidayRestBans,
                        fallbackAnchorMs = today
                    )
                }.getOrDefault(emptyList())

                views.setTextViewText(R.id.w_person, person.name)
                for (i in 0..6) {
                    val dateStr = Dates.formatYmd(Dates.addDays(today, i))
                    views.setTextViewText(DAY_IDS[i], if (i == 0) "今天" else "周" + WEEK[dayOfWeek(dateStr)])
                    views.setTextViewText(DATE_IDS[i], dayNumber(dateStr))
                    val ban = list.getOrNull(i)?.info.orEmpty()
                    val text = ban
                    views.setTextViewText(BAN_IDS[i], text)
                    views.setInt(BAN_IDS[i], "setBackgroundResource", if (ban.isBlank()) R.drawable.pill_none else pillFor(ban))
                    views.setTextViewTextSize(
                        BAN_IDS[i],
                        TypedValue.COMPLEX_UNIT_SP,
                        if (text.length >= 3) 8f else 9f
                    )
                }
            }

            val pi = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.w_root, pi)
            return views
        }

        private fun render(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = runCatching { buildViews(context) }.getOrNull() ?: return
            runCatching { mgr.updateAppWidget(widgetId, views) }
        }
    }
}
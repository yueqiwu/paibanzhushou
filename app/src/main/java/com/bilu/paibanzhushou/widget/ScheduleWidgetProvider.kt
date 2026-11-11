package com.bilu.paibanzhushou.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
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
 * 桌面小组件：显示当前人员「今天起 7 天」的班次。
 *
 * 为什么用 RemoteViews 而不是 Glance：minSdk 24，Glance 要 31+；而且这里只是
 * 7 行「日期 + 班次色块」，用固定 7 行的布局最省事（RemoteViews 在 minSdk 24
 * 上不能动态 addView，所以布局里写死 7 行，用不到的清空文字）。
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

        private val DATE_IDS = intArrayOf(
            R.id.w_date0, R.id.w_date1, R.id.w_date2, R.id.w_date3,
            R.id.w_date4, R.id.w_date5, R.id.w_date6
        )
        private val BAN_IDS = intArrayOf(
            R.id.w_ban0, R.id.w_ban1, R.id.w_ban2, R.id.w_ban3,
            R.id.w_ban4, R.id.w_ban5, R.id.w_ban6
        )
        private val WEEK = arrayOf("日", "一", "二", "三", "四", "五", "六")

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

        private fun colorOf(ban: String): Int {
            val hex = runCatching { BanColors.get(ban) }.getOrDefault("")
            if (hex.isBlank() || hex.equals("transparent", true)) return AndroidColor.parseColor("#F0F0F0")
            return runCatching { AndroidColor.parseColor(hex) }.getOrDefault(AndroidColor.parseColor("#F0F0F0"))
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

        private fun render(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)
            val today = Dates.todayStart()
            val todayStr = Dates.formatYmd(today)
            val people = runCatching {
                PeopleStore.loadPeople(todayStr, listOf("白班", "中班", "休息", "休息"))
            }.getOrDefault(emptyList())
            val activeId = runCatching { PeopleStore.loadActivePersonId() }.getOrNull()
            val person = people.firstOrNull { it.id == activeId } ?: people.firstOrNull()

            if (person == null) {
                views.setTextViewText(R.id.w_person, "")
                views.setTextViewText(R.id.w_range, "还没有人员")
                for (i in 0..6) {
                    views.setTextViewText(DATE_IDS[i], "")
                    views.setTextViewText(BAN_IDS[i], "")
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
                views.setTextViewText(R.id.w_range, todayStr + " 起 7 天")
                for (i in 0..6) {
                    val e = list.getOrNull(i)
                    if (e == null) {
                        views.setTextViewText(DATE_IDS[i], "")
                        views.setTextViewText(BAN_IDS[i], "")
                        continue
                    }
                    val md = e.date.substring(5).replace('-', '/')
                    views.setTextViewText(DATE_IDS[i], md + " 周" + WEEK[dayOfWeek(e.date)])
                    views.setTextViewText(BAN_IDS[i], e.info.ifBlank { "-" })
                    views.setInt(BAN_IDS[i], "setBackgroundColor", colorOf(e.info))
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
            runCatching { mgr.updateAppWidget(widgetId, views) }
        }
    }
}

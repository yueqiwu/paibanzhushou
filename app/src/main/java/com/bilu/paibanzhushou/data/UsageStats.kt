package com.bilu.paibanzhushou.data

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 匿名使用统计（只在后台网页看，App 界面里不出现任何入口、不弹任何提示）。
 *
 * 只上报三样东西：一个随机生成的安装标识（UUID，与本机任何硬件信息无关）、
 * 当天日期、版本号。不含人员、班次、排班周期等任何业务数据。
 * 同一自然日只上报一次；断网 / 服务器异常一律静默忽略，绝不影响启动速度与使用。
 */
object UsageStats {
    private const val TAG = "UsageStats"
    private const val PREFS = "usage_stats"
    private const val K_CID = "cid"
    private const val K_DAY = "last_day"
    private const val ENDPOINT = "https://qipaclub.top/paibanzhushou/stats.php"

    /** 在 Application / 首屏 onCreate 里调一次即可（内部异步，立即返回） */
    fun ping(context: Context) {
        val app = context.applicationContext
        runCatching {
            val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = day()
            if (sp.getString(K_DAY, null) == today) return
            val cid = sp.getString(K_CID, null) ?: UUID.randomUUID().toString().replace("-", "").also {
                sp.edit().putString(K_CID, it).apply()
            }
            // 先记下今天，失败也不重试，避免每次冷启动都打接口
            sp.edit().putString(K_DAY, today).apply()
            val version = Updater.currentVersion(app)
            Thread({
                runCatching { send(cid, version, today) }
                    .onFailure { Log.i(TAG, "skip " + it.javaClass.simpleName) }
            }, "usage-ping").apply { isDaemon = true }.start()
        }
    }

    private fun day(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun send(cid: String, version: String, day: String) {
        val q = ENDPOINT + "?a=ping&cid=" + cid +
                "&v=" + URLEncoder.encode(version, "UTF-8") + "&d=" + day
        val conn = (URL(q).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "banbiaoxiaoli/" + version)
        }
        try {
            val code = conn.responseCode
            if (code == 200) {
                conn.inputStream?.use { it.readBytes() }
            } else {
                Log.i(TAG, "http " + code)
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}

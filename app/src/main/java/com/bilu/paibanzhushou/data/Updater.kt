package com.bilu.paibanzhushou.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 从 GitHub Release 拿到的版本信息 */
data class ReleaseInfo(
    val version: String,
    val apkUrl: String,
    val apkSize: Long,
    val notes: String
)

/**
 * 应用内更新：查 GitHub Release 最新版 → 提示 → 下载 → 拉起系统安装器。
 *
 * 为什么走 GitHub Release：包本来就发在那里（官网 / 服务器只是镜像），
 * 不用额外维护一套更新接口；拿不到网络时静默失败即可，不打扰用户。
 */
object Updater {
    private const val API = "https://api.github.com/repos/yueqiwu/paibanzhushou/releases/latest"

    fun currentVersion(context: Context): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }

    /** 远端最新版；失败（无网 / 限流 / 解析失败）返回 null */
    fun fetchLatest(context: Context): ReleaseInfo? {
        val conn = runCatching {
            (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "banbiaoxiaoli/" + currentVersion(context))
            }
        }.getOrNull() ?: return null
        return try {
            android.util.Log.i("Updater", "GET " + API + " -> " + conn.responseCode)
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val tag = o.optString("tag_name", "").removePrefix("v")
            if (tag.isEmpty()) return null
            val assets = o.optJSONArray("assets") ?: return null
            var url = ""
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name", "")
                if (!name.endsWith(".apk", true)) continue
                url = a.optString("browser_download_url", "")
                size = a.optLong("size", 0L)
                if (url.isNotEmpty()) break
            }
            if (url.isEmpty()) return null
            android.util.Log.i("Updater", "latest=" + tag + " size=" + size + " url=" + url)
            ReleaseInfo(tag, url, size, o.optString("body", ""))
        } catch (t: Throwable) {
            android.util.Log.w("Updater", "fetch failed: " + t)
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** 版本号比较：remote 比 local 新才返回 true */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    /**
     * 下载源：自有域名优先（国内直连最快），GitHub 原链与两个 gh 代理兜底。
     * 具体顺序还会按实测速度重排，见 [rankBySpeed]。
     */
    private fun candidateUrls(info: ReleaseInfo): List<String> {
        val name = "banbiaoxiaoli-" + info.version + ".apk"
        return listOf(
            "https://qipaclub.top/paibanzhushou/" + name,
            info.apkUrl,
            "https://ghproxy.net/" + info.apkUrl,
            "https://gh-proxy.com/" + info.apkUrl
        ).filter { it.startsWith("http") }.distinct()
    }

    /**
     * 每个源先用 Range 抓 256KB 测一下速度，快的排前面。
     * 测速失败的排到最后（仍然作为兜底，总比没有强）。
     */
    private fun rankBySpeed(context: Context, urls: List<String>): List<String> {
        val ua = "banbiaoxiaoli/" + currentVersion(context)
        val scores = java.util.concurrent.ConcurrentHashMap<String, Double>()
        // 并行测速 + 总超时 4.5s：不能让用户对着「正在下载 0%」干等
        val threads = urls.map { url ->
            Thread {
                scores[url] = runCatching { probeSpeed(url, ua) }.getOrDefault(0.0)
                android.util.Log.i("Updater", "speed " + scores[url] + " KB/ms <- " + url)
            }.apply { isDaemon = true; start() }
        }
        val deadline = android.os.SystemClock.elapsedRealtime() + 4500
        for (t in threads) {
            val left = deadline - android.os.SystemClock.elapsedRealtime()
            if (left > 0) runCatching { t.join(left) }
        }
        return urls.sortedByDescending { scores[it] ?: 0.0 }
    }

    /** 拉 128KB 计时，返回 KB/ms；连不上或超时返回 0 */
    private fun probeSpeed(url: String, ua: String): Double {
        val probe = 128 * 1024
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 3000
            instanceFollowRedirects = true
            setRequestProperty("Range", "bytes=0-" + (probe - 1))
            setRequestProperty("User-Agent", ua)
        }
        var got = 0L
        val t0 = android.os.SystemClock.elapsedRealtime()
        try {
            if (c.responseCode !in 200..299) return 0.0
            c.inputStream.use { input ->
                val buf = ByteArray(32 * 1024)
                while (got < probe) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    got += r
                }
            }
        } finally {
            runCatching { c.disconnect() }
        }
        val ms = (android.os.SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
        return if (got <= 0) 0.0 else got.toDouble() / ms.toDouble()
    }

    /** 下载到应用私有目录，返回文件；[onProgress] 参数为 0..100（不知道总长时为 -1） */
    fun download(context: Context, info: ReleaseInfo, onProgress: (Int) -> Unit): File? {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, "banbiaoxiaoli-" + info.version + ".apk")
        if (target.exists() && info.apkSize > 0 && target.length() == info.apkSize) return target
        val ranked = rankBySpeed(context, candidateUrls(info))
        for ((i, url) in ranked.withIndex()) {
            android.util.Log.i("Updater", "download try #" + i + " " + url)
            val file = downloadFrom(context, url, info, dir, target, onProgress)
            if (file != null) return file
            onProgress(0)
        }
        return null
    }

    private fun downloadFrom(
        context: Context,
        url: String,
        info: ReleaseInfo,
        dir: File,
        target: File,
        onProgress: (Int) -> Unit
    ): File? {
        val conn = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "banbiaoxiaoli/" + currentVersion(context))
            }
        }.getOrNull() ?: return null
        return try {
            if (conn.responseCode !in 200..299) return null
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
            val tmp = File(dir, target.name + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (true) {
                        read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        done += read
                        onProgress(if (total > 0) ((done * 100) / total).toInt() else -1)
                    }
                }
            }
            // 长度对不上（被截断 / 代理返回错误页）就当这次失败，换下一个源
            if (info.apkSize > 0 && tmp.length() != info.apkSize) {
                android.util.Log.w("Updater", "size mismatch " + tmp.length() + " != " + info.apkSize)
                tmp.delete()
                return null
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return null
            }
            onProgress(100)
            return target
        } catch (t: Throwable) {
            android.util.Log.w("Updater", "download failed: " + t)
            return null
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** 拉起系统安装器；没有「安装未知应用」权限时先去要权限 */
    fun install(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

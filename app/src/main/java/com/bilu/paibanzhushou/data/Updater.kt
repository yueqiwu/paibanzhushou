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

    /** 下载到应用私有目录，返回文件；[onProgress] 参数为 0..100（不知道总长时为 -1） */
    fun download(context: Context, info: ReleaseInfo, onProgress: (Int) -> Unit): File? {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, "banbiaoxiaoli-" + info.version + ".apk")
        if (target.exists() && info.apkSize > 0 && target.length() == info.apkSize) return target
        val conn = runCatching {
            (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
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
            if (target.exists()) target.delete()
            tmp.renameTo(target)
            onProgress(100)
            target
        } catch (t: Throwable) {
            null
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

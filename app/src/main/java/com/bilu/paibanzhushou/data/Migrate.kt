package com.bilu.paibanzhushou.data

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * 从 uni-app 版（com.paibanzhushou.wyq）迁移数据。
 *
 * 两个包名、签名都不同，系统不会覆盖安装，数据目录也互相读不到，
 * 所以旧版把存储键读出来、编成深链（paibanzhushou://migrate?d=...）递给这里；
 * 深链被系统拦掉时，同一段文本也进了剪贴板，走 [parseClipboard] 兜底。
 *
 * 只认白名单里的键，键名与 uni-app 版、[Store] 完全一致，写入的也是原始字符串，
 * 不做任何字段转换 —— 结构本来就是为了迁移对齐的。
 */
object Migrate {
    /** 深链协议，对应 AndroidManifest 里 MainActivity 的 intent-filter */
    const val SCHEME = "paibanzhushou"
    const val HOST = "migrate"

    /** 迁移完成标记：有值就不再提示迁移 */
    const val KEY_MIGRATED = "paiban_migrated_v1"

    /** 旧版包名 */
    const val LEGACY_PACKAGE = "com.paibanzhushou.wyq"

    private const val LINK_PREFIX = "paibanzhushou://migrate?d="
    private const val SUPPORTED_VERSION = 1

    /** 需要迁移的键：与 utils/migrate.js 的 MIGRATE_KEYS 一一对应 */
    private val ALLOWED_KEYS = listOf(
        "paiban_people_v1",
        "paiban_active_person_v1",
        "paiban_config_v1",
        "PAIBAN_OPTIONS_KEY",
        "paiban_options_version_v1",
        "paiban_member_table_month_v1",
        "paiban_holiday_ban_v1"
    )

    private const val PEOPLE_KEY = "paiban_people_v1"

    data class Payload(
        val version: Int,
        val from: String,
        val entries: Map<String, String>
    )

    data class Result(
        val ok: Boolean,
        val peopleCount: Int,
        val importedKeys: Int,
        val message: String
    )

    /**
     * 待处理的深链：MainActivity 在 onCreate / onNewIntent 里写，界面用 Compose 状态直接观察，
     * 这样应用已经在前台时被深链唤起也能立刻弹确认框，不必重建 Activity。
     */
    var pendingLink by mutableStateOf<String?>(null)
        private set

    fun offerLink(link: String?) {
        if (!link.isNullOrEmpty()) pendingLink = link
    }

    fun consumeLink(): String? {
        val link = pendingLink
        pendingLink = null
        return link
    }

    /** 旧版是否还装着：还装着才值得提示用户回去点「把数据转过去」 */
    fun legacyInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(LEGACY_PACKAGE, 0)
        true
    } catch (t: Throwable) {
        false
    }

    fun alreadyMigrated(): Boolean = !Store.readStringOrNull(KEY_MIGRATED).isNullOrEmpty()

    /**
     * 解析深链。
     * @return 合法数据包；不是迁移链接 / 版本不对 / 解码失败都返回 null
     */
    fun parseLink(link: String?): Payload? {
        if (link.isNullOrBlank()) return null
        val text = link.trim()
        val encoded = when {
            text.startsWith(LINK_PREFIX) -> text.substring(LINK_PREFIX.length)
            // 剪贴板里也可能只有 ?d= 之后那一段
            !text.contains("://") -> text
            else -> return null
        }
        return parseEncoded(encoded)
    }

    /** 从剪贴板读一段迁移文本（用户在新版里点「从剪贴板导入」） */
    fun parseClipboard(context: Context): Payload? = try {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        parseLink(text)
    } catch (t: Throwable) {
        null
    }

    private fun parseEncoded(encoded: String): Payload? {
        val body = encoded.substringBefore('&').replace("=", "").trim()
        if (body.isEmpty()) return null
        val json = try {
            String(Base64.decode(body, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
        } catch (t: Throwable) {
            return null
        }
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optInt("v", 0) != SUPPORTED_VERSION) return null
        val data = root.optJSONObject("data") ?: return null
        val entries = LinkedHashMap<String, String>()
        ALLOWED_KEYS.forEach { key ->
            if (data.has(key)) {
                val value = data.optString(key, "")
                if (value.isNotEmpty()) entries[key] = value
            }
        }
        if (entries.isEmpty()) return null
        return Payload(
            version = SUPPORTED_VERSION,
            from = root.optString("from", ""),
            entries = entries
        )
    }

    /**
     * 把数据包写进本地存储。
     * 覆写式导入：旧版是数据源，用户点「导入」就是要把新版换成旧版的样子。
     */
    fun apply(payload: Payload): Result {
        var imported = 0
        payload.entries.forEach { (key, value) ->
            if (key in ALLOWED_KEYS) {
                Store.writeRaw(key, value)
                imported += 1
            }
        }
        val peopleCount = countPeople(payload.entries[PEOPLE_KEY])
        Store.writeRaw(KEY_MIGRATED, System.currentTimeMillis().toString())
        val message = when {
            peopleCount > 0 -> "已导入 $peopleCount 位人员"
            imported > 0 -> "已导入 $imported 项设置"
            else -> "没有可导入的数据"
        }
        return Result(ok = imported > 0, peopleCount = peopleCount, importedKeys = imported, message = message)
    }

    private fun countPeople(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return 0
        var count = 0
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i) != null) count += 1
        }
        return count
    }

    /** 供界面展示：把 5 位人员、7 项设置这样的摘要拼出来 */
    fun describe(payload: Payload): String {
        val peopleCount = countPeople(payload.entries[PEOPLE_KEY])
        val parts = ArrayList<String>(2)
        if (peopleCount > 0) parts.add("$peopleCount 位人员")
        val others = payload.entries.size - if (payload.entries.containsKey(PEOPLE_KEY)) 1 else 0
        if (others > 0) parts.add("$others 项设置")
        return if (parts.isEmpty()) "迁移数据" else parts.joinToString("、")
    }

    /** 给调试用：把 Uri 转成深链文本 */
    fun linkFromUri(uri: Uri?): String? {
        val raw = uri?.toString() ?: return null
        return if (raw.startsWith("$SCHEME://$HOST")) raw else null
    }
}

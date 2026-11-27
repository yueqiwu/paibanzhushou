package com.bilu.paibanzhushou.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import org.json.JSONArray

/**
 * 导出数据：把本机排班打成一条「paibanzhushou://migrate?d=...」文本，写进剪贴板。
 *
 * 为什么复用迁移链接：导入侧（[Migrate.parseClipboard] / 深链）已经是一套成熟链路，
 * 换机时在新手机点「从剪贴板导入旧版数据」就能恢复，格式与旧版迁移完全一致（v=1 + data）。
 * 文本本身也能直接发微信/存备忘录，不依赖文件权限。
 */
object Backup {

    /** 导出结果：链接本体 + 用来给用户看的一句话统计 */
    data class Export(
        val link: String,
        val keys: Int,
        val people: Int
    )

    /**
     * 收集当前本机数据。
     * @return 一条都收不到时返回 null（全新安装、还没动过设置）
     */
    fun build(): Export? {
        val entries = LinkedHashMap<String, String>()
        Migrate.ALLOWED_KEYS.forEach { key ->
            val raw = Store.readStringOrNull(key)
            if (!raw.isNullOrEmpty()) entries[key] = raw
        }
        if (entries.isEmpty()) return null
        val payload = Migrate.Payload(
            version = Migrate.SUPPORTED_VERSION,
            from = "native",
            entries = entries
        )
        // 与旧版 utils/migrate.js 一致：URL_SAFE + 不补 = 的 base64
        val body = Base64.encodeToString(
            Migrate.encodePayload(payload).toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return Export(
            link = Migrate.LINK_PREFIX + body,
            keys = entries.size,
            people = countPeople(entries["paiban_people_v1"])
        )
    }

    /** 写剪贴板；个别 ROM 在后台会拒绝，返回 false 让界面提示重试 */
    fun copyToClipboard(context: Context, text: String): Boolean = try {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (manager == null) {
            false
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("班表小历数据", text))
            true
        }
    } catch (t: Throwable) {
        false
    }

    private fun countPeople(raw: String?): Int {
        if (raw.isNullOrEmpty()) return 0
        return runCatching { JSONArray(raw).length() }.getOrDefault(0)
    }
}

package com.bilu.paibanzhushou.core

/**
 * 班次取色，移植自 utils/ban-color.js（含 JS 32 位整数溢出的哈希行为）。
 */
object BanColors {
    val DEFAULT_COLOR_MAP: Map<String, String> = linkedMapOf(
        "白班" to "#4A90E2",
        "中班" to "#6ac695",
        "值班" to "#D81B60",
        "夜班" to "#9C27B0",
        "休息" to "#FFD166"
    )

    private val EXTRA_COLOR_POOL = listOf(
        "#E53935", "#FB8C00", "#F9A825", "#C0CA33", "#43A047",
        "#00897B", "#00ACC1", "#3949AB", "#D81B60", "#6D4C41"
    )

    fun get(ban: String?): String {
        val b = ban ?: ""
        if (b.isEmpty()) return "transparent"
        DEFAULT_COLOR_MAP[b]?.let { return it }
        var hash = 0
        for (ch in b) {
            hash = ch.code + ((hash shl 5) - hash)
        }
        val abs = if (hash == Int.MIN_VALUE) 2147483648L else Math.abs(hash).toLong()
        return EXTRA_COLOR_POOL[(abs % EXTRA_COLOR_POOL.size).toInt()]
    }
}

package io.github.eslamasabry.oc.ui.kit.foundation

/** DIAGNOSTIC ONLY (glass-rest-diag): counts what draws or records, logged every 5 s by KitTheme. */
public object KitFrameProbe {
    private val counts = HashMap<String, Int>()

    public fun hit(key: String) {
        counts[key] = (counts[key] ?: 0) + 1
    }

    public fun drain(): String {
        val s = counts.entries.sortedByDescending { it.value }.joinToString(" ") { "${it.key}=${it.value}" }
        counts.clear()
        return s
    }
}

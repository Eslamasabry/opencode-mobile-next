package io.github.eslamasabry.opencode_mobile

import android.content.Context
import org.json.JSONObject

/** The words on the conversations widget. Pure choices live in [WidgetLayoutPolicy];
 * this only turns them into localized text. */
internal class WidgetCopy(private val context: Context, private val now: Long = System.currentTimeMillis()) {
    private fun s(id: Int, vararg args: Any) = NativeStrings.get(context, id, *args)

    fun ago(thenMs: Long): String {
        if (thenMs <= 0) return ""
        val e = WidgetElapsedPolicy.elapsed(now, thenMs)
        return when (e.kind) {
            WidgetLayoutPolicy.Ago.NOW -> s(R.string.widget_v2_ago_now)
            WidgetLayoutPolicy.Ago.MINUTES -> s(R.string.widget_v2_ago_min, e.count)
            WidgetLayoutPolicy.Ago.HOURS -> s(R.string.widget_v2_ago_hour, e.count)
            WidgetLayoutPolicy.Ago.YESTERDAY -> s(R.string.widget_v2_ago_yesterday)
            else -> e.label
        }
    }

    fun offline(lastSeenMs: Long): String =
        if (lastSeenMs > 0) s(R.string.widget_v2_offline_seen, ago(lastSeenMs)) else s(R.string.widget_v2_offline_plain)

    fun join(vararg parts: String): String = WidgetLayoutPolicy.join(*parts)

    private fun template(kind: String?): String? = when (kind) {
        "write" -> s(R.string.widget_v2_short_write, "%s")
        "read" -> s(R.string.widget_v2_short_read, "%s")
        "run" -> s(R.string.widget_v2_short_run, "%s")
        else -> null
    }

    /** The card's question and the exact command under it: see [WidgetLayoutPolicy.ask]. */
    fun ask(ask: JSONObject?, fallback: String): WidgetLayoutPolicy.Ask {
        val question = ask?.optString("q").orEmpty().ifBlank { fallback }
        return WidgetLayoutPolicy.ask(ask?.optString("file").orEmpty(), template(ask?.optString("kind")), question)
    }

    fun working() = s(R.string.widget_v2_working_now)
    fun ready() = s(R.string.widget_v2_ready)
    fun needsYou() = s(R.string.widget_v2_needs_you)
}

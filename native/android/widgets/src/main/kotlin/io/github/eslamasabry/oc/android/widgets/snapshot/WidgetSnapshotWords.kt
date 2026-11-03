package io.github.eslamasabry.oc.android.widgets.snapshot

import android.content.Context
import android.content.res.Configuration
import io.github.eslamasabry.oc.android.widgets.R
import java.util.Locale

/**
 * The words the widget snapshot writes (the launcher has no widget tree, so the caller supplies the app language).
 * Defaults are the English Flutter strings (`app_en.arb`); [load] reads the module's `strings_widget_snapshot.xml`
 * in the app's chosen language (English and Arabic).
 */
public data class WidgetSnapshotWords(
    /** `workspaceNewSession`: an untitled or placeholder-titled conversation. */
    val newConversation: String = "New conversation",
    val askOutsideWrite: String = "Write outside this project?",
    val askOutsideRead: String = "Read outside this project?",
    val askOutsideRun: String = "Run a command outside this project?",
    val askDirectory: String = "Work outside this project?",
    val askCommand: String = "Run this command?",
    val askRead: String = "Read this file?",
    val askEdit: String = "Edit this file?",
    /** `e7PermissionAction7`, with `%1$s` for the permission. */
    val usePermissionFormat: String = "Use %1\$s",
    val toolCommand: String = "Running a command…",
    val toolEdit: String = "Editing files…",
    val toolRead: String = "Reading files…",
    val toolSearch: String = "Searching files…",
    val toolWeb: String = "Browsing the web…",
    val toolSubagent: String = "Running a subagent…",
    val toolPlan: String = "Planning…",
    val toolWorking: String = "Working…",
    val toolCard: String = "Showing a card…",
    val toolBackgroundTask: String = "Checking a background task…",
    /** `liveToolOther`, with `%1$s` for the tool's words. */
    val toolOtherFormat: String = "Running %1\$s…",
) {
    public fun usePermission(permission: String): String = usePermissionFormat.replace("%1\$s", permission)

    public fun toolOther(words: String): String = toolOtherFormat.replace("%1\$s", words)

    public companion object {
        /** The words in [localeTag] (the app's language choice), else the phone's language. */
        public fun load(context: Context, localeTag: String? = null): WidgetSnapshotWords {
            val res =
                if (localeTag.isNullOrBlank()) {
                    context.resources
                } else {
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(Locale.forLanguageTag(localeTag))
                    context.createConfigurationContext(config).resources
                }
            return WidgetSnapshotWords(
                newConversation = res.getString(R.string.widget_snapshot_new_conversation),
                askOutsideWrite = res.getString(R.string.widget_snapshot_ask_outside_write),
                askOutsideRead = res.getString(R.string.widget_snapshot_ask_outside_read),
                askOutsideRun = res.getString(R.string.widget_snapshot_ask_outside_run),
                askDirectory = res.getString(R.string.widget_snapshot_ask_directory),
                askCommand = res.getString(R.string.widget_snapshot_ask_command),
                askRead = res.getString(R.string.widget_snapshot_ask_read),
                askEdit = res.getString(R.string.widget_snapshot_ask_edit),
                usePermissionFormat = res.getString(R.string.widget_snapshot_use_permission),
                toolCommand = res.getString(R.string.widget_snapshot_tool_command),
                toolEdit = res.getString(R.string.widget_snapshot_tool_edit),
                toolRead = res.getString(R.string.widget_snapshot_tool_read),
                toolSearch = res.getString(R.string.widget_snapshot_tool_search),
                toolWeb = res.getString(R.string.widget_snapshot_tool_web),
                toolSubagent = res.getString(R.string.widget_snapshot_tool_subagent),
                toolPlan = res.getString(R.string.widget_snapshot_tool_plan),
                toolWorking = res.getString(R.string.widget_snapshot_tool_working),
                toolCard = res.getString(R.string.widget_snapshot_tool_card),
                toolBackgroundTask = res.getString(R.string.widget_snapshot_tool_background_task),
                toolOtherFormat = res.getString(R.string.widget_snapshot_tool_other),
            )
        }
    }
}

/** Flutter `ConnectionController.toolSentence`: what a running tool is doing, in words, never its raw id or input. */
public object WidgetToolStep {
    private val cardShowNames = setOf("mcp__oc-ui__show", "oc-ui_show")
    private val mcpId = Regex("^mcp__(.+?)__(.+)$")
    private val camelBoundary = Regex("([a-z0-9])([A-Z])")
    private val separators = Regex("[_\\-.\\s]+")

    public fun sentence(tool: String, words: WidgetSnapshotWords): String =
        when (tool.lowercase()) {
            "bash", "shell" -> words.toolCommand
            "edit", "write", "patch", "multiedit", "apply_patch" -> words.toolEdit
            "read" -> words.toolRead
            "grep", "glob", "list", "ls" -> words.toolSearch
            "webfetch", "websearch" -> words.toolWeb
            "task", "subagent" -> words.toolSubagent
            "todowrite", "todoread" -> words.toolPlan
            "" -> words.toolWorking
            else ->
                when {
                    tool.trim() in cardShowNames -> words.toolCard
                    tool.trim().lowercase() == "task_notification" -> words.toolBackgroundTask
                    else -> idWords(tool).let { if (it.isEmpty()) words.toolWorking else words.toolOther(it) }
                }
        }

    /** Dart `toolIdWords`: `render_mermaid_diagram` → "render mermaid diagram". */
    internal fun idWords(id: String): String {
        var name = id.trim()
        mcpId.find(name)?.let { name = it.groupValues[2] }
        return name.replace(camelBoundary) { "${it.groupValues[1]} ${it.groupValues[2]}" }.replace(separators, " ").trim().lowercase()
    }
}

/**
 * Dart `presentedSessionTitleText` (the same rules as `:feature:chats` `SessionTitles`, kept here because a widget
 * module cannot depend on a feature): leaked model markup cut off, the server's dated placeholder becomes "New
 * conversation", an ISO stamp inside a longer title goes, and an empty title takes the caller's fallback.
 */
public object WidgetTitles {
    private val markers = listOf("<tool_call", "</tool_call", "<tool_use", "<function", "</function", "<|")
    private val truncatedMarker = Regex("""(<[A-Za-z_|/]*)\s*(?:…|\.\.\.)\s*$""")
    private val trailingEllipsis = Regex("""[\s…]*(?:\.\.\.)?[\s…]*$""")
    private val placeholder =
        Regex(
            """^(?:(?:New|Child)\s+session|Fork(?:ed)?(?:\s+session)?)?\s*[-:–]?\s*""" +
                """\d{4}-\d{2}-\d{2}T[\d:.]+(?:Z|[+-]\d{2}:?\d{2})?$""",
            RegexOption.IGNORE_CASE,
        )
    private val isoStamp = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?""")

    public fun present(raw: String?, fallback: String, newConversation: String): String {
        val title = text(raw)
        if (title.isEmpty()) return fallback
        if (placeholder.matches(title)) return newConversation
        val plain =
            title.replace(isoStamp, "").replace(Regex("""\s*[-:–]\s*$"""), "").replace(Regex("""\s{2,}"""), " ").trim()
        return plain.ifEmpty { newConversation }
    }

    private fun text(raw: String?): String {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return value
        val lower = value.lowercase()
        val at = markers.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        var cut = false
        if (at != null) {
            value = value.substring(0, at)
            cut = true
        } else {
            val tail = truncatedMarker.find(value)
            if (tail != null && markers.any { it.startsWith(tail.groupValues[1].lowercase()) }) {
                value = value.substring(0, tail.range.first)
                cut = true
            }
        }
        if (cut) value = trailingEllipsis.replaceFirst(value, "")
        return value.trim()
    }
}

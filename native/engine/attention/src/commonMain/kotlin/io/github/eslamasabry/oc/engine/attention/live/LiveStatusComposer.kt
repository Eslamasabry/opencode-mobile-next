package io.github.eslamasabry.oc.engine.attention.live

/**
 * The app-language words the live status uses (Flutter `liveTool*` and `notifStatus*` copy). The app supplies them
 * from `:ui:words` (English + Arabic); the engine holds no copy.
 */
public interface LiveWords {
    public val toolCommand: String
    public val toolEdit: String
    public val toolRead: String
    public val toolSearch: String
    public val toolWeb: String
    public val toolSubagent: String
    public val toolPlan: String
    public val toolWorking: String
    public val toolCard: String
    public val toolBackgroundTask: String

    /** "Running {tool}…" for a tool the app has no sentence for; [tool] is already in words. */
    public fun toolOther(tool: String): String

    /** "1 needs you" / "2 need you". */
    public fun needsYou(count: Int): String

    /** "2 chats working". */
    public fun chatsWorking(count: Int): String

    public val readyTitle: String
}

/** One session as the live status needs it. */
public data class LiveSession(val id: String, val title: String? = null, val updatedAt: Long = 0)

/** Session truth the live status is composed from (the connection's current picture). */
public data class LiveInput(
    val sessions: Map<String, LiveSession> = emptyMap(),
    val busy: Set<String> = emptySet(),
    /** Permissions still awaiting an answer. */
    val pendingPermissions: Int = 0,
    val pendingQuestions: Int = 0,
    val pendingForms: Int = 0,
)

/**
 * Composes and publishes the live status (port of `ConnectionController.liveStatus` + its publish hooks, B-ANDR-033):
 * counts running sessions and pending requests, names the freshest busy session, says what tool runs in the app's
 * language, and never repeats a tool's input. [publish] is [LiveStatusPublisher.publish].
 */
public class LiveStatusComposer(
    private var words: LiveWords,
    private val publish: suspend (LiveStatus) -> Unit,
) {
    private var input = LiveInput()
    private val runningTool = mutableMapOf<String, String>()

    /** The app language changed: later sentences use [next]. */
    public fun setWords(next: LiveWords) {
        words = next
    }

    /** The connection's state changed (Flutter `notifyListeners`). */
    public suspend fun update(next: LiveInput) {
        input = next
        publish(status())
    }

    /** A `message.part.updated` tool part: running shows its sentence, anything else clears it. */
    public suspend fun onToolPart(sessionId: String, tool: String, state: String) {
        if (state == "running" || state == "pending") runningTool[sessionId] = tool else runningTool.remove(sessionId)
        publish(status())
    }

    public fun status(): LiveStatus {
        val current = input.busy.mapNotNull { input.sessions[it] }.maxByOrNull { it.updatedAt }
        val title = current?.title?.trim()?.takeIf { it.isNotEmpty() }
        val detail = current?.let { runningTool[it.id] }?.let { toolSentence(it, words) }
        val pending = input.pendingPermissions + input.pendingQuestions + input.pendingForms
        val running = input.busy.size
        val status =
            when {
                pending > 0 -> words.needsYou(pending).let { LiveNotificationStatus(LiveStatusKind.NeedsYou, it, "OpenCode · $it") }
                running == 1 -> LiveNotificationStatus(LiveStatusKind.Working, detail ?: words.toolWorking, words.chatsWorking(1))
                running > 1 -> words.chatsWorking(running).let { LiveNotificationStatus(LiveStatusKind.SeveralWorking, it, it) }
                else -> LiveNotificationStatus(LiveStatusKind.Idle, words.readyTitle, words.readyTitle)
            }
        return LiveStatus(runningCount = running, pendingCount = pending, title = title, detail = detail, status = status)
    }

    public companion object {
        private val CARD_SHOW_NAMES = setOf("mcp__oc-ui__show", "oc-ui_show")
        private val MCP = Regex("^mcp__(.+?)__(.+)$")

        /** What a running tool is doing, in [words]; never the tool's input, never a raw id. */
        public fun toolSentence(tool: String, words: LiveWords): String =
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
                        tool.trim() in CARD_SHOW_NAMES -> words.toolCard
                        tool.trim().lowercase() == "task_notification" -> words.toolBackgroundTask
                        else -> toolIdWords(tool).let { if (it.isEmpty()) words.toolWorking else words.toolOther(it) }
                    }
            }

        /** `render_mermaid_diagram` → "render mermaid diagram"; an MCP id keeps its tool name only. */
        public fun toolIdWords(id: String): String {
            var name = id.trim()
            MCP.find(name)?.let { name = it.groupValues[2] }
            return name
                .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
                .replace(Regex("[_\\-.\\s]+"), " ")
                .trim()
                .lowercase()
        }
    }
}

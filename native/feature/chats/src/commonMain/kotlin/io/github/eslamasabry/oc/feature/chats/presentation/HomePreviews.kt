package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.OPENCODE_AGENT_ID
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A1's last-message line, apart from the reading (Flutter `_feedPreview`, lib/state/connection/chat_feed.dart:302: the
 * newest message that says something, whitespace folded, cut at 120).
 */
public object HomePreviewRules {
    /** How a waiting request's home line is worded: by what the request is, not always "run". */
    public enum class AskKind { Run, Edit, Read, Other }

    /** The server's permission type (`bash`, `edit`, ...) as a sentence kind; the same sets the request card reads. */
    public fun askKind(permission: String): AskKind =
        when (permission.trim().lowercase()) {
            "bash", "shell" -> AskKind.Run
            "edit", "write", "multiedit", "patch" -> AskKind.Edit
            "read" -> AskKind.Read
            else -> AskKind.Other
        }

    /** The longest preview kept; the row cuts it again to its width. */
    public const val MAX_CHARS: Int = 120

    /** How many of the newest entries one read asks for: enough to pass a turn's tool steps, never the history. */
    public const val PAGE: Int = 6

    /** A running conversation moves all the time: its line is read again at most this often. */
    public const val LIVE_REREAD_MS: Long = 15_000

    private val lineLead = Regex("^\\s{0,3}(?:#{1,6}\\s+|>\\s?|[-*+]\\s+(?:\\[[ xX]]\\s+)?|```\\S*)")
    private val link = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")
    private val strong = Regex("(\\*\\*|__|~~)(?=\\S)(.+?)(?<=\\S)\\1")
    private val code = Regex("`+([^`]*)`+")

    /**
     * [text] without the marks a reply is written with: the list shows words, not "**bold**", "`paths`", "# headings"
     * or "[labels](links)" (seen raw on the owner's phone). Headings, quotes, bullets and code fences lose their lead,
     * a link keeps its label, emphasis and code keep their words; emoji and everything else stay.
     */
    public fun plain(text: String): String =
        text.lineSequence()
            .joinToString("\n") { it.replace(lineLead, "") }
            .replace(link, "$1")
            .replace(strong, "$2")
            .replace(code, "$1")
            .replace("**", "")

    /** [text] on one line: marks removed, whitespace folded, trimmed, cut at [MAX_CHARS] with an ellipsis; null when empty. */
    public fun oneLine(text: String): String? {
        val folded = plain(text).replace(Regex("\\s+"), " ").trim()
        if (folded.isEmpty()) return null
        return if (folded.length <= MAX_CHARS) folded else folded.substring(0, MAX_CHARS - 1).trimEnd() + "…"
    }

    /** The newest message's words in [page] (chronological), user or agent; tools, files and thinking say nothing. */
    public fun lastText(page: List<MessageWithParts>): String? {
        for (message in page.asReversed()) {
            val words =
                message.parts
                    .filter { it.type == PartType.Text && !it.synthetic && it.text.isNotBlank() }
                    .joinToString(" ") { it.text }
            oneLine(words)?.let { return it }
        }
        return null
    }

    /**
     * Whether a row's line must be read (again): never read; or it moved since, unless it is running and was read
     * less than [LIVE_REREAD_MS] ago. Only OpenCode conversations are read: the other protocols' history reads are not
     * cheap, so their rows keep project and server.
     */
    public fun needsRead(item: ChatFeedItem, readAtActivityMs: Long?, readAtMs: Long?, nowMs: Long): Boolean {
        if (item.agentId != OPENCODE_AGENT_ID) return false
        if (readAtActivityMs == null || readAtMs == null) return true
        if (readAtActivityMs == item.lastActivityMs) return false
        return item.status != ChatStatus.Running || nowMs - readAtMs >= LIVE_REREAD_MS
    }

    /**
     * The quiet words a row shows when it has no line: its project (unless its dot already names it) and its server,
     * each kept whole in either reading direction; null when it has neither.
     */
    public fun fallback(project: String, server: String?, projectShown: Boolean): String? =
        listOfNotNull(project.takeIf { !projectShown && it.isNotBlank() }, server?.takeIf { it.isNotBlank() })
            .joinToString("\u00a0· ") { "\u2068$it\u2069" }
            .ifEmpty { null }

    /** [ui] with each shown row's line from [texts] (by row key), where the list did not bring its own. */
    public fun withPreviews(ui: ChatsUi, texts: Map<String, String>): ChatsUi {
        if (texts.isEmpty()) return ui
        fun ConversationRowUi.lined(): ConversationRowUi = if (preview != null) this else copy(preview = texts[key])
        return ui.copy(
            rightNow = ui.rightNow.map { it.lined() }.toImmutableList(),
            recent = ui.recent.map { it.lined() }.toImmutableList(),
            brief = ui.brief?.let { b -> b.copy(rows = b.rows.map { it.lined() }.toImmutableList()) },
        )
    }
}

/**
 * Reads the last message of the conversations the home shows, and only those: one small page each, one read at a time
 * per row, in the background. The list never waits for it; a row without a line keeps its project and server. What
 * was read stays until the conversation moves (cached in memory for the screen's life). A failed read is not retried
 * until the conversation moves again.
 */
internal class HomePreviews(
    private val scope: CoroutineScope,
    private val servers: () -> List<ConversationServer>,
    private val nowMs: () -> Long,
) {
    private class Read(val activityMs: Long, val atMs: Long)

    private val reads = HashMap<String, Read>()
    private val inFlight = HashSet<String>()
    private val mutable = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Row key → its last message's line. */
    val texts: StateFlow<Map<String, String>> = mutable.asStateFlow()

    /** The lines saved with the cached rows stand until the live read says otherwise, so a row never loses its line. */
    fun seed(rows: List<ChatFeedItem>) {
        val saved = rows.filter { it.preview.isNotBlank() }.associate { it.identity to it.preview }
        if (saved.isNotEmpty()) mutable.update { saved + it }
    }

    /** The rows the home shows now; reads what is missing or stale. */
    fun want(items: List<ChatFeedItem>) {
        val now = nowMs()
        val byProfile = servers().associateBy { it.profile }
        for (item in items) {
            if (item.preview.isNotBlank() || item.identity in inFlight) continue
            val known = reads[item.identity]
            if (!HomePreviewRules.needsRead(item, known?.activityMs, known?.atMs, now)) continue
            val server = byProfile[item.profileId] ?: continue
            inFlight += item.identity
            scope.launch {
                try {
                    val page = server.gateway.messagePage(item.sessionId, cursor = null, limit = HomePreviewRules.PAGE)
                    val text =
                        when (page) {
                            is Outcome.Ok -> HomePreviewRules.lastText(page.value.items)
                            is Outcome.Failed -> null
                        }
                    reads[item.identity] = Read(item.lastActivityMs, nowMs())
                    // A failed or empty read keeps the line read before, if any.
                    if (text != null) mutable.update { it + (item.identity to text) }
                } finally {
                    inFlight -= item.identity
                }
            }
        }
    }
}

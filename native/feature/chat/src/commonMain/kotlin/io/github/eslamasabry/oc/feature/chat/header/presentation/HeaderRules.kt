package io.github.eslamasabry.oc.feature.chat.header.presentation

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ContextLevel
import io.github.eslamasabry.oc.engine.transcript.turnfacts.contextLevel as levelOfFraction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Pure header rules, each tested from its catalog row. */
public object HeaderRules {
    /**
     * B-CHAT-018: the project's name is the last folder of its path, ignoring trailing slashes and Windows separators.
     * Null for an absent or root-only path.
     */
    public fun projectName(path: String?): String? =
        path?.replace('\\', '/')?.split('/')?.lastOrNull { it.isNotBlank() }?.trim()

    /**
     * B-CHAT-019 / B-CHAT-057: the fraction is tokens over the window; unknown when either is missing or the window is
     * not positive. The percent is whole, floored and clamped 0–100.
     */
    public fun contextPercent(tokens: Long?, limit: Long?): Int? {
        if (tokens == null || limit == null || limit <= 0 || tokens < 0) return null
        return ((tokens * 100) / limit).coerceIn(0, 100).toInt()
    }

    /** B-CHAT-057: the whole percent's level, by the one rule in `turnfacts` (70 % warning, 95 % full). */
    public fun contextLevel(percent: Int?): ContextLevel? = percent?.let { levelOfFraction(it / 100.0) }

    /**
     * The ⋯ menu (Flutter `sessionMenuItems`): Go to (Find, Details), then Do (Share or Stop sharing, Fork, Rename,
     * Continue with…, Copy transcript, Archive), Delete last. Gated on the conversation's own capabilities, never on
     * the backend kind.
     */
    public fun menu(caps: ServerCapabilities, shared: Boolean): ImmutableList<HeaderAction> =
        buildList {
            add(HeaderAction.Find)
            add(HeaderAction.Details)
            if (caps.sessionShare) add(if (shared) HeaderAction.Unshare else HeaderAction.Share)
            if (caps.sessionFork) add(HeaderAction.Fork)
            add(HeaderAction.Rename)
            if (caps.sessionModelProviderSwitching && caps.serverCatalog) add(HeaderAction.ContinueWith)
            add(HeaderAction.CopyTranscript)
            if (caps.sessionArchive) add(HeaderAction.Archive)
            if (caps.sessionDelete) add(HeaderAction.Delete)
        }.toImmutableList()

    /** Fork acts on prompts: before the first one it waits, dimmed, with the reason. */
    public fun needsPrompt(action: HeaderAction): Boolean = action == HeaderAction.Fork

    /** Rename refuses a blank title (Flutter `chatRenameEmpty`). */
    public fun renameValid(title: String): Boolean = title.isNotBlank()
}

/** The fixed words Copy transcript writes (the screen supplies them in the person's language). */
public data class TranscriptWords(
    val untitled: String,
    val user: String,
    val assistant: String,
    val reasoning: String,
    val partial: String,
)

/**
 * Copy transcript (Flutter `_transcriptMarkdown`, SEC-13): the person's own prompts stay as they typed them; every
 * other text — the title, replies and reasoning — goes through [mask] first, so a key a tool printed never reaches the
 * clipboard. [rows] are the engine's rows NEWEST FIRST; the copy reads oldest first.
 */
public object TranscriptCopy {
    public fun markdown(
        title: String?,
        rows: List<TranscriptRow>,
        words: TranscriptWords,
        partial: Boolean,
        mask: (String) -> String,
    ): String {
        val out = StringBuilder()
        out.append("# ").append(mask(title?.takeIf { it.isNotBlank() } ?: words.untitled)).append('\n')
        if (partial) out.append("\n> ").append(words.partial).append('\n')
        var lastSpeaker: String? = null
        var lastTurn: String? = null
        for (row in rows.asReversed()) {
            when (row) {
                is PromptRow -> {
                    if (row.text.isBlank()) continue
                    out.append("\n## ").append(words.user).append("\n\n").append(row.text.trim()).append("\n")
                    lastSpeaker = words.user
                    lastTurn = row.turnId
                }
                is BlockRow -> {
                    if (row.text.isBlank()) continue
                    if (lastSpeaker != words.assistant || lastTurn != row.turnId) {
                        out.append("\n## ").append(words.assistant).append("\n\n")
                        lastSpeaker = words.assistant
                        lastTurn = row.turnId
                    }
                    // Chunks of one block join without a seam (STREAM-FIX rows).
                    out.append(mask(row.text))
                    if (!row.continues) out.append(if (row.text.endsWith("\n")) "\n" else "\n\n")
                }
                is ThoughtRow -> {
                    if (row.firstLine.isBlank()) continue
                    out.append("\n<details><summary>").append(words.reasoning).append("</summary>\n\n")
                        .append(mask(row.firstLine.trim())).append("\n\n</details>\n")
                }
                else -> Unit
            }
        }
        return out.toString().trimEnd()
    }
}

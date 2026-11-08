package io.github.eslamasabry.oc.feature.chat.header.presentation

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
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
     * B-CHAT-018: the project's name is the last folder of its path, ignoring trailing slashes and
     * Windows separators. Null for an absent or root-only path.
     */
    public fun projectName(path: String?): String? =
        path?.replace('\\', '/')?.split('/')?.lastOrNull { it.isNotBlank() }?.trim()

    /**
     * B-CHAT-019 / B-CHAT-057: the fraction is tokens over the window; unknown when either is
     * missing or the window is not positive. The percent is whole, floored and clamped 0–100.
     */
    public fun contextPercent(tokens: Long?, limit: Long?): Int? {
        if (tokens == null || limit == null || limit <= 0 || tokens < 0) return null
        return ((tokens * 100) / limit).coerceIn(0, 100).toInt()
    }

    /**
     * B-CHAT-057: the whole percent's level, by the one rule in `turnfacts` (70 % warning, 95 %
     * full).
     */
    public fun contextLevel(percent: Int?): ContextLevel? = percent?.let {
        levelOfFraction(it / 100.0)
    }

    /**
     * The ⋯ menu (Flutter `sessionMenuItems`): Go to (Find, Details), then Do (Share or Stop
     * sharing, Fork, Rename, Continue with…, Continue on computer, Copy transcript, Archive),
     * Delete last. Gated on the conversation's own capabilities, never on the backend kind.
     *
     * B-163: "Schedule a prompt" (Flutter `canSchedulePrompts`) is offered when the server runs scheduled prompts
     * and the conversation has a project folder; it opens this server's scheduled prompts.
     */
    public fun menu(
        caps: ServerCapabilities,
        shared: Boolean,
        directory: String? = null,
        canExport: Boolean = false,
    ): ImmutableList<HeaderAction> =
        buildList {
            add(HeaderAction.Find)
            add(HeaderAction.Details)
            if (caps.scheduledPrompts && !directory.isNullOrBlank()) add(HeaderAction.Schedule)
            add(HeaderAction.CardsSettings)
            add(HeaderAction.AgentHandoff)
            if (caps.sessionShare) {
                add(if (shared) HeaderAction.Unshare else HeaderAction.Share)
                if (shared) add(HeaderAction.CopyShareLink)
            }
            if (caps.sessionFork) add(HeaderAction.Fork)
            add(HeaderAction.Rename)
            if (
                (caps.sessionModelProviderSwitching && caps.serverCatalog) || caps.agentHandoffFiles
            )
                add(HeaderAction.ContinueWith)
            if (caps.cliSessionResume) add(HeaderAction.ContinueOnComputer)
            add(HeaderAction.ContinueOnPhone)
            if (caps.agentHandoffFiles) add(HeaderAction.Handoff)
            if (caps.sessionImportExport) add(HeaderAction.ImportConversation)
            if (caps.agentFeatures) add(HeaderAction.AgentOptions)
            add(HeaderAction.CopyTranscript)
            if (canExport) add(HeaderAction.ExportTranscript)
            add(HeaderAction.Reload)
            if (caps.sessionArchive) add(HeaderAction.Archive)
            if (caps.sessionDelete) add(HeaderAction.Delete)
        }
        .toImmutableList()

    /** P-018: every way to carry this conversation on somewhere else, in menu order. */
    private val continueFamily =
        listOf(
            HeaderAction.ContinueWith,
            HeaderAction.AgentHandoff,
            HeaderAction.Handoff,
            HeaderAction.ContinueOnComputer,
            HeaderAction.ContinueOnPhone,
        )

    /**
     * P-018: the "Continue…" actions the menu offers, shown together on one "Continue elsewhere" page when there
     * are two or more (three look-alike rows in a list read as one muddle); empty when one or none, which then
     * stays in place.
     */
    public fun continueGroup(menu: List<HeaderAction>): List<HeaderAction> {
        val offered = continueFamily.filter { it in menu }
        return if (offered.size >= 2) offered else emptyList()
    }

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
    val attachment: String = "Attachment",
    val file: String = "file",
    val tool: String = "Tool",
    val error: String = "Error",
)

/**
 * Copy transcript (Flutter `_transcriptMarkdown`): every text — the title, the person's own prompts,
 * replies and reasoning — goes through [mask] first (E15-S06), so a key the person pasted or a tool
 * printed never reaches the clipboard. [rows] are the engine's rows NEWEST FIRST; the
 * copy reads oldest first.
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
        out.append("# ")
            .append(mask(title?.takeIf { it.isNotBlank() } ?: words.untitled))
            .append('\n')
        if (partial) out.append("\n> ").append(words.partial).append('\n')
        var lastSpeaker: String? = null
        var lastTurn: String? = null
        val block = StringBuilder()
        for (row in rows.asReversed()) {
            when (row) {
                is PromptRow -> {
                    if (row.text.isBlank()) continue
                    out.append("\n## ")
                        .append(words.user)
                        .append("\n\n")
                        .append(mask(row.text.trim()))
                        .append("\n")
                    lastSpeaker = words.user
                    lastTurn = row.turnId
                }
                is BlockRow -> {
                    if (block.isEmpty() && row.text.isBlank()) continue
                    if (lastSpeaker != words.assistant || lastTurn != row.turnId) {
                        out.append("\n## ").append(words.assistant).append("\n\n")
                        lastSpeaker = words.assistant
                        lastTurn = row.turnId
                    }
                    // Codex chat-header #7: a chunk's text drops its boundary newline, so chunks
                    // join with one.
                    // Codex chat-header #3: one block is masked as a whole (a key split across
                    // chunks is still a key).
                    if (block.isNotEmpty()) block.append('\n')
                    block.append(row.text)
                    if (!row.continues) {
                        val text = block.toString()
                        block.clear()
                        out.append(mask(text))
                        out.append(if (text.endsWith("\n")) "\n" else "\n\n")
                    }
                }
                is ThoughtRow -> {
                    if (row.firstLine.isBlank()) continue
                    out.append("\n<details><summary>")
                        .append(words.reasoning)
                        .append("</summary>\n\n")
                        .append(mask(row.firstLine.trim()))
                        .append("\n\n</details>\n")
                }
                else -> Unit
            }
        }
        if (block.isNotEmpty()) out.append(mask(block.toString()))
        return out.toString().trimEnd()
    }

    /**
     * Codex chat-header #6 (Flutter `_transcriptMarkdown`): the copy is built from the loaded
     * messages, not the display rows, so tool headings and output, attachments, full reasoning and
     * errors are all there. Each part is masked as a whole, the person's own words included
     * (E15-S06). [messages] are oldest first.
     */
    public fun markdownOf(
        title: String?,
        messages: List<MessageWithParts>,
        words: TranscriptWords,
        partial: Boolean,
        mask: (String) -> String,
    ): String {
        val out = StringBuilder()
        out.append(mask("# ${title?.takeIf { it.isNotBlank() } ?: words.untitled}\n"))
        if (partial) out.append("\n> ").append(words.partial).append('\n')
        for (message in messages) {
            if (message.info.id.startsWith("local-")) continue
            // E15-S07: a message with nothing to print (a step marker, an empty reply opening the
            // conversation) gets no heading, so the copy never starts with an empty section.
            val body = StringBuilder()
            fun put(text: String) {
                body.append(mask(text))
            }
            for (part in message.parts) {
                when (part.type) {
                    PartType.Text ->
                        if (part.text.isNotBlank()) put("${part.text.trim()}\n\n")
                    PartType.Reasoning ->
                        if (part.text.isNotBlank())
                            put(
                                "<details><summary>${words.reasoning}</summary>\n\n${part.text.trim()}\n\n</details>\n\n"
                            )
                    PartType.File ->
                        put("- ${words.attachment}: ${part.filename ?: part.url ?: words.file}\n")
                    PartType.Tool -> {
                        val name =
                            part.tool?.title?.takeIf { it.isNotBlank() } ?: part.toolName.orEmpty()
                        put("### ${words.tool}: $name\n\n")
                        val output = part.tool?.output?.trim()
                        if (!output.isNullOrEmpty()) put("```text\n$output\n```\n\n")
                    }
                    else -> Unit
                }
            }
            message.info.errorText?.takeIf { it.isNotBlank() }?.let { put("> ${words.error}: $it\n") }
            if (body.isBlank()) continue
            val own = message.info.role == Role.User
            out.append("\n## ").append(if (own) words.user else words.assistant).append("\n\n")
            out.append(body)
        }
        return out.toString().trimEnd()
    }
}

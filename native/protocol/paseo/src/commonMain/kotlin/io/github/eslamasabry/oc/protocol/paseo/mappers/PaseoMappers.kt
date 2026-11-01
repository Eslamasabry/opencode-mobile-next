package io.github.eslamasabry.oc.protocol.paseo.mappers

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionContextUse
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.paseo.arr
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.numberOrNull
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoList
import io.github.eslamasabry.oc.protocol.paseo.paseoMillis
import io.github.eslamasabry.oc.protocol.paseo.paseoReply
import io.github.eslamasabry.oc.protocol.paseo.paseoString
import io.github.eslamasabry.oc.protocol.paseo.paseoText
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.math.roundToLong

/*
 * FROZEN SIGNATURES (S3-PASEO slice A owns the bodies). Pure ports of lib/paseo/mappers.dart: no I/O, no clock
 * (callers pass nowMs), no copy (fixed English stand-ins only where Dart writes a value the model carries, e.g. the
 * "<Runtime> conversation" title stand-in). Strict reads throw PaseoException(InvalidResponse).
 */

// ---- agents → sessions

/** Dart `paseoSession`: one daemon agent is one session; model `provider/model`, mode as agent, cost, context use. */
public fun paseoSession(agent: JsonObject): Session {
    val id = paseoString(agent["id"], max = 256)
    val cwd = paseoString(agent["cwd"])
    val provider = paseoString(agent["provider"], max = 128)
    val title = agent["title"].stringOrNull()
    val model = agent["model"].stringOrNull()?.takeIf { it.isNotEmpty() }
    val usage = agent.obj("lastUsage")
    val cost = usage?.let { u -> u["totalCostUsd"].present() ?: u["costUsd"] }.numberOrNull()
    val mode = agent["currentModeId"].stringOrNull()
    return Session(
        id = SessionId(id),
        title = if (title != null && title.trim().isNotEmpty()) {
            paseoClippedTitle(paseoText(agent["title"], max = 4096))
        } else {
            "${paseoProviderName(provider)} conversation"
        },
        directory = cwd,
        time = SessionTime(createdMs = paseoMillis(agent["createdAt"]), updatedMs = paseoMillis(agent["updatedAt"])),
        agent = mode,
        model = model?.let { "$provider/$it" },
        cost = cost,
        contextUse = paseoContextUse(usage),
        // The agent's model and mode are its own state (the daemon applies a change to the running agent).
        selection = SessionSelection(
            model = model?.let { ModelRef(provider, it) },
            variant = agent["thinkingOptionId"].stringOrNull() ?: "",
            agent = mode,
            modelKnown = true,
            agentKnown = true,
        ),
    )
}

/** Dart `paseoProviderName`: claude → Claude Code, codex → Codex, opencode → OpenCode, copilot → GitHub Copilot, pi → Pi. */
public fun paseoProviderName(provider: String): String =
    when (provider) {
        "claude" -> "Claude Code"
        "codex" -> "Codex"
        "opencode" -> "OpenCode"
        "copilot" -> "GitHub Copilot"
        "pi" -> "Pi"
        else -> provider
    }

public fun paseoContextUse(usage: JsonElement?): SessionContextUse? {
    val map = usage.objectOrNull() ?: return null
    val used = map["contextWindowUsedTokens"].numberOrNull() ?: return null
    val max = map["contextWindowMaxTokens"].numberOrNull() ?: return null
    if (!used.isFinite() || !max.isFinite() || used <= 0 || max <= 0) return null
    return SessionContextUse(tokens = used.roundToLong(), limit = max.roundToLong())
}

private val SENTENCE_END = Regex("""[.!?…)"'`]$""")

/** A 58–60 character title that does not end like a sentence gets an ellipsis (B-AGENT-183). */
public fun paseoClippedTitle(title: String): String {
    val text = title.trim()
    if (text.length < 58 || text.length > 60) return title
    if (SENTENCE_END.containsMatchIn(text)) return title
    return "$text…"
}

/** How long a running status read from an agent *record* is believed (Dart `paseoRecordBusyFreshFor`, 3 min). */
public const val PASEO_RECORD_BUSY_FRESH_FOR_MS: Long = 3 * 60 * 1000L

/** Busy needs live evidence (B-AGENT-176…179): a loaded snapshot (`activeTurn` key present) or a fresh record. */
public fun paseoSessionStatus(agent: JsonObject, nowMs: Long): SessionStatus {
    if (!busyWord(agent["status"])) return SessionStatus.Idle
    // A loaded agent "initializing" with no turn is the daemon loading it, not a run.
    if (agent["status"].stringOrNull() == "initializing" && agent.containsKey("activeTurn") && agent["activeTurn"] is JsonNull) {
        return SessionStatus.Idle
    }
    val until = paseoRecordBusyUntilMs(agent) ?: return SessionStatus.Busy
    return if (nowMs < until) SessionStatus.Busy else SessionStatus.Idle
}

/** When a record's running status stops counting; null when not running or loaded; 0 when `updatedAt` is unreadable. */
public fun paseoRecordBusyUntilMs(agent: JsonObject): Long? {
    if (!busyWord(agent["status"]) || agent.containsKey("activeTurn")) return null
    val updated = paseoMillis(agent["updatedAt"]) ?: return 0L
    return updated + PASEO_RECORD_BUSY_FRESH_FOR_MS
}

private fun busyWord(status: JsonElement?): Boolean = status.stringOrNull().let { it == "initializing" || it == "running" }

// ---- tools

/** The OpenCode tool vocabulary the chat draws (B-AGENT-184: Claude's Task → the sub-agent card). */
public fun paseoToolName(name: String, detail: JsonObject): String =
    when (detail["type"].stringOrNull()) {
        "shell" -> "bash"
        "read" -> "read"
        "edit" -> "edit"
        "write" -> "write"
        "search" -> when (detail["toolName"].stringOrNull()) {
            "glob" -> "glob"
            "web_search" -> "websearch"
            else -> "grep"
        }
        "fetch" -> "webfetch"
        // Claude Code's sub-agent (its Agent tool, Task before): the app's sub-agent card.
        "sub_agent" -> "task"
        "plan" -> "plan"
        "worktree_setup" -> "worktree_setup"
        else -> when {
            detail["type"].stringOrNull() == "plain_text" && name.lowercase() == "skill" -> "skill"
            name.lowercase() in setOf("agent", "task") -> "task"
            name.isEmpty() -> "tool"
            else -> name.lowercase()
        }
    }

/** A stopped step reads Stopped (cancelled), never Failed. */
public fun paseoToolStatus(status: JsonElement?): ToolStatus =
    when (status.stringOrNull()) {
        "completed" -> ToolStatus.Completed
        "canceled", "cancelled" -> ToolStatus.Cancelled
        "failed", "error" -> ToolStatus.Error
        else -> ToolStatus.Running
    }

/** Claude's words for a tool call it was told to stop, as Paseo forwards them in a failed call's error. */
private val INTERRUPT_REPORT = Regex("""^\[Request interrupted by user[^\]]*\]$""")

public fun paseoInterruptedToolCall(item: JsonObject): Boolean {
    if (item["status"].stringOrNull() != "failed") return false
    return INTERRUPT_REPORT.matches(toolErrorText(item).trim())
}

/** What Paseo and Claude Code say when the person answered a permission card with Reject. */
private val DECLINED_REPORT = Regex(
    "^(permission (request )?(denied|canceled)|" +
        "the user doesn't want to proceed|" +
        "the user answered with a message instead of approving|" +
        "user (denied|rejected|declined)|" +
        "permission to use .* has been denied)",
    RegexOption.IGNORE_CASE,
)

/** A failed call the person declined at its permission card (B-AGENT-186). */
public fun paseoDeclinedToolCall(item: JsonObject): Boolean {
    if (item["status"].stringOrNull() != "failed") return false
    // The app knows the person rejected this call's card: whatever reason they typed comes back as the error text.
    if (item["declinedByPerson"].let { it is JsonPrimitive && !it.isString && it.content == "true" }) return true
    return DECLINED_REPORT.containsMatchIn(toolErrorText(item).trim())
}

/** Dart `paseoMergeToolCall`: a later report of the same call merged into the earlier one. */
public fun paseoMergeToolCall(earlier: JsonObject?, later: JsonObject): JsonObject {
    if (earlier == null || earlier["type"].stringOrNull() != "tool_call" || later["type"].stringOrNull() != "tool_call") {
        return later
    }
    fun name(item: JsonObject): String = item["name"].stringOrNull()?.trim() ?: ""
    fun parsed(detail: JsonElement?): Boolean =
        detail is JsonObject && detail["type"].stringOrNull().let { it != null && it != "unknown" }
    val laterName = name(later)
    val earlierName = name(earlier)
    val earlierMeta = earlier["metadata"].objectOrNull()
    val laterMeta = later["metadata"].objectOrNull()
    val interrupted = paseoInterruptedToolCall(later)
    val merged = LinkedHashMap<String, JsonElement>(earlier)
    merged.putAll(later)
    if ((laterName.isEmpty() || laterName.lowercase() == "tool") && earlierName.isNotEmpty()) {
        merged["name"] = earlier["name"] ?: JsonNull
    }
    if (!parsed(later["detail"]) && parsed(earlier["detail"])) merged["detail"] = earlier["detail"]!!
    if (earlierMeta != null || laterMeta != null) {
        merged["metadata"] = JsonObject(LinkedHashMap<String, JsonElement>().apply {
            earlierMeta?.let(::putAll)
            laterMeta?.let(::putAll)
        })
    }
    if (interrupted) {
        merged["status"] = JsonPrimitive("canceled")
        merged["error"] = JsonNull
    }
    // A step that has finished does not start again: a late report that still says it runs is the older news.
    if (!interrupted && paseoToolStatus(earlier["status"]) != ToolStatus.Running && paseoToolStatus(later["status"]) == ToolStatus.Running) {
        merged["status"] = earlier["status"] ?: JsonNull
        merged["error"] = earlier["error"] ?: JsonNull
    }
    return JsonObject(merged)
}

// ---- timeline items → messages

/** A stable message id: the item's own (`messageId`, `clientMessageId`, `callId`) or `<u|a|r|t|i>:<seq>`. */
public fun paseoItemId(item: JsonObject, fallbackSeq: Long?): String {
    val type = item["type"].stringOrNull()
    val own = when (type) {
        "user_message" -> item["messageId"].present() ?: item["clientMessageId"]
        "assistant_message" -> item["messageId"]
        "tool_call" -> item["callId"]
        else -> null
    }.stringOrNull()
    if (own != null && own.isNotEmpty() && own.length <= 256) return own
    val prefix = when (type) {
        "user_message" -> "u"
        "assistant_message" -> "a"
        "reasoning" -> "r"
        "tool_call" -> "t"
        else -> "i"
    }
    return "$prefix:${fallbackSeq ?: 0}"
}

/** An agent's own words for a sign-in it can no longer use (not the transient "another process is refreshing"). */
private val SIGN_IN_FAILURE = Regex(
    "failed to authenticate|oauth (session|token) (has )?expired|" +
        "could not be refreshed|please run /login|invalid api key|" +
        "authentication_error|not logged in",
    RegexOption.IGNORE_CASE,
)

/** How Paseo's ACP bridge words an agent's RPC failure in the reply text: an error, never prose. */
private fun systemError(text: String): Boolean = text.trimStart().startsWith("[System Error]")

private val SIGN_IN_ASK = Regex("""\b(sign|log) ?in\b""", RegexOption.IGNORE_CASE)

/** Claude Code's own line when a call to Anthropic failed and the turn stopped. */
private val CLAUDE_TURN_FAILURE = Regex("""^\s*(?:API Error\b|Claude AI usage limit reached\b|Credit balance is too low\b)""")

/** One timeline item as a message (null for kinds with nothing to show). Error-ish replies become error rows. */
public fun paseoItemMessage(
    agentId: String,
    item: JsonObject,
    id: String,
    provider: String,
    createdMs: Long?,
    completedMs: Long?,
): MessageWithParts? {
    val type = item["type"].stringOrNull() ?: return null
    fun error(text: String, kind: MessageErrorKind?) = MessageWithParts(
        MessageInfo(
            id = id,
            sessionId = agentId,
            role = Role.Assistant,
            providerId = provider,
            time = MsgTime(createdMs, completedMs ?: createdMs),
            errorText = text,
            errorKind = kind,
        ),
        persistentListOf(),
    )
    // An agent that can't sign in, the bridge's error report, or Claude Code's failed-turn line: error rows whose
    // words only reach Details.
    if (type == "assistant_message") {
        val text = paseoText(item["text"])
        val system = systemError(text)
        val signIn = SIGN_IN_FAILURE.containsMatchIn(text) || (system && SIGN_IN_ASK.containsMatchIn(text))
        if (signIn || system || CLAUDE_TURN_FAILURE.containsMatchIn(text)) {
            return error(paseoText(item["text"], max = 2000), if (signIn) MessageErrorKind.ProviderAuth else null)
        }
    }
    fun newPart(partType: PartType, rawType: String, text: String = "", toolName: String? = null, tool: ToolState? = null, callId: String? = null) =
        Part(
            id = "$id:0",
            messageId = id,
            sessionId = agentId,
            type = partType,
            rawType = rawType,
            text = text,
            callId = callId,
            toolName = toolName,
            tool = tool,
        )
    val part: Part = when (type) {
        "user_message", "assistant_message" -> newPart(PartType.Text, "text", text = paseoText(item["text"]))
        "reasoning" -> newPart(PartType.Reasoning, "reasoning", text = paseoText(item["text"]))
        "tool_call" -> {
            val status = if (paseoInterruptedToolCall(item)) ToolStatus.Cancelled else paseoToolStatus(item["status"])
            val detail = item.obj("detail") ?: EMPTY
            val toolName = paseoToolName(paseoText(item["name"], max = 256), detail)
            val notice = toolName.trim().lowercase() == "task_notification"
            val input: JsonElement = if (notice) taskNoticeInput(item, detail) else toolInput(detail)
            val output: JsonElement = when {
                status == ToolStatus.Cancelled -> JsonPrimitive("")
                notice -> JsonPrimitive(taskNoticeOutput(detail))
                else -> toolOutput(item, detail)
            }
            val error: JsonElement? = if (status == ToolStatus.Error) {
                // The card reads this as the person's choice, not a failure.
                if (paseoDeclinedToolCall(item)) JsonPrimitive(DECLINED_TEXT) else toolOutput(item, detail)
            } else {
                null
            }
            val metadata = withBackgroundTask(toolMetadata(detail), toolName, status, detail)
            newPart(PartType.Tool, "tool", toolName = toolName, callId = id, tool = toolState(status, input, output, error, metadata))
        }
        "todo" -> {
            val todos = item.arr("items") ?: JsonArray(emptyList())
            val rows = todos.take(200).mapNotNull { raw ->
                val row = raw.objectOrNull() ?: return@mapNotNull null
                if (paseoText(row["text"]).trim().isEmpty()) return@mapNotNull null
                val state = row["status"].stringOrNull()
                jsonOf(
                    "content" to paseoText(row["text"], max = 2000),
                    "status" to when (state) {
                        "pending", "in_progress", "completed" -> state
                        else -> if (row["completed"].let { it is JsonPrimitive && !it.isString && it.content == "true" }) "completed" else "pending"
                    },
                )
            }
            newPart(
                PartType.Tool,
                "tool",
                toolName = "todowrite",
                callId = id,
                tool = toolState(ToolStatus.Completed, jsonOf("todos" to rows), JsonPrimitive(""), null, null),
            )
        }
        "compaction" -> {
            // The notice OpenCode 2's compaction draws: a line in the transcript, not an assistant reply.
            val done = item["status"].stringOrNull() == "completed"
            val pre = item["preTokens"].numberOrNull()
            val trigger = item["trigger"].stringOrNull()?.takeIf { it == "auto" || it == "manual" }
            val meta = LinkedHashMap<String, JsonElement>()
            if (trigger != null) meta["trigger"] = JsonPrimitive(trigger)
            if (done && pre != null && pre > 0) meta["preTokens"] = JsonPrimitive(pre.roundToLong())
            return MessageWithParts(
                MessageInfo(id = id, sessionId = agentId, role = Role.User, time = MsgTime(createdMs, completedMs)),
                persistentListOf(
                    newPart(
                        PartType.Compaction,
                        "v2:compaction",
                        text = if (done) "Conversation shortened." else "Shortening conversation…",
                        toolName = if (done) "completed" else "running",
                        tool = ToolState(status = ToolStatus.Completed, metadata = JsonObject(meta)),
                    ),
                ),
            )
        }
        "notification" -> {
            val level = when (item["level"].stringOrNull()) {
                "warning" -> "warning"
                "error" -> "error"
                else -> "info"
            }
            val message = paseoText(item["message"], max = 4000)
            if (message.trim().isEmpty()) return null
            return MessageWithParts(
                MessageInfo(id = id, sessionId = agentId, role = Role.User, time = MsgTime(createdMs, completedMs ?: createdMs)),
                persistentListOf(newPart(PartType.Notice, "v2:notice", text = message, toolName = "agent-$level")),
            )
        }
        "error" -> {
            // The agent's own words go to the error row's details; the row says what happened in the app's words.
            val words = paseoText(item["message"], max = 4000)
            return error(if (words.trim().isEmpty()) "The agent could not finish this reply." else words, null)
        }
        else -> return null
    }
    val user = type == "user_message"
    return MessageWithParts(
        MessageInfo(
            id = id,
            sessionId = agentId,
            role = if (user) Role.User else Role.Assistant,
            providerId = if (user) null else provider,
            time = MsgTime(createdMs, completedMs),
        ),
        persistentListOf(part),
    )
}

/** A mapped history and each tool call's merged item by call id (so later stream reports merge into it). */
public class PaseoTimeline(public val messages: ImmutableList<MessageWithParts>, public val tools: Map<String, JsonObject>)

/**
 * Dart `paseoTimelineMessages` for a `fetch_agent_timeline_response` payload, oldest first; entries sharing an id
 * collapse to the latest, a re-reported tool call merges into its first report. Throws
 * PaseoException(InvalidResponse, reason = "fetch_agent_timeline_response") when the payload does not map.
 */
public fun paseoTimeline(agentId: String, payload: JsonObject, busy: Boolean): PaseoTimeline =
    paseoReply("fetch_agent_timeline_response") {
        val result = LinkedHashMap<String, MessageWithParts>()
        val tools = LinkedHashMap<String, JsonObject>()
        val toolCreated = HashMap<String, Long?>()
        val entries = paseoList(payload["entries"], max = 20_000)
        for (i in entries.indices) {
            val entry = entries[i].objectOrNull() ?: continue
            var item = entry["item"].objectOrNull() ?: continue
            val seq = entry["seqStart"].longOrNull()
            val id = paseoItemId(item, seq ?: i.toLong())
            val at = paseoMillis(entry["timestamp"])
            val last = i == entries.size - 1
            val isTool = item["type"].stringOrNull() == "tool_call"
            val earlier = if (isTool) tools[id] else null
            if (isTool) {
                item = paseoMergeToolCall(earlier, item)
                tools[id] = item
                if (!toolCreated.containsKey(id)) toolCreated[id] = at
            }
            val message = paseoItemMessage(
                agentId,
                item,
                id = id,
                provider = paseoText(entry["provider"], max = 128),
                createdMs = if (earlier != null) toolCreated[id] else at,
                // Only the newest item of a running agent can still be streaming.
                completedMs = if (busy && last) null else at,
            ) ?: continue
            if (earlier == null || !result.containsKey(id)) result.remove(id)
            result[id] = message
        }
        PaseoTimeline(result.values.toImmutableList(), tools)
    }

/** An agent's own words for a sign-in it can no longer use (Dart `paseoSignInFailure`). */
public fun paseoSignInFailure(text: String): Boolean = SIGN_IN_FAILURE.containsMatchIn(text)

// ---- pictures (B-AGENT-170)

/** Why a prompt's attachments cannot go to a Paseo agent (words in :ui:words). */
public enum class PaseoImageProblem { TooMany, NotPicture, TooLarge, UnsupportedType }

/** The prompt's pictures as Paseo `images` (`{data, mimeType}`, type sniffed from the bytes), or the problem. */
public sealed interface PaseoImages {
    public class Ok(public val images: List<JsonObject>) : PaseoImages

    public class Refused(public val problem: PaseoImageProblem) : PaseoImages
}

private val DATA_URL = Regex("""^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$""", RegexOption.DOT_MATCHES_ALL)

/** Paseo's Claude adapter forwards an image only when its type is one of these four (isImageMimeType). */
private val CLAUDE_IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

public fun paseoImages(attachments: List<PromptAttachment>): PaseoImages {
    if (attachments.size > 8) return PaseoImages.Refused(PaseoImageProblem.TooMany)
    val images = mutableListOf<JsonObject>()
    for (attachment in attachments) {
        val match = DATA_URL.matchEntire(attachment.url)
        if (!attachment.mime.startsWith("image/") || match == null) return PaseoImages.Refused(PaseoImageProblem.NotPicture)
        val data = match.groupValues[2]
        if (data.length > 14 * 1024 * 1024) return PaseoImages.Refused(PaseoImageProblem.TooLarge)
        // The type sent is the one the bytes really are; an unsupported picture is refused here.
        val declared = match.groupValues[1].lowercase()
        val type = sniffImageType(data) ?: if (declared == "image/jpg") "image/jpeg" else declared
        if (type !in CLAUDE_IMAGE_TYPES) return PaseoImages.Refused(PaseoImageProblem.UnsupportedType)
        images += jsonOf("data" to data, "mimeType" to type)
    }
    return PaseoImages.Ok(images)
}

/** The picture type the first bytes of [base64Data] show, or null if unknown. */
private fun sniffImageType(base64Data: String): String? {
    val head = if (base64Data.length < 32) base64Data else base64Data.substring(0, 32)
    // 32 base64 characters are 24 bytes, enough for every signature below.
    val b = try {
        Base64.Default.decode(head)
    } catch (_: IllegalArgumentException) {
        return null
    }
    fun starts(sig: IntArray, at: Int = 0): Boolean {
        if (b.size < at + sig.size) return false
        return sig.indices.all { (b[at + it].toInt() and 0xFF) == sig[it] }
    }
    fun ascii(text: String) = IntArray(text.length) { text[it].code }
    return when {
        starts(intArrayOf(137, 80, 78, 71, 13, 10, 26, 10)) -> "image/png"
        starts(intArrayOf(255, 216, 255)) -> "image/jpeg"
        starts(ascii("GIF8")) -> "image/gif"
        starts(ascii("RIFF")) && starts(ascii("WEBP"), 8) -> "image/webp"
        else -> null
    }
}

/** A JSON value that Dart's `??` would keep (absent and null fall through). */
private fun JsonElement?.present(): JsonElement? = this?.takeUnless { it is JsonNull }

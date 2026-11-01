package io.github.eslamasabry.oc.protocol.paseo.mappers

import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionContextUse
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * FROZEN SIGNATURES (S3-PASEO slice A owns the bodies). Pure ports of lib/paseo/mappers.dart: no I/O, no clock
 * (callers pass nowMs), no copy (fixed English stand-ins only where Dart writes a value the model carries, e.g. the
 * "<Runtime> conversation" title stand-in). Strict reads throw PaseoException(InvalidResponse).
 */

// ---- agents → sessions

/** Dart `paseoSession`: one daemon agent is one session; model `provider/model`, mode as agent, cost, context use. */
public fun paseoSession(agent: JsonObject): Session = TODO("slice A")

/** Dart `paseoProviderName`: claude → Claude Code, codex → Codex, opencode → OpenCode, copilot → GitHub Copilot, pi → Pi. */
public fun paseoProviderName(provider: String): String = TODO("slice A")

public fun paseoContextUse(usage: JsonElement?): SessionContextUse? = TODO("slice A")

/** A 58–60 character title that does not end like a sentence gets an ellipsis (B-AGENT-183). */
public fun paseoClippedTitle(title: String): String = TODO("slice A")

/** How long a running status read from an agent *record* is believed (Dart `paseoRecordBusyFreshFor`, 3 min). */
public const val PASEO_RECORD_BUSY_FRESH_FOR_MS: Long = 3 * 60 * 1000L

/** Busy needs live evidence (B-AGENT-176…179): a loaded snapshot (`activeTurn` key present) or a fresh record. */
public fun paseoSessionStatus(agent: JsonObject, nowMs: Long): SessionStatus = TODO("slice A")

/** When a record's running status stops counting; null when not running or loaded; 0 when `updatedAt` is unreadable. */
public fun paseoRecordBusyUntilMs(agent: JsonObject): Long? = TODO("slice A")

// ---- tools

/** The OpenCode tool vocabulary the chat draws (B-AGENT-184: Claude's Task → the sub-agent card). */
public fun paseoToolName(name: String, detail: JsonObject): String = TODO("slice A")

public fun paseoToolStatus(status: JsonElement?): ToolStatus = TODO("slice A")

public fun paseoInterruptedToolCall(item: JsonObject): Boolean = TODO("slice A")

/** A failed call the person declined at its permission card (B-AGENT-186). */
public fun paseoDeclinedToolCall(item: JsonObject): Boolean = TODO("slice A")

/** Dart `paseoMergeToolCall`: a later report of the same call merged into the earlier one. */
public fun paseoMergeToolCall(earlier: JsonObject?, later: JsonObject): JsonObject = TODO("slice A")

// ---- timeline items → messages

/** A stable message id: the item's own (`messageId`, `clientMessageId`, `callId`) or `<u|a|r|t|i>:<seq>`. */
public fun paseoItemId(item: JsonObject, fallbackSeq: Long?): String = TODO("slice A")

/** One timeline item as a message (null for kinds with nothing to show). Error-ish replies become error rows. */
public fun paseoItemMessage(
    agentId: String,
    item: JsonObject,
    id: String,
    provider: String,
    createdMs: Long?,
    completedMs: Long?,
): MessageWithParts? = TODO("slice A")

/** A mapped history and each tool call's merged item by call id (so later stream reports merge into it). */
public class PaseoTimeline(public val messages: ImmutableList<MessageWithParts>, public val tools: Map<String, JsonObject>)

/**
 * Dart `paseoTimelineMessages` for a `fetch_agent_timeline_response` payload, oldest first; entries sharing an id
 * collapse to the latest, a re-reported tool call merges into its first report. Throws
 * PaseoException(InvalidResponse, reason = "fetch_agent_timeline_response") when the payload does not map.
 */
public fun paseoTimeline(agentId: String, payload: JsonObject, busy: Boolean): PaseoTimeline = TODO("slice A")

/** An agent's own words for a sign-in it can no longer use (Dart `paseoSignInFailure`). */
public fun paseoSignInFailure(text: String): Boolean = TODO("slice A")

// ---- pictures (B-AGENT-170)

/** Why a prompt's attachments cannot go to a Paseo agent (words in :ui:words). */
public enum class PaseoImageProblem { TooMany, NotPicture, TooLarge, UnsupportedType }

/** The prompt's pictures as Paseo `images` (`{data, mimeType}`, type sniffed from the bytes), or the problem. */
public sealed interface PaseoImages {
    public class Ok(public val images: List<JsonObject>) : PaseoImages

    public class Refused(public val problem: PaseoImageProblem) : PaseoImages
}

public fun paseoImages(attachments: List<PromptAttachment>): PaseoImages = TODO("slice A")

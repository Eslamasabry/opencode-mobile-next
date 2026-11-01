package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.ids.MessageId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Dart PromptAttachment: `url` is a data: URL or a server file URL; the mention fields tie it to `@path` text. */
public data class PromptAttachment(
    val mime: String,
    val filename: String,
    val url: String,
    val mentionPath: String? = null,
    val mentionStart: Int? = null,
    val mentionEnd: Int? = null,
)

public data class PromptAgentMention(val name: String, val value: String, val start: Int, val end: Int)

/**
 * How a prompt reaches a running turn (Dart PromptDelivery). The composer offers [Steer] wherever it exists (owner
 * rule); [Queue] is the legacy OC2 wire option.
 */
public enum class PromptDelivery { Steer, Queue }

/** Model choice for one send: model + optional variant (Dart passes them separately). */
public data class ModelSelection(val model: ModelRef, val variant: String? = null)

/** One prompt as the delivery pipeline hands it to a gateway (plan 04 §11.1 `OutgoingPrompt`). */
public data class OutgoingPrompt(
    val text: String,
    val model: ModelSelection? = null,
    val agent: String? = null,
    val attachments: ImmutableList<PromptAttachment> = persistentListOf(),
    val mentions: ImmutableList<PromptAgentMention> = persistentListOf(),
    val delivery: PromptDelivery? = null,
    /** Set only on gateways with `clientPromptMessageID` (CorrelatedPromptGateway). */
    val clientMessageId: MessageId? = null,
)

/** What a send proved. Never "completed" (plan 04 §11.1). */
public sealed interface PromptAdmission {
    public data class Confirmed(val receiptId: String?) : PromptAdmission

    /** The response was lost: look it up (command receipts); never resend automatically. */
    public data object Uncertain : PromptAdmission
}

public enum class StopResult { Stopped, NothingRunning, Uncertain }

/** Dart MidTurnPrompt: what a message sent mid-turn does on gateways that never queue. */
public enum class MidTurnPrompt { JoinsTurn, RestartsTurn }

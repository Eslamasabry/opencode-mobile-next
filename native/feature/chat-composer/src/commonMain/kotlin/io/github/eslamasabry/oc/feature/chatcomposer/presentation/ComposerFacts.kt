package io.github.eslamasabry.oc.feature.chatcomposer.presentation

import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import kotlin.time.Instant

/** Why the conversation takes no prompts from here; the pill shows the reason and hides Send, + and the mic. */
enum class ReadOnlyReason { SubAgent }

/** The approval chip (plan 09 §10.3): filled shield "Auto-approve", outline "Ask first", "Auto-approve paused". */
enum class ApprovalMode { AsksFirst, AutoApprove, Paused }

/**
 * What the model chip knows (plan 09 §10.3, B-CHAT-066/067, B-KIT-057). Ids are the wire ids; the label never shows
 * them raw. [modelId] null = no model picked for this conversation (the server default applies).
 */
data class ModelFacts(
    val modelId: String? = null,
    /** The catalog's name for [modelId]; null while the catalog loads or when the catalog lacks the model. */
    val catalogName: String? = null,
    /** A name seen before for [modelId] (kept across restarts by the model engine). */
    val knownName: String? = null,
    val catalogLoading: Boolean = false,
    /** The server's default model by name, for a conversation with no model picked. */
    val serverDefaultName: String? = null,
    /** False: no provider signed in, before the first send ("No model signed in"). */
    val signedIn: Boolean = true,
    /** The catalog's release date, for the "new" mark (< 30 days). */
    val released: Instant? = null,
    /** The chosen effort (`high`, `xhigh`…), shown only when the model offers a real choice. */
    val effort: String? = null,
    val effortIsChoice: Boolean = false,
)

/**
 * Everything the composer reads from the engines for one conversation, as plain values (plan 09 §6.3, §10.2). The
 * port fills it from capabilities, the delivery engine, the request engine and settings; the UI never asks a backend.
 */
data class ComposerFacts(
    /** A reply runs in this conversation. */
    val busy: Boolean = false,
    /** A request waits: the card owns the composer (no glow, no Stop). */
    val requestWaiting: Boolean = false,
    /** `capabilities.promptSteering` for this conversation. */
    val steering: Boolean = false,
    /** What a prompt sent mid-turn does where steering does not exist; null = unknown, promise nothing. */
    val midTurn: MidTurnPrompt? = null,
    val offline: Boolean = false,
    val readOnly: ReadOnlyReason? = null,
    /** The prompt is being got ready (transport, model choice, storage). */
    val sending: Boolean = false,
    /** The running step looks stuck: the same Stop turns amber. */
    val stuck: Boolean = false,
    val model: ModelFacts = ModelFacts(),
    val approval: ApprovalMode = ApprovalMode.AsksFirst,
    /** `oc.effectsActivityGlow` (absent reads as on, B-CHAT-006). */
    val glowSetting: Boolean = true,
    /** Voice input can run here; false hides the mic (the field then shows Send only once there is text). */
    val micAvailable: Boolean = true,
)

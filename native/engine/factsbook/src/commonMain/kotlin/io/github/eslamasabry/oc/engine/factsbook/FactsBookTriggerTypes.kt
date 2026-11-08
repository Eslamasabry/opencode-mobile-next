package io.github.eslamasabry.oc.engine.factsbook

import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Value types of the node's local event triggers (spec 44, App API 1.3; F: factsbook_trigger_types.dart): rules,
 * tests, events, firings, reminder proposals and conversation offers. Unknown keys are ignored and an unknown word
 * falls to the cautious reading. No type here ever holds the raw text of an event: the node does not return it.
 */

/** Where an event comes from. Notifications are not a source in this milestone. */
public enum class TriggerSourceKind(public val wire: String, public val defaultInstance: String) {
    Note("note", "manual-note"),
    Share("share", "manual-share"),
    ChatSelection("chat_selection", "selected-chat");

    public companion object {
        public fun parse(raw: String?): TriggerSourceKind = entries.firstOrNull { it.wire == raw } ?: Share
    }
}

/** An authenticated client, a kind and an opaque instance: the rule's consent. A claimed sender never is. */
public data class TriggerSource(val clientId: String, val kind: TriggerSourceKind, val instance: String) {
    public fun toJson(): JsonObject =
        JsonObject(mapOf("client_id" to JsonPrimitive(clientId), "kind" to JsonPrimitive(kind.wire), "instance" to JsonPrimitive(instance)))

    internal companion object {
        fun read(json: JsonObject?): TriggerSource =
            TriggerSource(json?.str("client_id").orEmpty(), TriggerSourceKind.parse(json?.str("kind")), json?.str("instance").orEmpty())
    }
}

/** `TriggerTemplate.id`. [BillDue] is reserved: the node refuses it until money storage is qualified. */
public enum class TriggerTemplateId(public val wire: String) {
    Appointment("appointment"),
    Delivery("delivery"),
    Booking("booking"),
    Subscription("subscription"),
    BillDue("bill_due");

    public companion object {
        public fun parse(raw: String?): TriggerTemplateId = entries.firstOrNull { it.wire == raw } ?: Appointment

        /** What the editor offers when the node does not list its own. */
        public val offered: List<TriggerTemplateId> = listOf(Appointment, Delivery, Booking, Subscription)
    }
}

/** The versioned deterministic reading of a message; [locale] is `en` or `ar`. */
public data class TriggerTemplate(val id: TriggerTemplateId, val version: Long = CURRENT_VERSION, val locale: String = "en") {
    public fun toJson(): JsonObject =
        JsonObject(mapOf("id" to JsonPrimitive(id.wire), "version" to JsonPrimitive(version), "locale" to JsonPrimitive(locale)))

    public companion object {
        public const val CURRENT_VERSION: Long = 2

        internal fun read(json: JsonObject?): TriggerTemplate =
            TriggerTemplate(
                TriggerTemplateId.parse(json?.str("id")),
                json?.long("version") ?: CURRENT_VERSION,
                if (json?.str("locale") == "ar") "ar" else "en",
            )
    }
}

public enum class TriggerOutcomeKind(public val wire: String) { Fact("fact"), Reminder("reminder"), Conversation("conversation") }

/** A rule's outcome. [raw] keeps a fact outcome the app does not build, so it is sent back exactly as given. */
public data class TriggerOutcome(val kind: TriggerOutcomeKind, val raw: JsonObject? = null) {
    public fun toJson(): JsonObject = raw ?: JsonObject(mapOf("kind" to JsonPrimitive(kind.wire)))

    internal companion object {
        fun read(json: JsonObject): TriggerOutcome? =
            TriggerOutcomeKind.entries.firstOrNull { it.wire == json.str("kind") }?.let { TriggerOutcome(it, json) }
    }
}

/** `RuleDefinition` (format version 1). */
public data class RuleDefinition(
    val name: String,
    val enabled: Boolean,
    val source: TriggerSource,
    val template: TriggerTemplate,
    val area: String,
    /** Literal, normalized substrings that must all appear. Not regular expressions. */
    val containsAll: List<String>,
    val outcomes: List<TriggerOutcome>,
    val limitPerDay: Long,
    /** An exact match on the sender an untrusted message claims: a filter, never proof. */
    val senderClaim: String? = null,
) {
    public fun hasOutcome(kind: TriggerOutcomeKind): Boolean = outcomes.any { it.kind == kind }

    public fun toJson(): JsonObject =
        JsonObject(
            buildMap {
                put("v", JsonPrimitive(1))
                put("name", JsonPrimitive(name))
                put("enabled", JsonPrimitive(enabled))
                put("source", source.toJson())
                put("template", template.toJson())
                put("area", JsonPrimitive(area))
                put("contains_all", JsonArray(containsAll.map(::JsonPrimitive)))
                put("outcomes", JsonArray(outcomes.map { it.toJson() }))
                put("limit_per_day", JsonPrimitive(limitPerDay))
                // The node accepts only false until automatic starts are qualified.
                put("auto_start", JsonPrimitive(false))
                senderClaim?.trim()?.takeIf { it.isNotEmpty() }?.let { put("sender_claim", JsonPrimitive(senderClaim)) }
            }
        )

    internal companion object {
        fun read(json: JsonObject?): RuleDefinition =
            RuleDefinition(
                name = json?.str("name").orEmpty(),
                enabled = (json?.get("enabled") as? JsonPrimitive)?.content != "false",
                source = TriggerSource.read(json?.obj("source")),
                template = TriggerTemplate.read(json?.obj("template")),
                area = json?.str("area") ?: "general",
                containsAll = json?.strings("contains_all").orEmpty(),
                outcomes = json?.arr("outcomes").orEmpty().mapNotNull { (it as? JsonObject)?.let(TriggerOutcome::read) },
                limitPerDay = json?.long("limit_per_day") ?: 20,
                senderClaim = json?.str("sender_claim"),
            )
    }
}

/** What is wrong with a draft rule before the node is asked (the node checks all of it again). */
public enum class RuleProblem { NameEmpty, NameTooLong, TooManyWords, WordEmpty, WordTooLong, NoOutcome, TemplateUnavailable, LimitOutOfRange, SenderClaimInvalid }

/** The rules of a rule, from the contract (F: `RuleRules`). */
public object RuleRules {
    public const val MAX_NAME_BYTES: Int = 80
    public const val MAX_WORDS: Int = 4
    public const val MAX_WORD_BYTES: Int = 40
    public const val MAX_PER_DAY: Long = 20

    public fun bytes(text: String): Int = text.encodeToByteArray().size

    /** Splits a typed line on commas (Latin and Arabic) and line breaks; trims, drops empties and repeats. */
    public fun splitWords(text: String): List<String> {
        val seen = mutableSetOf<String>()
        return text.split(Regex("[,،\n]")).map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }

    public fun check(rule: RuleDefinition, templates: Set<TriggerTemplateId> = TriggerTemplateId.offered.toSet()): List<RuleProblem> =
        buildList {
            if (rule.name.trim().isEmpty()) add(RuleProblem.NameEmpty)
            if (bytes(rule.name.trim()) > MAX_NAME_BYTES) add(RuleProblem.NameTooLong)
            if (rule.containsAll.size > MAX_WORDS) add(RuleProblem.TooManyWords)
            if (rule.containsAll.any { it.trim().isEmpty() }) add(RuleProblem.WordEmpty)
            if (rule.containsAll.any { bytes(it) > MAX_WORD_BYTES }) add(RuleProblem.WordTooLong)
            if (rule.outcomes.isEmpty()) add(RuleProblem.NoOutcome)
            if (rule.template.id !in templates) add(RuleProblem.TemplateUnavailable)
            if (rule.limitPerDay !in 1..MAX_PER_DAY) add(RuleProblem.LimitOutOfRange)
            val claim = rule.senderClaim
            if (claim != null && (claim.trim().isEmpty() || bytes(claim.trim()) > MAX_NAME_BYTES)) add(RuleProblem.SenderClaimInvalid)
        }
}

/** A stored rule at a revision. A deleted rule keeps its history but is not listed. */
public data class Rule(val id: String, val revision: Long, val definition: RuleDefinition, val updatedAt: Instant, val deleted: Boolean = false) {
    internal companion object {
        fun read(json: JsonObject): Rule =
            Rule(json.need("id"), json.long("rev") ?: 0, RuleDefinition.read(json.obj("definition")), json.time("updated_at"), json.isTrue("deleted"))
    }
}

/** What the sender said it was. A claim, never proof. */
public data class EventClaims(val app: String? = null, val sender: String? = null)

/** The sample a rule is tested on. Nothing is stored. */
public data class TriggerSample(
    val kind: TriggerSourceKind,
    val text: String,
    val area: String,
    val locale: String,
    val sourceInstance: String,
    val messageKey: String = "sample",
    val revision: Long = 1,
) {
    public fun toJson(): JsonObject =
        JsonObject(
            mapOf(
                "kind" to JsonPrimitive(kind.wire),
                "text" to JsonPrimitive(text),
                "area" to JsonPrimitive(area),
                "locale" to JsonPrimitive(locale),
                "source_instance" to JsonPrimitive(sourceInstance),
                "message_key" to JsonPrimitive(messageKey),
                "revision" to JsonPrimitive(revision),
            )
        )

    override fun toString(): String = "TriggerSample(kind=$kind)"
}

/** Why an event or a test came out as it did. An unknown word reads as [Uncertain]. */
public enum class TriggerReason(public val wire: String) {
    Matched("matched"),
    NoMatch("no_match"),
    Uncertain("uncertain"),
    SensitiveUnavailable("sensitive_unavailable"),
    SourceCap("source_cap"),
    VaultCap("vault_cap"),
    RuleCap("rule_cap"),
    StormCollapsed("storm_collapsed"),
    NoEnabledRule("no_enabled_rule"),
    TargetStale("target_stale"),
    TargetMissing("target_missing"),
    TargetDenied("target_denied"),
    Rejected("rejected");

    public companion object {
        public fun parse(raw: String?): TriggerReason = entries.firstOrNull { it.wire == raw } ?: Uncertain
    }
}

/** What a rule would do with a sample. Nothing was saved. [whenText] is local ISO text. */
public data class RuleTestResult(val matched: Boolean, val reason: TriggerReason, val summary: String? = null, val whenText: String? = null) {
    internal companion object {
        fun read(json: JsonObject): RuleTestResult =
            RuleTestResult(json.isTrue("matched"), TriggerReason.parse(json.str("reason")), json.str("summary"), json.str("when"))
    }
}

/** An unknown word reads as untrusted. */
public enum class EventTrust(public val wire: String) {
    UserAuthored("user_authored"),
    UserSelected("user_selected"),
    ThirdPartyUntrusted("third_party_untrusted");

    public companion object {
        public fun parse(raw: String?): EventTrust = entries.firstOrNull { it.wire == raw } ?: ThirdPartyUntrusted
    }
}

/** Metadata of one message rules looked at; never its text. */
public data class TriggerEvent(
    val id: String,
    val source: TriggerSource,
    val claims: EventClaims,
    val trust: EventTrust,
    val area: String,
    /** `sens` was not `normal` (sensitive or uncertain). */
    val sensitive: Boolean,
    val capturedAt: Instant,
    val reason: TriggerReason,
    val firings: List<String>,
) {
    internal companion object {
        fun read(json: JsonObject): TriggerEvent {
            val claims = json.obj("claims")
            return TriggerEvent(
                id = json.need("id"),
                source = TriggerSource.read(json.obj("source")),
                claims = EventClaims(claims?.str("app"), claims?.str("sender")),
                trust = EventTrust.parse(json.str("trust")),
                area = json.str("area") ?: "general",
                sensitive = json.str("sens") != "normal",
                capturedAt = json.time("captured_at"),
                reason = TriggerReason.parse(json.obj("processing")?.str("reason")),
                firings = json.strings("firings"),
            )
        }
    }
}

public enum class FiringState { Proposed, Capped, Collapsed, Suppressed }

/** One rule acting on one event. [outcomes] are the ids of the review cards, proposals and offers it made. */
public data class TriggerFiring(
    val id: String,
    val eventId: String,
    val ruleId: String,
    val template: TriggerTemplate,
    val at: Instant,
    val state: FiringState,
    val outcomes: List<String>,
    /** How many more messages were folded into this one (a burst). */
    val collapsedCount: Long = 0,
) {
    internal companion object {
        fun read(json: JsonObject): TriggerFiring =
            TriggerFiring(
                id = json.need("id"),
                eventId = json.need("event_id"),
                ruleId = json.need("rule_id"),
                template = TriggerTemplate.read(json.obj("template")),
                at = json.time("at"),
                state = FiringState.entries.firstOrNull { it.name.lowercase() == json.str("state") } ?: FiringState.Suppressed,
                outcomes = json.strings("outcomes"),
                collapsedCount = json.long("collapsed_count") ?: 0,
            )
    }
}

/** A reminder a rule suggests. It is never a schedule until the person confirms exactly this [preview]. */
public data class ReminderProposal(
    val id: String,
    val revision: Long,
    val eventId: String,
    val firingId: String,
    val area: String,
    val preview: ReminderPreview,
    val digest: String,
    val proposed: Boolean,
) {
    internal companion object {
        fun read(json: JsonObject): ReminderProposal =
            ReminderProposal(
                id = json.need("id"),
                revision = json.long("rev") ?: 0,
                eventId = json.str("event_id").orEmpty(),
                firingId = json.str("firing_id").orEmpty(),
                area = json.str("area") ?: "general",
                preview = ReminderPreview.read(json.obj("preview") ?: throw FactsBookShapeError("preview")),
                digest = json.need("digest"),
                proposed = json.str("state") == "proposed",
            )
    }
}

public enum class ProposalDecision(public val wire: String) { Confirm("confirm"), Reject("reject"), Skip("skip") }

/** "Talk about this with your agent?": only [summary] may seed a chat; the raw message is never part of an offer. */
public data class ConversationOffer(
    val id: String,
    val revision: Long,
    val eventId: String,
    val firingId: String,
    val area: String,
    val summary: String,
    val digest: String,
    /** `offered` while it can still be answered. */
    val state: String,
    val expiresAt: Instant,
) {
    public fun isOpen(now: Instant): Boolean = state == "offered" && now < expiresAt

    internal companion object {
        fun read(json: JsonObject): ConversationOffer =
            ConversationOffer(
                id = json.need("id"),
                revision = json.long("rev") ?: 0,
                eventId = json.str("event_id").orEmpty(),
                firingId = json.str("firing_id").orEmpty(),
                area = json.str("area") ?: "general",
                summary = json.str("summary").orEmpty(),
                digest = json.need("digest"),
                state = json.str("state") ?: "cancelled",
                expiresAt = json.time("expires_at"),
            )
    }
}

/** How a rule did in the last seven days, counted from its firings. */
public data class RuleActivity(val proposed: Int = 0, val ignored: Int = 0, val offers: Int = 0)

/** The trigger calls of a node (F: the trigger half of FactsBookGateway). Writes to rules and decisions are signed. */
public interface FactsBookTriggerCalls {
    public suspend fun rules(): FbResult<List<Rule>>

    /** `POST /v1/rules` (new) or `PUT /v1/rules/{id}` (a replacement, also how pause works), signed. */
    public suspend fun saveRule(definition: RuleDefinition, replacing: Rule? = null): FbResult<Rule>

    public suspend fun deleteRule(rule: Rule): FbResult<Rule>

    /** `POST /v1/rules/test`: what a draft would do with [sample]. Nothing is written; no proof is needed. */
    public suspend fun testDraft(draft: RuleDefinition, sample: TriggerSample): FbResult<RuleTestResult>

    public suspend fun events(): FbResult<List<TriggerEvent>>

    public suspend fun firings(): FbResult<List<TriggerFiring>>

    /** Only the proposals still waiting. */
    public suspend fun reminderProposals(): FbResult<List<ReminderProposal>>

    public suspend fun decideReminderProposal(proposal: ReminderProposal, decision: ProposalDecision): FbResult<MutationResult>

    /** Replaces a proposal's preview; it stays proposed under a new revision. Signed. */
    public suspend fun editReminderProposal(proposal: ReminderProposal, preview: ReminderPreview): FbResult<MutationResult>

    public suspend fun offers(): FbResult<List<ConversationOffer>>

    public suspend fun cancelOffer(offer: ConversationOffer): FbResult<ConversationOffer>
}

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

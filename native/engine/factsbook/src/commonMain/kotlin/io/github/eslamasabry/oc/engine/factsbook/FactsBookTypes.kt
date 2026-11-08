package io.github.eslamasabry.oc.engine.factsbook

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * Value types of the FactsBook App API v1 (F: lib/domain/factsbook/factsbook_types.dart), read field for field like
 * the Flutter `fromJson`s: unknown keys are ignored (`/v1` only adds fields) and an unknown enum word falls to the
 * most cautious reading. A body that cannot be read becomes `bad_response` at the gateway, never a crash.
 */

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.need(key: String): String = str(key) ?: throw FactsBookShapeError(key)

internal fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

internal fun JsonObject.isTrue(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

internal fun JsonObject.strings(key: String): List<String> =
    arr(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

internal fun JsonObject.time(key: String): Instant = timeOrNull(key) ?: throw FactsBookShapeError(key)

internal fun JsonObject.timeOrNull(key: String): Instant? = str(key)?.let(::parseInstant)

internal fun JsonElement?.isNullish(): Boolean = this == null || this is JsonNull

/** Dart `DateTime.parse` reads a time without a zone as local; the node always sends `Z` or an offset. */
internal fun parseInstant(raw: String): Instant =
    try {
        Instant.parse(raw)
    } catch (e: IllegalArgumentException) {
        throw FactsBookShapeError("time", e)
    }

internal class FactsBookShapeError(what: String, cause: Throwable? = null) : IllegalStateException(what, cause)

internal fun <T> JsonObject.list(key: String, read: (JsonObject) -> T): List<T> =
    arr(key).mapNotNull { (it as? JsonObject)?.let(read) }

/** How a fact came to be known (`Fact.provenance`). An unknown word reads as [Guessed]: never gains trust. */
public enum class FactProvenance(public val wire: String) {
    Told("told"),
    Seen("seen"),
    Guessed("guessed"),
    Calculated("calculated"),
    /** A hand edit whose author is not verified: shown like a guess, never as told. */
    External("external");

    public companion object {
        public fun parse(raw: String?): FactProvenance = entries.firstOrNull { it.wire == raw } ?: Guessed
    }
}

public enum class FactReview(public val wire: String) {
    Proposed("proposed"),
    Approved("approved"),
    AutoSafe("auto_safe"),
    Rejected("rejected");

    public companion object {
        /** Unknown: not approved, so it still reads as waiting. */
        public fun parse(raw: String?): FactReview = entries.firstOrNull { it.wire == raw } ?: Proposed
    }
}

public data class Evidence(val source: String, val observedAt: Instant) {
    internal companion object {
        fun read(json: JsonObject): Evidence = Evidence(json.need("source"), json.time("observed_at"))
    }
}

/** One fact (`Fact`): always with provenance, source and date. */
public data class Fact(
    val id: String,
    val thingId: String,
    val text: String,
    val provenance: FactProvenance,
    val observedAt: Instant,
    val source: String,
    val category: String = "",
    val area: String = "",
    val sensitive: Boolean = false,
    val revision: Long = 0,
    val review: FactReview = FactReview.Approved,
    val support: List<Evidence> = emptyList(),
) {
    val waitingForOk: Boolean get() = review == FactReview.Proposed

    internal companion object {
        fun read(json: JsonObject): Fact =
            Fact(
                id = json.need("id"),
                thingId = json.need("thing_id"),
                text = json.need("text"),
                category = json.str("category").orEmpty(),
                source = json.str("source").orEmpty(),
                observedAt = json.time("observed_at"),
                provenance = FactProvenance.parse(json.str("provenance")),
                area = json.str("area").orEmpty(),
                sensitive = json.str("sens") == "sensitive",
                revision = json.long("rev") ?: 0,
                review = FactReview.parse(json.str("review")),
                support = json.list("support", Evidence::read),
            )
    }
}

public enum class ThingKind(public val wire: String) {
    Person("person"),
    Organisation("organisation"),
    Account("account"),
    Card("card"),
    Bill("bill"),
    Invoice("invoice"),
    Place("place"),
    Project("project"),
    Topic("topic"),
    Event("event"),
    Document("document"),
    Subscription("subscription"),
    Warranty("warranty"),
    Health("health"),
    /** A kind a newer node added. */
    Other("other");

    public companion object {
        public fun parse(raw: String?): ThingKind = entries.firstOrNull { it.wire == raw } ?: Other
    }
}

/** A thing and its facts (`Thing`). */
public data class Thing(
    val id: String,
    val title: String,
    val kind: ThingKind,
    val updatedAt: Instant,
    val area: String = "",
    val sensitive: Boolean = false,
    val revision: Long = 0,
    val facts: List<Fact> = emptyList(),
) {
    /** The newest fact: what a list row's source and date line speaks of. */
    val newestFact: Fact? get() = facts.maxByOrNull { it.observedAt }

    internal companion object {
        fun read(json: JsonObject): Thing =
            Thing(
                id = json.need("id"),
                title = json.str("title").orEmpty(),
                kind = ThingKind.parse(json.str("kind")),
                area = json.str("area").orEmpty(),
                sensitive = json.str("sens") == "sensitive",
                revision = json.long("rev") ?: 0,
                facts = json.list("facts", Fact::read),
                updatedAt = json.time("updated_at"),
            )
    }
}

/** One search result (`SearchHit`): source and date on every hit. */
public data class SearchHit(
    val thingId: String,
    val factId: String,
    val title: String,
    val snippet: String,
    val source: String,
    val provenance: FactProvenance,
    val observedAt: Instant,
    val area: String = "",
) {
    internal companion object {
        fun read(json: JsonObject): SearchHit =
            SearchHit(
                thingId = json.need("thing_id"),
                factId = json.need("fact_id"),
                title = json.str("title").orEmpty(),
                snippet = json.str("snippet").orEmpty(),
                source = json.str("source").orEmpty(),
                provenance = FactProvenance.parse(json.str("provenance")),
                observedAt = json.time("observed_at"),
                area = json.str("area").orEmpty(),
            )
    }
}

/** [lockedNotesSkipped]: locked notes are not searched until unlocked (screen 11 banner). */
public data class SearchResult(val query: String, val hits: List<SearchHit>, val lockedNotesSkipped: Boolean = false)

public enum class CaptureKind(public val wire: String) {
    Note("note"),
    ChatSelection("chat_selection"),
    Share("share"),
    ImportText("import");

    public companion object {
        public fun parse(raw: String?): CaptureKind = entries.firstOrNull { it.wire == raw } ?: Note
    }
}

/** A capture the person sends (`Capture`, `POST /v1/inbox`). */
public data class CaptureRequest(val kind: CaptureKind, val text: String, val area: String? = null) {
    /** The trust the app claims: a typed note is user-authored, a shared or pasted text user-selected. */
    val trust: String
        get() =
            when (kind) {
                CaptureKind.Note -> "user_authored"
                CaptureKind.ImportText -> "imported"
                else -> "user_selected"
            }

    public fun toJson(): JsonObject =
        JsonObject(
            buildMap {
                put("kind", JsonPrimitive(kind.wire))
                put("text", JsonPrimitive(text))
                area?.let { put("area", JsonPrimitive(it)) }
                put("trust", JsonPrimitive(trust))
            }
        )

    internal companion object {
        fun read(json: JsonObject): CaptureRequest =
            CaptureRequest(CaptureKind.parse(json.str("kind")), json.need("text"), json.str("area"))
    }
}

/** `CaptureResult`: saved, or kept in the node's own Not saved yet queue. Never a loss. */
public data class CaptureResult(val saved: Boolean, val itemId: String, val couldntSaveId: String? = null) {
    internal companion object {
        fun read(json: JsonObject): CaptureResult =
            CaptureResult(json.str("status") == "saved", json.need("item_id"), json.str("couldnt_save_id"))
    }
}

/** `MutationResult`: `ok`, `proposed`, `pending` or `skipped`. */
public data class MutationResult(val status: String, val id: String, val revision: Long? = null, val reviewId: String? = null) {
    internal companion object {
        fun read(json: JsonObject): MutationResult =
            MutationResult(json.str("status") ?: "ok", json.need("id"), json.long("rev"), json.str("review_id"))
    }
}

/** `Share`: who else may read an area. */
public data class AreaShare(
    val readByDefault: Boolean = false,
    val allow: List<String> = emptyList(),
    val deny: List<String> = emptyList(),
) {
    internal companion object {
        fun read(json: JsonObject?): AreaShare =
            if (json == null) AreaShare()
            else AreaShare(json.str("default") == "read", json.strings("allow"), json.strings("deny"))
    }
}

/** `Area`: a built-in area with its rules (the contract has no call to create one). */
public data class Area(
    val slug: String,
    /** `storage: sensitive`: the locked folder. */
    val sensitiveStorage: Boolean,
    /** `review: first` (everything waits for the person) versus `auto_safe_only`. */
    val reviewFirst: Boolean,
    val share: AreaShare,
    val brief: Boolean,
    val export: Boolean,
    /** `generic`, `masked` or `full`. */
    val notify: String,
) {
    internal companion object {
        fun read(json: JsonObject): Area =
            Area(
                slug = json.need("slug"),
                sensitiveStorage = json.str("storage") == "sensitive",
                reviewFirst = json.str("review") == "first",
                share = AreaShare.read(json.obj("share")),
                brief = json.isTrue("brief"),
                export = json.isTrue("export"),
                notify = json.str("notify") ?: "generic",
            )
    }
}

/** `CouldntSave.reason_code` (closed list), plus the phone's own refusal when its queue is full. */
public enum class CouldNotSaveReason(public val wire: String) {
    StorageFull("storage_full"),
    ClassificationFailed("classification_failed"),
    ParserError("parser_error"),
    NodeUnreachable("node_unreachable"),
    OutboxFull("outbox_full"),
    CloudQuota("cloud_quota"),
    KeyLocked("key_locked"),
    ValidationRejected("validation_rejected");

    public companion object {
        public fun parse(raw: String?): CouldNotSaveReason = entries.firstOrNull { it.wire == raw } ?: NodeUnreachable
    }
}

public enum class CouldNotSaveState(public val wire: String) {
    Queued("queued"),
    Retrying("retrying"),
    Saved("saved"),
    Kept("kept"),
    Discarded("discarded");

    public companion object {
        public fun parse(raw: String?): CouldNotSaveState = entries.firstOrNull { it.wire == raw } ?: Queued
    }
}

/**
 * A "Not saved yet" entry: the node's (`CouldntSave`, no text, only the item it holds) or this phone's own outbox
 * entry (with its [text], never logged). Kept until the person discards it; at 30 days the app asks.
 */
public data class CouldNotSaveEntry(
    val id: String,
    val reason: CouldNotSaveReason,
    val createdAt: Instant,
    val itemId: String? = null,
    val text: String? = null,
    val retries: Long = 0,
    val state: CouldNotSaveState = CouldNotSaveState.Queued,
    val promptDue: Boolean = false,
    val onPhone: Boolean = false,
) {
    /** True from day 30: the screen asks Retry, Export or Discard. */
    public fun needsThirtyDayPrompt(now: Instant): Boolean =
        promptDue || (onPhone && now - createdAt >= ASK_AFTER)

    override fun toString(): String = "CouldNotSaveEntry($id, $reason, onPhone=$onPhone)"

    public companion object {
        public val ASK_AFTER: Duration = 30.days

        internal fun read(json: JsonObject): CouldNotSaveEntry =
            CouldNotSaveEntry(
                id = json.need("cqs"),
                itemId = json.str("item"),
                reason = CouldNotSaveReason.parse(json.str("reason_code")),
                createdAt = json.time("created_at"),
                retries = json.long("retries") ?: 0,
                state = CouldNotSaveState.parse(json.str("state")),
                promptDue = json.isTrue("prompt_due"),
            )
    }
}

public enum class NeedsYouKind { Review, NotSaved, Offer }

public data class NeedsYouItem(val kind: NeedsYouKind, val count: Int, val since: Instant? = null)

/** `Status.modules[*].state`: absent, stale, failed and disabled stay distinct; `ok` never means "working now". */
public enum class ModuleState(public val wire: String) {
    Ok("ok"),
    Waiting("waiting"),
    Stale("stale"),
    Failed("failed"),
    Disabled("disabled"),
    Absent("absent");

    public companion object {
        /** An unknown state shows as the least reassuring known one. */
        public fun parse(raw: String?): ModuleState = entries.firstOrNull { it.wire == raw } ?: Failed
    }
}

public data class ModuleStatus(val state: ModuleState, val code: String? = null, val since: Instant? = null, val lastOk: Instant? = null) {
    internal companion object {
        fun read(json: JsonObject): ModuleStatus =
            ModuleStatus(ModuleState.parse(json.str("state")), json.str("code"), json.timeOrNull("since"), json.timeOrNull("last_ok"))
    }
}

/** `Status`: the node as the app sees it. */
public data class NodeStatus(
    val modules: Map<String, ModuleStatus>,
    val executor: String? = null,
    /** `absent`, `locked` or `unlocked`. */
    val key: String = "absent",
    val indexNormal: ModuleState = ModuleState.Ok,
    /** `locked`, `waiting`, `failed` or `ok`. */
    val indexSensitive: String = "locked",
    /** Null when cloud freshness was never established: never read as fresh. */
    val staleSeconds: Long? = null,
    val safeInCloud: Boolean = false,
    val lastCareAt: Instant? = null,
    val careOverdueReason: String? = null,
    val generatedAt: Instant? = null,
    val stateRevision: Long = 0,
) {
    public fun module(name: String): ModuleStatus = modules[name] ?: ModuleStatus(ModuleState.Absent)

    internal companion object {
        fun read(json: JsonObject): NodeStatus {
            val index = json.obj("index")
            return NodeStatus(
                modules = json.obj("modules")?.mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to ModuleStatus.read(it) } }?.toMap()
                    ?: throw FactsBookShapeError("modules"),
                executor = json.str("executor"),
                key = json.str("key") ?: "absent",
                indexNormal = ModuleState.parse(index?.str("normal")),
                indexSensitive = index?.str("sensitive") ?: "locked",
                staleSeconds = json.long("stale_seconds"),
                generatedAt = json.timeOrNull("generated_at"),
                stateRevision = json.long("state_revision") ?: 0,
                safeInCloud = json.isTrue("safe_in_cloud"),
                lastCareAt = json.timeOrNull("last_care_at"),
                careOverdueReason = json.str("care_overdue_reason"),
            )
        }
    }
}

public enum class ReviewState(public val wire: String) {
    Proposed("proposed"),
    Approved("approved"),
    Rejected("rejected"),
    Stale("stale");

    public companion object {
        public fun parse(raw: String?): ReviewState = entries.firstOrNull { it.wire == raw } ?: Stale
    }
}

/** A proposal waiting for the person (`Review`, `rvw_<ULID>`). Only what the count and the cards need. */
public data class ReviewItem(
    val id: String,
    val reviewClass: String,
    val state: ReviewState,
    val digest: String,
    val text: String?,
    val area: String = "",
    val sensitive: Boolean = false,
) {
    internal companion object {
        fun read(json: JsonObject): ReviewItem =
            ReviewItem(
                id = json.need("rvw"),
                reviewClass = json.str("class") ?: "fact_add",
                state = ReviewState.parse(json.str("state")),
                digest = json.need("digest"),
                text = json.obj("proposal")?.str("text"),
                area = json.str("area").orEmpty(),
                sensitive = json.str("sens") == "sensitive",
            )
    }
}

/** One event of an outbox batch: a capture made while the node could not be reached, with its own key. */
public data class OutboxEvent(val key: String, val payload: JsonObject) {
    public fun toJson(): JsonObject =
        JsonObject(mapOf("key" to JsonPrimitive(key), "type" to JsonPrimitive("capture"), "payload" to payload))
}

public enum class OutboxReceiptStatus { Accepted, Duplicate, Refused }

public data class OutboxReceipt(val key: String, val status: OutboxReceiptStatus) {
    internal companion object {
        fun read(json: JsonObject): OutboxReceipt =
            OutboxReceipt(
                json.need("key"),
                when (json.str("status")) {
                    "accepted" -> OutboxReceiptStatus.Accepted
                    "duplicate" -> OutboxReceiptStatus.Duplicate
                    else -> OutboxReceiptStatus.Refused
                },
            )
    }
}

/** What a pairing gave back (`PairResponse`). The token is a credential: it goes to the secret store only. */
public class PairResult(
    public val token: String,
    public val clientId: String,
    public val scopes: List<String>,
    public val vault: String,
    public val rootFingerprint: String,
    public val node: String,
) {
    override fun toString(): String = "PairResult(client=$clientId)"

    internal companion object {
        fun read(json: JsonObject): PairResult =
            PairResult(
                json.need("token"),
                json.need("client_id"),
                json.strings("scopes"),
                json.need("vault"),
                json.need("root_fingerprint"),
                json.need("node"),
            )
    }
}

/** What the node's milestone supports (`api/capabilities.json`). A disabled capability is never faked. */
public data class FactsBookCapabilities(val milestone: String, val supported: Set<String>, val disabled: Map<String, String>) {
    public fun supports(capability: String): Boolean = capability in supported

    public fun isDisabled(capability: String): Boolean = capability in disabled

    public companion object {
        public const val REMINDERS: String = "reminder_parse_confirm_pause"
        public const val TRIGGERS: String = "local_trigger_slice"
        public const val SENSITIVE_UNLOCK: String = "sensitive_unlock"
        public const val SEARCH: String = "normal_fts_search"

        /** The B5 matrix as shipped, until the node's own answer replaces it (F: factsbook_capabilities.dart). */
        public val B5: FactsBookCapabilities =
            FactsBookCapabilities(
                milestone = "B5",
                supported =
                    setOf(
                        "local_capture", "capture_review_with_immutable_evidence", "owner_review_and_correction",
                        "normal_fts_search", "encrypted_unsaved_export", "local_console_queue_identity_repair",
                        "client_revocation", "signed_control_verification_material", "local_export_and_fresh_restore",
                        "reminder_parse_confirm_pause", "authorized_source_retrieval", "immutable_owner_decisions",
                        "permanent_tombstones", "local_trigger_slice", "owner_classified_trigger_capture",
                        "source_authorization_revalidation", "stable_source_storm_summary", "sender_claim_condition",
                        "nonpersistent_draft_rule_test", "reminder_proposal_edit", "client_reported_first_message",
                        "capability_discovery", "explicit_conversation_offers",
                    ),
                disabled =
                    mapOf(
                        "cloud_transport" to "qualification not started",
                        "safe_in_cloud" to "no checkpoint upload/readback",
                        "sensitive_unlock" to "key lifetime and recovery gates unresolved",
                        "phone_alarm_delivery" to "host gate unresolved",
                        "automatic_conversation_start" to "Unavailable: no qualified client/budget/tool enforcement",
                        "notification_capture" to "v1.1 G-NL unresolved",
                    ),
            )

        internal fun read(json: JsonObject): FactsBookCapabilities =
            FactsBookCapabilities(
                milestone = json.str("milestone").orEmpty(),
                supported =
                    json.obj("supported")?.filterValues { (it as? JsonPrimitive)?.booleanOrNull == true }?.keys.orEmpty(),
                disabled =
                    json.obj("disabled")?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty(),
            )
    }
}

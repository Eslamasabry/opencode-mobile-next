package io.github.eslamasabry.oc.engine.factsbook

import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A failure the person can act on: the node's `code` (its error envelope `{error:{code,request_id,retry_after_s}}`),
 * or `node_unreachable` when no answer came and `bad_response` when the answer could not be read. The node's own
 * message is never kept: the app words its own copy from [code], and [requestId] goes under Details.
 */
public data class FactsBookError(
    val code: String,
    val requestId: String? = null,
    val retryAfterSeconds: Long? = null,
    val status: Int? = null,
) {
    /** `501 not_enabled`: the node does not offer this yet. */
    val notEnabled: Boolean get() = code == "not_enabled"

    /** The change was based on something that has since changed. */
    val stale: Boolean get() = code == "stale_approval" || code == "precondition_failed" || code == "conflict"

    /** Could not reach the node at all: the capture path keeps the text. */
    val unreachable: Boolean get() = code == UNREACHABLE

    public companion object {
        public const val UNREACHABLE: String = "node_unreachable"
        public const val BAD_RESPONSE: String = "bad_response"
        public const val OWNER_REQUIRED: String = "owner_required"

        /** F: http_factsbook_gateway.dart `_codeFor`. */
        public fun codeFor(status: Int): String =
            when (status) {
                401 -> "unauthorized"
                403 -> "forbidden_scope"
                404 -> "not_found"
                409 -> "conflict"
                412 -> "precondition_failed"
                423 -> "locked_no_key"
                429 -> "slow_down"
                501 -> "not_enabled"
                503 -> "starting"
                else -> "internal"
            }
    }
}

/** A gateway answer: the value, or a [FactsBookError] (an expected failure as a value; nothing here throws). */
public sealed interface FbResult<out T> {
    public data class Ok<out T>(val value: T) : FbResult<T>

    public data class Err(val error: FactsBookError) : FbResult<Nothing>
}

public inline fun <T, R> FbResult<T>.map(transform: (T) -> R): FbResult<R> =
    when (this) {
        is FbResult.Ok -> FbResult.Ok(transform(value))
        is FbResult.Err -> this
    }

public fun <T> FbResult<T>.valueOrNull(): T? = (this as? FbResult.Ok)?.value

public fun <T> FbResult<T>.errorOrNull(): FactsBookError? = (this as? FbResult.Err)?.error

/**
 * One HTTP exchange with a node, as the transport saw it. [Answer.body] is the raw text (null for none). [Unreachable]
 * means nothing came back (refused, DNS, reset, timeout, a refused trust gate).
 */
public sealed interface FbHttpResult {
    public class Answer(public val status: Int, public val body: String?) : FbHttpResult {
        override fun toString(): String = "Answer($status)"
    }

    public data object Unreachable : FbHttpResult
}

/** The transport port. Real: `NetFactsBookHttp` over :core:net; tests: a fake. Never logs a body or header. */
public fun interface FactsBookHttp {
    public suspend fun send(
        method: String,
        path: String,
        query: Map<String, String>,
        headers: Map<String, String>,
        body: String?,
    ): FbHttpResult
}

/**
 * The protocol-neutral door to a FactsBook node (App API v1). Owner-only calls (review decisions, area sharing,
 * settings) carry the [OwnerSigner]'s proof; without a signer they fail with `owner_required` and nothing is sent.
 */
public interface FactsBookGateway {
    public suspend fun status(): FbResult<NodeStatus>

    public suspend fun things(kind: ThingKind? = null): FbResult<List<Thing>>

    public suspend fun thing(id: String): FbResult<Thing>

    public suspend fun addFact(thing: Thing, text: String): FbResult<MutationResult>

    public suspend fun editFact(fact: Fact, text: String): FbResult<MutationResult>

    public suspend fun search(query: String): FbResult<SearchResult>

    public suspend fun capture(request: CaptureRequest, idempotencyKey: String): FbResult<CaptureResult>

    public suspend fun pendingCaptures(): FbResult<List<CouldNotSaveEntry>>

    public suspend fun retryCapture(id: String, idempotencyKey: String): FbResult<CaptureResult>

    public suspend fun discardCapture(id: String, idempotencyKey: String): FbResult<Unit>

    public suspend fun sendOutbox(events: List<OutboxEvent>): FbResult<List<OutboxReceipt>>

    public suspend fun reviewItems(): FbResult<List<ReviewItem>>

    public suspend fun areas(): FbResult<List<Area>>

    public suspend fun capabilities(): FbResult<FactsBookCapabilities>

    /** `POST /v1/review/{id}/decision`, signed, bound to the exact digest and revisions the person saw. */
    public suspend fun decideReview(item: ReviewItem, decision: ReviewDecision, edited: JsonObject? = null): FbResult<MutationResult>

    /** `PATCH /v1/areas/{slug}`: who else may read the area, signed against the current policy revision. */
    public suspend fun setAreaShare(area: Area, share: AreaShare): FbResult<MutationResult>

    /** `GET /v1/config` (admin). */
    public suspend fun settings(): FbResult<FactsBookSettings>

    /** `PATCH /v1/config` with a new daily brief cap, signed against the revision the person saw. */
    public suspend fun setBriefCap(current: FactsBookSettings, cap: Long): FbResult<MutationResult>

    /** `PATCH /v1/config` with a new home time zone (an IANA name), signed against the revision the person saw. */
    public suspend fun setHomeZone(current: FactsBookSettings, zone: String): FbResult<MutationResult>

    /** `GET /v1/inbox/{id}/export`: an unsaved capture as an encrypted object. */
    public suspend fun exportCapture(id: String): FbResult<CaptureExport>

    /** `GET /v1/control?since=0` (admin): the signed control chain. */
    public suspend fun control(): FbResult<ControlChain>
}

/**
 * The App API v1 over [FactsBookHttp] (F: http_factsbook_gateway.dart): a bearer token, JSON, the error envelope
 * mapped to [FactsBookError]. Requests go one at a time (the node answers 429 to a second one while busy), and a 429
 * or `busy` is asked again three times, briefly. Every mutation carries an idempotency key, so asking again never
 * saves twice.
 */
public class HttpFactsBookGateway(
    private val http: FactsBookHttp,
    private val token: String?,
    private val random: Random = Random.Default,
    private val retryDelayMs: Long = 250,
    /** Signs owner-only changes; null while this phone has no owner device id. */
    private val owner: OwnerSigner? = null,
) : FactsBookGateway, FactsBookReminders, FactsBookTriggerCalls {
    private val lane = Mutex()

    override fun toString(): String = "HttpFactsBookGateway"

    /** Letters only: the node's content classifier reads keys too (a long run of digits looks like a card). */
    internal fun key(): String = "app-" + (1..24).map { 'a' + random.nextInt(26) }.joinToString("")

    private suspend fun send(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonObject? = null,
        idempotencyKey: String? = null,
        authorized: Boolean = true,
    ): FbResult<JsonElement?> =
        lane.withLock {
            var attempt = 0
            var result = once(method, path, query, body, idempotencyKey, authorized)
            while (attempt < 3 && result.errorOrNull()?.let { it.status == 429 || it.code == "busy" } == true) {
                attempt += 1
                delay(retryDelayMs * attempt)
                result = once(method, path, query, body, idempotencyKey, authorized)
            }
            result
        }

    private suspend fun once(
        method: String,
        path: String,
        query: Map<String, String>,
        body: JsonObject?,
        idempotencyKey: String?,
        authorized: Boolean,
    ): FbResult<JsonElement?> {
        // The node refuses a write that does not say it is JSON, even with nothing to send: send `{}`.
        val sends = body ?: if (method == "GET" || method == "DELETE") null else JsonObject(emptyMap())
        val headers =
            buildMap {
                put("Accept", "application/json")
                if (sends != null) put("Content-Type", "application/json")
                if (authorized && token != null) put("Authorization", "Bearer $token")
                if (idempotencyKey != null) put("Idempotency-Key", idempotencyKey)
            }
        return when (val answer = http.send(method, path, query, headers, sends?.toString())) {
            FbHttpResult.Unreachable -> FbResult.Err(FactsBookError(FactsBookError.UNREACHABLE))
            is FbHttpResult.Answer -> decode(answer)
        }
    }

    private fun decode(answer: FbHttpResult.Answer): FbResult<JsonElement?> {
        val decoded =
            answer.body?.takeIf { it.isNotBlank() }?.let {
                try {
                    Json.parseToJsonElement(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        if (answer.status >= 400) {
            val envelope = ((decoded as? JsonObject)?.get("error") as? JsonObject)
            return FbResult.Err(
                if (envelope != null) {
                    FactsBookError(
                        envelope.str("code") ?: "internal",
                        requestId = envelope.str("request_id"),
                        retryAfterSeconds = envelope.long("retry_after_s"),
                        status = answer.status,
                    )
                } else {
                    FactsBookError(FactsBookError.codeFor(answer.status), status = answer.status)
                }
            )
        }
        return FbResult.Ok(decoded)
    }

    /** Reads with [read], so a body the app cannot read is a plain failure and never a crash. */
    private fun <T> FbResult<JsonElement?>.reading(read: (JsonObject) -> T): FbResult<T> =
        when (this) {
            is FbResult.Err -> this
            is FbResult.Ok ->
                try {
                    (value as? JsonObject)?.let { FbResult.Ok(read(it)) } ?: bad
                } catch (e: IllegalStateException) {
                    bad
                } catch (e: IllegalArgumentException) {
                    bad
                }
        }

    private val bad = FbResult.Err(FactsBookError(FactsBookError.BAD_RESPONSE))

    /** Every page, at most [MAX_PAGES], so one bad cursor cannot loop. */
    private suspend fun <T> all(path: String, read: (JsonObject) -> T, query: Map<String, String> = emptyMap()): FbResult<List<T>> {
        val out = mutableListOf<T>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = send("GET", path, buildMap { put("limit", "200"); putAll(query); cursor?.let { put("cursor", it) } })
            val items = page.reading { json -> json.list("items", read) to json.str("next_cursor") }
            when (items) {
                is FbResult.Err -> return items
                is FbResult.Ok -> {
                    out += items.value.first
                    cursor = items.value.second ?: return FbResult.Ok(out)
                }
            }
        }
        return FbResult.Ok(out)
    }

    private fun seg(id: String): String = encodeSegment(id)

    override suspend fun status(): FbResult<NodeStatus> = send("GET", "/v1/status").reading(NodeStatus::read)

    override suspend fun things(kind: ThingKind?): FbResult<List<Thing>> =
        all("/v1/things", Thing::read, if (kind != null && kind != ThingKind.Other) mapOf("kind" to kind.wire) else emptyMap())

    override suspend fun thing(id: String): FbResult<Thing> = send("GET", "/v1/things/${seg(id)}").reading(Thing::read)

    override suspend fun addFact(thing: Thing, text: String): FbResult<MutationResult> =
        send(
            "POST",
            "/v1/things/${seg(thing.id)}/facts",
            body = obj("text" to JsonPrimitive(text), "category" to JsonPrimitive("note"), "base_rev" to JsonPrimitive(thing.revision)),
            idempotencyKey = key(),
        ).reading(MutationResult::read)

    override suspend fun editFact(fact: Fact, text: String): FbResult<MutationResult> =
        send(
            "PATCH",
            "/v1/facts/${seg(fact.id)}",
            body =
                obj(
                    "text" to JsonPrimitive(text),
                    "category" to JsonPrimitive(if (fact.category in CATEGORIES) fact.category else "note"),
                    "base_rev" to JsonPrimitive(fact.revision),
                ),
            idempotencyKey = key(),
        ).reading(MutationResult::read)

    override suspend fun search(query: String): FbResult<SearchResult> {
        val hits = all("/v1/search", SearchHit::read, mapOf("q" to query))
        if (hits is FbResult.Err) return hits
        // The hits stand without a status; the locked-notes line then stays on.
        val locked = (status() as? FbResult.Ok)?.value?.indexSensitive?.let { it != "ok" } ?: true
        return FbResult.Ok(SearchResult(query, (hits as FbResult.Ok).value, locked))
    }

    override suspend fun capture(request: CaptureRequest, idempotencyKey: String): FbResult<CaptureResult> =
        send("POST", "/v1/inbox", body = request.toJson(), idempotencyKey = idempotencyKey).reading(CaptureResult::read)

    override suspend fun pendingCaptures(): FbResult<List<CouldNotSaveEntry>> =
        all("/v1/inbox/pending", CouldNotSaveEntry::read)

    override suspend fun retryCapture(id: String, idempotencyKey: String): FbResult<CaptureResult> =
        send("POST", "/v1/inbox/${seg(id)}/retry", idempotencyKey = idempotencyKey).reading(CaptureResult::read)

    override suspend fun discardCapture(id: String, idempotencyKey: String): FbResult<Unit> =
        send("DELETE", "/v1/inbox/${seg(id)}", idempotencyKey = idempotencyKey).map { }

    override suspend fun sendOutbox(events: List<OutboxEvent>): FbResult<List<OutboxReceipt>> =
        send("POST", "/v1/outbox", body = obj("events" to JsonArray(events.map { it.toJson() })), idempotencyKey = key())
            .reading { it.list("receipts", OutboxReceipt::read) }

    /** The node lists every proposal it has had; only the ones still waiting are the person's to decide. */
    override suspend fun reviewItems(): FbResult<List<ReviewItem>> =
        all("/v1/review", ReviewItem::read).map { items -> items.filter { it.state == ReviewState.Proposed } }

    override suspend fun areas(): FbResult<List<Area>> = all("/v1/areas", Area::read)

    override suspend fun capabilities(): FbResult<FactsBookCapabilities> =
        send("GET", "/v1/capabilities").reading(FactsBookCapabilities::read)

    /** [body] with the owner's `proof`; the screen lock is asked here, before anything is sent. */
    private suspend fun signed(method: String, path: String, body: JsonObject): FbResult<JsonObject> {
        val signer = owner ?: return FbResult.Err(FactsBookError(FactsBookError.OWNER_REQUIRED))
        return signer.proof(method, path, body).map { JsonObject(body + ("proof" to it)) }
    }

    private suspend fun signedSend(method: String, path: String, body: JsonObject): FbResult<MutationResult> =
        when (val proved = signed(method, path, body)) {
            is FbResult.Err -> proved
            is FbResult.Ok -> send(method, path, body = proved.value, idempotencyKey = key()).reading(MutationResult::read)
        }

    override suspend fun decideReview(item: ReviewItem, decision: ReviewDecision, edited: JsonObject?): FbResult<MutationResult> =
        signedSend(
            "POST",
            "/v1/review/${seg(item.id)}/decision",
            JsonObject(
                buildMap {
                    put("decision", JsonPrimitive(decision.wire))
                    put("digest", JsonPrimitive(item.digest))
                    put("preconditions", JsonObject(item.preconditions.mapValues { JsonPrimitive(it.value) }))
                    if (edited != null) put("edited", edited)
                }
            ),
        )

    override suspend fun setAreaShare(area: Area, share: AreaShare): FbResult<MutationResult> {
        if (owner == null) return FbResult.Err(FactsBookError(FactsBookError.OWNER_REQUIRED))
        val policy = when (val r = send("GET", "/v1/policy").reading { it }) { is FbResult.Err -> return r; is FbResult.Ok -> r.value }
        return signedSend(
            "PATCH",
            "/v1/areas/${seg(area.slug)}",
            obj("base_rev" to JsonPrimitive(policy.long("policy_version") ?: 0), "area" to area.copy(share = share).toJson()),
        )
    }

    override suspend fun settings(): FbResult<FactsBookSettings> = send("GET", "/v1/config").reading(FactsBookSettings::read)

    override suspend fun setBriefCap(current: FactsBookSettings, cap: Long): FbResult<MutationResult> =
        signedSend("PATCH", "/v1/config", obj("base_rev" to JsonPrimitive(current.revision), "settings" to current.withBriefCap(cap)))

    override suspend fun setHomeZone(current: FactsBookSettings, zone: String): FbResult<MutationResult> =
        signedSend("PATCH", "/v1/config", obj("base_rev" to JsonPrimitive(current.revision), "settings" to current.withHomeZone(zone)))

    override suspend fun exportCapture(id: String): FbResult<CaptureExport> =
        send("GET", "/v1/inbox/${seg(id)}/export").reading { CaptureExport(pretty.encodeToString(JsonObject.serializer(), it), it.str("item").orEmpty()) }

    override suspend fun control(): FbResult<ControlChain> = send("GET", "/v1/control", mapOf("since" to "0")).reading(ControlChain::read)

    // ---- Event triggers (spec 44): asked only where the capability matrix lists them -------------------------------

    override suspend fun rules(): FbResult<List<Rule>> = all("/v1/rules", Rule::read).map { list -> list.filter { !it.deleted } }

    private suspend fun <T> signedRead(method: String, path: String, body: JsonObject, read: (JsonObject) -> T): FbResult<T> =
        when (val proved = signed(method, path, body)) {
            is FbResult.Err -> proved
            is FbResult.Ok -> send(method, path, body = proved.value, idempotencyKey = key()).reading(read)
        }

    override suspend fun saveRule(definition: RuleDefinition, replacing: Rule?): FbResult<Rule> =
        signedRead(
            if (replacing == null) "POST" else "PUT",
            if (replacing == null) "/v1/rules" else "/v1/rules/${seg(replacing.id)}",
            obj("base_rev" to JsonPrimitive(replacing?.revision ?: 0), "rule" to definition.toJson()),
            Rule::read,
        )

    override suspend fun deleteRule(rule: Rule): FbResult<Rule> =
        signedRead("DELETE", "/v1/rules/${seg(rule.id)}", obj("base_rev" to JsonPrimitive(rule.revision)), Rule::read)

    override suspend fun testDraft(draft: RuleDefinition, sample: TriggerSample): FbResult<RuleTestResult> =
        send("POST", "/v1/rules/test", body = obj("rule" to draft.toJson(), "sample" to sample.toJson())).reading(RuleTestResult::read)

    override suspend fun events(): FbResult<List<TriggerEvent>> = all("/v1/events", TriggerEvent::read)

    override suspend fun firings(): FbResult<List<TriggerFiring>> = all("/v1/trigger-firings", TriggerFiring::read)

    override suspend fun reminderProposals(): FbResult<List<ReminderProposal>> =
        all("/v1/reminder-proposals", ReminderProposal::read).map { list -> list.filter { it.proposed } }

    override suspend fun decideReminderProposal(proposal: ReminderProposal, decision: ProposalDecision): FbResult<MutationResult> =
        signedSend(
            "POST",
            "/v1/reminder-proposals/${seg(proposal.id)}/decision",
            obj("decision" to JsonPrimitive(decision.wire), "rev" to JsonPrimitive(proposal.revision), "digest" to JsonPrimitive(proposal.digest)),
        )

    override suspend fun editReminderProposal(proposal: ReminderProposal, preview: ReminderPreview): FbResult<MutationResult> =
        signedSend(
            "POST",
            "/v1/reminder-proposals/${seg(proposal.id)}/decision",
            obj(
                "decision" to JsonPrimitive("edit"),
                "rev" to JsonPrimitive(proposal.revision),
                "digest" to JsonPrimitive(proposal.digest),
                "preview" to preview.raw,
                "preview_digest" to JsonPrimitive(preview.digest),
            ),
        )

    override suspend fun offers(): FbResult<List<ConversationOffer>> = all("/v1/offers", ConversationOffer::read)

    override suspend fun cancelOffer(offer: ConversationOffer): FbResult<ConversationOffer> =
        send(
            "POST",
            "/v1/offers/${seg(offer.id)}/cancel",
            body = obj("rev" to JsonPrimitive(offer.revision), "digest" to JsonPrimitive(offer.digest)),
            idempotencyKey = key(),
        ).reading(ConversationOffer::read)

    override suspend fun previewReminder(
        what: String,
        timing: ReminderTiming,
        zone: String,
        locale: String,
        scheduleId: String?,
        area: String,
    ): FbResult<ReminderPreview> =
        send(
            "POST",
            "/v1/reminders/parse",
            body =
                obj(
                    "text" to JsonPrimitive(scheduleText(scheduleId ?: scheduleId(random), timing, zone)),
                    "what" to JsonPrimitive(what.trim()),
                    "locale" to JsonPrimitive(locale),
                    "zone" to JsonPrimitive(zone),
                    "area" to JsonPrimitive(area),
                ),
        ).reading(ReminderPreview::read)

    override suspend fun createReminder(preview: ReminderPreview, urgent: Boolean, floatingConfirmed: Boolean): FbResult<MutationResult> =
        send(
            "POST",
            "/v1/reminders",
            body =
                JsonObject(
                    buildMap {
                        put("preview", preview.raw)
                        put("preview_digest", JsonPrimitive(preview.digest))
                        put("confirmed", JsonPrimitive(true))
                        put("urgent", JsonPrimitive(urgent))
                        if (floatingConfirmed) put("floating_confirmed", JsonPrimitive(true))
                    }
                ),
            idempotencyKey = key(),
        ).reading(MutationResult::read)

    override suspend fun reminders(): FbResult<List<Reminder>> = all("/v1/reminders", Reminder::read)

    override suspend fun setReminderPaused(reminder: Reminder, paused: Boolean): FbResult<MutationResult> =
        send(
            "PATCH",
            "/v1/reminders/${seg(reminder.id)}",
            body = obj("base_rev" to JsonPrimitive(reminder.revision), "paused" to JsonPrimitive(paused)),
            idempotencyKey = key(),
        ).reading(MutationResult::read)

    override suspend fun updateReminder(reminder: Reminder, preview: ReminderPreview, floatingConfirmed: Boolean): FbResult<MutationResult> =
        send(
            "PATCH",
            "/v1/reminders/${seg(reminder.id)}",
            body =
                JsonObject(
                    buildMap {
                        put("base_rev", JsonPrimitive(reminder.revision))
                        put("preview", preview.raw)
                        put("preview_digest", JsonPrimitive(preview.digest))
                        put("confirmed", JsonPrimitive(true))
                        if (floatingConfirmed) put("floating_confirmed", JsonPrimitive(true))
                    }
                ),
            idempotencyKey = key(),
        ).reading(MutationResult::read)

    /** `GET /v1/info` then `POST /v1/pair`: a one-time code to a bearer token. Unauthorized calls. */
    public suspend fun pair(code: String, clientName: String, scopes: List<String>): FbResult<PairResult> =
        send(
            "POST",
            "/v1/pair",
            body =
                obj(
                    "code" to JsonPrimitive(code.trim()),
                    "client_name" to JsonPrimitive(clientName),
                    "scopes_requested" to JsonArray(scopes.map(::JsonPrimitive)),
                ),
            authorized = false,
        ).reading(PairResult::read)

    public companion object {
        internal const val MAX_PAGES: Int = 20

        /** The categories `FactInput` accepts; anything else is sent as a note. */
        private val CATEGORIES = setOf("preference", "habit", "fact", "note", "task", "relationship", "event")

        private fun obj(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(mapOf(*pairs))

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val pretty = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        /** Dart `Uri.encodeComponent`: everything but unreserved characters is percent-encoded (UTF-8). */
        internal fun encodeSegment(value: String): String =
            buildString {
                for (byte in value.encodeToByteArray()) {
                    val c = byte.toInt() and 0xff
                    val ch = c.toChar()
                    if (ch.isLetterOrDigit() && c < 0x80 || ch in "-_.!~*'()") append(ch)
                    else append('%').append(HEX[c shr 4]).append(HEX[c and 0xf])
                }
            }

        private const val HEX = "0123456789ABCDEF"
    }
}

package io.github.eslamasabry.oc.engine.factsbook

import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public enum class FactsBookLoad { Idle, Loading, Ready, Failed }

/** What a capture came to. Never a loss: saved at the node, held by the node, held on this phone, or refused. */
public sealed interface CaptureOutcome {
    public data class SavedAtNode(val itemId: String) : CaptureOutcome

    public data class QueuedAtNode(val itemId: String, val entryId: String?) : CaptureOutcome

    public data class KeptOnPhone(val entry: CouldNotSaveEntry) : CaptureOutcome

    /** The phone's queue is full: nothing was kept; the text is still in the editor. */
    public data object RefusedFull : CaptureOutcome

    /** The node refused it (not a reach problem): the text stays on screen with the reason. */
    public data class Refused(val error: FactsBookError) : CaptureOutcome
}

/** Everything the Memory section and the FactsBook pages read (F: FactsBookSession + FactsBookController). */
public data class FactsBookState(
    /** The pairing has been read (or there is no profile). Until then the section shows nothing. */
    val loaded: Boolean = false,
    val profileId: String? = null,
    val link: FactsBookLink? = null,
    val paired: Boolean = false,
    /** An owner device id is set: this phone could sign approvals (signing is not ported yet). */
    val canApprove: Boolean = false,
    val load: FactsBookLoad = FactsBookLoad.Idle,
    val status: NodeStatus? = null,
    val review: List<ReviewItem> = emptyList(),
    /** The three things with the newest facts, for "Recently added". */
    val recent: List<Thing> = emptyList(),
    /** The node's queue and this phone's outbox, together. */
    val pending: List<CouldNotSaveEntry> = emptyList(),
    val error: FactsBookError? = null,
    val capabilities: FactsBookCapabilities = FactsBookCapabilities.B5,
) {
    val reviewWaiting: Int get() = review.size

    /** What needs the person, derived from the review queue and Not saved yet; counted once. */
    val needsYou: List<NeedsYouItem>
        get() {
            val notSaved = pending.filter { it.state != CouldNotSaveState.Discarded }
            return buildList {
                if (reviewWaiting > 0) add(NeedsYouItem(NeedsYouKind.Review, reviewWaiting))
                if (notSaved.isNotEmpty()) add(NeedsYouItem(NeedsYouKind.NotSaved, notSaved.size, notSaved.minOf { it.createdAt }))
            }
        }

    val needsYouCount: Int get() = needsYou.sumOf { it.count }

    override fun toString(): String = "FactsBookState(paired=$paired, load=$load, error=${error?.code})"
}

/**
 * The FactsBook pairing of a server profile and every read and write the pages make (F: factsbook_session.dart,
 * factsbook_controller.dart). One serial lane: [refresh], [capture], [pair] and [disconnect] never overlap. A failed read
 * keeps the last copy and records the error; nothing claims to be live.
 */
public class FactsBookEngine(
    private val store: FactsBookStore,
    /** Builds the gateway of a pairing (the real one over a trust-gated client; a fake in tests). */
    private val gatewayFor: (FactsBookLink, String?) -> FactsBookGateway,
    private val outboxFor: (String) -> FactsBookOutboxStore,
    private val clock: Clock,
    private val random: Random = Random.Default,
) {
    private val lane = Mutex()
    private val mutable = MutableStateFlow(FactsBookState())
    public val state: StateFlow<FactsBookState> = mutable.asStateFlow()

    private var gateway: FactsBookGateway? = null
    private var outbox: FactsBookOutbox? = null

    /** The gateway, for the pages' own reads (things, a thing, search, areas). Null while unpaired. */
    public val node: FactsBookGateway? get() = gateway

    /** Reads the pairing of [profileId] in place (Flutter's keys) and builds the gateway. Safe to call again. */
    public suspend fun open(profileId: String?): Unit =
        lane.withLock {
            if (state.value.loaded && state.value.profileId == profileId) return@withLock
            gateway = null
            outbox = null
            if (profileId == null) {
                mutable.value = FactsBookState(loaded = true)
                return@withLock
            }
            buildLocked(profileId)
        }

    private suspend fun buildLocked(profileId: String) {
        val link = store.link(profileId)
        val token = link?.let { store.token(profileId) }
        val box = FactsBookOutbox(outboxFor(profileId), clock).also { it.load() }
        outbox = box
        gateway = if (link != null && token != null) gatewayFor(link, token) else null
        mutable.value =
            FactsBookState(
                loaded = true,
                profileId = profileId,
                link = link,
                paired = gateway != null,
                canApprove = gateway != null && link?.ownerDeviceId != null,
                pending = box.entries.map(OutboxEntry::asEntry),
            )
    }

    /** Reads everything the overview shows, after sending what the outbox holds. */
    public suspend fun refresh(): Unit =
        lane.withLock { refreshLocked() }

    private suspend fun refreshLocked() {
        val node = gateway ?: return
        if (state.value.load == FactsBookLoad.Idle || state.value.load == FactsBookLoad.Failed) {
            mutable.update { it.copy(load = FactsBookLoad.Loading) }
        }
        val failure = readAll(node)
        val phone = outbox?.entries.orEmpty().map(OutboxEntry::asEntry)
        if (failure != null) {
            mutable.update {
                it.copy(
                    error = failure,
                    pending = it.pending.filter { e -> !e.onPhone } + phone,
                    load = if (it.status == null) FactsBookLoad.Failed else FactsBookLoad.Ready,
                )
            }
        }
    }

    /** Null when every read answered; the first failure otherwise (and nothing of this round is kept). */
    private suspend fun readAll(node: FactsBookGateway): FactsBookError? {
        outbox?.flush(node)?.errorOrNull()?.let { if (!it.unreachable) return it }
        val status = when (val r = node.status()) { is FbResult.Err -> return r.error; is FbResult.Ok -> r.value }
        val review = when (val r = node.reviewItems()) { is FbResult.Err -> return r.error; is FbResult.Ok -> r.value }
        val pending = when (val r = node.pendingCaptures()) { is FbResult.Err -> return r.error; is FbResult.Ok -> r.value }
        val things = when (val r = node.things()) { is FbResult.Err -> return r.error; is FbResult.Ok -> r.value }
        val caps = (node.capabilities() as? FbResult.Ok)?.value
        mutable.update {
            it.copy(
                status = status,
                review = review,
                pending = pending + outbox?.entries.orEmpty().map(OutboxEntry::asEntry),
                recent = newest(things),
                error = null,
                load = FactsBookLoad.Ready,
                // A node without its own matrix keeps the shipped one; its missing routes still answer not found.
                capabilities = caps ?: it.capabilities,
            )
        }
        return null
    }

    /** Letters only: the node's content classifier refuses a key with a long run of digits. */
    private fun captureKey(): String = "cap-" + (1..24).map { 'a' + random.nextInt(26) }.joinToString("")

    /**
     * Saves a capture. A node that cannot be reached is not an error here: the text goes to the outbox, unless the
     * outbox is full, in which case the newest is refused and the caller keeps the text on screen.
     */
    public suspend fun capture(request: CaptureRequest): CaptureOutcome =
        lane.withLock {
            val key = captureKey()
            val node = gateway
            val outcome =
                when (val result = node?.capture(request, key) ?: FbResult.Err(FactsBookError(FactsBookError.UNREACHABLE))) {
                    is FbResult.Ok ->
                        if (result.value.saved) CaptureOutcome.SavedAtNode(result.value.itemId)
                        else CaptureOutcome.QueuedAtNode(result.value.itemId, result.value.couldntSaveId)
                    is FbResult.Err ->
                        if (!result.error.unreachable && result.error.code != "outbox_full") {
                            CaptureOutcome.Refused(result.error)
                        } else {
                            when (val kept = outbox?.add(request, key) ?: OutboxAdd.Full) {
                                is OutboxAdd.Kept -> CaptureOutcome.KeptOnPhone(kept.entry.asEntry())
                                OutboxAdd.Full -> CaptureOutcome.RefusedFull
                            }
                        }
                }
            if (outcome !is CaptureOutcome.Refused) refreshLocked()
            if (gateway == null) {
                mutable.update { it.copy(pending = outbox?.entries.orEmpty().map(OutboxEntry::asEntry)) }
            }
            outcome
        }

    /** Sends one phone entry again, or asks the node to retry one of its own. */
    public suspend fun retry(entry: CouldNotSaveEntry): FbResult<Unit> =
        lane.withLock {
            val node = gateway ?: return@withLock FbResult.Err(FactsBookError(FactsBookError.UNREACHABLE))
            val result =
                if (entry.onPhone) outbox?.flush(node, only = entry.id)?.map { } ?: FbResult.Ok(Unit)
                else node.retryCapture(entry.id, captureKey()).map { }
            refreshLocked()
            result
        }

    /** Discards one entry. The caller has already asked the person. */
    public suspend fun discard(entry: CouldNotSaveEntry): FbResult<Unit> =
        lane.withLock {
            val result =
                if (entry.onPhone) {
                    outbox?.remove(entry.id)
                    FbResult.Ok(Unit)
                } else {
                    gateway?.discardCapture(entry.id, captureKey()) ?: FbResult.Err(FactsBookError(FactsBookError.UNREACHABLE))
                }
            mutable.update {
                it.copy(pending = it.pending.filter { e -> e.id != entry.id })
            }
            result
        }

    /**
     * One-time code to a token (F: FactsBookSession.pair). Only a private [address] is accepted; anything else fails
     * with `address_not_allowed`. [pairWith] builds the unauthorized gateway for the address.
     */
    public suspend fun pair(
        address: String,
        code: String,
        ownerDeviceId: String?,
        pairWith: (String) -> HttpFactsBookGateway,
    ): FbResult<Unit> =
        lane.withLock {
            val profileId = state.value.profileId ?: return@withLock FbResult.Err(FactsBookError("unauthorized"))
            val base = FactsBookAddress.parse(address) ?: return@withLock FbResult.Err(FactsBookError("address_not_allowed"))
            val owner = ownerDeviceId?.trim()?.ifEmpty { null }
            val scopes = listOf("read", "write") + if (owner != null) listOf("admin", "owner") else emptyList()
            val result =
                when (val paired = pairWith(base).pair(code, "OpenCode Mobile", scopes)) {
                    is FbResult.Err -> return@withLock paired
                    is FbResult.Ok -> paired.value
                }
            val link = FactsBookLink(base, result.clientId, result.vault, result.rootFingerprint, result.node, result.scopes, owner)
            if (!store.savePairing(profileId, link, result.token)) {
                return@withLock FbResult.Err(FactsBookError("storage_full"))
            }
            buildLocked(profileId)
            refreshLocked()
            FbResult.Ok(Unit)
        }

    /** Forgets the node on this phone: the token, the owner key and the pairing. The node's data and the outbox stay. */
    public suspend fun disconnect(): Unit =
        lane.withLock {
            val profileId = state.value.profileId ?: return@withLock
            store.forget(profileId)
            buildLocked(profileId)
        }

    private companion object {
        fun newest(things: List<Thing>): List<Thing> =
            things.filter { it.newestFact != null }.sortedByDescending { it.newestFact!!.observedAt }.take(3)
    }
}

package io.github.eslamasabry.oc.engine.delivery.queue

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.engine.delivery.port.AutomationPolicy
import io.github.eslamasabry.oc.engine.delivery.port.DeliveryConnection
import io.github.eslamasabry.oc.engine.delivery.port.SendLink
import io.github.eslamasabry.oc.engine.delivery.port.hasStatus
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceipt
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceiptController
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceiptState
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchProof
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournal
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournals
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Symbolic reasons stored in an entry's `error` (never server prose, never exception text; `:ui:words` words them).
 * Entries written by Flutter keep their English sentence and stay readable.
 */
public object QueueErrors {
    public const val SESSION_REVERT_PENDING: String = "SessionRevertPending"
    public const val UNCONFIRMED: String = "Unconfirmed"
    public const val ACCEPTED_UNRECORDED: String = "AcceptedUnrecorded"

    public fun of(failure: Failure): String =
        when (failure) {
            is Failure.Server -> "Server:${failure.status ?: 0}"
            is Failure.Rejected -> "Rejected:${failure.status ?: 0}"
            is Failure.Conflict -> "Conflict:${failure.tag?.raw ?: ""}"
            is Failure.StagedRevert -> SESSION_REVERT_PENDING
            else -> failure::class.simpleName ?: "Failure"
        }
}

/** Everything the screens read about the queue. */
public data class QueueState(
    val entries: ImmutableList<QueuedPrompt> = persistentListOf(),
    val readable: Boolean = true,
    /** Ids a flush or an online send has on the wire right now: cannot be removed or resent (B-STATE-045). */
    val sending: ImmutableSet<String> = persistentSetOf(),
    /** The server accepted these, but the device could not record it. Resending would be a certain duplicate. */
    val acceptedUnrecorded: ImmutableSet<String> = persistentSetOf(),
    /** Set when the queue dropped entries on its own; [OfflineQueue.takeEviction] clears it. */
    val eviction: QueueEviction? = null,
    val flushRevision: Int = 0,
    val lastFlushedCount: Int = 0,
    val lastFlushSkippedForOtherProfiles: Int = 0,
) {
    /** Queued prompts for one conversation, oldest first; online sends in flight are bubbles, not queue rows. */
    public fun queuedFor(ref: ConversationRef, onlineInFlight: Set<String>): List<QueuedPrompt> =
        entries.filter { it.profileId == ref.profileId && it.sessionId == ref.sessionId && it.id !in onlineInFlight }
}

/** Why a prompt was not queued. [DuplicateId] protects the whole queue: a repeated id makes Flutter's reader fail it. */
public enum class QueueRefusal { Closed, TooLarge, NoRoom, DuplicateId }

/**
 * The offline queue and its flush (port of `lib/state/connection/queue.dart`; plan 04 §13; plan 09 §4.5). Rows
 * B-STATE-008, 009, 033…046, 062, 066 (count and sweep), 119.
 *
 * Invariants (never relaxed):
 * 1. A prompt is never sent twice and never silently lost.
 * 2. The dispatch marker is on disk before the request leaves the device; a refused marker write sends nothing.
 * 3. After acceptance the removal is persisted; a refused removal or a death in between leaves "possibly sent".
 * 4. A failure after dispatch never auto-retries; only the person's explicit resend clears the marker (durably, first).
 * 5. A corrupt queue fails closed.
 *
 * Every queue change runs inside [changes] (Dart's `_serializeQueueChange`); network calls never hold it.
 */
public class OfflineQueue(
    private val store: OfflineQueueStore,
    private val journals: PendingCommandJournals,
    private val connection: DeliveryConnection,
    private val policy: AutomationPolicy,
    private val clock: Clock,
    /** The connection scope: an explicit resend starts its flush here, so leaving a screen never cancels it. */
    private val scope: CoroutineScope,
) {
    private val changes = Mutex()
    private var cache: List<QueuedPrompt>? = null
    private var lastReadable = true
    private var inFlight: String? = null
    private val onlineInFlight = LinkedHashSet<String>()
    private val acceptedUnrecorded = LinkedHashSet<String>()
    private val explicitResends = LinkedHashSet<String>()
    private val closedProfiles = HashSet<ProfileId>()
    private var flushing = false
    private var flushAgain = false
    private var eviction: QueueEviction? = null
    private var flushRevision = 0
    private var lastFlushed = 0
    private var lastSkipped = 0

    private val mutableState = MutableStateFlow(QueueState())
    public val state: StateFlow<QueueState> = mutableState

    // ---------------------------------------------------------------- reads

    /** The queue with its limits applied; a queue trimmed on first read is written back (B-STATE-046). */
    private fun queue(): List<QueuedPrompt> {
        cache?.let { return it }
        val read = store.load()
        lastReadable = read is QueueRead.Readable
        val loaded = (read as? QueueRead.Readable)?.entries.orEmpty()
        val limited = QueueLimits.enforce(loaded, clock.now().toEpochMilliseconds())
        cache = limited.kept
        if (limited.removed > 0) {
            eviction = limited
            scope.launch { changes.withLock { store.save(limited.kept) } }
        }
        publish()
        return limited.kept
    }

    public fun entries(): List<QueuedPrompt> = queue()

    public fun sending(id: String): Boolean = inFlight == id || id in onlineInFlight

    public fun acceptedUnrecorded(id: String): Boolean = id in acceptedUnrecorded

    /** Queued prompts of [profile] (shown or not). Throws nothing: an unreadable queue is [QueueState.readable] false. */
    public fun countForProfile(profile: ProfileId): Int = queue().count { it.profileId == profile }

    public fun reviewCount(profile: ProfileId): Int = queue().count { it.profileId == profile && it.dispatched && it.id !in onlineInFlight }

    public fun countForOtherProfiles(profile: ProfileId?): Int = queue().count { it.profileId != profile }

    public fun queuedFor(ref: ConversationRef): List<QueuedPrompt> = mutableState.value.queuedFor(ref, onlineInFlight).also { queue() }

    /** Reads and clears the pending eviction (shown once). */
    public fun takeEviction(): QueueEviction? = eviction.also {
        eviction = null
        publish()
    }

    public fun storedBytes(): Long = store.storedBytes()

    // ---------------------------------------------------------------- person's edits

    /**
     * Adds a drafted prompt (B-STATE-036, 046). `Ok(null)` = queued; `Ok(refusal)` = not queued, the composer keeps the
     * words; `Failed(StorageRefused)` = the write was refused, nothing changed.
     */
    public suspend fun queuePrompt(prompt: QueuedPrompt): Outcome<QueueRefusal?> =
        changes.withLock {
            if (prompt.profileId in closedProfiles) return@withLock QueueRefusal.Closed.ok()
            if (prompt.payloadBytes > QueueLimits.MAX_ENTRY_BYTES) return@withLock QueueRefusal.TooLarge.ok()
            val current = queue()
            if (current.any { it.id == prompt.id }) return@withLock QueueRefusal.DuplicateId.ok()
            val limited = QueueLimits.enforce(current + prompt, clock.now().toEpochMilliseconds())
            // The new entry losing its own eviction pass, or an entry on the wire being evicted, means no room.
            val keptIds = limited.kept.mapTo(HashSet()) { it.id }
            if (prompt.id !in keptIds || current.any { sending(it.id) && it.id !in keptIds }) {
                return@withLock QueueRefusal.NoRoom.ok()
            }
            if (!store.save(limited.kept)) return@withLock Failure.StorageRefused().failed()
            cache = limited.kept
            if (limited.removed > 0) eviction = limited
            publish()
            null.ok()
        }

    /**
     * Removes an entry. `Ok(false)` when it is on the wire right now (the decision was made against a bubble that said
     * "queued"). B-STATE-035, 045.
     */
    public suspend fun remove(id: String): Outcome<Boolean> =
        changes.withLock {
            if (sending(id)) return@withLock false.ok()
            val kept = queue().filter { it.id != id }
            if (kept.size == queue().size) return@withLock true.ok()
            if (!store.save(kept)) return@withLock Failure.StorageRefused().failed()
            cache = kept
            acceptedUnrecorded -= id
            explicitResends -= id
            publish()
            true.ok()
        }

    /** The person's clear of every queued prompt. Refused while an online send is in flight. */
    public suspend fun clearAll(): Boolean =
        changes.withLock {
            if (onlineInFlight.isNotEmpty()) return@withLock false
            if (queue().isEmpty() && store.readable) return@withLock true
            if (!store.save(emptyList())) return@withLock false
            cache = emptyList()
            lastReadable = true
            acceptedUnrecorded.clear()
            publish()
            true
        }

    /**
     * The person's answer to an unconfirmed send (B-STATE-044): clear the marker durably, then flush that entry. A
     * receipt-bearing command is bound to its first dispatch: resend only looks it up (B-STATE-008). `Ok(false)` when the
     * entry is not in review any more; `Failed(StorageRefused)` leaves it in review and sends nothing.
     */
    public suspend fun resend(id: String): Outcome<Boolean> {
        val profile = queue().firstOrNull { it.id == id }?.profileId ?: return false.ok()
        val journal = (journals.forProfile(profile) as? Outcome.Ok)?.value
        val receipts = journal?.read()
        if (receipts is Outcome.Failed) return false.ok() // an unreadable journal never authorises a resend
        if (receipts is Outcome.Ok && receipts.value.any { it.commandId == CommandReceipt.queueCommandId(id) }) {
            return checkReceipt(id).ok()
        }
        val cleared = changes.withLock {
            if (sending(id) || id in acceptedUnrecorded) return@withLock false.ok()
            val q = queue()
            val index = q.indexOfFirst { it.id == id }
            if (index < 0 || !q[index].dispatched) return@withLock false.ok()
            val next = q.toMutableList().also { it[index] = q[index].withDispatchedAt(null) }
            if (!store.save(next)) return@withLock Failure.StorageRefused().failed()
            cache = next
            explicitResends += id
            publish()
            true.ok()
        }
        if (cleared is Outcome.Ok && cleared.value) scope.launch { flush() }
        return cleared
    }

    /**
     * The person's Retry on an entry whose send failed before it left the device: it goes out in a flush that starts
     * now, even with automatic sending off. False when it is gone, on its way, or left the device once.
     */
    public suspend fun retry(id: String): Boolean {
        if (sending(id)) return false
        val entry = queue().firstOrNull { it.id == id } ?: return false
        if (entry.dispatched) return false
        explicitResends += id
        publish()
        flush()
        return true
    }

    // ---------------------------------------------------------------- online sends (plan 04 §13.5)

    /** Persists (or refreshes) an online send's marked entry before the composer clears (B-STATE-041, 042). */
    public suspend fun holdOnline(prompt: QueuedPrompt): Boolean =
        changes.withLock {
            if (!prompt.dispatched || prompt.profileId in closedProfiles ||
                prompt.payloadBytes > QueueLimits.MAX_ENTRY_BYTES
            ) {
                return@withLock false
            }
            val q = queue()
            val existing = q.indexOfFirst { it.id == prompt.id }
            if (existing >= 0 && (prompt.id !in onlineInFlight || q[existing].profileId != prompt.profileId ||
                    q[existing].sessionId != prompt.sessionId)
            ) {
                return@withLock false
            }
            val next = q.toMutableList().also { if (existing < 0) it += prompt else it[existing] = prompt }
            // Online journal admission must never evict somebody else's work.
            if (QueueLimits.enforce(next, clock.now().toEpochMilliseconds()).removed > 0) return@withLock false
            if (!store.save(next)) return@withLock false
            onlineInFlight += prompt.id
            cache = next
            publish()
            true
        }

    /** Re-checks the deletion fence immediately before the network write. */
    public fun canDispatchOnline(id: String): Boolean =
        id in onlineInFlight &&
            queue().any { it.id == id && it.profileId == connection.activeProfile.value && it.profileId !in closedProfiles }

    /**
     * Finishes an online send: confirmed acceptance and pre-flight refusals remove the marker; an unknown outcome
     * stays for explicit review with its reason (B-STATE-041, 043).
     */
    public suspend fun finishOnline(id: String, dispatched: Boolean, accepted: Boolean, error: String?) {
        changes.withLock {
            try {
                if (id !in onlineInFlight) return@withLock
                if (accepted || !dispatched) {
                    val next = queue().filter { it.id != id }
                    if (store.save(next)) {
                        cache = next
                    } else if (accepted) {
                        acceptedUnrecorded += id
                    }
                } else if (error != null) {
                    val next = queue().map { if (it.id == id) it.withError(error) else it }
                    if (store.save(next)) cache = next
                }
            } finally {
                onlineInFlight -= id
                publish()
            }
        }
    }

    // ---------------------------------------------------------------- profile removal (B-STATE-062, 066 partly)

    /** Closes queue admission for [profile] at once (online holds, new queue entries, dispatch of its online sends). */
    public suspend fun closeProfile(profile: ProfileId) {
        closedProfiles += profile
        journals.closeProfile(profile)
        changes.withLock { }
    }

    /** Count for the removal confirmation; null when the queue cannot be read (never a zero, B-STATE-066). */
    public fun inspectForRemoval(profile: ProfileId): Int? = if (!store.readable) null else countForProfile(profile)

    /**
     * Sweeps [profile]'s entries, markers included. Refused (nothing removed, markers intact) when the queue is
     * unreadable, a send of that profile is on the wire, or storage refuses (B-STATE-062, 066).
     */
    public suspend fun sweepProfile(profile: ProfileId): Outcome<Int> =
        changes.withLock {
            if (!store.readable) return@withLock Failure.StorageRefused().failed()
            val q = queue()
            if (q.any { it.profileId == profile && sending(it.id) }) return@withLock Failure.Refused(RefusalReason.DispatchUncertain).failed()
            val kept = q.filter { it.profileId != profile }
            val removed = q.size - kept.size
            if (removed == 0) return@withLock 0.ok()
            if (!store.save(kept)) return@withLock Failure.StorageRefused().failed()
            cache = kept
            publish()
            removed.ok()
        }

    // ---------------------------------------------------------------- receipts (B-STATE-008, 009)

    /**
     * "Check again": looks the original receipt up; never sends. Removes the entry only when the server shows the
     * admission, and only the exact entry the receipt was made for (a late lookup keeps a replacement draft).
     */
    public suspend fun checkReceipt(id: String): Boolean {
        val link = connection.link.value ?: return false
        if (!link.connected || inFlight == id || !receiptsSupported(link)) return false
        val origin = link.scope
        val journal = (journals.forProfile(origin.profileId) as? Outcome.Ok)?.value ?: return false
        val commandId = CommandReceipt.queueCommandId(id)
        val receipts = (journal.read() as? Outcome.Ok)?.value ?: return false
        if (receipts.none { it.commandId == commandId && it.scope == origin.receiptScope }) return false
        fun same(): Boolean = connection.link.value === link && link.scope == origin
        val checked = CommandReceiptController(journal).retry(commandId) { receipt ->
            val found = link.lookupAdmission(SessionId(receipt.sessionId), MessageId(receipt.receiptId))
            same() && found is Outcome.Ok && found.value
        }
        val receipt = (checked as? Outcome.Ok)?.value ?: return false
        if (!same() || receipt.state != CommandReceiptState.Confirmed) return false
        val removed = changes.withLock {
            if (!same()) return@withLock false
            val q = queue()
            val kept = q.filter {
                it.id != id || it.profileId != origin.profileId || it.sessionId.raw != receipt.sessionId || !it.dispatched
            }
            if (kept.size == q.size || !store.save(kept)) return@withLock false
            cache = kept
            publish()
            true
        }
        if (removed) {
            acceptedUnrecorded -= id
            publish()
        }
        return removed
    }

    private suspend fun reconcileReceipts(link: SendLink) {
        if (!receiptsSupported(link)) return
        for (entry in queue().toList()) {
            if (entry.profileId != link.scope.profileId || !entry.dispatched) continue
            checkReceipt(entry.id)
        }
    }

    private fun receiptsSupported(link: SendLink): Boolean = link.capabilities.commandReceipts && link.correlated

    // ---------------------------------------------------------------- flush (plan 04 §13.4)

    /**
     * Sends the active profile's queued prompts, oldest first. A failure without a server status stops the batch (the
     * server is still unreachable); one with a status keeps that entry with its reason and continues. Entries whose
     * marker is set are never sent here. Requests during a flush coalesce into one more pass.
     */
    public suspend fun flush() {
        val link = connection.link.value ?: return
        if (!link.capabilities.offlinePromptQueue) return
        if (flushing) {
            flushAgain = true
            return
        }
        val origin = link.scope
        val profile = origin.profileId
        fun eligible(e: QueuedPrompt) = e.profileId == profile && !e.dispatched &&
            (e.id in explicitResends || policy.allowsQueuedSends(profile))
        flushing = true
        var sent = 0
        try {
            reconcileReceipts(link)
            for (entry in queue().toList()) {
                if (!eligible(entry)) continue
                val explicit = entry.id in explicitResends
                fun allowed(): Boolean =
                    connection.link.value === link && link.scope == origin && profile !in closedProfiles &&
                        (explicit || policy.allowsQueuedSends(profile))
                if (!allowed()) break
                val transport = link.awaitActionTransport()
                changes.withLock { }
                if (!allowed() || transport is Outcome.Failed || !link.connected) break
                if (queue().none { it.id == entry.id && !it.dispatched }) continue
                when (sendOne(link, entry, explicit, ::allowed)) {
                    Step.Sent -> sent += 1
                    Step.Next -> Unit
                    Step.Stop -> break
                }
            }
        } finally {
            inFlight = null
            flushing = false
            if (sent > 0) {
                lastFlushed = sent
                lastSkipped = countForOtherProfiles(profile)
                flushRevision += 1
            }
            publish()
            if (flushAgain) {
                flushAgain = false
                scope.launch { flush() }
            }
        }
    }

    private enum class Step { Sent, Next, Stop }

    private suspend fun sendOne(link: SendLink, entry: QueuedPrompt, explicit: Boolean, allowed: () -> Boolean): Step {
        val session = entry.sessionId
        // 1. Staged revert (read only): keep the entry with its reason, never commit the revert (B-PROTO-023, 043).
        if (link.capabilities.sessionRevert) {
            when (val pending = link.revertPending(session)) {
                is Outcome.Failed -> return preflightFailed(entry, pending.failure)
                is Outcome.Ok -> if (pending.value) {
                    replace(entry.id) { it.withError(QueueErrors.SESSION_REVERT_PENDING) }
                    return Step.Next
                }
            }
            if (!allowed()) return Step.Stop
        }
        // 2. The entry's intentional model/agent choice (persisted offline entries carry it).
        val selection = entry.model?.let { ModelSelection(it, entry.variant?.takeIf(String::isNotEmpty)) }
        val agent = entry.agent?.takeIf(String::isNotEmpty)
        if (selection != null || agent != null) {
            val applied = link.applySelection(session, selection, agent)
            if (applied is Outcome.Failed) return preflightFailed(entry, applied.failure)
            if (!allowed()) return Step.Stop
            if (queue().none { it.id == entry.id }) return Step.Next // discarded meanwhile: never sent (B-STATE-037)
        }
        // 3. The durable dispatch marker. Removal and resend refuse this id from the moment this write begins.
        val marked = replace(entry.id) {
            inFlight = entry.id
            it.withDispatchedAt(clock.now().toEpochMilliseconds())
        }
        when (marked) {
            null -> return Step.Next
            false -> {
                inFlight = null
                return Step.Stop // refused marker: nothing was sent (B-STATE-042)
            }
            true -> Unit
        }
        if (!allowed()) {
            // Nothing left the device: put the entry back. A refused rollback keeps the conservative marker.
            replace(entry.id) { entry }
            inFlight = null
            return Step.Stop
        }
        explicitResends -= entry.id
        // 4. Dispatch.
        val prompt = OutgoingPrompt(
            text = entry.text,
            model = selection,
            agent = agent,
            attachments = entry.attachments,
            mentions = entry.mentions,
        )
        val proof: DispatchResult = if (receiptsSupported(link)) {
            dispatchWithReceipt(link, entry, prompt, allowed)
        } else {
            when (val r = link.prompt(session, prompt) { allowed() }) {
                is Outcome.Ok -> if (r.value is PromptAdmission.Confirmed) DispatchResult.Accepted else DispatchResult.Unknown(null)
                is Outcome.Failed -> DispatchResult.Unknown(r.failure)
            }
        }
        val step = when (proof) {
            DispatchResult.Accepted -> {
                // Until this write lands the marker keeps the entry out of every future flush: a refusal or a death
                // here can only cost a review, never a duplicate.
                if (persistRemoval(entry.id)) {
                    Step.Sent
                } else {
                    acceptedUnrecorded += entry.id
                    replace(entry.id) { it.withError(QueueErrors.ACCEPTED_UNRECORDED) }
                    Step.Stop
                }
            }
            is DispatchResult.Unknown -> {
                // After dispatch a status does not prove non-delivery: keep the marker, wait for the person.
                replace(entry.id) { it.withError(proof.failure?.let(QueueErrors::of) ?: QueueErrors.UNCONFIRMED) }
                if (proof.failure?.hasStatus == true) Step.Next else Step.Stop
            }
        }
        inFlight = null
        publish()
        return step
    }

    private sealed interface DispatchResult {
        data object Accepted : DispatchResult

        data class Unknown(val failure: Failure?) : DispatchResult
    }

    private suspend fun dispatchWithReceipt(
        link: SendLink,
        entry: QueuedPrompt,
        prompt: OutgoingPrompt,
        allowed: () -> Boolean,
    ): DispatchResult {
        val journal: PendingCommandJournal =
            (journals.forProfile(entry.profileId) as? Outcome.Ok)?.value ?: return DispatchResult.Unknown(null)
        val wireId = link.newClientMessageId() ?: return DispatchResult.Unknown(null)
        var failure: Failure? = null
        val receipt = CommandReceipt(
            commandId = CommandReceipt.queueCommandId(entry.id),
            receiptId = wireId.raw,
            sessionId = entry.sessionId.raw,
            tabId = entry.sessionId.raw,
            scope = link.scope.receiptScope,
            createdAt = clock.now().toEpochMilliseconds(),
            state = CommandReceiptState.Sent,
        )
        val sent = CommandReceiptController(journal).send(receipt) { receiptId ->
            if (!allowed()) return@send DispatchProof.Uncertain
            val r = link.prompt(entry.sessionId, prompt.copy(clientMessageId = MessageId(receiptId))) {
                allowed() && journal.isOpen // profile deletion fences the actual send
            }
            when {
                r is Outcome.Ok && r.value is PromptAdmission.Confirmed &&
                    (r.value as PromptAdmission.Confirmed).receiptId.let { it == null || it == receiptId } -> DispatchProof.Confirmed
                else -> {
                    failure = (r as? Outcome.Failed)?.failure
                    DispatchProof.Uncertain
                }
            }
        }
        return when {
            sent is Outcome.Ok && sent.value.state == CommandReceiptState.Confirmed -> DispatchResult.Accepted
            else -> DispatchResult.Unknown(failure)
        }
    }

    /** Before the marker: nothing left. With a status the entry keeps its reason and the batch goes on. */
    private suspend fun preflightFailed(entry: QueuedPrompt, failure: Failure): Step {
        if (!failure.hasStatus) return Step.Stop
        replace(entry.id) { it.withError(QueueErrors.of(failure)) }
        return Step.Next
    }

    /** Null when the entry is gone; false when storage refused (memory and disk unchanged); true when persisted. */
    private suspend fun replace(id: String, update: (QueuedPrompt) -> QueuedPrompt): Boolean? =
        changes.withLock {
            val q = queue()
            val index = q.indexOfFirst { it.id == id }
            if (index < 0) return@withLock null
            val next = q.toMutableList().also { it[index] = update(q[index]) }
            if (!store.save(next)) return@withLock false
            cache = next
            publish()
            true
        }

    /** A stale in-memory removal is never applied over a persisted entry: it would resend after a restart. */
    private suspend fun persistRemoval(id: String): Boolean =
        changes.withLock {
            val q = queue()
            val kept = q.filter { it.id != id }
            if (kept.size == q.size) return@withLock true
            if (!store.save(kept)) return@withLock false
            cache = kept
            publish()
            true
        }

    private fun publish() {
        val entries = cache ?: return
        if (entries.isNotEmpty()) lastReadable = true // a non-empty queue is only ever written over a readable blob
        mutableState.value =
            QueueState(
                entries = entries.toImmutableList(),
                readable = lastReadable,
                sending = (listOfNotNull(inFlight) + onlineInFlight).toImmutableSet(),
                acceptedUnrecorded = acceptedUnrecorded.toImmutableSet(),
                eviction = eviction,
                flushRevision = flushRevision,
                lastFlushedCount = lastFlushed,
                lastFlushSkippedForOtherProfiles = lastSkipped,
            )
    }

    /** Online sends in flight (bubbles, not queue rows). */
    public fun onlineInFlight(): Set<String> = onlineInFlight.toSet()

    /** Dispatched, not confirmed: the draft never restores one of these (09 §10.8). */
    public fun unconfirmedTexts(ref: ConversationRef): Set<String> =
        queue().filter { it.ref == ref && it.dispatched }.mapTo(LinkedHashSet()) { it.text }
}

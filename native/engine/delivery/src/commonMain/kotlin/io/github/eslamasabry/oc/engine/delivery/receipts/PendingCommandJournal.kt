package io.github.eslamasabry.oc.engine.delivery.receipts

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Profiles that exist now (Dart reads `oc.profiles`; here the profiles engine answers). Bound by
 * the app graph.
 */
public fun interface ProfileDirectory {
    public fun exists(profile: ProfileId): Boolean
}

/**
 * `oc.pendingCommands.<profileId>` (port of `lib/state/pending_command_journal.dart`; plan 04
 * §13.6, rows B-STATE-008, B-STATE-009). Durable command identities only. Completed receipts stay
 * as tombstones; reaching the limits blocks new commands and never evicts. One writer per profile:
 * the profile's [WriterLanes] lane plus this journal's own order.
 *
 * Fail closed everywhere: an unreadable envelope, a write the store refused, or a read-back that
 * differs marks the journal unavailable for this process, and an unavailable journal grants no
 * claim (so nothing is sent through it).
 */
public class PendingCommandJournal
internal constructor(
    public val profile: ProfileId,
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val profiles: ProfileDirectory,
    private val isCurrentOwner: () -> Boolean = { true },
) {
    private val order = Mutex()
    private val admitted = AtomicReference(true)
    private val storageAvailable = AtomicReference(true)

    public val key: String = "$KEY_PREFIX${profile.raw}"

    private fun admission(): Failure? =
        when {
            !admitted.load() || !isCurrentOwner() || !profiles.exists(profile) ->
                Failure.Refused(RefusalReason.ProfileClosing)
            !storageAvailable.load() -> Failure.StorageRefused()
            else -> null
        }

    public fun read(): Outcome<List<CommandReceipt>> {
        admission()?.let {
            return it.failed()
        }
        return storedSnapshot()
    }

    /** Read-only deletion inspection after admission has closed and the writer has drained. */
    internal fun closedSnapshot(): Outcome<List<CommandReceipt>> {
        if (admitted.load() || !storageAvailable.load()) return Failure.StorageRefused().failed()
        return storedSnapshot()
    }

    private fun storedSnapshot(): Outcome<List<CommandReceipt>> {
        // B-STATE-050: a store that cannot load fails closed before any platform write, and stays
        // closed.
        val value =
            try {
                store.get(key)
            } catch (e: IllegalStateException) {
                storageAvailable.store(false)
                return Failure.StorageRefused().failed()
            } ?: return emptyList<CommandReceipt>().ok()
        val parsed = (value as? PrefValue.Str)?.value?.let(::parse)
        if (parsed == null) {
            storageAvailable.store(false)
            return Failure.StorageRefused().failed()
        }
        return parsed.ok()
    }

    /**
     * The caller may transmit ONLY after this answers `Ok(true)`. An existing id (sent, uncertain,
     * confirmed or failed) answers `Ok(false)` and is never replaced (B-STATE-009: simultaneous
     * sends with one command dispatch once).
     */
    public suspend fun claim(receipt: CommandReceipt): Outcome<Boolean> = serialized {
        val commands =
            when (val r = read()) {
                is Outcome.Ok -> r.value
                is Outcome.Failed -> return@serialized r
            }
        if (commands.any { it.commandId == receipt.commandId }) return@serialized false.ok()
        if (
            !receipt.valid ||
                receipt.state != CommandReceiptState.Sent ||
                commands.size >= MAX_ENTRIES
        ) {
            return@serialized Failure.Refused(RefusalReason.DispatchUncertain).failed()
        }
        write(commands + receipt).flatMapTo { true }
    }

    /**
     * Changes the state of a known command without changing its identity. An absent receipt is
     * never created here; uncertain and terminal receipts never become `sent` again, and terminal
     * ones never change (B-STATE-009).
     */
    public suspend fun update(receipt: CommandReceipt): Outcome<Unit> = serialized {
        val commands =
            when (val r = read()) {
                is Outcome.Ok -> r.value.toMutableList()
                is Outcome.Failed -> return@serialized r
            }
        val index = commands.indexOfFirst { it.commandId == receipt.commandId }
        if (
            !receipt.valid ||
                index < 0 ||
                !commands[index].sameIdentity(receipt) ||
                !validTransition(commands[index].state, receipt.state)
        ) {
            return@serialized Failure.Refused(RefusalReason.DispatchUncertain).failed()
        }
        commands[index] = receipt
        write(commands)
    }

    /**
     * Closes new admission at once, then waits for every admitted operation (Dart `closeProfile`).
     * The profile deletion owner awaits this BEFORE key discovery and removal; a closed journal
     * stays closed (B-STATE-009, 041).
     */
    public suspend fun close() {
        admitted.store(false)
        order.withLock {}
    }

    public val isOpen: Boolean
        get() = admitted.load() && isCurrentOwner()

    private suspend fun <T> serialized(block: suspend () -> Outcome<T>): Outcome<T> {
        admission()?.let {
            return it.failed()
        }
        return order.withLock {
            admission()?.let {
                return@withLock it.failed()
            }
            block()
        }
    }

    private suspend fun write(commands: List<CommandReceipt>): Outcome<Unit> {
        val encoded = encode(commands)
        if (DartJson.utf8(encoded).size > MAX_BYTES)
            return Failure.Refused(RefusalReason.DispatchUncertain).failed()
        admission()?.let {
            return it.failed()
        }
        val verified =
            attempt({ Failure.StorageRefused() }) {
                val committed = store.commit(lanes.forProfile(profile)) { putString(key, encoded) }
                committed is Outcome.Ok && (store.get(key) as? PrefValue.Str)?.value == encoded
            }
        // Only acknowledged, read-back data grants dispatch; failed or throwing storage stays
        // unavailable.
        if (verified !is Outcome.Ok || !verified.value) {
            storageAvailable.store(false)
            return Failure.StorageRefused().failed()
        }
        admission()?.let {
            return it.failed()
        }
        return Unit.ok()
    }

    private inline fun <T> Outcome<Unit>.flatMapTo(value: () -> T): Outcome<T> =
        when (this) {
            is Outcome.Ok -> value().ok()
            is Outcome.Failed -> this
        }

    public companion object {
        public const val KEY_PREFIX: String = "oc.pendingCommands."
        public const val MAX_ENTRIES: Int = 1000
        public const val MAX_BYTES: Int = 1024 * 1024
        private val PROFILE_ID = Regex("^[a-zA-Z0-9_-]{1,128}$")

        internal fun validTransition(
            previous: CommandReceiptState,
            next: CommandReceiptState,
        ): Boolean =
            when (previous) {
                CommandReceiptState.Sent -> true
                CommandReceiptState.Uncertain -> next != CommandReceiptState.Sent
                CommandReceiptState.Confirmed,
                CommandReceiptState.Failed -> next == previous
            }

        internal fun profileIdValid(profile: ProfileId): Boolean = PROFILE_ID.matches(profile.raw)

        public fun encode(commands: List<CommandReceipt>): String =
            DartJson.encode(
                linkedMapOf<String, Any?>(
                    "schemaVersion" to 1L,
                    "commands" to commands.map { it.toJson() },
                )
            )

        /**
         * Dart `read`: ≤ 1 MiB UTF-8, `{schemaVersion: 1, commands: [...]}` and nothing else, ≤
         * 1000 unique valid entries.
         */
        public fun parse(raw: String): List<CommandReceipt>? {
            if (DartJson.utf8(raw).size > MAX_BYTES) return null
            val envelope = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
            val version = envelope["schemaVersion"]
            val versionOk = version == 1L || version == 1.0 // Dart: 1.0 == 1
            val list = envelope["commands"] as? List<*>
            if (envelope.size != 2 || !versionOk || list == null || list.size > MAX_ENTRIES)
                return null
            val ids = HashSet<String>()
            return list.map { entry ->
                val receipt = CommandReceipt.fromJson(entry) ?: return null
                if (!ids.add(receipt.commandId)) return null
                receipt
            }
        }
    }
}

/**
 * One journal per profile per process (Dart's `Expando` owners), and the closed set: a profile
 * closed for deletion gets no new journal, even if the deletion is later cancelled.
 */
public class PendingCommandJournals(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val profiles: ProfileDirectory,
) {
    private data class Owners(
        val journals: Map<ProfileId, PendingCommandJournal> = emptyMap(),
        val closed: Set<ProfileId> = emptySet(),
        val epochs: Map<ProfileId, Long> = emptyMap(),
    )

    private val owners = AtomicReference(Owners())

    private fun fresh(profile: ProfileId, epoch: Long?): PendingCommandJournal =
        PendingCommandJournal(profile, store, lanes, profiles) {
            val now = owners.load()
            profile !in now.closed && now.epochs[profile] == epoch
        }

    public fun forProfile(profile: ProfileId): Outcome<PendingCommandJournal> {
        while (true) {
            val before = owners.load()
            if (
                !PendingCommandJournal.profileIdValid(profile) ||
                    profile in before.closed ||
                    !profiles.exists(profile)
            )
                return Failure.Refused(RefusalReason.ProfileClosing).failed()
            before.journals[profile]?.let {
                return it.ok()
            }
            val journal = fresh(profile, before.epochs[profile])
            if (
                owners.compareAndSet(
                    before,
                    before.copy(journals = before.journals + (profile to journal)),
                )
            )
                return journal.ok()
        }
    }

    /** Read only, after close: deleting uncertain work must never admit another command. */
    public suspend fun deletionSnapshot(profile: ProfileId): Outcome<List<CommandReceipt>> {
        val owner: PendingCommandJournal
        while (true) {
            val before = owners.load()
            if (profile !in before.closed || !PendingCommandJournal.profileIdValid(profile))
                return Failure.StorageRefused().failed()
            val existing = before.journals[profile]
            if (existing != null) {
                owner = existing
                break
            }
            val created = fresh(profile, before.epochs[profile])
            if (
                owners.compareAndSet(
                    before,
                    before.copy(journals = before.journals + (profile to created)),
                )
            ) {
                owner = created
                break
            }
        }
        owner.close()
        return owner.closedSnapshot()
    }

    /**
     * Closes admission atomically across every connection before draining any suspending writer.
     */
    public suspend fun closeProfile(profile: ProfileId) {
        val previous: PendingCommandJournal?
        while (true) {
            val before = owners.load()
            val after =
                before.copy(
                    closed = before.closed + profile,
                    epochs = before.epochs + (profile to ((before.epochs[profile] ?: 0L) + 1L)),
                )
            if (owners.compareAndSet(before, after)) {
                previous = before.journals[profile]
                break
            }
        }
        previous?.close()
    }

    /** Only an aborted deletion reopens, with a fresh owner; every old facade remains closed. */
    public suspend fun cancelDeletion(profile: ProfileId) {
        val before = owners.load()
        if (profile !in before.closed) return
        before.journals[profile]?.close()
        while (true) {
            val now = owners.load()
            if (
                now.epochs[profile] != before.epochs[profile] ||
                    now.journals[profile] !== before.journals[profile] ||
                    !profiles.exists(profile)
            )
                return
            if (
                owners.compareAndSet(
                    now,
                    now.copy(journals = now.journals - profile, closed = now.closed - profile),
                )
            )
                return
        }
    }
}

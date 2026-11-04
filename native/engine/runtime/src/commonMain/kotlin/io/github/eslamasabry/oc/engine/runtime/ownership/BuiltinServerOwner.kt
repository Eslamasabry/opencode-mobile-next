package io.github.eslamasabry.oc.engine.runtime.ownership

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The acknowledged runtime pointer; a wrong-type or unreadable value must return Failed. */
public interface BuiltinOwnerStorage {
    public suspend fun read(): Outcome<String?>

    public suspend fun write(value: String): Outcome<Unit>
}

/**
 * B-SEC-011: the one serial owner of the phone runtime's profile pointer. Admission closes
 * synchronously before deletion waits for an admitted claim. A clear failure keeps deletion
 * retryable; no optimistic cache is held here.
 */
public class BuiltinServerOwner(private val storage: BuiltinOwnerStorage) {
    private val lane = Mutex()
    private val closed = AtomicReference<Set<String>>(emptySet())

    public fun stopAdmission(profile: String) {
        while (true) {
            val before = closed.load()
            if (closed.compareAndSet(before, before + profile)) return
        }
    }

    public fun reopen(profile: String) {
        while (true) {
            val before = closed.load()
            if (closed.compareAndSet(before, before - profile)) return
        }
    }

    public suspend fun drain() {
        lane.withLock {}
    }

    public suspend fun claim(profile: String, readable: () -> Boolean): Outcome<Unit> {
        fun admitted(): Boolean = profile.isNotEmpty() && profile !in closed.load() && readable()
        if (!admitted()) return Failure.Refused(RefusalReason.ProfileClosing).failed()
        return lane.withLock {
            // A previous refused write cannot conceal a different acknowledged owner.
            when (val prior = storage.read()) {
                is Outcome.Failed -> return@withLock prior
                is Outcome.Ok -> Unit
            }
            if (!admitted()) return@withLock Failure.Refused(RefusalReason.ProfileClosing).failed()
            val saved = storage.write(profile)
            if (saved is Outcome.Failed) return@withLock saved
            if (!admitted()) Failure.Refused(RefusalReason.ProfileClosing).failed() else Unit.ok()
        }
    }

    /** The empty tombstone suppresses legacy fallback to another saved local server. */
    public suspend fun clearProfile(profile: String): Outcome<Int> {
        stopAdmission(profile)
        return lane.withLock {
            when (val current = storage.read()) {
                is Outcome.Failed -> current
                is Outcome.Ok -> {
                    if (current.value != profile) return@withLock 0.ok()
                    when (val saved = storage.write("")) {
                        is Outcome.Failed -> saved
                        is Outcome.Ok -> 1.ok()
                    }
                }
            }
        }
    }
}

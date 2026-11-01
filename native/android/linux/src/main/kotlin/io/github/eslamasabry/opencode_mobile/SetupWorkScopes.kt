package io.github.eslamasabry.opencode_mobile

import java.util.concurrent.atomic.AtomicBoolean

/** One CPU admission per whole setup job, including a worker not yet scheduled. */
internal class SetupWorkScopes(
    private val acquire: (Any, () -> Boolean) -> Unit,
    private val release: (Any) -> Unit,
) {
    class Scope internal constructor() {
        internal val active = AtomicBoolean(true)
        internal val entered = AtomicBoolean(false)
    }
    private val current = ThreadLocal<Scope>()
    fun prepare(): Scope = Scope().also { scope ->
        var admitted = false
        try { acquire(scope) { scope.active.get() }; admitted = true }
        finally { if (!admitted) close(scope) }
    }
    fun close(scope: Scope) { scope.active.set(false); release(scope) }
    fun <T> run(scope: Scope, work: () -> T): T {
        check(scope.entered.compareAndSet(false, true)) { "The phone setup could not start. Try again." }
        val previous = current.get()
        current.set(scope)
        try { return work() } finally {
            if (previous == null) current.remove() else current.set(previous)
            close(scope)
        }
    }
    /** A revoked/capped job still owns this context; its next child cannot refill it. */
    fun childNeedsLease(): Boolean = current.get() == null
}

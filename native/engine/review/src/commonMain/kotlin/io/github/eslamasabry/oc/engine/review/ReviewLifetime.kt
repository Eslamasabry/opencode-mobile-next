package io.github.eslamasabry.oc.engine.review

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * One review's own lifetime under [parent]: [scope] runs on [parent] plus [extra], and [close] stops everything the
 * review started without touching the parent or its other reviews. Kept here so a feature holding reviews needs no
 * coroutine machinery of its own (K1).
 */
public class ReviewLifetime(parent: CoroutineScope, extra: CoroutineContext) {
    private val job = SupervisorJob(parent.coroutineContext[Job])

    public val scope: CoroutineScope = CoroutineScope(parent.coroutineContext + extra + job)

    public fun close() {
        job.cancel()
    }
}

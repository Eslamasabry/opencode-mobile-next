package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Outcome

/** Real app-owned bytes while a persisted native setup step waits for its component. */
public data class SetupAppProgress(
    val stage: String? = null,
    val bytesDone: Long? = null,
    val bytesTotal: Long? = null,
)

/** App bindings own their download lease; cancel may never stop a download started elsewhere. */
public interface SetupAppComponents {
    public suspend fun check(id: String): Outcome<SetupCheckResult>

    public suspend fun install(id: String, onProgress: (SetupAppProgress) -> Unit): Outcome<Unit>

    public suspend fun cancel(id: String)
}

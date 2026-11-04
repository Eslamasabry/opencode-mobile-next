package io.github.eslamasabry.oc.feature.codexprobe

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow

public data class CodexProbeUi(
    val busy: Boolean = false,
    val probed: Boolean = false,
    val connected: Boolean = false,
    val accountQualified: Boolean = false,
    val failure: Failure? = null,
)

public interface CodexProbeHost {
    public val state: StateFlow<CodexProbeUi>
    public val startCommand: String

    public suspend fun probe(address: String, directory: String, token: String): Outcome<Unit>

    public suspend fun open(address: String, directory: String, token: String): Outcome<Unit>

    public suspend fun close()
}

public interface CodexProbeActions {
    public val startCommand: String

    public fun probe(address: String, directory: String, token: String)

    public fun open(address: String, directory: String, token: String)

    public fun close()

    public fun plans()

    public fun back()
}

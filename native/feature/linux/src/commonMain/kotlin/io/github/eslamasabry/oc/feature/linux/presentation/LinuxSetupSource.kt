package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import kotlinx.coroutines.flow.StateFlow

/** The durable phone setup job, shared by setup, Repair and Add tools. */
public interface LinuxSetupSource {
    public val progress: StateFlow<SetupProgress>
    public val toolsChanged: StateFlow<Int>

    public suspend fun refresh(): Outcome<Unit>

    public suspend fun start(): Outcome<Unit>

    public suspend fun addPython(): Outcome<Unit>

    public suspend fun resume(): Outcome<Unit>

    public suspend fun cancel(): Outcome<Unit>
}

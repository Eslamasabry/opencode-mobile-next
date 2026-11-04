package io.github.eslamasabry.oc.engine.project.terminal

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import kotlinx.coroutines.flow.StateFlow

/** App-owned shells. Closing a screen never stops a shell or discards its emulator. */
public data class LocalShell(
    val id: Int,
    val number: Int,
    val pid: Int,
    val running: Boolean,
    val exitCode: Int?,
    val transcript: String,
    val view: PlatformViewHandle,
    val control: Boolean = false,
    val confined: Boolean = true,
    val rows: Int = 24,
    val cols: Int = 80,
)

public interface LocalTerminalPort {
    public val shells: StateFlow<List<LocalShell>>

    public suspend fun start(rows: Int = 24, cols: Int = 80): Outcome<Int>

    public suspend fun send(id: Int, text: String): Outcome<Unit>

    public suspend fun resize(id: Int, rows: Int, cols: Int): Outcome<Unit>

    public suspend fun stop(id: Int): Outcome<Unit>

    public suspend fun remove(id: Int): Outcome<Unit>

    public suspend fun restart(id: Int): Outcome<Int>

    public suspend fun control(id: Int, enabled: Boolean): Outcome<Unit>
}

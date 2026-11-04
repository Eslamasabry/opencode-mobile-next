package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import kotlinx.coroutines.flow.StateFlow

/** The platform's real terminal emulator. Its lifetime never owns or closes the remote channel. */
public interface RemoteTerminalRenderer {
    public suspend fun open(
        onInput: (String) -> Unit,
        onResize: (rows: Int, cols: Int) -> Unit,
    ): Outcome<RemoteTerminalRenderSession>
}

public interface RemoteTerminalRenderSession {
    public val view: PlatformViewHandle
    public val transcript: StateFlow<String>

    public suspend fun append(text: String): Outcome<Unit>

    public suspend fun close(): Outcome<Unit>
}

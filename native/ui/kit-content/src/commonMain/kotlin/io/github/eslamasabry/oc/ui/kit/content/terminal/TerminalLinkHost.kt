package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** The app checks ExternalLinkPolicy; the slot asks before the approved opener is called. */
public fun interface TerminalLinkHost {
    public fun review(url: String): TerminalLinkReview?
}

public class TerminalLinkReview(
    public val host: String,
    public val insecure: Boolean,
    public val confirm: () -> Unit,
)

public val LocalTerminalLinkHost: ProvidableCompositionLocal<TerminalLinkHost?> =
    staticCompositionLocalOf {
        null
    }

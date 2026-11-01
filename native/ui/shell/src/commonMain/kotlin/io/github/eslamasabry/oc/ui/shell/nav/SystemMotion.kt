package io.github.eslamasabry.oc.ui.shell.nav

import kotlinx.coroutines.flow.StateFlow

/**
 * The system animator scale, live (plan 08 §3.1: the demo read it once, which was a bug). 0 = "Remove animations".
 * A service bound per platform through Metro (ADR §7.3 rule 1). Moves behind `KitPlatform`'s motion signals when the
 * kit owner lands unit M0.
 */
public interface SystemMotion {
    public val animatorScale: StateFlow<Float>
}

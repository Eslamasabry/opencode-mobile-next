package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Immutable

/**
 * Enabled, or disabled with the reason a person hears once (plan 06 §16.2; A11Y: a disabled control says why). The type
 * carries the rule, so a disabled part cannot be built without its reason.
 */
@Immutable
public sealed interface KitEnabled {
    public data object Enabled : KitEnabled

    /**
     * [answer]: what a tap does instead of the reason toast, for a reason that belongs to a place on the page (an empty
     * box: the answer is to bring the person to it). The toast drops over the page's title, so a reason that has a
     * place of its own says it there.
     */
    public data class Disabled(override val reason: String, val answer: (() -> Unit)? = null) : KitEnabled

    public val isEnabled: Boolean get() = this is Enabled

    /** The reason a disabled part states once; null when enabled. */
    public val reason: String? get() = null

    public companion object {
        /** Bridges the slice-0 `enabled` + `disabledReason` pair. */
        public fun of(enabled: Boolean, reason: String?): KitEnabled =
            if (enabled) Enabled else Disabled(reason.orEmpty())
    }
}

package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Immutable

/**
 * Enabled, or disabled with the reason a person hears once (plan 06 §16.2; A11Y: a disabled control says why). The type
 * carries the rule, so a disabled part cannot be built without its reason.
 */
@Immutable
public sealed interface KitEnabled {
    public data object Enabled : KitEnabled

    public data class Disabled(override val reason: String) : KitEnabled

    public val isEnabled: Boolean get() = this is Enabled

    /** The reason a disabled part states once; null when enabled. */
    public val reason: String? get() = null

    public companion object {
        /** Bridges the slice-0 `enabled` + `disabledReason` pair. */
        public fun of(enabled: Boolean, reason: String?): KitEnabled =
            if (enabled) Enabled else Disabled(reason.orEmpty())
    }
}

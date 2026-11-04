package io.github.eslamasabry.oc.ui.shell

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.kit.status.KitStatus

/**
 * One app-wide condition; app bindings project authoritative engine state into the existing kit
 * slot.
 */
public interface ShellStatusSource {
    @Composable public fun status(): KitStatus?
}

package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.runtime.components.ComponentRemovalEntry
import io.github.eslamasabry.oc.engine.runtime.components.RemovalResult
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflightResult
import kotlinx.coroutines.flow.StateFlow

/** Observed native inventory and admission; unknown readings never imply absent programs. */
public data class LinuxToolsUi(
    val entries: List<ComponentRemovalEntry> = emptyList(),
    val loading: Boolean = false,
    val removing: Boolean = false,
    val removal: RemovalResult? = null,
    val preflight: SetupPreflightResult? = null,
    val details: Redacted? = null,
)

public interface LinuxToolsSource {
    public val state: StateFlow<LinuxToolsUi>

    public suspend fun refresh()

    public suspend fun pythonBytes(): Long?

    public suspend fun removePython()

    /** The app's own settings page (Force stop is the ownership recovery). */
    public fun openAppSettings()

    /** The phone's storage settings, for a setup refused on free space. */
    public fun openStorageSettings()
}

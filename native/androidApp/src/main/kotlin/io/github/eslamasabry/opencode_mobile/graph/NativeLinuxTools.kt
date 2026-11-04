package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.runtime.components.*
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxToolsSource
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxToolsUi
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SetupRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The visible components panel and setup share one mutation lane and one tools revision. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NativeLinuxTools(
    private val app: Application,
    private val setup: NativePhoneSetup,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
    appScope: AppCoroutineScope,
) : LinuxToolsSource {
    private val serial = dispatchers.serial("linux-tools")
    private val scope = CoroutineScope(appScope.coroutineContext + serial)
    private val shown = MutableStateFlow(LinuxToolsUi())
    private var inventoryRevision = 0
    private var refreshRequested = false
    override val state: StateFlow<LinuxToolsUi> = shown.asStateFlow()
    private val linux by lazy { BuiltinLinux.get(app) }

    private suspend fun <T> native(block: () -> T): Outcome<T> =
        withContext(dispatchers.io) { attempt(::classifyRuntime) { block() } }

    private val removal =
        ComponentRemoval(
            shell =
                LinuxShell { script, timeout ->
                    native {
                        linux.run(script, timeout.inWholeSeconds).let {
                            ShellResult(it.exitCode, it.output)
                        }
                    }
                },
            status =
                object : SetupStatusReader {
                    override suspend fun linuxState(): Outcome<LinuxInstallState> = native {
                        val status = linux.serverIdleStatus()
                        LinuxInstallState(
                            installed = status["installed"] == true,
                            installing = status["phase"] == "installing",
                        )
                    }

                    override suspend fun setupRecord(): Outcome<String?> = native {
                        SetupRunner.get(app).status()
                    }
                },
            registry = RemovableComponents.builtin,
            toolsChanged = ToolsChangedListener { setup.toolsInvalidated() },
            scope = scope,
        )

    init {
        scope.launch {
            setup.admission.collect { result -> shown.update { it.copy(preflight = result) } }
        }
        scope.launch {
            setup.progress.collect { progress ->
                val details =
                    progress.logTail
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            redactor.details(redactor.diagnostics(redactor.excerpt(it)))
                        }
                shown.update { it.copy(details = details) }
            }
        }
        scope.launch {
            setup.toolsChanged.collect {
                inventoryRevision++
                removal.invalidateInventory()
                scope.launch { refresh() }
            }
        }
    }

    override suspend fun refresh() =
        withContext(serial) {
            if (shown.value.loading || shown.value.removing) {
                refreshRequested = true
                return@withContext
            }
            shown.update { it.copy(loading = true) }
            try {
                do {
                    refreshRequested = false
                    val revision = inventoryRevision
                    val entries = setup.toolsCommand { removal.inventory() }
                    if (revision == inventoryRevision) shown.update { it.copy(entries = entries) }
                    else refreshRequested = true
                } while (refreshRequested)
            } finally {
                shown.update { it.copy(loading = false) }
            }
        }

    override suspend fun pythonBytes(): Long? = setup.toolsCommand {
        removal.freedBytes(ComponentIds.PYTHON)
    }

    override suspend fun removePython() =
        withContext(serial) {
            if (shown.value.removing || shown.value.loading) return@withContext
            shown.update { it.copy(removing = true, removal = null) }
            try {
                setup.toolsCommand {
                    val result = removal.remove(ComponentIds.PYTHON)
                    val entries = removal.inventory()
                    shown.update { it.copy(entries = entries, removal = result) }
                }
            } finally {
                shown.update { it.copy(removing = false) }
            }
            if (refreshRequested) refresh()
        }

    override fun openAppSettings() {
        open(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${app.packageName}"),
            )
        )
    }

    override fun openStorageSettings() {
        if (!open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))) openAppSettings()
    }

    private fun open(intent: Intent): Boolean =
        try {
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}

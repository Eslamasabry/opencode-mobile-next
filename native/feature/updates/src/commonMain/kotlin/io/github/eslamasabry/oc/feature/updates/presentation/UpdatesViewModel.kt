package io.github.eslamasabry.oc.feature.updates.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.updates.AppUpdates
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

private data class LocalLinkState(
    val prompt: NoteLinkPrompt? = null,
    val noBrowser: Boolean = false,
)

/**
 * The Updates page (Flutter `UpdatesScreen`). While it is open the app-wide notice steps aside (setPageOpen). Engine
 * commands run in the app scope, not the page's: leaving the page never cancels a download the person started (the
 * engine's own Cancel does). Failures are already in [AppUpdates.state]; the returned Outcome is not shown.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class UpdatesViewModel(
    private val updates: AppUpdates,
    private val links: UpdateLinks,
    private val appScope: AppCoroutineScope,
    private val clock: Clock,
    private val nav: AppNavigator,
) : ViewModel(), UpdatesActions {
    private val localLinks = MutableStateFlow(LocalLinkState())

    init {
        updates.setPageOpen(true)
    }

    public val state: StateFlow<UpdatesUi> =
        combine(updates.state, localLinks) { engine, linkState ->
                updatesUi(engine, clock.now())
                    .copy(linkPrompt = linkState.prompt, noBrowser = linkState.noBrowser)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                updatesUi(updates.state.value, clock.now()),
            )

    override fun onBack(): Unit = nav.back()

    override fun onUpdate() {
        appScope.launch { updates.updateNow() }
    }

    override fun onCancel() {
        updates.cancel()
    }

    override fun onInstall() {
        appScope.launch { updates.updateNow() }
    }

    override fun onTryAgain() {
        val now = updates.state.value
        if (now.phase == UpdatePhase.Failed && !now.retryHelps) return
        appScope.launch { updates.updateNow() }
    }

    override fun onOpenRelease() {
        val url = updates.state.value.release?.pageUrl.orEmpty()
        if (url.isEmpty()) return
        viewModelScope.launch {
            val result = links.openReleasePage(url)
            if (result is Outcome.Failed && result.failure is Failure.Device) {
                localLinks.update { it.copy(noBrowser = true) }
            }
        }
    }

    override fun onCheckNow() {
        appScope.launch { updates.checkNow() }
    }

    override fun onChecks(on: Boolean) {
        appScope.launch { updates.setEnabled(on) }
    }

    override fun onOpenNoteLink(url: String) {
        val prompt = links.inspectNoteLink(url) ?: return
        localLinks.update { it.copy(prompt = prompt, noBrowser = false) }
    }

    override fun onConfirmNoteLink() {
        val prompt = localLinks.value.prompt ?: return
        localLinks.update { it.copy(prompt = null) }
        viewModelScope.launch {
            val result = links.openNoteLink(prompt.url)
            if (result is Outcome.Failed) {
                localLinks.update { it.copy(noBrowser = true) }
            }
        }
    }

    override fun onDismissNoteLink() {
        localLinks.update { it.copy(prompt = null) }
    }

    override fun onDismissLinkNotice() {
        localLinks.update { it.copy(noBrowser = false) }
    }

    override fun onCleared() {
        updates.setPageOpen(false)
    }
}

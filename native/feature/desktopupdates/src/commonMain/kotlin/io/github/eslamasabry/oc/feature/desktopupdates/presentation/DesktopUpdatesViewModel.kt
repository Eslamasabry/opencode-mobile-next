package io.github.eslamasabry.oc.feature.desktopupdates.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.updates.DesktopReleaseUpdates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public enum class DesktopCheckPhase {
    Never,
    Checking,
    Current,
    Available,
    Failed,
}

public data class DesktopUpdatesUi(
    val version: String,
    val phase: DesktopCheckPhase = DesktopCheckPhase.Never,
    val releaseTag: String? = null,
    val releasePage: String? = null,
    val askingToOpen: Boolean = false,
    val openFailed: Boolean = false,
)

public interface DesktopUpdatesActions {
    public fun onCheck()

    public fun onOpenRelease()

    public fun onConfirmOpen()

    public fun onCancelOpen()

    public fun onClose()
}

public fun interface DesktopReleaseLinks {
    /**
     * The platform rechecks the trusted release URL, then ExternalLinkPolicy → ExternalLinkOpener
     * after confirmation.
     */
    public suspend fun open(url: String): Outcome<Unit>
}

public class DesktopUpdatesViewModel(
    private val updates: DesktopReleaseUpdates,
    version: String,
    private val links: DesktopReleaseLinks,
    private val close: () -> Unit,
) : ViewModel(), DesktopUpdatesActions {
    private val mutable = MutableStateFlow(DesktopUpdatesUi(version))
    public val state: StateFlow<DesktopUpdatesUi> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            updates.state.collect { result ->
                mutable.update {
                    it.copy(
                        phase =
                            when {
                                result.checking -> DesktopCheckPhase.Checking
                                result.failure != null -> DesktopCheckPhase.Failed
                                result.release != null -> DesktopCheckPhase.Available
                                result.checked -> DesktopCheckPhase.Current
                                else -> DesktopCheckPhase.Never
                            },
                        releaseTag = result.release?.tag,
                        releasePage = result.release?.pageUrl,
                    )
                }
            }
        }
    }

    override fun onCheck() {
        if (updates.state.value.checking) return
        viewModelScope.launch { updates.checkNow() }
    }

    override fun onOpenRelease() {
        if (state.value.releasePage != null)
            mutable.update { it.copy(askingToOpen = true, openFailed = false) }
    }

    override fun onConfirmOpen() {
        val url = state.value.takeIf { it.askingToOpen }?.releasePage ?: return
        mutable.update { it.copy(askingToOpen = false) }
        viewModelScope.launch {
            val failed = links.open(url) is Outcome.Failed
            mutable.update { it.copy(openFailed = failed) }
        }
    }

    override fun onCancelOpen() {
        mutable.update { it.copy(askingToOpen = false) }
    }

    override fun onClose() = close()
}

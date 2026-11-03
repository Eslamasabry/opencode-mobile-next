package io.github.eslamasabry.oc.feature.about.managespace

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The page's state holder. It lives in its own Activity (Android starts the manage-space activity without the app's
 * navigation), so it is a plain presenter over [scope] rather than a routed ViewModel. One act at a time: while a
 * clear runs, the other choices wait ([ManageSpaceUi.busy]).
 */
public class ManageSpacePresenter(
    private val port: ManageSpacePort,
    private val scope: CoroutineScope,
    private val close: () -> Unit,
) : ManageSpaceActions {
    private val mutable = MutableStateFlow(ManageSpaceUi())
    public val state: StateFlow<ManageSpaceUi> = mutable.asStateFlow()

    public fun start() {
        scope.launch { measure() }
    }

    private suspend fun measure() {
        val facts =
            when (val read = port.measure()) {
                is Outcome.Ok -> read.value
                // Unmeasurable is still a page: the rows say less, never a raw error.
                is Outcome.Failed -> ManageSpaceFacts(cacheBytes = null, savedServers = 0)
            }
        mutable.update { it.copy(facts = facts) }
    }

    override fun onClose(): Unit = close()

    override fun onClearCache() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, notice = null) }
        scope.launch {
            val notice =
                when (val cleared = port.clearCache()) {
                    is Outcome.Ok -> CacheNotice.Cleared(cleared.value)
                    is Outcome.Failed -> CacheNotice.Failed
                }
            mutable.update { it.copy(busy = false, notice = notice) }
            measure()
        }
    }

    override fun onDeleteAll() {
        if (mutable.value.busy) return
        mutable.update { it.copy(confirmingDelete = true) }
    }

    override fun onCancelDelete() {
        mutable.update { it.copy(confirmingDelete = false) }
    }

    override fun onConfirmDelete() {
        if (mutable.value.busy) return
        mutable.update { it.copy(confirmingDelete = false, busy = true) }
        scope.launch {
            // Android ends the process on success; a refusal leaves the page usable.
            if (!port.clearAllData()) mutable.update { it.copy(busy = false) }
        }
    }
}

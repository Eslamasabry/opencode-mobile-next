package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.engine.updates.AppUpdates
import io.github.eslamasabry.oc.feature.updates.presentation.UpdateNoticeActions
import io.github.eslamasabry.oc.feature.updates.presentation.noticeUi
import io.github.eslamasabry.oc.feature.updates.ui.updateStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusScope
import io.github.eslamasabry.oc.ui.kit.status.LocalKitStatusConditions
import kotlinx.coroutines.launch

@ContributesTo(AppScope::class)
interface UpdateNoticeAccessors {
    val updateNotices: UpdateNoticeRuntime
}

/**
 * The app-wide update lane survives navigation and competes in the window's existing status slot.
 */
@SingleIn(AppScope::class)
@Inject
class UpdateNoticeRuntime(
    val updates: AppUpdates,
    private val scope: AppCoroutineScope,
) : UpdateNoticeActions {
    override fun onUpdate() {
        scope.launch { updates.updateNow() }
    }

    override fun onInstall() {
        scope.launch { updates.updateNow() }
    }

    override fun onNotNow() {
        updates.notNow()
    }

    override fun onCancel() {
        updates.cancel()
    }
}

@Composable
internal fun UpdateNoticeHost(runtime: UpdateNoticeRuntime, content: @Composable () -> Unit) {
    val state by runtime.updates.state.collectAsStateWithLifecycle()
    val notice = noticeUi(state)
    val inherited = LocalKitStatusConditions.current
    val conditions = if (notice == null) inherited else inherited + updateStatus(notice, runtime)
    KitStatusScope(conditions, content)
}

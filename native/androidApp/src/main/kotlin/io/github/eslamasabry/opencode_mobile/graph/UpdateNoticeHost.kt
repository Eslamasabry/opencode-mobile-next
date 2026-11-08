package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.engine.updates.AppUpdates
import io.github.eslamasabry.oc.feature.updates.presentation.UpdateNoticeActions
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatePromptActions
import io.github.eslamasabry.oc.feature.updates.presentation.noticeUi
import io.github.eslamasabry.oc.feature.updates.presentation.promptUi
import io.github.eslamasabry.oc.feature.updates.ui.UpdatePromptSheet
import io.github.eslamasabry.oc.feature.updates.ui.updateStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusScope
import io.github.eslamasabry.oc.ui.kit.status.LocalKitStatusConditions
import io.github.eslamasabry.oc.ui.shell.ShellOverlay
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
) : UpdateNoticeActions, UpdatePromptActions {
    override fun onPromptUpdate() {
        scope.launch { updates.updateNow() }
    }

    override fun onPromptLater() {
        updates.later()
    }

    override fun onPromptClose() {
        updates.closePrompt()
    }

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

/** The start-up / resume update prompt above whatever screen is open (Flutter `InAppUpdateNotice`, #126). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ShellOverlay>())
class UpdatePromptOverlay(private val runtime: UpdateNoticeRuntime) : ShellOverlay {
    @Composable
    override fun Content() {
        val state by runtime.updates.state.collectAsStateWithLifecycle()
        UpdatePromptSheet(promptUi(state), runtime)
    }
}

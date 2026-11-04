package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.settings.presentation.ServerConfigurationActions
import io.github.eslamasabry.oc.feature.settings.presentation.ServerConfigurationUi
import io.github.eslamasabry.oc.feature.settings.presentation.ServerConfigurationViewModel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.configInspectionExplanation
import io.github.eslamasabry.oc.ui.words.res.configInspectionRefresh
import io.github.eslamasabry.oc.ui.words.res.configInspectionTitle
import io.github.eslamasabry.oc.ui.words.res.configInspectionUnavailable
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ServerConfigurationPage(onBack: () -> Unit) {
    val vm = metroViewModel<ServerConfigurationViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    ServerConfigurationScreen(state, vm, onBack)
}

/**
 * A read-only source list. The adapter has already removed sensitive values before this state
 * exists.
 */
@Composable
public fun ServerConfigurationScreen(
    state: ServerConfigurationUi,
    actions: ServerConfigurationActions,
    onBack: () -> Unit,
) {
    val title = stringResource(Res.string.configInspectionTitle)
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = onBack,
            )
        },
        width = KitScreenWidth.Reading,
        page = true,
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            KitGroupNote(stringResource(Res.string.configInspectionExplanation))
            when (state) {
                ServerConfigurationUi.Loading ->
                    KitStateView(kind = KitStateKind.Loading, title = title)
                ServerConfigurationUi.Unavailable ->
                    KitStateView(
                        kind = KitStateKind.Error,
                        title = stringResource(Res.string.configInspectionUnavailable),
                        primary =
                            KitAction(stringResource(Res.string.commonRetry), actions::refresh),
                    )
                is ServerConfigurationUi.Ready -> {
                    KitActionBlock(
                        primary =
                            KitAction(
                                stringResource(Res.string.configInspectionRefresh),
                                actions::refresh,
                            ),
                        modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                    )
                    KitText(
                        state.text,
                        modifier =
                            Modifier.padding(LocalKitTokens.current.gutter.dp)
                                .testTag("server-configuration-masked"),
                        role = TypeRole.Mono,
                        kind = KitTextKind.Reading,
                    )
                }
            }
        }
    }
}

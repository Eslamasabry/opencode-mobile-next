package io.github.eslamasabry.oc.feature.chat.agentfeatures.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeaturesActions
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeaturesBody
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeaturesUi
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.body
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentFeaturesEmpty
import io.github.eslamasabry.oc.ui.words.res.agentFeaturesFailed
import io.github.eslamasabry.oc.ui.words.res.agentFeaturesTitle
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import org.jetbrains.compose.resources.stringResource

/** Visible from the conversation menu; only kit controls render the agent's own switches. */
@Composable
public fun AgentFeaturesScreen(state: AgentFeaturesUi, actions: AgentFeaturesActions) {
    KitSheet(
        visible = state.visible,
        onDismissRequest = actions::close,
        title = stringResource(Res.string.agentFeaturesTitle),
        subtitle = state.snapshot?.owner,
        closeLabel = stringResource(Res.string.kitSheetClose),
        loading = state.loading && state.snapshot != null, // quiet: the kept list stays, a thin line says it updates
    ) {
        when (state.body()) {
            AgentFeaturesBody.Failure ->
                KitStateView(
                    kind = KitStateKind.Error,
                    title = stringResource(Res.string.agentFeaturesFailed),
                    failure = state.failure,
                    onRetry = actions::refresh,
                    retryLabel = stringResource(Res.string.commonRetry),
                )
            AgentFeaturesBody.Skeleton -> KitSkeletonRows(count = 3)
            AgentFeaturesBody.Empty ->
                KitText(stringResource(Res.string.agentFeaturesEmpty))
            AgentFeaturesBody.Rows ->
                KitRowGroup {
                    for (feature in state.snapshot!!.features) {
                        if (feature.on != null) {
                            KitSwitchRow(
                                feature.label,
                                feature.on,
                                supporting = feature.description,
                                onCheckedChange = { actions.set(feature.id, it) },
                            )
                        } else {
                            KitText(feature.label)
                            feature.description?.let { KitText(it) }
                            for (option in feature.options) {
                                KitChoiceRow(
                                    option.label,
                                    selected = option.id == feature.selected,
                                    onClick = { actions.set(feature.id, option.id) },
                                )
                            }
                        }
                    }
                }
        }
    }
}

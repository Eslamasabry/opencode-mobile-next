package io.github.eslamasabry.oc.feature.search.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun WebSearchPanel(state: WebSearchUi, actions: WebSearchActions) {
    if (!state.available && !state.open) return
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitSwitchRow(
            title = stringResource(Res.string.protoWebSearch),
            supporting = stringResource(Res.string.protoWebSearchHelp),
            checked = state.open,
            onCheckedChange = { actions.toggle() },
        )
        if (state.open) {
            val query = rememberTextFieldState()
            val held =
                if (state.loading) KitEnabled.Disabled(stringResource(Res.string.protoWorking))
                else KitEnabled.Enabled
            if (!state.available) KitNotice(stringResource(Res.string.protoWebUnavailable))
            if (state.failed) KitNotice(stringResource(Res.string.protoWebFailed))
            if (state.invalidQuery) KitNotice(stringResource(Res.string.protoWebInvalid))
            KitField(query, stringResource(Res.string.protoWebQuery), enabled = held)
            KitRowGroup(label = stringResource(Res.string.protoWebProvider)) {
                for (provider in state.providers) KitRow(
                    title = provider.name,
                    selected = provider.id == state.provider,
                    enabled = !state.loading,
                    disabledReason = stringResource(Res.string.protoWorking),
                    onClick = { actions.chooseProvider(provider.id) },
                )
            }
            KitButton(
                stringResource(Res.string.protoWebSearch),
                { actions.search(query.text.toString()) },
                enabled =
                    if (state.provider == null || !state.available)
                        KitEnabled.Disabled(stringResource(Res.string.protoWebUnavailable))
                    else held,
                working = state.loading,
            )
            if (state.providers.isEmpty() && !state.loading && state.available)
                KitButton(stringResource(Res.string.commonRetry), actions::discover)
            if (state.searched && state.results.isEmpty())
                KitNotice(stringResource(Res.string.protoWebEmpty))
            for (result in state.results) {
                KitRow(
                    title = result.title?.takeIf { it.isNotBlank() } ?: result.url,
                    supporting = result.url,
                    onClick = { actions.openResult(result.url) },
                )
                result.content?.takeIf { it.isNotBlank() }?.let { KitText(it) }
            }
        }
        val review = state.review
        KitConfirm(
            spec =
                review?.let {
                    KitConfirmSpec(
                        title = stringResource(Res.string.protoWebOpenQuestion, it.host),
                        confirmLabel = stringResource(Res.string.protoWebOpenBrowser),
                        body =
                            stringResource(
                                if (it.insecure) Res.string.protoWebInsecure
                                else Res.string.protoWebLeaveApp
                            ),
                    )
                },
            onConfirm = actions::confirmOpen,
            onCancel = actions::discardReview,
        )
    }
}

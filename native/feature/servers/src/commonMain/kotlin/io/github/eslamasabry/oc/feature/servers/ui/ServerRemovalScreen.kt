package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ServerRemovalScreen(state: ServerRemovalUi, actions: ServerRemovalActions) {
    val plan = state.plan
    val consequences = buildList {
        if (plan != null) {
            if (plan.queued > 0)
                // N-48: waiting prompts go with the server (keeping them was never possible); the confirm says so.
                add(pluralStringResource(Res.plurals.remove40QueueLost, plan.queued, plan.queued))
            if (plan.uncertain > 0)
                add(pluralStringResource(Res.plurals.remove40Uncertain, plan.uncertain, plan.uncertain))
            if (plan.drafts > 0) add(pluralStringResource(Res.plurals.remove40DraftsLost, plan.drafts, plan.drafts))
            if (plan.active) add(stringResource(Res.string.remove40Active))
        }
        add(stringResource(Res.string.remove40ServerKeeps))
    }
    KitBackGuard(state.busy) {}
    KitConfirm(
        if (state.confirming && plan != null)
            KitConfirmSpec(
                stringResource(Res.string.remove40ConfirmTitle, plan.name),
                stringResource(Res.string.serversRemove),
                kind = KitConfirmKind.Destructive,
            )
        else null,
        actions::confirm,
        actions::cancel,
    )
    val enabled =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.serversWorking))
        else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.remove40Title),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        edges = KitScreenEdges.Bleed,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.loading) KitNotice(stringResource(Res.string.remove40Loading))
            state.failure?.let {
                KitErrorNotice(
                    stringResource(Res.string.remove40Failed),
                    failure = it,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                )
            }
            plan?.let { KitText(it.name, role = TypeRole.Headline) }
            if (plan != null) KitText(stringResource(Res.string.remove40Body), tone = TextTone.Secondary)
            KitConsequences(consequences)
            if (plan != null)
                KitButton(
                    stringResource(Res.string.serversRemove),
                    actions::askRemove,
                    destructive = true,
                    enabled = enabled,
                    working = state.busy,
                )
            KitButton(
                stringResource(Res.string.remove40Refresh),
                actions::refresh,
                role = KitButtonRole.Tertiary,
                enabled = enabled,
            )
        }
    }
}

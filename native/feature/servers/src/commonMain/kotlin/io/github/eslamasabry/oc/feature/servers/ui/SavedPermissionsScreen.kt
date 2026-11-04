package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun SavedPermissionsScreen(state: SavedPermissionsUi, actions: SavedPermissionsActions) {
    val confirm = state.confirm
    KitConfirm(
        confirm?.let {
            KitConfirmSpec(
                stringResource(Res.string.permissions40RevokeTitle),
                stringResource(Res.string.permissions40Revoke),
                kind = KitConfirmKind.Destructive,
                body = stringResource(Res.string.permissions40RevokeBody),
                consequences =
                    listOf(
                        it.action,
                        it.resource.ifEmpty { stringResource(Res.string.permissions40AllResources) },
                    ),
            )
        },
        actions::confirmRevoke,
        actions::cancelRevoke,
    )
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.permissions40Title),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitText(stringResource(Res.string.permissions40Intro))
            if (state.loading) KitNotice(stringResource(Res.string.permissions40Loading))
            state.failure?.let {
                KitErrorNotice(
                    stringResource(Res.string.permissions40Failed),
                    failure = it,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                )
            }
            state.revoked?.let {
                KitNotice(stringResource(Res.string.permissions40Revoked, it))
                KitButton(
                    stringResource(Res.string.permissions40Dismiss),
                    actions::dismissRevoked,
                    role = KitButtonRole.Tertiary,
                )
            }
            if (!state.loading && state.permissions.isEmpty() && state.failure == null)
                KitNotice(stringResource(Res.string.permissions40Empty))
            for (permission in state.permissions) {
                KitRow(
                    title = permission.action,
                    supporting =
                        permission.resource.ifEmpty {
                            stringResource(Res.string.permissions40AllResources)
                        },
                    supportingMaxLines = 3,
                )
                KitButton(
                    stringResource(Res.string.permissions40Revoke),
                    { actions.askRevoke(permission.id) },
                    role = KitButtonRole.Tertiary,
                    destructive = true,
                    working = state.removing == permission.id,
                    enabled =
                        if (state.removing != null)
                            KitEnabled.Disabled(stringResource(Res.string.serversWorking))
                        else KitEnabled.Enabled,
                )
            }
            KitButton(
                stringResource(Res.string.permissions40Refresh),
                actions::refresh,
                role = KitButtonRole.Secondary,
            )
        }
    }
}

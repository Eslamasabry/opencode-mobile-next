package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.presentation.ImportedSignInUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Imported labels are advisory; neither config discovery nor a label claims a working credential.
 */
@Composable
internal fun ImportedSignInsSheet(state: ImportedSignInUi?, actions: ServersActions) {
    KitSheet(
        visible = state != null,
        onDismissRequest = actions::closeImportedSignIns,
        title = stringResource(Res.string.secImportedSignInsTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Content,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitNotice(stringResource(Res.string.secImportedSignInsHelp))
            when {
                state?.failed == true ->
                    KitText(stringResource(Res.string.secImportedSignInsFailed))
                state?.names.isNullOrEmpty() ->
                    KitText(stringResource(Res.string.secImportedSignInsEmpty))
                else -> for (name in state?.names.orEmpty()) KitText(name)
            }
            KitButton(
                stringResource(Res.string.secImportedSignInsOpen),
                actions::openProviderSignIn,
            )
        }
    }
}

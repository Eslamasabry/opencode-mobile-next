package io.github.eslamasabry.oc.feature.servers.links

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitAlert
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The notice a link that cannot complete leaves (F: lib/main_parts/link_routes.dart): "Add this server?" with Add
 * server / Dismiss for a server this phone has not saved; the re-enter-password and connection-failed lines over
 * Servers; and the address sheet ("Open a shared conversation") with the reason in plain words and its category
 * under Details.
 */
@Composable
public fun LinkNoticeHost(notice: LinkNotice?, onAddServer: () -> Unit, onDismiss: () -> Unit) {
    KitConfirm(
        spec =
            if (notice != LinkNotice.ServerMissing) null
            else
                KitConfirmSpec(
                    title = stringResource(Res.string.handoffUiLinkAddTitle),
                    confirmLabel = stringResource(Res.string.handoffUiLinkAddServer),
                    kind = KitConfirmKind.Neutral,
                    body = stringResource(Res.string.handoffUiLinkServerMissing),
                    cancelLabel = stringResource(Res.string.handoffUiLinkDismiss),
                    icon = KitIcons.add,
                ),
        onConfirm = {
            onDismiss()
            onAddServer()
        },
        onCancel = onDismiss,
    )
    val line =
        when (notice) {
            LinkNotice.Reentry -> Res.string.handoffUiLinkReentry
            LinkNotice.ConnectionFailed -> Res.string.handoffUiLinkConnectionFailed
            else -> null
        }
    if (line != null) {
        KitAlert(
            visible = true,
            title = stringResource(line),
            onDismissRequest = onDismiss,
            icon = KitIcons.warning,
        )
    }
    val address = notice as? LinkNotice.AddressLink
    if (address != null) {
        val redactor = LocalKitRedactor.current
        KitSheet(
            visible = true,
            onDismissRequest = onDismiss,
            title = stringResource(Res.string.sessionAddressOpenTitle),
            closeLabel = stringResource(Res.string.kitSheetClose),
            icon = KitIcons.link,
        ) {
            Column(
                Modifier.testTag("session-address-sheet"),
                verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                KitNotice(
                    stringResource(failureWords(address.failure)),
                    Modifier.testTag("session-address-failure"),
                    tone = StatusTone.Failure,
                    icon = KitIcons.error,
                )
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyAll),
                    modifier = Modifier.testTag("session-address-details"),
                    values =
                        listOf(
                            KitTechnicalValue(
                                stringResource(Res.string.sessionAddressReason),
                                redactor.details(address.failure.word),
                            )
                        ),
                )
            }
        }
    }
}

internal fun failureWords(failure: SessionAddressFailure): StringResource =
    when (failure) {
        SessionAddressFailure.Unavailable -> Res.string.sessionAddressFailUnavailable
        SessionAddressFailure.InvalidLink -> Res.string.sessionAddressFailInvalidLink
        SessionAddressFailure.TooLarge -> Res.string.sessionAddressFailTooLarge
        SessionAddressFailure.Credentials -> Res.string.sessionAddressFailCredentials
    }

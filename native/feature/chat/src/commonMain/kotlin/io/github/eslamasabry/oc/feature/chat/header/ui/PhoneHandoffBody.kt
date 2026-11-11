package io.github.eslamasabry.oc.feature.chat.header.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderActions
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderSheet
import io.github.eslamasabry.oc.feature.chat.header.presentation.PhoneAddressFailure
import io.github.eslamasabry.oc.feature.chat.header.presentation.PhoneSheetRules
import io.github.eslamasabry.oc.feature.chat.header.presentation.PhoneSheetShow
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.content.qr.KitQr
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.handoffSheetPhoneServerNote
import io.github.eslamasabry.oc.ui.words.res.handoffUiPhoneCopyLink
import io.github.eslamasabry.oc.ui.words.res.handoffUiPhoneIntro
import io.github.eslamasabry.oc.ui.words.res.handoffUiPhoneLinkLabel
import io.github.eslamasabry.oc.ui.words.res.handoffUiPhoneQrLabel
import io.github.eslamasabry.oc.ui.words.res.handoffUiPhoneUnavailable
import io.github.eslamasabry.oc.ui.words.res.kitCopied
import io.github.eslamasabry.oc.ui.words.res.sessionAddressDisclosure
import io.github.eslamasabry.oc.ui.words.res.sessionAddressFailCredentials
import io.github.eslamasabry.oc.ui.words.res.sessionAddressFailInvalidDescriptor
import io.github.eslamasabry.oc.ui.words.res.sessionAddressFailInvalidLink
import io.github.eslamasabry.oc.ui.words.res.sessionAddressFailPrivateRouteRequired
import io.github.eslamasabry.oc.ui.words.res.sessionAddressFailTimedOut
import io.github.eslamasabry.oc.ui.words.res.sessionAddressFailTooLarge
import io.github.eslamasabry.oc.ui.words.res.sessionAddressFailUnavailable
import io.github.eslamasabry.oc.ui.words.res.sessionAddressFailUnreachable
import io.github.eslamasabry.oc.ui.words.res.sessionAddressInclude
import io.github.eslamasabry.oc.ui.words.res.sessionAddressIntro
import io.github.eslamasabry.oc.ui.words.res.sessionAddressUnsupportedHost
import org.jetbrains.compose.resources.stringResource

/**
 * Flutter `_ContinueOnPhoneBody` (session_handoff_sheets.dart:300-430): the code and the link for the other phone.
 * The "Include this server's address" switch exists only when the host passed an offer, and it is off every time
 * the sheet opens (opening it is not consent to show the address). With it on, the code and the link are the
 * portable ones, built again on every switch and every Copy; a build that fails shows no code at all, only why.
 */
@Composable
internal fun PhoneBody(sheet: HeaderSheet.ContinueOnPhone, actions: ChatHeaderActions) {
    var include by remember { mutableStateOf(false) }
    val offer = sheet.address
    val show = PhoneSheetRules.show(sheet.link, offer, include)
    Column(
        Modifier.fillMaxWidth().testTag("continue-on-phone-sheet"),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        if (show == PhoneSheetShow.None) {
            KitNotice(
                modifier = Modifier.testTag("continue-on-phone-unavailable"),
                message = stringResource(Res.string.handoffUiPhoneUnavailable),
            )
            return@Column
        }
        KitText(
            stringResource(
                if (include && offer != null) Res.string.sessionAddressIntro else Res.string.handoffUiPhoneIntro
            )
        )
        if (offer != null) {
            KitRowGroup {
                KitSwitchRow(
                    stringResource(Res.string.sessionAddressInclude),
                    include,
                    Modifier.testTag("session-address-include"),
                    supporting = offer.hostPort,
                    enabled =
                        if (offer.hostPort == null)
                            KitEnabled.Disabled(stringResource(Res.string.sessionAddressUnsupportedHost))
                        else KitEnabled.Enabled,
                    onCheckedChange = { include = it },
                )
            }
            if (offer.hostPort != null) {
                KitText(
                    stringResource(Res.string.sessionAddressDisclosure),
                    Modifier.testTag("session-address-disclosure"),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            } else {
                KitText(
                    stringResource(Res.string.sessionAddressUnsupportedHost),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            }
        }
        when (show) {
            is PhoneSheetShow.Failure ->
                // The address could not go in: say why, and draw no code rather than fall back to the local link.
                KitNotice(
                    modifier = Modifier.testTag("session-address-failure"),
                    message = stringResource(failureWords(show.failure)),
                )
            is PhoneSheetShow.Portable -> {
                Code(show.link, "session-address-qr", "session-address-link") { actions.copyPhoneAddressLink() }
            }
            is PhoneSheetShow.Local -> {
                Code(show.link, "session-link-qr", "continue-on-phone-link") { actions.copyPhoneLink() }
                // What happens on a phone that does not have this server yet.
                KitNotice(
                    modifier = Modifier.testTag("continue-on-phone-server-note"),
                    message = stringResource(Res.string.handoffSheetPhoneServerNote),
                )
            }
            PhoneSheetShow.None -> Unit
        }
    }
}

@Composable
private fun ColumnScope.Code(link: String, qrTag: String, linkTag: String, onCopy: () -> Unit) {
    KitQr(
        link,
        stringResource(Res.string.handoffUiPhoneQrLabel),
        Modifier.align(Alignment.CenterHorizontally).testTag(qrTag),
    )
    KitSectionLabel(stringResource(Res.string.handoffUiPhoneLinkLabel))
    KitCodeBlock(
        source = link,
        kind = KitCodeKind.Command,
        maxLines = Int.MAX_VALUE,
        modifier = Modifier.testTag(linkTag),
        wrap = true,
        copyLabel = stringResource(Res.string.handoffUiPhoneCopyLink),
        copiedLabel = stringResource(Res.string.kitCopied),
        onCopy = { onCopy() },
    )
}

private fun failureWords(failure: PhoneAddressFailure) =
    when (failure) {
        PhoneAddressFailure.Unavailable -> Res.string.sessionAddressFailUnavailable
        PhoneAddressFailure.InvalidLink -> Res.string.sessionAddressFailInvalidLink
        PhoneAddressFailure.TooLarge -> Res.string.sessionAddressFailTooLarge
        PhoneAddressFailure.Credentials -> Res.string.sessionAddressFailCredentials
        PhoneAddressFailure.PrivateRouteRequired -> Res.string.sessionAddressFailPrivateRouteRequired
        PhoneAddressFailure.Unreachable -> Res.string.sessionAddressFailUnreachable
        PhoneAddressFailure.TimedOut -> Res.string.sessionAddressFailTimedOut
        PhoneAddressFailure.InvalidDescriptor -> Res.string.sessionAddressFailInvalidDescriptor
    }

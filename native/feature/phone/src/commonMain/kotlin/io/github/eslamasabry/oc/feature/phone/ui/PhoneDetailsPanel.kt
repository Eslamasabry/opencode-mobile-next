package io.github.eslamasabry.oc.feature.phone.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneProotMode
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneActions
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Measured reply speed stays visible; native logs and performance readings stay inside Details. */
@Composable
internal fun PhoneDetailsPanel(state: ThisPhoneUi, actions: ThisPhoneActions) {
    val reading = state.details
    reading.reply?.let { reply ->
        KitRow(
            title = stringResource(Res.string.r40PhoneReplySpeed),
            modifier = Modifier.testTag("this-phone-reply-speed"),
            supporting =
                if (reply.firstMs == null)
                    stringResource(Res.string.r40PhoneReplyNoWords, seconds(reply.totalMs))
                else
                    stringResource(
                        Res.string.r40PhoneReplyLast,
                        seconds(reply.firstMs),
                        seconds(reply.totalMs),
                    ),
            supportingMaxLines = 2,
        )
    }
    if (!reading.installed) return
    val names = reading.installedNames
    if (names == null && reading.loading) {
        KitRow(
            title = stringResource(Res.string.r40PhoneInstalled),
            supporting = stringResource(Res.string.r40PhoneChecking),
        )
    } else if (names != null) {
        KitExpandRow(
            title = stringResource(Res.string.r40PhoneInstalled),
            expanded = state.installedOpen,
            modifier = Modifier.testTag("this-phone-installed"),
            supporting =
                names.joinToString(stringResource(Res.string.phoneSetupStartListSeparator)),
            onExpandedChange = actions::showInstalled,
        ) {
            names.forEach {
                KitRow(
                    title = it,
                    trailing =
                        KitRowTrailing.Value(
                            stringResource(Res.string.r40PhoneInstalledMark),
                            chevron = false,
                        ),
                )
            }
        }
    }
    reading.inventoryFailure?.let {
        KitErrorNotice(
            message = stringResource(Res.string.r40PhoneInventoryFailed),
            failure = it,
            retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::refreshDetails),
        )
    }
    KitExpandRow(
        title = stringResource(Res.string.kitDetails),
        expanded = state.detailsOpen,
        modifier = Modifier.testTag("this-phone-details"),
        onExpandedChange = actions::showDetails,
    ) {
        KitRowGroup {
            reading.prootMode?.let { mode ->
                KitRow(
                    title = stringResource(Res.string.r40PhoneLinuxMode),
                    trailing =
                        KitRowTrailing.Value(
                            stringResource(
                                when (mode) {
                                    PhoneProotMode.Fast -> Res.string.r40PhoneLinuxFast
                                    PhoneProotMode.Slow -> Res.string.r40PhoneLinuxSlow
                                    PhoneProotMode.Unknown -> Res.string.r40PhoneUnknown
                                }
                            ),
                            chevron = false,
                        ),
                )
            }
            reading.workHeld?.let { held ->
                KitRow(
                    title = stringResource(Res.string.r40PhoneAwake),
                    trailing =
                        KitRowTrailing.Value(
                            stringResource(
                                if (held) Res.string.r40PhoneAwakeNow
                                else Res.string.r40PhoneAwakeWhenWorking
                            ),
                            chevron = false,
                        ),
                )
            }
            reading.reply?.let { reply ->
                reply.firstMs?.let { first ->
                    KitRow(
                        title = stringResource(Res.string.r40PhoneFirstWords),
                        supporting =
                            if (reply.serverFirstMs == null) seconds(first)
                            else
                                stringResource(
                                    Res.string.r40PhoneFirstSplit,
                                    seconds(first),
                                    seconds(reply.serverFirstMs),
                                ),
                        supportingMaxLines = 2,
                    )
                }
            }
            KitRow(
                title =
                    stringResource(
                        if (reading.loading) Res.string.r40PhoneChecking
                        else Res.string.r40PhoneRefresh
                    ),
                onClick = if (reading.loading) null else actions::refreshDetails,
            )
        }
        reading.reply?.model?.let { model ->
            KitDetailsFold(
                label = stringResource(Res.string.r40PhoneModel),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                values = listOf(KitTechnicalValue(stringResource(Res.string.r40PhoneModel), model)),
                initiallyExpanded = true,
            )
        }
        reading.nativeFailure?.let {
            KitDetailsFold(
                label = stringResource(Res.string.r40PhoneRuntimeDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                text = it,
            )
        }
        reading.historyFailure?.let {
            KitDetailsFold(
                label = stringResource(Res.string.r40PhoneHistoryDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                text = it,
            )
        }
        reading.logFailure?.let {
            KitErrorNotice(
                message = stringResource(Res.string.r40PhoneLogFailed),
                failure = it,
                retry =
                    KitLineAction(stringResource(Res.string.kitTryAgain), actions::refreshDetails),
            )
        }
        val log = reading.log
        if (log == null || log.text.isBlank())
            KitNotice(message = stringResource(Res.string.r40PhoneLogEmpty))
        else
            KitDetailsFold(
                label = stringResource(Res.string.r40PhoneLog),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                text = log,
                initiallyExpanded = true,
            )
    }
}

@Composable
private fun seconds(ms: Long): String =
    stringResource(Res.string.r40PhoneSeconds, Numbers.decimal(ms / 1000.0, 1))

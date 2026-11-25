package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRefusal
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateActions
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetUi
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAbout
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAtLeast
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonBoth
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonKeep
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonRemove
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonUpdate
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonUpdateAll
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesDownloadsUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesFailed
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesFrees
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesFreesUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNoSpace
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesShortSpace
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesVersionNew
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesVersions
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveBusy
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveUnconfirmed
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveUnsupported
import io.github.eslamasabry.oc.ui.words.res.appUpdateNotNow
import io.github.eslamasabry.oc.ui.words.res.chatsHomeDone
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadUnavailable
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadUnsafe
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** "Update 2 and remove 1" / "Update 3" / "Remove 1" / "Keep these versions": the button names what it does. */
@Composable
internal fun agentUpdatesButton(sheet: AgentUpdateSheetUi): String =
    when {
        sheet.updating > 0 && sheet.removing > 0 ->
            pluralStringResource(Res.plurals.agentUpdatesButtonBoth, sheet.removing, sheet.updating, sheet.removing)
        sheet.updating > 0 -> pluralStringResource(Res.plurals.agentUpdatesButtonUpdate, sheet.updating, sheet.updating)
        sheet.removing > 0 -> pluralStringResource(Res.plurals.agentUpdatesButtonRemove, sheet.removing, sheet.removing)
        else -> stringResource(Res.string.agentUpdatesButtonKeep)
    }

/** "Update all · 660 MB": primary button when every agent switch is off. */
@Composable
internal fun agentUpdatesUpdateAllButton(sheet: AgentUpdateSheetUi): String =
    stringResource(Res.string.agentUpdatesButtonUpdateAll, PhoneSizes.text(sheet.allDownloadBytes))

internal data class SheetButtons(
    val primary: KitAction,
    val secondary: KitAction?,
    val tertiary: List<KitAction> = emptyList(),
)

/** The sheet's pinned actions: Update all + Keep + Not now when all switches are off, else Update/Remove + Not now. */
@Composable
internal fun sheetButtons(shown: AgentUpdateSheetUi, result: Boolean, actions: AgentUpdateActions): SheetButtons {
    if (result) {
        return SheetButtons(
            primary = KitAction(stringResource(Res.string.chatsHomeDone), actions::closeAgentUpdateResult),
            secondary = null,
        )
    }
    val notNow = KitAction(stringResource(Res.string.appUpdateNotNow), actions::agentUpdatesNotNow)
    val nothingChosen = shown.updating == 0 && shown.removing == 0
    val short = if (nothingChosen) shown.allShort else shown.short
    val noRoom =
        short?.let {
            val needs = PhoneSizes.text(it.neededBytes)
            KitEnabled.Disabled(stringResource(Res.string.agentUpdatesShortSpace, needs, PhoneSizes.text(it.freeBytes)))
        } ?: KitEnabled.Enabled
    return if (nothingChosen) {
        SheetButtons(
            primary =
                KitAction(
                    agentUpdatesUpdateAllButton(shown),
                    { actions.proceedAgentUpdates(updateAll = true) },
                    enabled = noRoom,
                    working = shown.working,
                ),
            secondary =
                KitAction(
                    stringResource(Res.string.agentUpdatesButtonKeep),
                    { actions.proceedAgentUpdates() },
                    working = shown.working,
                ),
            tertiary = listOf(notNow),
        )
    } else {
        SheetButtons(
            primary =
                KitAction(
                    agentUpdatesButton(shown),
                    { actions.proceedAgentUpdates() },
                    enabled = noRoom,
                    working = shown.working,
                ),
            secondary = notNow,
        )
    }
}

/** "Version 0.16.1 → 0.17.2 · 330 MB", or "… · Download size not known" when the size is null. */
@Composable
internal fun versionAndSize(from: String?, to: String, downloadBytes: Long?): String {
    val size = downloadBytes?.let(PhoneSizes::text) ?: stringResource(Res.string.agentUpdatesDownloadsUnknown)
    return "${versions(from, to)} · $size"
}

@Composable
internal fun versions(from: String?, to: String): String =
    if (from != null) stringResource(Res.string.agentUpdatesVersions, KitBidi.ltr(from), KitBidi.ltr(to))
    else stringResource(Res.string.agentUpdatesVersionNew, KitBidi.ltr(to))

@Composable
internal fun frees(bytes: Long?): String =
    bytes?.let { stringResource(Res.string.agentUpdatesFrees, PhoneSizes.text(it)) }
        ?: stringResource(Res.string.agentUpdatesFreesUnknown)

/** "About 1.2 GB", or "At least 1.2 GB" when a part's size is not known. */
@Composable
internal fun amount(bytes: Long, known: Boolean): String =
    stringResource(if (known) Res.string.agentUpdatesAbout else Res.string.agentUpdatesAtLeast, PhoneSizes.text(bytes))

/** Why a removal did not happen, in the Agents page's own words (never the script's output). */
@Composable
internal fun refusalWords(why: RemovalRefusal): String =
    stringResource(
        when (why) {
            RemovalRefusal.NotRemovable -> Res.string.agentsRemoveUnsupported
            RemovalRefusal.Busy -> Res.string.agentsRemoveBusy
            RemovalRefusal.Unsafe -> Res.string.linuxAgentPayloadUnsafe
            RemovalRefusal.Failed -> Res.string.linuxAgentPayloadUnavailable
            RemovalRefusal.Unconfirmed -> Res.string.agentsRemoveUnconfirmed
        }
    )

/**
 * Why the answer did not start. No room: with the amounts when the sheet knows them ("These updates need about N and
 * this phone has M free. Keep or remove an agent, or free up space."), else the same without amounts; anything else is
 * the plain line (Details below it).
 */
@Composable
internal fun failureWords(failure: Failure, sheet: AgentUpdateSheetUi): String {
    val noRoom = (failure as? Failure.Media)?.problem == MediaProblem.DownloadNoSpace
    val free = sheet.freeBytes
    return when {
        noRoom && free != null && sheet.downloadBytes > free -> {
            val needs = PhoneSizes.text(sheet.downloadBytes)
            stringResource(Res.string.agentUpdatesShortSpace, needs, PhoneSizes.text(free))
        }
        noRoom -> stringResource(Res.string.agentUpdatesNoSpace)
        else -> stringResource(Res.string.agentUpdatesFailed)
    }
}

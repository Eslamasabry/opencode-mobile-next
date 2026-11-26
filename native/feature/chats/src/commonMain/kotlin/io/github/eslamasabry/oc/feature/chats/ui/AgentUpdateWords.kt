package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRefusal
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateActions
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSubmit
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAbout
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAtLeast
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonBoth
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonRemove
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonUpdateAll
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonUpdateSelected
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesDownloadsUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesFailed
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesFrees
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesFreesUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNoSpace
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesShortSpace
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesVersionNew
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesVersions
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesVersionsSpoken
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveBusy
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveUnconfirmed
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveUnsupported
import io.github.eslamasabry.oc.ui.words.res.appUpdateNotNow
import io.github.eslamasabry.oc.ui.words.res.chatsHomeDone
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadUnavailable
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadUnsafe
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Main button copy: "Update all · 660 MB" when nothing or everything is switched on, "Update 1 selected · 390 MB"
 * when a subset is chosen (numbers through [Numbers.format], size matching the total line above, with lower bound
 * when a part has unknown size and never "0 B").
 */
@Composable
internal fun agentUpdatesButton(sheet: AgentUpdateSheetUi): String {
    val nothingChosen = sheet.updating == 0 && sheet.removing == 0
    val allUpdating = sheet.optional.isNotEmpty() && sheet.updating == sheet.optional.size && sheet.removing == 0
    return when {
        sheet.updating > 0 && sheet.removing > 0 ->
            pluralStringResource(Res.plurals.agentUpdatesButtonBoth, sheet.removing, sheet.updating, sheet.removing)
        nothingChosen -> agentUpdatesUpdateAllButton(sheet)
        allUpdating ->
            stringResource(
                Res.string.agentUpdatesButtonUpdateAll,
                UpdateSheetCopy.buttonAmount(sheet.downloadBytes, sheet.downloadKnown),
            )
        sheet.updating > 0 ->
            pluralStringResource(
                Res.plurals.agentUpdatesButtonUpdateSelected,
                sheet.updating,
                Numbers.format(sheet.updating),
                UpdateSheetCopy.buttonAmount(sheet.downloadBytes, sheet.downloadKnown),
            )
        else -> pluralStringResource(Res.plurals.agentUpdatesButtonRemove, sheet.removing, sheet.removing)
    }
}

/** "Update all · 660 MB" (or lower bound / unknown): primary button when every agent switch is off. */
@Composable
internal fun agentUpdatesUpdateAllButton(sheet: AgentUpdateSheetUi): String =
    stringResource(
        Res.string.agentUpdatesButtonUpdateAll,
        UpdateSheetCopy.buttonAmount(sheet.allDownloadBytes, sheet.allDownloadKnown),
    )

internal data class SheetButtons(
    val primary: KitAction,
    val secondary: KitAction?,
    val tertiary: List<KitAction> = emptyList(),
)

/** The sheet's two pinned actions: primary follows selection (Update all / Update N selected) and quiet Not now. */
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
    val submit = if (nothingChosen) AgentUpdateSubmit.UpdateAll else AgentUpdateSubmit.Chosen
    return SheetButtons(
        primary =
            KitAction(
                agentUpdatesButton(shown),
                { actions.proceedAgentUpdates(submit) },
                enabled = noRoom,
                working = shown.working,
                swapLabels = UpdateSheetCopy.candidatePrimaryLabels(shown),
            ),
        secondary = notNow,
    )
}

/** "Version 0.16.1 → 0.17.2 · 330 MB", or "… · Download size not known" when the size is null. */
@Composable
internal fun versionAndSize(from: String?, to: String, downloadBytes: Long?): String {
    val size = downloadBytes?.let(PhoneSizes::text) ?: stringResource(Res.string.agentUpdatesDownloadsUnknown)
    return "${versions(from, to)} · $size"
}

/** Spoken label for screen readers: "From 0.16.1 to 0.17.2 · 330 MB". */
@Composable
internal fun versionAndSizeSpoken(from: String?, to: String, downloadBytes: Long?): String {
    val size = downloadBytes?.let(PhoneSizes::text) ?: stringResource(Res.string.agentUpdatesDownloadsUnknown)
    val pair =
        if (from != null) stringResource(Res.string.agentUpdatesVersionsSpoken, KitBidi.ltr(from), KitBidi.ltr(to))
        else stringResource(Res.string.agentUpdatesVersionNew, KitBidi.ltr(to))
    return "$pair · $size"
}

@Composable
internal fun versions(from: String?, to: String): String =
    if (from != null) stringResource(Res.string.agentUpdatesVersions, KitBidi.ltr(from), KitBidi.ltr(to))
    else stringResource(Res.string.agentUpdatesVersionNew, KitBidi.ltr(to))

@Composable
internal fun frees(bytes: Long?): String =
    bytes?.let { stringResource(Res.string.agentUpdatesFrees, PhoneSizes.text(it)) }
        ?: stringResource(Res.string.agentUpdatesFreesUnknown)

/** "About 1.2 GB", "At least 1.2 GB" when a part's size is unknown, or an unknown-size line when none is known. */
@Composable
internal fun amount(bytes: Long, known: Boolean, frees: Boolean = false): String =
    when {
        known -> stringResource(Res.string.agentUpdatesAbout, PhoneSizes.text(bytes))
        bytes > 0L -> stringResource(Res.string.agentUpdatesAtLeast, PhoneSizes.text(bytes))
        frees -> stringResource(Res.string.agentUpdatesFreesUnknown)
        else -> stringResource(Res.string.agentUpdatesDownloadsUnknown)
    }

private object UpdateSheetCopy {
    @Composable
    fun buttonAmount(bytes: Long, known: Boolean): String =
        when {
            known -> PhoneSizes.text(bytes)
            bytes > 0L -> stringResource(Res.string.agentUpdatesAtLeast, PhoneSizes.text(bytes))
            else -> stringResource(Res.string.agentUpdatesDownloadsUnknown)
        }

    @Composable
    fun candidatePrimaryLabels(sheet: AgentUpdateSheetUi): List<String> {
        val maxAmount = buttonAmount(sheet.allDownloadBytes, sheet.allDownloadKnown)
        val allLabel = agentUpdatesUpdateAllButton(sheet)
        val selected =
            (1 until sheet.optional.size).map { count ->
                pluralStringResource(
                    Res.plurals.agentUpdatesButtonUpdateSelected,
                    count,
                    Numbers.format(count),
                    maxAmount,
                )
            }
        return (listOf(allLabel, agentUpdatesButton(sheet)) + selected).distinct()
    }
}

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

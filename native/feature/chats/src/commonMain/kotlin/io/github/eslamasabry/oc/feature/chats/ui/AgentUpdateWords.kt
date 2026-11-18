package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetUi
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAbout
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAtLeast
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonBoth
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonKeep
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonRemove
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonUpdate
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesChoiceKeep
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesChoiceRemove
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesChoiceUpdate
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesDownloads
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesDownloadsUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesFrees
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesFreesUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesStays
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesStaysUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesVersionNew
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesVersions
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

@Composable
internal fun choiceWord(choice: AgentUpdateChoice): String =
    stringResource(
        when (choice) {
            AgentUpdateChoice.Update -> Res.string.agentUpdatesChoiceUpdate
            AgentUpdateChoice.Keep -> Res.string.agentUpdatesChoiceKeep
            AgentUpdateChoice.Remove -> Res.string.agentUpdatesChoiceRemove
        }
    )

/** What the chosen answer does: the download, the version it stays on, or the room it frees. */
@Composable
internal fun consequence(row: AgentUpdateRowUi): String =
    when (row.choice) {
        AgentUpdateChoice.Update -> downloads(row.downloadBytes)
        AgentUpdateChoice.Keep ->
            row.from?.let { stringResource(Res.string.agentUpdatesStays, KitBidi.ltr(it)) }
                ?: stringResource(Res.string.agentUpdatesStaysUnknown)
        AgentUpdateChoice.Remove -> frees(row.installedBytes)
    }

@Composable
internal fun versions(from: String?, to: String): String =
    if (from != null) stringResource(Res.string.agentUpdatesVersions, KitBidi.ltr(from), KitBidi.ltr(to))
    else stringResource(Res.string.agentUpdatesVersionNew, KitBidi.ltr(to))

@Composable
internal fun downloads(bytes: Long?): String =
    bytes?.let { stringResource(Res.string.agentUpdatesDownloads, PhoneSizes.text(it)) }
        ?: stringResource(Res.string.agentUpdatesDownloadsUnknown)

@Composable
internal fun frees(bytes: Long?): String =
    bytes?.let { stringResource(Res.string.agentUpdatesFrees, PhoneSizes.text(it)) }
        ?: stringResource(Res.string.agentUpdatesFreesUnknown)

/** "About 1.2 GB", or "At least 1.2 GB" when a part's size is not known. */
@Composable
internal fun amount(bytes: Long, known: Boolean): String =
    stringResource(if (known) Res.string.agentUpdatesAbout else Res.string.agentUpdatesAtLeast, PhoneSizes.text(bytes))

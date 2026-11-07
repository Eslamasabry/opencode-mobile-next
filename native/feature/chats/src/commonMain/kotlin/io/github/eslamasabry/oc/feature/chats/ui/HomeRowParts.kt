package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.HomeUsageLimit
import io.github.eslamasabry.oc.feature.chats.presentation.RunLength
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitDividerInset
import io.github.eslamasabry.oc.ui.kit.foundation.kitList
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRowWorking
import io.github.eslamasabry.oc.ui.words.res.chatsHomeShowAll
import io.github.eslamasabry.oc.ui.words.res.p12ProjectsTitle
import io.github.eslamasabry.oc.ui.words.res.s10HomeRowHoursMinutes
import io.github.eslamasabry.oc.ui.words.res.s10HomeRowMinutes
import io.github.eslamasabry.oc.ui.words.res.s10HomeRowWorkingFor
import io.github.eslamasabry.oc.ui.words.res.s10HomeUsageLeft
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupUsage
import io.github.eslamasabry.oc.ui.words.res.stateAttentionTitle
import org.jetbrains.compose.resources.stringResource

// The home's parts under and inside its rows (E20-S24, B-161, B-162), kept apart from ChatsScreen.kt.

/**
 * All conversations, Projects, Activity and Usage: quiet rows under the list, one tap each. E20-S24: flat on the page
 * with hairlines, like the conversation rows above, never a panel of their own (a panel left bands of page between the
 * groups in the panelled looks). B-162: Usage closes the list with today's tightest limit when one is known.
 */
@Composable
internal fun MorePlaces(actions: ChatsActions, usage: HomeUsageLimit?) {
    // Its own list for the layout guard: these titles start past their tiles, the conversations' on the page edge.
    Column(Modifier.padding(top = Space.S4.dp).kitList("HomePlaces")) {
        KitRow(
            title = stringResource(Res.string.chatsHomeShowAll),
            leading = KitRowLeading.Glyph(KitIcons.chat, tile = true),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::showAll,
        )
        PlacesDivider()
        KitRow(
            title = stringResource(Res.string.p12ProjectsTitle),
            leading = KitRowLeading.Glyph(KitIcons.projects, tile = true),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::openProjects,
        )
        PlacesDivider()
        KitRow(
            title = stringResource(Res.string.stateAttentionTitle),
            leading = KitRowLeading.Glyph(KitIcons.activity, tile = true),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::openAttention,
        )
        PlacesDivider()
        KitRow(
            title = stringResource(Res.string.settingsHubGroupUsage),
            supporting =
                usage?.let {
                    stringResource(Res.string.s10HomeUsageLeft, "\u2068${it.name}\u2069", Numbers.format(it.percentLeft))
                },
            leading = KitRowLeading.Glyph(KitIcons.usage, tile = true),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::openUsage,
        )
    }
}

/** The hairline between two of [MorePlaces]' rows, inset past their tiles to the words. */
@Composable
private fun PlacesDivider() {
    KitDivider(inset = KitDividerInset.Text)
}

/** "Working 34m" / "Working 1h 5m" for a live run's length; plain "Working" under a minute or with no start. */
@Composable
internal fun liveRunWords(): (Long?) -> String {
    val working = stringResource(Res.string.chatsHomeRowWorking)
    val workingFor = stringResource(Res.string.s10HomeRowWorkingFor)
    val minutes = stringResource(Res.string.s10HomeRowMinutes)
    val hoursMinutes = stringResource(Res.string.s10HomeRowHoursMinutes)
    return remember(working, workingFor, minutes, hoursMinutes) {
        { elapsedMs ->
            when (val length = RunLength.of(elapsedMs)) {
                null -> working
                is RunLength.Minutes -> workingFor.replace("%1\$s", minutes.replace("%1\$s", Numbers.format(length.minutes)))
                is RunLength.HoursMinutes ->
                    workingFor.replace(
                        "%1\$s",
                        hoursMinutes.replace("%1\$s", Numbers.format(length.hours)).replace("%2\$s", Numbers.format(length.minutes)),
                    )
            }
        }
    }
}

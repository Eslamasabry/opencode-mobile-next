package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.chats.presentation.RunLength
import io.github.eslamasabry.oc.ui.kit.text.KitDurationStyle
import io.github.eslamasabry.oc.ui.kit.text.kitDurationParts
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRowWorking
import io.github.eslamasabry.oc.ui.words.res.s10HomeRowWorkingFor
import org.jetbrains.compose.resources.stringResource

// The home's parts inside its rows (B-161), kept apart from ChatsScreen.kt.

/** "Working 34m" / "Working 1h 5m" for a live run's length; plain "Working" under a minute or with no start. */
@Composable
internal fun liveRunWords(): (Long?) -> String {
    val working = stringResource(Res.string.chatsHomeRowWorking)
    val workingFor = stringResource(Res.string.s10HomeRowWorkingFor)
    return { elapsedMs ->
        when (val length = RunLength.of(elapsedMs)) {
            null -> working
            is RunLength.Minutes ->
                workingFor.replace("%1\$s", kitDurationParts(minutes = length.minutes.toLong(), style = KitDurationStyle.Compact))
            is RunLength.HoursMinutes ->
                workingFor.replace(
                    "%1\$s",
                    kitDurationParts(hours = length.hours.toLong(), minutes = length.minutes.toLong(), style = KitDurationStyle.Compact),
                )
        }
    }
}

package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.common.text.shortCalendarDate
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.relativeAgeDays
import io.github.eslamasabry.oc.ui.words.res.relativeAgeHours
import io.github.eslamasabry.oc.ui.words.res.relativeAgeJustNow
import io.github.eslamasabry.oc.ui.words.res.relativeAgeMinutes
import io.github.eslamasabry.oc.ui.words.res.relativeAgeYesterday
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitLocaleTag
import org.jetbrains.compose.resources.stringResource

/** Flutter relative_age.dart: future dates clamp to now; a week or more uses a local short date. */
@Composable
public fun kitRelativeAge(atMs: Long, nowMs: Long): String {
    val age = if (atMs >= nowMs) 0L else (nowMs - atMs).takeIf { it >= 0 } ?: Long.MAX_VALUE
    val minutes = age / 60_000
    val hours = age / 3_600_000
    val days = age / 86_400_000
    return when {
        minutes < 1 -> stringResource(Res.string.relativeAgeJustNow)
        hours < 1 -> stringResource(Res.string.relativeAgeMinutes, Numbers.format(minutes))
        days < 1 -> stringResource(Res.string.relativeAgeHours, Numbers.format(hours))
        days < 2 -> stringResource(Res.string.relativeAgeYesterday)
        days < 7 -> stringResource(Res.string.relativeAgeDays, Numbers.format(days))
        else -> {
            shortCalendarDate(atMs, nowMs, LocalKitLocaleTag.current)
        }
    }
}

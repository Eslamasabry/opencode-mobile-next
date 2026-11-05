package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.common.text.shortCalendarDate
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceDaysAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceHoursAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceJustNow
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceMinutesAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceYesterday
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitLocaleTag
import org.jetbrains.compose.resources.stringResource

/** Flutter relative_age.dart (its e7Workspace* words): future dates clamp to now; a week or more uses a local short date. */
@Composable
public fun kitRelativeAge(atMs: Long, nowMs: Long): String {
    val age = if (atMs >= nowMs) 0L else (nowMs - atMs).takeIf { it >= 0 } ?: Long.MAX_VALUE
    val minutes = age / 60_000
    val hours = age / 3_600_000
    val days = age / 86_400_000
    return when {
        minutes < 1 -> stringResource(Res.string.e7WorkspaceJustNow)
        hours < 1 -> stringResource(Res.string.e7WorkspaceMinutesAgo, Numbers.format(minutes))
        days < 1 -> stringResource(Res.string.e7WorkspaceHoursAgo, Numbers.format(hours))
        days < 2 -> stringResource(Res.string.e7WorkspaceYesterday)
        days < 7 -> stringResource(Res.string.e7WorkspaceDaysAgo, Numbers.format(days))
        else -> {
            shortCalendarDate(atMs, nowMs, LocalKitLocaleTag.current)
        }
    }
}

/** KitTime's locale-neutral selection; the platform supplies local calendar/clock formatting.
 * All comparisons and formatters receive the same local value (kit_time.dart:42-57).
 */
public object KitTime {
    public fun <T> clockWith(at: T, use24h: Boolean, toLocal: (T) -> T, formatTime: (T, Boolean) -> String): String =
        formatTime(toLocal(at), use24h)

    public fun <T> momentWith(
        at: T,
        now: T,
        use24h: Boolean,
        toLocal: (T) -> T,
        sameDay: (T, T) -> Boolean,
        sameYear: (T, T) -> Boolean,
        formatTime: (T, Boolean) -> String,
        formatMonthDay: (T) -> String,
        formatDate: (T) -> String,
    ): String {
        val local = toLocal(at)
        val today = toLocal(now)
        val time = formatTime(local, use24h)
        if (sameDay(local, today)) return time
        val day = if (sameYear(local, today)) formatMonthDay(local) else formatDate(local)
        return "$day, $time"
    }
}

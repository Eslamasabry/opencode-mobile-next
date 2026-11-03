package io.github.eslamasabry.oc.ui.words

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceDaysAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceHoursAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceJustNow
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceMinutesAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceYesterday
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * "How long ago", the one wording everywhere (Flutter `relativeAgeLabel`, B-L10N-004): Just now, 5 min ago, 2h ago,
 * Yesterday, 3d ago. The words come from the app language; the count is written with the phone's digits.
 */
public sealed interface RelativeAge {
    public data object JustNow : RelativeAge

    public data class Minutes(val count: Long) : RelativeAge

    public data class Hours(val count: Long) : RelativeAge

    public data object Yesterday : RelativeAge

    public data class Days(val count: Long) : RelativeAge

    public companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        /** The age of something [ageMs] old; a future moment (a clock skew) reads Just now. */
        public fun of(ageMs: Long): RelativeAge {
            val age = ageMs.coerceAtLeast(0)
            return when {
                age < MINUTE -> JustNow
                age < HOUR -> Minutes(age / MINUTE)
                age < DAY -> Hours(age / HOUR)
                age < 2 * DAY -> Yesterday
                else -> Days(age / DAY)
            }
        }
    }
}

private fun RelativeAge.words(): Pair<StringResource, String?> = when (this) {
    RelativeAge.JustNow -> Res.string.e7WorkspaceJustNow to null
    is RelativeAge.Minutes -> Res.string.e7WorkspaceMinutesAgo to Numbers.format(count)
    is RelativeAge.Hours -> Res.string.e7WorkspaceHoursAgo to Numbers.format(count)
    RelativeAge.Yesterday -> Res.string.e7WorkspaceYesterday to null
    is RelativeAge.Days -> Res.string.e7WorkspaceDaysAgo to Numbers.format(count)
}

/** [age] in the app language, for a screen. */
@Composable
public fun relativeAgeText(age: RelativeAge): String {
    val (res, count) = age.words()
    return if (count == null) stringResource(res) else stringResource(res, count)
}

/** [age] in the app language, outside composition (notifications, widgets, tests). */
public suspend fun relativeAgeWords(age: RelativeAge): String {
    val (res, count) = age.words()
    return if (count == null) getString(res) else getString(res, count)
}

/**
 * What a conversation's title reads as (Flutter `presentedSessionTitle`, B-L10N-004). A real title is shown exactly
 * as the server sent it, never translated; the server's dated placeholder ("New session - 2026-…") reads as the app
 * language's "New conversation"; no title at all reads as the surface's own [fallback], or "New conversation".
 */
public sealed interface SessionTitleShown {
    /** The server's title, as written. */
    public data class Real(val text: String) : SessionTitleShown

    /** The app's words for a conversation that has no readable title of its own. */
    public data class Placeholder(val fallback: String?) : SessionTitleShown

    public companion object {
        /** [title] is the readable title (markup already cut); [placeholder] is true for the server's dated one. */
        public fun of(title: String?, placeholder: Boolean, fallback: String? = null): SessionTitleShown = when {
            title.isNullOrBlank() -> Placeholder(fallback)
            placeholder -> Placeholder(null)
            else -> Real(title)
        }
    }
}

@Composable
public fun sessionTitleText(shown: SessionTitleShown): String = when (shown) {
    is SessionTitleShown.Real -> shown.text
    is SessionTitleShown.Placeholder -> shown.fallback ?: stringResource(Res.string.workspaceNewSession)
}

public suspend fun sessionTitleWords(shown: SessionTitleShown): String = when (shown) {
    is SessionTitleShown.Real -> shown.text
    is SessionTitleShown.Placeholder -> shown.fallback ?: getString(Res.string.workspaceNewSession)
}

package io.github.eslamasabry.oc.engine.agents.feed

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Where "Today" starts on the person's own clock: the Conversations list's Today / Earlier split (07 §5 B01). */
public fun interface FeedDay {
    public fun startOfToday(now: Instant): Instant
}

/** The system time zone, read at each call so a zone change applies on the next list build. */
@ContributesBinding(AppScope::class)
@Inject
public class SystemFeedDay : FeedDay {
    override fun startOfToday(now: Instant): Instant {
        val zone = TimeZone.currentSystemDefault()
        return now.toLocalDateTime(zone).date.atStartOfDayIn(zone)
    }
}

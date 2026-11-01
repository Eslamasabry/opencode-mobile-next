package io.github.eslamasabry.oc.engine.agents.feed

import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Where "Today" starts on the person's own clock: the Conversations list's Today / Earlier split (07 §5 B01). */
public fun interface FeedDay {
    public fun startOfToday(now: Instant): Instant
}

/**
 * The system time zone, read at each call so a zone change applies on the next list build. Bound by `:feature:chats`
 * (`ChatsBindings`): app graphs see only the modules they depend on directly.
 */
public class SystemFeedDay : FeedDay {
    override fun startOfToday(now: Instant): Instant {
        val zone = TimeZone.currentSystemDefault()
        return now.toLocalDateTime(zone).date.atStartOfDayIn(zone)
    }
}

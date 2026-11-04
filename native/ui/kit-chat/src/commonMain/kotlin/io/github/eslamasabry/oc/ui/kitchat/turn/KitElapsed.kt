package io.github.eslamasabry.oc.ui.kitchat.turn

import kotlin.time.Duration

/**
 * How long something has run, as the parts a person reads (B-KIT-033). Port of Dart `KitSince.durationWords`
 * (`lib/ui/kit/kit_since.dart`): under an hour it reads in minutes ("less than a minute", "45 min"), from an hour up in
 * hours and minutes, from a day up in days and hours — never "2,715 min". A negative span reads as no time at all.
 */
public sealed interface KitElapsed {
    /** Under an hour: whole [minutes] (0 = less than a minute). */
    public data class Minutes(val minutes: Int) : KitElapsed

    /** One hour up to a day: [hours] and the leftover [minutes] (0 = none to say). */
    public data class Hours(val hours: Int, val minutes: Int) : KitElapsed

    /** A day or more: [days] and the leftover [hours] (0 = none to say). */
    public data class Days(val days: Int, val hours: Int) : KitElapsed

    public companion object {
        public fun of(elapsed: Duration): KitElapsed {
            val span = if (elapsed.isNegative()) Duration.ZERO else elapsed
            val hours = span.inWholeHours
            return when {
                hours < 1 -> Minutes(span.inWholeMinutes.toInt())
                span.inWholeDays < 1 -> Hours(hours.toInt(), (span.inWholeMinutes % 60).toInt())
                else -> Days(span.inWholeDays.toInt(), (hours % 24).toInt())
            }
        }
    }
}

/** The words for [KitElapsed]; the host passes its localized strings (kit-chat carries no copy). */
public interface KitElapsedWords {
    /** `kitSinceAge`: "less than a minute" for 0, "1 min", "{n} min". */
    public fun minutes(minutes: Int): String

    public fun hours(hours: Int): String

    public fun hoursMinutes(hours: Int, minutes: Int): String

    public fun days(days: Int): String

    public fun daysHours(days: Int, hours: Int): String
}

/** [elapsed] in words. */
public fun KitElapsedWords.say(elapsed: Duration): String =
    when (val e = KitElapsed.of(elapsed)) {
        is KitElapsed.Minutes -> minutes(e.minutes)
        is KitElapsed.Hours -> if (e.minutes == 0) hours(e.hours) else hoursMinutes(e.hours, e.minutes)
        is KitElapsed.Days -> if (e.hours == 0) days(e.days) else daysHours(e.days, e.hours)
    }

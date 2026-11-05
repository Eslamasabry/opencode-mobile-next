package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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

/** The phases of a wait (Dart `KitSincePhase`): nothing pending, waiting, or past the escalation (8 s). */
public enum class KitSincePhase { Idle, Waiting, Slow }

/** Where a wait stands (Dart `KitSinceStatus`). */
public data class KitSinceStatus(val phase: KitSincePhase, val elapsed: Duration) {
    val isSlow: Boolean get() = phase == KitSincePhase.Slow
}

/** How often a [KitSince] ticks after the escalation (Dart `KitSinceTicks`). */
public enum class KitSinceTicks { None, Minutes, Seconds, SecondsThenMinutes }

private val escalateAfter: Duration get() = MotionTokens.ESCALATE_AFTER.milliseconds

/** `KitSince.statusOf`: how long since [since], as of [now] (a negative span reads as no time at all). */
public fun kitSinceStatusOf(since: Instant?, now: Instant = Clock.System.now()): KitSinceStatus {
    if (since == null) return KitSinceStatus(KitSincePhase.Idle, Duration.ZERO)
    val elapsed = (now - since).let { if (it.isNegative()) Duration.ZERO else it }
    return KitSinceStatus(if (elapsed >= escalateAfter) KitSincePhase.Slow else KitSincePhase.Waiting, elapsed)
}

/** The wait before the next status change (Dart `_schedule`), or null when nothing changes any more. */
internal fun kitSinceNextWait(status: KitSinceStatus, ticks: KitSinceTicks): Duration? {
    val everySecond = ticks == KitSinceTicks.Seconds || (ticks == KitSinceTicks.SecondsThenMinutes && status.elapsed < 1.minutes)
    if (everySecond && status.phase != KitSincePhase.Idle) return 1.seconds - (status.elapsed - status.elapsed.inWholeSeconds.seconds)
    return when (status.phase) {
        KitSincePhase.Idle -> null
        KitSincePhase.Waiting -> escalateAfter - status.elapsed
        KitSincePhase.Slow ->
            if (ticks != KitSinceTicks.Minutes && ticks != KitSinceTicks.SecondsThenMinutes) null
            else 1.minutes - (status.elapsed - status.elapsed.inWholeMinutes.minutes)
    }?.let { if (it.isNegative()) Duration.ZERO else it }
}

/**
 * A wait that escalates (Dart `KitSince`): tells [content] whether nothing is pending, a wait is young, or it has passed
 * 8 s, and re-runs it when that changes (and every second or minute per [ticks]). The part draws nothing itself.
 * [onEscalated] fires once per [since] when the wait turns slow.
 *
 * Cap: wrapper
 */
@Composable
public fun KitSince(
    since: Instant?,
    ticks: KitSinceTicks = KitSinceTicks.None,
    onEscalated: (() -> Unit)? = null,
    now: () -> Instant = { Clock.System.now() },
    content: @Composable (KitSinceStatus) -> Unit,
) {
    var status by remember(since, ticks) { mutableStateOf(kitSinceStatusOf(since, now())) }
    LaunchedEffect(since) { if (since != null && kitSinceStatusOf(since, now()).isSlow) onEscalated?.invoke() }
    LaunchedEffect(since, ticks) {
        while (true) {
            val wait = kitSinceNextWait(status, ticks) ?: break
            delay(wait)
            val next = kitSinceStatusOf(since, now())
            if (next.isSlow && !status.isSlow) onEscalated?.invoke()
            status = next
        }
    }
    content(status)
}

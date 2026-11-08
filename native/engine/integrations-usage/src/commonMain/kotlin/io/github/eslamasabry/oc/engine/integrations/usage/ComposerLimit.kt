package io.github.eslamasabry.oc.engine.integrations.usage

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

// The "Approaching limit" strip at the top of the composer box (owner 2026-11-08). For the provider and model of the
// open conversation it picks the tightest plan window from the readings behind Usage › Plans and says how close it is
// and when it resets. It never invents a number: no reading, no strip.
//
// It lives here, beside the readings, because the module rules do not let :engine:usage read :engine:integrations-usage.

/** What the open conversation runs on. [providerId] is the server's id ("anthropic", "openai", "claude", "gemini"…). */
public data class ComposerLimitTarget(
    val providerId: String,
    val modelId: String? = null,
    /** The connection (profile id, or [UsageConnection.PHONE_ID]) the conversation lives on. */
    val connectionId: String? = null,
)

public enum class ComposerLimitLevel { Approaching, Reached }

/** When the window resets, already placed on the phone's own clock. */
public sealed interface ComposerLimitReset {
    /** Under a day away: "Resets in 2 h 10 min". [minutes] is 0–59; the total is never below one minute. */
    public data class Within(val hours: Int, val minutes: Int) : ComposerLimitReset

    /** Within the coming week: "Resets Friday at 5:00 AM". [isoWeekday] is 1 = Monday … 7 = Sunday; [hour] is 0–23. */
    public data class OnDay(val isoWeekday: Int, val hour: Int, val minute: Int) : ComposerLimitReset

    /** Further away (a monthly window): the full local date and time of [epochMs]. */
    public data class OnDate(val epochMs: Long) : ComposerLimitReset
}

/** One strip to show. [reset] is null when the reader gave no reset time (the strip then shows its title only). */
public data class ComposerLimitNotice(
    val level: ComposerLimitLevel,
    val reset: ComposerLimitReset?,
    /** Usage's provider id ("claude", "codex", "gemini"…), for View usage. */
    val providerId: String,
    /** The subscription the numbers belong to ([UsageSubscription.key]). */
    val subscriptionKey: String,
    /** The connection the numbers were read from, for View usage; null when it is not known. */
    val readFrom: String?,
    /** The window that decided the strip. */
    val windowId: String,
    val usedPercent: Double,
    val resetsAt: Instant?,
)

/** "Approaching limit" from here (percent used). */
public const val COMPOSER_LIMIT_APPROACHING: Double = 80.0

/** "Limit reached" from here. */
public const val COMPOSER_LIMIT_REACHED: Double = 100.0

/** Usage's provider id for a conversation's provider id, or the id itself when Usage has no other name for it. */
public fun composerLimitProvider(id: String): String {
    val lower = id.lowercase()
    return canonicalUsageProviderId(lower)
        ?: when (lower) {
            "google", "gemini", "gemini-cli", "google-vertex" -> "gemini"
            "antigravity", "agy" -> "antigravity"
            else -> lower
        }
}

/** Model families a window can be limited to ("seven_day_opus"); a window naming none applies to every model. */
private val modelFamilies = listOf("opus", "sonnet", "haiku", "fable", "flash")

private fun UsageWindow.family(): String? {
    val words = (id + " " + label).lowercase()
    return modelFamilies.firstOrNull { it in words }
}

private fun UsageWindow.appliesTo(modelId: String?): Boolean {
    val family = family() ?: return true
    return modelId != null && family in modelId.lowercase()
}

/** Subscriptions whose numbers are real readings (fresh or the last known while a connection is offline). */
private val readStates =
    setOf(SubscriptionState.Ok, SubscriptionState.NearLimit, SubscriptionState.Exhausted, SubscriptionState.OfflineStale)

/**
 * The strip for [target] at [now], or null. Candidates are the provider's subscriptions with numbers; when several
 * accounts exist only those signed in on the conversation's connection count, so another account's limit is never
 * shown. Among the windows that apply to the model and have not reset yet, the most used one decides. At the limit the
 * reset is the latest one among the full windows (when the conversation can go on again).
 */
public fun composerLimitNotice(
    snapshot: UsageSnapshot,
    target: ComposerLimitTarget?,
    now: Instant,
    zoneId: String?,
): ComposerLimitNotice? {
    if (target == null) return null
    val provider = composerLimitProvider(target.providerId)
    val all = snapshot.subscriptions.filter { composerLimitProvider(it.providerId) == provider && it.state in readStates }
    val onHere = all.filter { s -> target.connectionId != null && s.signedInOn.any { it.connection.id == target.connectionId } }
    val candidates =
        when {
            onHere.isNotEmpty() -> onHere
            all.map { it.accountRef ?: it.key }.distinct().size == 1 -> all
            else -> return null
        }
    var best: Pair<UsageSubscription, UsageWindow>? = null
    for (s in candidates) {
        for (w in s.windows) {
            if (w.resetPassed(now) || !w.appliesTo(target.modelId)) continue
            val held = best?.second
            if (held == null || w.usedPercent > held.usedPercent) best = s to w
        }
    }
    val (sub, window) = best ?: return null
    val level =
        when {
            window.usedPercent >= COMPOSER_LIMIT_REACHED -> ComposerLimitLevel.Reached
            window.usedPercent >= COMPOSER_LIMIT_APPROACHING -> ComposerLimitLevel.Approaching
            else -> return null
        }
    val resetsAt =
        if (level == ComposerLimitLevel.Approaching) window.resetsAt
        else {
            val full =
                candidates.flatMap { it.windows }
                    .filter { !it.resetPassed(now) && it.appliesTo(target.modelId) && it.usedPercent >= COMPOSER_LIMIT_REACHED }
            if (full.any { it.resetsAt == null }) null else full.mapNotNull { it.resetsAt }.maxOrNull()
        }
    return ComposerLimitNotice(
        level = level,
        reset = resetsAt?.let { composerLimitReset(it, now, zoneId) },
        providerId = provider,
        subscriptionKey = sub.key,
        readFrom = sub.readFrom?.id,
        windowId = window.id,
        usedPercent = window.usedPercent,
        resetsAt = resetsAt,
    )
}

/** [at] on the phone's clock: a countdown under a day, the weekday within a week, else the date. Null without a zone. */
public fun composerLimitReset(at: Instant, now: Instant, zoneId: String?): ComposerLimitReset? {
    val left = at - now
    if (left < DAY) {
        val minutes = ((left.inWholeSeconds + 59) / 60).coerceAtLeast(1)
        return ComposerLimitReset.Within((minutes / 60).toInt(), (minutes % 60).toInt())
    }
    val zone = zoneId?.let(::zoneOrNull) ?: return null
    if (left > WEEK) return ComposerLimitReset.OnDate(at.toEpochMilliseconds())
    val local = at.toLocalDateTime(zone)
    return ComposerLimitReset.OnDay(local.dayOfWeek.isoDayNumber, local.hour, local.minute)
}

private fun zoneOrNull(id: String): TimeZone? =
    try {
        TimeZone.of(id)
    } catch (_: IllegalArgumentException) {
        null
    }

private val DAY = (24 * 3600).seconds
private val WEEK = (7 * 24 * 3600).seconds

/**
 * What the person dismissed, for this app run. A dismissal covers one window at one level until that window resets
 * (its reset time changes) or crosses the next threshold (Approaching → Reached).
 */
public class ComposerLimitDismissals {
    private data class Entry(val resetsAt: Instant?, val level: ComposerLimitLevel)

    private val entries = MutableStateFlow<Map<String, Entry>>(emptyMap())

    /** Changes whenever a dismissal is added, so a presenter can recompute. */
    public val version: StateFlow<Int> get() = _version.asStateFlow()
    private val _version = MutableStateFlow(0)

    public fun dismiss(notice: ComposerLimitNotice) {
        entries.value = entries.value + (keyOf(notice) to Entry(notice.resetsAt, notice.level))
        _version.value += 1
    }

    public fun hides(notice: ComposerLimitNotice): Boolean {
        val entry = entries.value[keyOf(notice)] ?: return false
        return entry.resetsAt == notice.resetsAt && entry.level >= notice.level
    }

    private fun keyOf(n: ComposerLimitNotice) = "${n.subscriptionKey}|${n.windowId}"
}

/**
 * The composer's limit strip as a flow: the readings ([snapshot], e.g. `UsageSubscriptionsEngine.snapshot`), the open
 * conversation and the dismissals, recomputed every [tick] so the countdown moves. [zoneId] is the phone's IANA zone.
 */
public class ComposerLimitPresenter(
    private val snapshot: Flow<UsageSnapshot>,
    private val clock: Clock,
    private val zoneId: () -> String,
    private val dismissals: ComposerLimitDismissals = ComposerLimitDismissals(),
    private val tick: kotlin.time.Duration = 30.seconds,
) {
    /** The strip for [target], or null when there is nothing true to say. */
    public fun notice(target: Flow<ComposerLimitTarget?>): Flow<ComposerLimitNotice?> {
        val ticks = flow {
            while (true) {
                emit(clock.now())
                delay(tick)
            }
        }
        return combine(snapshot, target, ticks, dismissals.version) { snap, t, now, _ ->
            composerLimitNotice(snap, t, now, zoneId())?.takeUnless(dismissals::hides)
        }.distinctUntilChanged()
    }

    /** ✕: hides [notice] until its window resets or reaches the next threshold. */
    public fun dismiss(notice: ComposerLimitNotice) {
        dismissals.dismiss(notice)
    }
}

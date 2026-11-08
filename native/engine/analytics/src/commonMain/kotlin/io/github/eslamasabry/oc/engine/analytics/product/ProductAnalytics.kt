package io.github.eslamasabry.oc.engine.analytics.product

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import io.github.eslamasabry.oc.engine.analytics.health.HealthFlow
import io.github.eslamasabry.oc.engine.analytics.health.JankMeter
import io.github.eslamasabry.oc.engine.analytics.health.Telemetry

/**
 * Flutter 2.1.5 `AnalyticsLifecycle`: `app_open` cold once per process and warm after each return from the
 * background, `session_length` when the app goes to the background, and `update_installed` when the app starts on a
 * newer version than last time. Inert while consent is off, like every other event.
 */
@OptIn(ExperimentalAtomicApi::class)
public class ProductLifecycle(private val analytics: Telemetry, private val clock: Clock) {
    private val away = AtomicBoolean(false)
    private val since = AtomicLong(clock.now().toEpochMilliseconds())

    /**
     * The process started. [previousVersion] is the stored `oc.telemetry.lastVersion`; returns the version to store
     * now, or null when it is unchanged.
     */
    public fun coldStart(currentVersion: String, previousVersion: String?): String? {
        analytics.breadcrumb("app.start")
        analytics.track(ProductEvent.AppOpen, mapOf("kind" to "cold"))
        if (previousVersion == currentVersion) return null
        if (previousVersion != null) {
            analytics.track(ProductEvent.UpdateInstalled)
            HealthFlow { analytics }.updateResult(kind = "in_app", result = "installed")
        }
        return currentVersion
    }

    /** The app went to the background; [jank] ends its session first, as Flutter does. */
    public fun onBackground(jank: JankMeter? = null) {
        if (!away.compareAndSet(expectedValue = false, newValue = true)) return
        analytics.breadcrumb("app.background")
        jank?.endSession()
        val elapsed = (clock.now().toEpochMilliseconds() - since.load()).coerceAtLeast(0).milliseconds
        analytics.track(ProductEvent.SessionLength, mapOf("minutes" to ProductBuckets.sessionMinutesOf(elapsed)))
    }

    public fun onForeground() {
        if (!away.compareAndSet(expectedValue = true, newValue = false)) return
        since.store(clock.now().toEpochMilliseconds())
        analytics.breadcrumb("app.foreground")
        analytics.track(ProductEvent.AppOpen, mapOf("kind" to "warm"))
    }
}

/**
 * The one-time question "Help improve OpenCode Mobile?" (Flutter 2.1.5 `askTelemetryConsentOnce`). Asked once:
 * `oc.telemetryConsentAsked` is set before it shows, so back, a swipe or the app closing count as an answer. Nothing is
 * asked when the build has no vendor; a person who already turned the switch on is not asked either.
 */
public enum class ConsentQuestion {
    /** Asked before (in Flutter or here), or this build cannot send anything: do nothing. */
    Skip,

    /** Store "asked" and show nothing: the switch is already on. */
    MarkOnly,

    /** Store "asked", then show the question. */
    Show;

    public companion object {
        public fun decide(asked: Boolean, available: Boolean, consented: Boolean): ConsentQuestion =
            when {
                asked || !available -> Skip
                consented -> MarkOnly
                else -> Show
            }
    }
}

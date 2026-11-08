package io.github.eslamasabry.oc.engine.analytics.product

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.StateFlow

/**
 * The vendor side of product analytics (Flutter 2.1.5 `FirebaseAnalyticsAdapter`): Google Analytics for Firebase in
 * `:androidApp`, nothing anywhere else. It receives only events [ProductAnalytics] already checked against
 * [productSchema], and only while consent is on, except [setCollectionEnabled].
 */
public interface ProductEventSink {
    /** Starts or stops vendor collection. Never resets anything: the install id must survive the Flutter update. */
    public fun setCollectionEnabled(enabled: Boolean)

    /**
     * The person turned the switch off: reset what the vendor kept on the phone (its random install id), so turning
     * it on again later starts afresh (PRIVACY.md).
     */
    public fun forget()

    public fun event(name: String, props: Map<String, String>)
}

/** The build without a Firebase config: drops everything. */
public object NoOpProductEventSink : ProductEventSink {
    override fun setCollectionEnabled(enabled: Boolean): Unit = Unit

    override fun forget(): Unit = Unit

    override fun event(name: String, props: Map<String, String>): Unit = Unit
}

/**
 * Product analytics over one [ProductEventSink], under the same switch as crash reports ([consent], the
 * Settings › Privacy and data switch). Never throws, never blocks. Nothing reaches the sink while [consent] is off or
 * the build has no vendor ([available] false).
 */
public class ProductAnalytics(
    private val sink: ProductEventSink,
    private val consent: StateFlow<Boolean>,
    /** Whether a vendor is built into this build at all (the Firebase config was present). */
    public val available: Boolean,
) {
    init {
        if (available) safely { sink.setCollectionEnabled(consent.value) }
    }

    /**
     * Applies a changed answer to the vendor; the caller has already stored it. [chosenNow] is true for the person's
     * own answer (the switch, the question) and false when the stored Flutter answer is adopted at start: only the
     * person's "off" resets the vendor's install id. Flutter's answer is carried over without a reset, so the
     * Analytics app-instance id survives the update.
     */
    public fun applyConsent(granted: Boolean, chosenNow: Boolean) {
        if (!available) return
        safely { sink.setCollectionEnabled(granted) }
        if (chosenNow && !granted) safely { sink.forget() }
    }

    /** One event; dropped unless the person agreed, and reduced to what [productSchema] lists. */
    public fun track(event: ProductEvent, props: Map<String, Any?> = emptyMap()) {
        if (!available || !consent.value) return
        val safe = cleanProductProps(event, props) ?: return
        safely { sink.event(event.wire, safe) }
    }

    /** `feature_used` for one use of [feature]. */
    public fun feature(feature: ProductFeature) {
        track(ProductEvent.FeatureUsed, mapOf("feature" to feature))
    }

    /** `screen_view` for the page a route names; only its kind word is kept. */
    public fun screen(route: String) {
        track(ProductEvent.ScreenView, mapOf("screen" to ProductScreen.of(route)))
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Analytics must never be the reason the app fails.
        }
    }
}

/**
 * Flutter 2.1.5 `AnalyticsLifecycle`: `app_open` cold once per process and warm after each return from the
 * background, `session_length` when the app goes to the background, and `update_installed` when the app starts on a
 * newer version than last time. Inert while consent is off, like every other event.
 */
@OptIn(ExperimentalAtomicApi::class)
public class ProductLifecycle(private val analytics: ProductAnalytics, private val clock: Clock) {
    private val away = AtomicBoolean(false)
    private val since = AtomicLong(clock.now().toEpochMilliseconds())

    /**
     * The process started. [previousVersion] is the stored `oc.telemetry.lastVersion`; returns the version to store
     * now, or null when it is unchanged.
     */
    public fun coldStart(currentVersion: String, previousVersion: String?): String? {
        analytics.track(ProductEvent.AppOpen, mapOf("kind" to "cold"))
        if (previousVersion == currentVersion) return null
        if (previousVersion != null) analytics.track(ProductEvent.UpdateInstalled)
        return currentVersion
    }

    public fun onBackground() {
        if (!away.compareAndSet(expectedValue = false, newValue = true)) return
        val elapsed = (clock.now().toEpochMilliseconds() - since.load()).coerceAtLeast(0).milliseconds
        analytics.track(ProductEvent.SessionLength, mapOf("minutes" to ProductBuckets.sessionMinutesOf(elapsed)))
    }

    public fun onForeground() {
        if (!away.compareAndSet(expectedValue = true, newValue = false)) return
        since.store(clock.now().toEpochMilliseconds())
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

package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.analytics.product.ConsentCarryOver
import io.github.eslamasabry.oc.engine.analytics.product.ConsentQuestion
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.ProductLifecycle
import io.github.eslamasabry.oc.engine.analytics.product.TelemetryAnswers
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashOffer
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashOfferFacts
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashOfferMarks
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitHistory
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.opencode_mobile.telemetry.Telemetry
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesTo(AppScope::class)
interface TelemetryAccessors {
    val telemetryContinuity: TelemetryContinuity
}

/**
 * B-180 and E02-S91, the Flutter 2.1.5 twin, app side. Once the adopted prefs store opens:
 * - the stored consent answer (`oc.telemetry.crashReports`, Flutter's or this app's) takes over, and every later
 *   change is written back under the same key, so a rollback to Flutter sees it;
 * - `app_open` cold, `update_installed` against `oc.telemetry.lastVersion`, warm opens and `session_length` from the
 *   activities, and `screen_view` by page kind from the navigator's top route;
 * - the one-time "Help improve OpenCode Mobile?" question, unless `oc.telemetryConsentAsked` says it was asked;
 * - the crash offer, read from the same launch report the exit notice reads.
 */
@SingleIn(AppScope::class)
@Inject
class TelemetryContinuity(
    private val application: Application,
    private val stores: PreviewStores,
    private val navigator: AppNavigator,
    private val exits: ExitHistory,
    private val buildInfo: AppBuildInfo,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val answers by lazy { TelemetryAnswers(stores.prefs, stores.lanes.app) }
    private val lifecycle by lazy { ProductLifecycle(Telemetry.product, clock) }
    private val ready = CompletableDeferred<Boolean>()
    private var started = false

    private val mutableQuestion = MutableStateFlow(false)

    /** True while the one-time consent question is to be shown. */
    val question: StateFlow<Boolean> = mutableQuestion.asStateFlow()

    val crashOffer: CrashOffer =
        CrashOffer(
            object : CrashOfferMarks {
                override fun lastOfferedMs(): Long = answers.crashLastOfferedMs()

                override suspend fun markOffered(atMs: Long): Boolean =
                    answers.setCrashLastOfferedMs(atMs) is Outcome.Ok

                override fun enabled(): Boolean = answers.crashOfferEnabled()
            },
            clock,
        )

    fun start() {
        if (started) return
        started = true
        followActivities()
        scope.launch {
            val open = stores.warm() is Outcome.Ok
            ready.complete(open)
            if (!open) return@launch
            val reporting = Telemetry.reporting
            ConsentCarryOver.adopt(answers.consent(), reporting.reportingConsent.value)?.let(Telemetry::adopt)
            launch { reporting.reportingConsent.drop(1).collect { answers.setConsent(it) } }
            lifecycle.coldStart(appVersion(), answers.lastVersion())?.let { answers.setLastVersion(it) }
            launch {
                navigator.top
                    .filterNotNull()
                    .map { routeName(it::class.java.name) }
                    .distinctUntilChanged()
                    .collect(Telemetry.product::screen)
            }
            when (
                ConsentQuestion.decide(
                    asked = answers.consentAsked(),
                    available = Telemetry.vendorAvailable,
                    consented = reporting.reportingConsent.value,
                )
            ) {
                ConsentQuestion.Skip -> Unit
                ConsentQuestion.MarkOnly -> answers.markConsentAsked()
                // Marked before it shows: back, a swipe or the app closing count as an answer.
                ConsentQuestion.Show ->
                    if (answers.markConsentAsked() is Outcome.Ok) mutableQuestion.value = true
            }
            val report = withContext(dispatchers.io) { exits.launchReport() }
            crashOffer.consider(report, ::withDevice)
        }
    }

    /** "Share anonymous usage" or "Not now" (or back): the question never returns. */
    fun answerQuestion(share: Boolean) {
        mutableQuestion.value = false
        if (!share) return
        Telemetry.reporting.setReportingConsent(true)
        // This start's cold open happened before the answer and was dropped.
        Telemetry.product.track(ProductEvent.AppOpen, mapOf("kind" to "cold"))
    }

    /** Settings › Privacy and data › "Offer crash reports" (the row is the settings builder's; see the S23 report). */
    suspend fun setCrashOfferEnabled(on: Boolean): Outcome<Unit> =
        if (ready.await()) answers.setCrashOfferEnabled(on) else Outcome.Ok(Unit)

    private fun appVersion(): String = "${buildInfo.versionName}+${buildInfo.versionCode}"

    private fun withDevice(facts: CrashOfferFacts): CrashOfferFacts =
        facts.copy(
            androidRelease = Build.VERSION.RELEASE.orEmpty().filter { it.isLetterOrDigit() || it == '.' }.take(16),
            sdk = Build.VERSION.SDK_INT,
            manufacturer = plainFact(Build.MANUFACTURER),
            model = plainFact(Build.MODEL),
        )

    /** Started activities: the first start after none is a warm open, the last stop is the app going away. */
    private fun followActivities() {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var startedCount = 0

                override fun onActivityStarted(activity: Activity) {
                    if (startedCount++ == 0) lifecycle.onForeground()
                }

                override fun onActivityStopped(activity: Activity) {
                    startedCount = (startedCount - 1).coerceAtLeast(0)
                    if (startedCount == 0 && !activity.isChangingConfigurations) lifecycle.onBackground()
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    companion object {
        /** `io.github…routes.ChatsRoute$Home` → `ChatsRoute.Home`: the route's kind, never an argument. */
        fun routeName(className: String): String = className.substringAfterLast('.').replace('$', '.')

        /** Flutter `KeepAliveInfo.plainFact`: letters, digits, spaces and a few marks only, bounded. */
        fun plainFact(raw: String?): String =
            raw.orEmpty().filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.trim().take(40)
    }
}

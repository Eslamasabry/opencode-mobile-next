package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.analytics.Reporting
import io.github.eslamasabry.oc.engine.analytics.product.ConsentCarryOver
import io.github.eslamasabry.oc.engine.analytics.product.ConsentQuestion
import io.github.eslamasabry.oc.engine.analytics.product.TelemetryAnswers
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashOffer
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashOfferFacts
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashOfferMarks
import io.github.eslamasabry.oc.engine.diagnostics.exit.LaunchReport
import io.github.eslamasabry.oc.feature.settings.presentation.CrashOfferSetting
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.shell.ShellOverlay
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.telemetryConsentBody
import io.github.eslamasabry.oc.ui.words.res.telemetryConsentNever
import io.github.eslamasabry.oc.ui.words.res.telemetryConsentNotNow
import io.github.eslamasabry.oc.ui.words.res.telemetryConsentSends
import io.github.eslamasabry.oc.ui.words.res.telemetryConsentShare
import io.github.eslamasabry.oc.ui.words.res.telemetryConsentTitle
import io.github.eslamasabry.opencode_mobile.telemetry.Telemetry
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/*
 * s23-continuity hooks (B-180, E02-S91): Flutter 2.1.5's answers, read and written in place in the adopted
 * preferences, so a person who answered in Flutter is never asked again and a rollback still sees the answer.
 * - The consent ("Send reports to developer"): Flutter's stored answer wins once the store is readable; later changes
 *   from the Privacy switch or the question are written back under Flutter's key.
 * - The one-time "Help improve OpenCode Mobile?" question, only in a build that can send (Crashlytics present).
 * - The crash offer after an unexpected close, shown on the app's status line ("Review and send" / "Not now").
 * - Privacy's "Offer crash reports" switch.
 */

/** The pure start-up steps, so they are tested without Android. */
class TelemetryContinuity(
    private val answers: TelemetryAnswers,
    private val reporting: Reporting,
    private val available: Boolean,
    clock: Clock,
) : CrashOfferMarks, CrashOfferSetting {
    val offer: CrashOffer = CrashOffer(this, clock)
    private val asking = MutableStateFlow(false)

    /** The consent question is open. */
    val question: StateFlow<Boolean> = asking.asStateFlow()

    /** Once the adopted store is readable: carry the stored answer over, then decide the question. */
    suspend fun adopt() {
        ConsentCarryOver.adopt(answers.consent(), reporting.reportingConsent.value)?.let(reporting::setReportingConsent)
        when (ConsentQuestion.decide(answers.consentAsked(), available, reporting.reportingConsent.value)) {
            ConsentQuestion.Skip -> Unit
            ConsentQuestion.MarkOnly -> answers.markConsentAsked()
            // "Asked" is saved before it shows, so Back, a swipe or the app closing count as an answer.
            ConsentQuestion.Show -> if (answers.markConsentAsked() is Outcome.Ok) asking.value = true
        }
    }

    /** A consent change from anywhere (the switch, the question) lands under Flutter's key. */
    suspend fun mirror(granted: Boolean) {
        answers.setConsent(granted)
    }

    fun answer(share: Boolean) {
        asking.value = false
        reporting.setReportingConsent(share)
    }

    suspend fun considerCrash(report: LaunchReport) {
        offer.consider(report)
    }

    override fun lastOfferedMs(): Long = answers.crashLastOfferedMs()

    override suspend fun markOffered(atMs: Long): Boolean = answers.setCrashLastOfferedMs(atMs) is Outcome.Ok

    override fun enabled(): Boolean = answers.crashOfferEnabled()

    override suspend fun setEnabled(on: Boolean): Boolean = answers.setCrashOfferEnabled(on) is Outcome.Ok

    /** The person answered the offer (either way): it never returns for this crash. */
    fun takeCrash(): CrashOfferFacts? = offer.take()
}

@ContributesTo(AppScope::class)
interface ContinuityAccessors {
    val telemetryContinuity: AppTelemetryContinuity
}

/** The app-wide owner: started once from OcApplication, after the migration gate opens. */
@SingleIn(AppScope::class)
@Inject
class AppTelemetryContinuity(
    private val stores: PreviewStores,
    private val exits: io.github.eslamasabry.oc.engine.diagnostics.exit.ExitHistory,
    private val reporting: Reporting,
    clock: Clock,
    appScope: AppCoroutineScope,
) {
    private val scope = CoroutineScope(appScope.coroutineContext)
    val runtime: TelemetryContinuity =
        TelemetryContinuity(TelemetryAnswers(stores.prefs, stores.lanes.app), reporting, Telemetry.vendorAvailable, clock)
    /** s28: Flutter 2.1.5's split answers (usage and app health, crash reports). */
    val health: HealthConsentStore = HealthConsentStore(stores.prefs, stores.lanes.app, Telemetry.vendorAvailable)
    private var started = false

    /** The first-start question: "Share both" turns on both answers (F: telemetry_consent.dart:49). */
    fun answer(share: Boolean) {
        runtime.answer(share)
        if (share) scope.launch { health.write(io.github.eslamasabry.oc.feature.settings.presentation.HealthAnswers(true, true)) }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            // Carried over once before the question is decided, so an old answer is never asked again.
            health.migrate()
            runtime.adopt()
            // The split keys win over the old single one once they exist.
            health.storedCrashes()?.let { if (it != reporting.reportingConsent.value) reporting.setReportingConsent(it) }
            launch {
                reporting.reportingConsent.drop(1).collect {
                    runtime.mirror(it)
                    health.mirrorCrashes(it)
                }
            }
            runtime.considerCrash(exits.launchReport())
        }
    }
}

/** The one-time question above whatever screen is open (Flutter `askTelemetryConsentOnce`). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ShellOverlay>())
class TelemetryConsentOverlay(private val continuity: AppTelemetryContinuity) : ShellOverlay {
    @Composable
    override fun Content() {
        val open by continuity.runtime.question.collectAsStateWithLifecycle()
        KitConfirm(
            spec =
                if (!open) null
                else
                    KitConfirmSpec(
                        title = stringResource(Res.string.telemetryConsentTitle),
                        confirmLabel = stringResource(Res.string.telemetryConsentShare),
                        body = stringResource(Res.string.telemetryConsentBody),
                        consequences =
                            listOf(
                                stringResource(Res.string.telemetryConsentSends),
                                stringResource(Res.string.telemetryConsentNever),
                            ),
                        cancelLabel = stringResource(Res.string.telemetryConsentNotNow),
                    ),
            onConfirm = { continuity.answer(true) },
            onCancel = { continuity.answer(false) },
        )
    }
}

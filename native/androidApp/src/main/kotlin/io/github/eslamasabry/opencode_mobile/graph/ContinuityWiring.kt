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
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryService
import io.github.eslamasabry.oc.engine.analytics.product.ConsentQuestion
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.TelemetryAnswers
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashOffer
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashOfferFacts
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashOfferMarks
import io.github.eslamasabry.oc.engine.diagnostics.exit.LaunchReport
import io.github.eslamasabry.oc.feature.settings.presentation.CrashOfferSetting
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequence
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequenceMark
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.shell.ShellOverlay
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.telemetryConsentBody
import io.github.eslamasabry.oc.ui.words.res.telemetryConsentChoose
import io.github.eslamasabry.oc.ui.words.res.telemetryConsentSave
import io.github.eslamasabry.oc.ui.words.res.telemetryCrashesDetail
import io.github.eslamasabry.oc.ui.words.res.telemetryCrashesTitle
import io.github.eslamasabry.oc.ui.words.res.telemetryUsageDetail
import io.github.eslamasabry.oc.ui.words.res.telemetryUsageTitle
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/*
 * s23-continuity hooks (B-180, E02-S91) and the B-180/B-190 consent: Flutter 2.1.5's answers, read and written in
 * place in the adopted preferences, so a person who answered in Flutter is never asked again and a rollback still sees
 * the answer.
 * - The two answers (`usage`, `crashes`) under Flutter's keys, carried over from 2.1.5's first single answer once
 *   (HealthConsentStore.migrate), then applied to the vendor (TelemetryService.start).
 * - The one-time "Help improve OpenCode Mobile?" question, only in a build that can send (Firebase present): "Share
 *   both", "Not now", or "Choose", which opens the two switches.
 * - The crash offer after an unexpected close, shown on the app's status line ("Review and send" / "Not now").
 * - Privacy's "Offer crash reports" switch.
 */

/** What the consent question shows. */
enum class ConsentAsk { Closed, Question, Choose }

/** The pure start-up steps, so they are tested without Android. */
class TelemetryContinuity(
    private val answers: TelemetryAnswers,
    private val health: HealthConsentStore,
    private val telemetry: TelemetryService,
    clock: Clock,
) : CrashOfferMarks, CrashOfferSetting {
    val offer: CrashOffer = CrashOffer(this, clock)
    private val asking = MutableStateFlow(ConsentAsk.Closed)

    /** The consent question (or its two switches) is open. */
    val question: StateFlow<ConsentAsk> = asking.asStateFlow()

    /** Once the adopted store is readable: carry an old answer over, apply the answers, then decide the question. */
    suspend fun adopt() {
        health.migrate()
        telemetry.start(health.stored(), store = health, reports = health)
        val consented = telemetry.consent.let { it.usage || it.crashes }
        when (ConsentQuestion.decide(answers.consentAsked(), telemetry.available, consented)) {
            ConsentQuestion.Skip -> Unit
            ConsentQuestion.MarkOnly -> answers.markConsentAsked()
            // "Asked" is saved before it shows, so Back, a swipe or the app closing count as an answer.
            ConsentQuestion.Show -> if (answers.markConsentAsked() is Outcome.Ok) asking.value = ConsentAsk.Question
        }
    }

    /** "Share both" turns both answers on; "Not now" (or Back) leaves both off and is never asked again. */
    suspend fun answer(share: Boolean) {
        asking.value = ConsentAsk.Closed
        if (!share) return
        telemetry.setConsent(usage = true, crashes = true)
        // This start's cold open happened before the answer and was dropped.
        telemetry.track(ProductEvent.AppOpen, mapOf("kind" to "cold"))
    }

    /** "Choose": the two switches, each with what it sends. */
    fun choose() {
        asking.value = ConsentAsk.Choose
    }

    /** "Save my choices". Closing the sheet without saving leaves both off. */
    suspend fun saveChoice(usage: Boolean, crashes: Boolean) {
        asking.value = ConsentAsk.Closed
        telemetry.setConsent(usage, crashes)
        if (usage) telemetry.track(ProductEvent.AppOpen, mapOf("kind" to "cold"))
    }

    fun dismissChoice() {
        asking.value = ConsentAsk.Closed
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
    private val health: AppTelemetryHealth,
    clock: Clock,
    appScope: AppCoroutineScope,
) {
    private val scope = CoroutineScope(appScope.coroutineContext)
    val store: HealthConsentStore = HealthConsentStore(stores.prefs, stores.lanes.app)
    val runtime: TelemetryContinuity =
        TelemetryContinuity(TelemetryAnswers(stores.prefs, stores.lanes.app), store, Telemetry.service, clock)

    /** s28: Privacy's "Usage and app health" and "Crash reports" switches over the live service. */
    val consent: HealthConsentPort = HealthConsentPort(Telemetry.service)
    private var started = false

    fun answer(share: Boolean) {
        scope.launch { runtime.answer(share) }
    }

    fun choose() {
        runtime.choose()
    }

    fun saveChoice(usage: Boolean, crashes: Boolean) {
        scope.launch { runtime.saveChoice(usage, crashes) }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            // Carried over once and applied before the question is decided, so an old answer is never asked again.
            runtime.adopt()
            health.start(store)
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
        val ask by continuity.runtime.question.collectAsStateWithLifecycle()
        KitConfirm(
            spec =
                if (ask != ConsentAsk.Question) null
                else
                    KitConfirmSpec(
                        title = stringResource(Res.string.telemetryConsentTitle),
                        confirmLabel = stringResource(Res.string.telemetryConsentShare),
                        body = stringResource(Res.string.telemetryConsentBody),
                        consequenceItems =
                            listOf(
                                KitConsequence(stringResource(Res.string.telemetryConsentSends)),
                                KitConsequence(
                                    stringResource(Res.string.telemetryConsentNever),
                                    mark = KitConsequenceMark.Kept,
                                ),
                            ),
                        cancelLabel = stringResource(Res.string.telemetryConsentNotNow),
                        icon = KitIcons.shield,
                    ),
            onConfirm = { continuity.answer(true) },
            onCancel = { continuity.answer(false) },
            // The alternative cancels first ("Not now" changes nothing), then opens the two switches.
            alternative = KitAction(stringResource(Res.string.telemetryConsentChoose), continuity::choose),
        )
        ConsentChoiceSheet(
            visible = ask == ConsentAsk.Choose,
            onSave = continuity::saveChoice,
            onDismiss = continuity.runtime::dismissChoice,
        )
    }
}

/** "Choose": the two switches with what each sends, and one button that applies them (both start off). */
@Composable
private fun ConsentChoiceSheet(visible: Boolean, onSave: (Boolean, Boolean) -> Unit, onDismiss: () -> Unit) {
    var usage by rememberSaveable(visible) { mutableStateOf(false) }
    var crashes by rememberSaveable(visible) { mutableStateOf(false) }
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.telemetryConsentTitle),
        icon = KitIcons.shield,
        primary = KitAction(stringResource(Res.string.telemetryConsentSave), { onSave(usage, crashes) }),
    ) {
        KitRowGroup {
            KitSwitchRow(
                title = stringResource(Res.string.telemetryUsageTitle),
                checked = usage,
                modifier = Modifier.testTag("telemetry-consent-usage"),
                leading = KitRowLeading.Glyph(KitIcons.shield),
                supporting = stringResource(Res.string.telemetryUsageDetail),
                supportingMaxLines = 8,
                onCheckedChange = { usage = it },
            )
            KitSwitchRow(
                title = stringResource(Res.string.telemetryCrashesTitle),
                checked = crashes,
                modifier = Modifier.testTag("telemetry-consent-crashes"),
                leading = KitRowLeading.Glyph(KitIcons.bug),
                supporting = stringResource(Res.string.telemetryCrashesDetail),
                supportingMaxLines = 6,
                onCheckedChange = { crashes = it },
            )
        }
    }
}

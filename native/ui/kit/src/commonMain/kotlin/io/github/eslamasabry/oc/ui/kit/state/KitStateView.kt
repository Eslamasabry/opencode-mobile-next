package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.Arrangement
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitBottomInset
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.kitSinceStillWaiting
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import org.jetbrains.compose.resources.stringResource

/** Plan 06 §7.6: a state view is at most 440 dp wide. */
internal const val STATE_MAX_WIDTH_DP: Int = 440

/** A wait escalates once at 8 s (Dart `KitMotion.escalateAfter`, STATE-6). */
public val STATE_ESCALATE_AFTER: Duration = 8.seconds

/** Which state a [KitStateView] shows (Dart `KitStateView` forms and tones). */
public enum class KitStateKind { Loading, Working, Empty, Finished, Error, Missing, Calm }

/** One extra way out an error state offers (pure; B-KIT-034). */
public enum class ErrorWay { TryAgain, SwitchServer, CopyDetails, ReportProblem }

/** Where a [KitStateView] sits (Dart `KitStateSize`). */
public enum class KitStateSize {
    /** Fills the body, centred vertically, with no card around it. */
    Page,

    /** Inside a list: the same slots at smaller type, starting at the gutter. */
    Inline,
}

/** Pure rules of [KitStateView] (B-KIT-034). */
public object StateViewRules {
    /** True once the wait has lasted [STATE_ESCALATE_AFTER]. A slow wait is never called a failure. */
    public fun isSlow(waited: Duration): Boolean = waited >= STATE_ESCALATE_AFTER

    /** A network failure: the fix is to try again or switch server, not to report a problem. */
    public fun isNetwork(f: Failure?): Boolean =
        f is Failure.Network || f is Failure.TimedOut || f is Failure.Busy || f is Failure.Unknown ||
            (f is Failure.Refused && f.reason == RefusalReason.DispatchUncertain)

    /**
     * The error defaults (Dart `KitStateView.error`): network failures offer Try again and Switch server; others Try
     * again and Report a problem; both offer Copy details when the failure carries redacted details. A way shows only
     * when its handler exists ("no handler, no button").
     */
    public fun errorWays(failure: Failure?, canRetry: Boolean, canSwitch: Boolean, canReport: Boolean): List<ErrorWay> =
        errorWays(failure, canRetry, canSwitch, canReport, failure?.details != null)

    /** [errorWays] with the details given apart from the failure (Dart `KitStateView.error(details:)`). */
    public fun errorWays(
        failure: Failure?,
        canRetry: Boolean,
        canSwitch: Boolean,
        canReport: Boolean,
        hasDetails: Boolean,
    ): List<ErrorWay> =
        buildList {
            if (canRetry) add(ErrorWay.TryAgain)
            if (isNetwork(failure)) {
                if (canSwitch) add(ErrorWay.SwitchServer)
            }
            if (hasDetails) add(ErrorWay.CopyDetails)
            if (!isNetwork(failure) && canReport) add(ErrorWay.ReportProblem)
        }

    /** Missing capability: a prerequisite must offer its enable action (refused without one). */
    public fun missingIsValid(prerequisite: Boolean, hasEnable: Boolean): Boolean = !prerequisite || hasEnable

    /** The words under the title once a wait escalated: always the escalation's own 8 s (Dart `KitSince.slowLabel`). */
    public fun slowSeconds(): Long = STATE_ESCALATE_AFTER.inWholeSeconds
}

/** How far above the middle of its room a calm page sets its words (Dart `_calmLift`). */
private const val CALM_LIFT: Float = .1f // F: lib/ui/kit/kit_state_view.dart:438

/**
 * A whole state (Dart `KitStateView`; plan 06 §7.6): a bare glyph (no tile), a title that says the state now, at most two short
 * [body] sentences, an optional [progress], then the actions in the one hierarchy. There is no free-text error
 * parameter: an [KitStateKind.Error] takes the [failure] (its sentence comes from the screen, mapped through
 * `:ui:words`, as [body]) and adds the kit's ways out — Try again ([onRetry]), Switch server ([onSwitchServer]) for
 * network failures, Copy details ([details], by default the failure's redacted details) and Report a problem
 * ([onReport]); the details fold under the actions. A [KitStateKind.Missing] states its [cost] above its enable action.
 * [KitStateKind.Calm] draws no tile: one big calm line, the next step, and the actions at the bottom of the page.
 * A [size] of [KitStateSize.Page] fills the height it is given and centres; [KitStateSize.Inline] sits in a list.
 * With [since] the view escalates once at 8 s: the body becomes "Still waiting after 8 s" and the [onSlow] ways out
 * unfold (at most two); the title stays. Working → Finished plays `Done` once (not under motion Off).
 *
 * States: loading, working, empty, error, missing, slow, finished, calm
 * Text: reading
 * Cap: State (440)
 * Motion: none of its own beyond the progress bar; Off = still
 * Replaces: KitStateView, .error, .missing, .calm (lib/ui/kit/kit_state_view.dart)
 */
@Composable
public fun KitStateView(
    kind: KitStateKind,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    progress: KitProgress? = null,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    tertiary: List<KitAction> = emptyList(),
    failure: Failure? = null,
    since: TimeMark? = null,
    onSlow: List<KitAction> = emptyList(),
    prerequisite: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onSwitchServer: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    retryLabel: String? = null,
    switchServerLabel: String? = null,
    size: KitStateSize = KitStateSize.Page,
    details: Redacted? = failure?.details,
    cost: List<String> = emptyList(),
) {
    kitAssert(onSlow.size <= 2) { "KitStateView.onSlow: at most two ways out" }
    kitAssert(kind != KitStateKind.Missing || StateViewRules.missingIsValid(prerequisite, primary != null)) {
        "KitStateView.missing: a prerequisite without its enable action is refused (B-KIT-034)"
    }
    kitAssert(onRetry == null || retryLabel != null) { "KitStateView: Try again needs its words" }
    val platform = LocalKitPlatform.current
    val appearance = LocalKitAppearance.current
    val motion = KitMotion.state

    // Working → Finished while the person watches: one soft Done (skipped under Off, plan 06 §4.9).
    val previous = remember { PreviousKind() }
    LaunchedEffect(kind) {
        if (previous.kind == KitStateKind.Working && kind == KitStateKind.Finished && appearance.haptics && motion.level != MotionLevel.Off) {
            platform.haptic(KitHaptic.Done)
        }
        previous.kind = kind
    }

    val page = size == KitStateSize.Page
    // A page's state sits in the middle; an inline one starts at the gutter, where the words around it do.
    val capped = modifier.fillMaxWidth().kitMaxWidth(CapRole.State, if (page) CapAlign.Center else CapAlign.Start)
    if (kind == KitStateKind.Calm) {
        CalmState(title, body, primary, secondary, tertiary, page, capped)
        return
    }

    var slow by remember(since) { mutableStateOf(since != null && StateViewRules.isSlow(since.elapsedNow())) }
    if (since != null && !slow) {
        LaunchedEffect(since) {
            delay((STATE_ESCALATE_AFTER - since.elapsedNow()).coerceAtLeast(Duration.ZERO))
            slow = true
        }
    }
    val shownBody = if (slow && since != null) stringResource(Res.string.kitSinceStillWaiting, StateViewRules.slowSeconds()) else body

    @Suppress("DEPRECATION") // see KitField: no common plain-text clipboard write without the manager yet
    val clipboard = LocalClipboardManager.current
    val copyLabel = stringResource(Res.string.kitCopyDetails)
    val reportLabel = stringResource(Res.string.kitReportProblem)
    val (p, s, t) =
        if (kind == KitStateKind.Error) {
            val ways = StateViewRules.errorWays(failure, onRetry != null, onSwitchServer != null, onReport != null, details != null)
            val extra =
                ways.mapNotNull { w ->
                    when (w) {
                        ErrorWay.CopyDetails ->
                            KitAction(copyLabel, onClick = {
                                details?.let { clipboard.setText(AnnotatedString(it.text)) }
                            }, icon = KitIcons.copy, haptic = KitHaptic.Send)
                        ErrorWay.ReportProblem -> KitAction(reportLabel, onClick = onReport)
                        else -> null
                    }
                }
            Triple(
                primary ?: onRetry?.let { KitAction(retryLabel.orEmpty(), it) },
                secondary ?: onSwitchServer?.takeIf { ErrorWay.SwitchServer in ways }?.let { KitAction(switchServerLabel.orEmpty(), it) },
                tertiary + extra,
            )
        } else {
            Triple(primary, secondary, tertiary)
        }
    val foldText = if (kind == KitStateKind.Error) details else null
    val detailsLabel = stringResource(Res.string.kitDetails)

    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val tint =
        when (kind) {
            KitStateKind.Loading, KitStateKind.Working -> colors.accent
            KitStateKind.Finished -> colors.success
            KitStateKind.Error -> colors.text1
            else -> colors.text2
        }
    val glyph = icon ?: when (kind) {
        KitStateKind.Error -> KitIcons.error
        KitStateKind.Missing -> KitIcons.locked
        KitStateKind.Finished -> KitPartGlyphs.Check
        else -> KitPartGlyphs.Ring
    }
    val gap = if (page) Space.S5 else Space.S3
    val glyphSize = if (page) FlutterKitTokens.MARK_ICON_SIZE else tokens.smallIcon

    val column: @Composable () -> Unit = {
        // Alone on its page the words are centred in the free area; the inline form (inside a list or card) starts at the rail.
        Column(
            Modifier.fillMaxWidth().testTag("kit-state-view").semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = if (page) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            // The bare glyph on the title's rail: no tile, box or circle behind it (audit 2026-11-09 C01).
            KitGlyph(glyph, tint, glyphSize.dp, Modifier.testTag("kit-state-icon"))
            Spacer(Modifier.height(gap.dp))
            KitText(
                title,
                Modifier.fillMaxWidth().semantics { heading() },
                role = if (page) TypeRole.Title else TypeRole.Headline,
                textAlign = if (page) TextAlign.Center else null,
            )
            shownBody?.let {
                Spacer(Modifier.height((if (page) Space.S2 else Space.S1).dp))
                KitText(
                    it,
                    Modifier.fillMaxWidth(),
                    role = if (page) TypeRole.Body else TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    textAlign = if (page) TextAlign.Center else null,
                )
            }
            progress?.let {
                Spacer(Modifier.height(gap.dp))
                KitProgressView(it)
            }
            if (cost.isNotEmpty()) {
                Spacer(Modifier.height(gap.dp))
                KitCostNotice(cost)
            }
            if (p != null || s != null || t.isNotEmpty()) {
                Spacer(Modifier.height((if (page) Space.S6 else Space.S3).dp))
                KitActionBlock(primary = p, secondary = s, tertiary = t)
            }
            if (slow && onSlow.isNotEmpty()) {
                Spacer(Modifier.height((if (page) Space.S3 else Space.S2).dp))
                KitActionBlock(tertiary = onSlow)
            }
            if (foldText != null) {
                Spacer(Modifier.height((if (page) Space.S3 else Space.S1).dp))
                KitDetailsFold(label = detailsLabel, copyLabel = copyLabel, text = foldText)
            }
        }
    }

    if (!page) {
        val side = kitPageGutter()
        Box(capped.padding(start = side, top = tokens.gutter.dp, end = side, bottom = Space.S2.dp)) {
            column()
        }
        return
    }
    BoxWithConstraints(capped) {
        val bounded = constraints.hasBoundedHeight
        Box(
            Modifier.fillMaxWidth()
                .then(if (bounded) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .heightIn(min = if (bounded) maxHeight else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.padding(horizontal = kitPageGutter(), vertical = FlutterKitTokens.STATE_VERTICAL_PADDING.dp)) {
                column()
            }
        }
    }
}

/** Dart `KitStateView.calm`: no tile, one big calm line, the next step, the actions at the bottom. */
@Composable
private fun CalmState(
    title: String,
    body: String?,
    primary: KitAction?,
    secondary: KitAction?,
    tertiary: List<KitAction>,
    page: Boolean,
    modifier: Modifier,
) {
    val tokens = LocalKitTokens.current
    // The secondary action is a quiet text button under the primary; it was never drawn here before.
    val quiet = listOfNotNull(secondary) + tertiary.take(2) // F: lib/ui/kit/kit_state_view.dart:565
    val answer: @Composable () -> Unit = {
        KitStatusAnswer(title, line = body, scale = if (page) KitAnswerScale.Calm else KitAnswerScale.Compact)
    }
    val quietRow: @Composable () -> Unit = {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.CenterHorizontally)) {
            quiet.forEach { KitButton(it, role = KitButtonRole.Tertiary) }
        }
    }
    if (!page) {
        Column(modifier.padding(vertical = Space.S6.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            answer()
            if (quiet.isNotEmpty()) {
                Spacer(Modifier.height(Space.S3.dp))
                quietRow()
            }
        }
        return
    }
    val hasBottom = primary != null || quiet.isNotEmpty()
    val inset = kitBottomInset().bottom
    BoxWithConstraints(modifier) {
        val bounded = constraints.hasBoundedHeight
        val spread = bounded && hasBottom
        // Optical centre: a little above the middle of the room, where the eye expects the middle to be.
        val lift = if (spread) maxHeight * CALM_LIFT else 0.dp
        // Above the dock and the gesture bar, like a list's end.
        val end = Space.S4.dp + if (bounded) inset else 0.dp
        Column(
            Modifier.fillMaxWidth()
                .then(if (bounded) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .heightIn(min = if (bounded) maxHeight else 0.dp)
                .padding(bottom = end)
                .padding(horizontal = kitPageGutter())
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            if (spread) Spacer(Modifier.height(0.dp))
            Box(Modifier.padding(top = Space.S5.dp, bottom = Space.S5.dp + lift)) { answer() }
            if (hasBottom) {
                Column(Modifier.fillMaxWidth()) {
                    primary?.let { KitButton(it, Modifier.fillMaxWidth(), KitButtonRole.Primary) }
                    if (quiet.isNotEmpty()) {
                        if (primary != null) Spacer(Modifier.height(Space.S2.dp))
                        quietRow()
                    }
                }
            }
        }
    }
}

private class PreviousKind {
    var kind: KitStateKind? = null
}

package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
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

/** Pure rules of [KitStateView] (B-KIT-034). */
public object StateViewRules {
    /** True once the wait has lasted [STATE_ESCALATE_AFTER]. A slow wait is never called a failure. */
    public fun isSlow(waited: Duration): Boolean = waited >= STATE_ESCALATE_AFTER

    /** A network failure: the fix is to try again or switch server, not to report a problem. */
    public fun isNetwork(f: Failure?): Boolean =
        f is Failure.Network || f is Failure.Busy || f is Failure.Unknown

    /**
     * The error defaults (Dart `KitStateView.error`): network failures offer Try again and Switch server; others Try
     * again and Report a problem; both offer Copy details when the failure carries redacted details. A way shows only
     * when its handler exists ("no handler, no button").
     */
    public fun errorWays(failure: Failure?, canRetry: Boolean, canSwitch: Boolean, canReport: Boolean): List<ErrorWay> =
        buildList {
            if (canRetry) add(ErrorWay.TryAgain)
            if (isNetwork(failure)) {
                if (canSwitch) add(ErrorWay.SwitchServer)
            }
            if (failure?.details != null) add(ErrorWay.CopyDetails)
            if (!isNetwork(failure) && canReport) add(ErrorWay.ReportProblem)
        }

    /** Missing capability: a prerequisite must offer its enable action (refused without one). */
    public fun missingIsValid(prerequisite: Boolean, hasEnable: Boolean): Boolean = !prerequisite || hasEnable
}

/**
 * A whole state (Dart `KitStateView`; plan 06 §7.6): an icon tile, a title that says the state now, at most two short
 * [body] sentences, an optional [progress], then the actions in the one hierarchy. There is no free-text error
 * parameter: an [KitStateKind.Error] takes the [failure] (its sentence comes from the screen, mapped through
 * `:ui:words`, as [body]) and adds the kit's ways out — Try again ([onRetry]), Switch server ([onSwitchServer]) for
 * network failures, Copy details (the failure's already-redacted details) and Report a problem ([onReport]).
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
) {
    kitAssert(onSlow.size <= 2) { "KitStateView.onSlow: at most two ways out" }
    kitAssert(kind != KitStateKind.Missing || StateViewRules.missingIsValid(prerequisite, primary != null)) {
        "KitStateView.missing: a prerequisite without its enable action is refused (B-KIT-034)"
    }
    kitAssert(onRetry == null || retryLabel != null) { "KitStateView: Try again needs its words" }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
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

    var slow by remember(since) { mutableStateOf(since != null && StateViewRules.isSlow(since.elapsedNow())) }
    if (since != null && !slow) {
        LaunchedEffect(since) {
            delay((STATE_ESCALATE_AFTER - since.elapsedNow()).coerceAtLeast(Duration.ZERO))
            slow = true
        }
    }
    val shownBody =
        if (slow && since != null) stringResource(Res.string.kitSinceStillWaiting, since.elapsedNow().inWholeSeconds.coerceAtLeast(STATE_ESCALATE_AFTER.inWholeSeconds))
        else body

    val tint =
        when (kind) {
            KitStateKind.Loading, KitStateKind.Working -> colors.accent
            KitStateKind.Finished -> colors.success
            KitStateKind.Error -> colors.text1
            else -> colors.text2
        }
    val glyph = icon ?: when (kind) {
        KitStateKind.Error -> KitPartGlyphs.Warning
        KitStateKind.Finished -> KitPartGlyphs.Check
        else -> KitPartGlyphs.Ring
    }

    @Suppress("DEPRECATION") // see KitField: no common plain-text clipboard write without the manager yet
    val clipboard = LocalClipboardManager.current
    val copyLabel = stringResource(Res.string.kitCopyDetails)
    val reportLabel = stringResource(Res.string.kitReportProblem)
    val (p, s, t) =
        if (kind == KitStateKind.Error) {
            val ways = StateViewRules.errorWays(failure, onRetry != null, onSwitchServer != null, onReport != null)
            val extra =
                ways.mapNotNull { w ->
                    when (w) {
                        ErrorWay.CopyDetails ->
                            KitAction(copyLabel, onClick = {
                                failure?.details?.let { clipboard.setText(AnnotatedString(it.text)) }
                            }, haptic = KitHaptic.Send)
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
            Triple(primary, secondary, tertiary + if (slow) onSlow else emptyList())
        }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = STATE_MAX_WIDTH_DP.dp).fillMaxWidth().testTag("kit-state-view"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (kind != KitStateKind.Calm) {
                Box(
                    Modifier.size((tokens.iconTile + Space.S4).dp).clip(KitShapeId.Tile.toShape()).background(colors.surface3),
                    contentAlignment = Alignment.Center,
                ) { KitGlyph(glyph, tint, tokens.smallIcon.dp) }
            }
            KitText(title, Modifier.semantics { heading() }, role = TypeRole.Title, textAlign = TextAlign.Center)
            shownBody?.let {
                KitText(
                    it,
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    role = TypeRole.Body,
                    tone = TextTone.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
            progress?.let { KitProgressView(it) }
            KitActionBlock(primary = p, secondary = s, tertiary = t)
        }
    }
}

private class PreviousKind {
    var kind: KitStateKind? = null
}

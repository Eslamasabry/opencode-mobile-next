package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitProgressEtaHours
import io.github.eslamasabry.oc.ui.words.res.kitProgressEtaMinutes
import io.github.eslamasabry.oc.ui.words.res.kitProgressEtaSeconds
import io.github.eslamasabry.oc.ui.words.res.kitProgressStep
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Progress inside a state (Dart `KitProgress`): waiting (indeterminate), known (a fraction), or staged ("Step 3 of 5 ·
 * Installing"). [eta] is never invented: the caller passes it only from a measurement (STATE-6).
 */
@Immutable
public sealed interface KitProgress {
    public val caption: String?
    public val eta: Duration?

    public data class Waiting(override val caption: String? = null) : KitProgress {
        override val eta: Duration? get() = null
    }

    public data class Known(val value: Float, override val caption: String? = null, override val eta: Duration? = null) : KitProgress

    /** A job made of stages; the bar is at `(step - 1 + stepValue) / of`. */
    public data class Staged(
        val step: Int,
        val of: Int,
        val label: String,
        val stepValue: Float? = null,
        override val caption: String? = null,
        override val eta: Duration? = null,
    ) : KitProgress
}

/** The bar's colour (Dart `KitProgress.tone`): moving = accent; stopped = `text3`; failed = `text1`, never red. */
public enum class KitProgressTone { Moving, Stopped, Failed }

/** The eta's unit and rounded-up count (Dart `_etaWords`). */
public data class EtaWords(val unit: Unit, val count: Int) {
    public enum class Unit { Seconds, Minutes, Hours }
}

/** Pure rules of [KitProgress] (B-KIT-025). */
public object ProgressRules {
    /** 0..1, or null while waiting. Staged: `(step - 1 + (stepValue ?: 0)) / of`. */
    public fun value(p: KitProgress): Float? =
        when (p) {
            is KitProgress.Waiting -> null
            is KitProgress.Known -> p.value
            is KitProgress.Staged -> (p.step - 1 + (p.stepValue ?: 0f)) / p.of
        }

    /** The arguments are inside their ranges (debug-asserted by the view). */
    public fun valid(p: KitProgress): Boolean =
        when (p) {
            is KitProgress.Waiting -> true
            is KitProgress.Known -> p.value in 0f..1f
            is KitProgress.Staged -> p.of >= 1 && p.step in 1..p.of && (p.stepValue == null || p.stepValue in 0f..1f)
        }

    /**
     * Under a minute: rounded up to the next 10 s; under an hour: whole minutes rounded up; beyond: whole hours rounded
     * up — never promising less time than measured (2 h 54 min is "about 3 h"). Zero or less is never shown.
     */
    public fun eta(eta: Duration?): EtaWords? {
        if (eta == null || eta <= Duration.ZERO) return null
        return when {
            eta < 1.minutes -> EtaWords(EtaWords.Unit.Seconds, ceil(eta.inWholeMilliseconds / 10_000.0).toInt() * 10)
            eta < 1.hours -> EtaWords(EtaWords.Unit.Minutes, ceil(eta.inWholeSeconds / 60.0).toInt())
            else -> EtaWords(EtaWords.Unit.Hours, ceil(eta.inWholeSeconds / 3600.0).toInt())
        }
    }

    /** The job a value belongs to: within one job the bar never goes backwards. A new job may start lower. */
    public fun jobOf(p: KitProgress): Any? = (p as? KitProgress.Staged)?.let { Triple(it.step, it.of, it.label) } ?: p::class

    /** The value to show: never below [shown] within the same job. */
    public fun forward(shown: Float?, next: Float?, sameJob: Boolean): Float? =
        if (sameJob && shown != null && next != null && next < shown) shown else next
}

/** Words of the line under a bar, joined with " · " (Dart `KitProgress.line`). */
@Composable
public fun KitProgress.line(): String {
    val parts = mutableListOf<String>()
    if (this is KitProgress.Staged) {
        parts += stringResource(Res.string.kitProgressStep, step, of)
        parts += label
    }
    caption?.let { parts += it }
    ProgressRules.eta(eta)?.let { e ->
        parts +=
            when (e.unit) {
                EtaWords.Unit.Seconds -> pluralStringResource(Res.plurals.kitProgressEtaSeconds, e.count, Numbers.format(e.count))
                EtaWords.Unit.Minutes -> pluralStringResource(Res.plurals.kitProgressEtaMinutes, e.count, Numbers.format(e.count))
                EtaWords.Unit.Hours -> pluralStringResource(Res.plurals.kitProgressEtaHours, e.count, Numbers.format(e.count))
            }
    }
    return parts.joinToString(" · ")
}

/**
 * A [KitProgress] drawn: a rounded 4 dp bar and its one line (Dart `KitProgressView`). Waiting is indeterminate (a
 * sweeping segment; a still 30 % segment under reduced motion, never read as "30 percent"); known and staged are
 * determinate and ease to the new value (instant under Off). Within one job the bar never goes backwards. [tone]:
 * neutral = a stopped job (`text3`), failure = a failed one (`text1`), never red. The line is read once, as the bar's
 * label.
 *
 * States: waiting, known, staged, stopped, failed
 * Text: frame (line)
 * Cap: Reading
 * Motion: value eases STANDARD; Off = instant; waiting sweeps only when loops are allowed
 * Replaces: KitProgress, KitProgressView, KitProgress.staged (lib/ui/kit/kit_progress.dart)
 */
@Composable
public fun KitProgressView(
    progress: KitProgress,
    modifier: Modifier = Modifier,
    semanticsLabel: String? = null,
    tone: KitProgressTone = KitProgressTone.Moving,
) {
    kitAssert(ProgressRules.valid(progress)) { "KitProgress: value, step or stepValue out of range (B-KIT-025)" }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ink =
        when (tone) {
            KitProgressTone.Stopped -> colors.text3
            KitProgressTone.Failed -> colors.text1
            KitProgressTone.Moving -> colors.accent
        }
    val job = ProgressRules.jobOf(progress)
    val memory = remember { ForwardMemory() }
    val raw = ProgressRules.value(progress)
    val target = ProgressRules.forward(memory.shown, raw, memory.job == job)
    kitAssert(memory.job != job || memory.shown == null || raw == null || raw >= memory.shown!!) {
        "KitProgress: a bar never goes backwards within one job (B-KIT-025)"
    }
    memory.job = job
    memory.shown = target
    val line = progress.line()
    val label = listOfNotNull(semanticsLabel?.takeIf { it.isNotEmpty() }, line.takeIf { it.isNotEmpty() }).joinToString(", ")
    val eased by animateFloatAsState(target ?: 0f, specs.enter(), label = "kit-progress")
    val sweep = if (target == null) sweepPhase() else null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(tokens.progressBar.dp).testTag("kit-state-progress").semantics {
                if (label.isNotEmpty()) contentDescription = label
                progressBarRangeInfo = if (target == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(target, 0f..1f)
            },
        ) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(colors.surface3, cornerRadius = r)
            val (from, to) =
                when {
                    target != null -> 0f to eased
                    sweep == null -> 0f to STILL_WAITING_END
                    else -> {
                        val p = sweep.value
                        (p * 1.35f - 0.35f).coerceAtLeast(0f) to (p * 1.35f).coerceAtMost(1f)
                    }
                }
            if (to > from) {
                val start = if (rtl) size.width * (1f - to) else size.width * from
                drawRoundRect(ink, topLeft = Offset(start, 0f), size = Size(size.width * (to - from), size.height), cornerRadius = r)
            }
        }
        if (line.isNotEmpty()) {
            KitText(line, Modifier.clearAndSetSemantics {}, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
        }
    }
}

private class ForwardMemory {
    var job: Any? = null
    var shown: Float? = null
}

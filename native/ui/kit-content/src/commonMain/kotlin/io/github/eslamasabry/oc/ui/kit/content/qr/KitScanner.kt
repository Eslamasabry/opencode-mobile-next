package io.github.eslamasabry.oc.ui.kit.content.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitScannerPaused
import io.github.eslamasabry.oc.ui.words.res.kitScannerPreview
import io.github.eslamasabry.oc.ui.words.res.kitScannerSlow
import io.github.eslamasabry.oc.ui.words.res.kitScannerStarting
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** What the scanner shows (Dart `_Phase`): the camera opening, the live preview, the app away, or nothing (failed). */
public enum class KitScannerPhase { Starting, Scanning, Paused, Failed }

/** What the host does when a camera start attempt answers (the branches of Dart `_KitScannerState._start`). */
public enum class KitScannerOpened {
    /** A newer attempt (or the part's end) made this one stale: change nothing. */
    Ignore,

    /** The camera could not open: report it once (Dart `onFailed`); the host shows its own state instead. */
    Failed,

    /** The app went away, or a code was accepted, while the camera opened: stop it again. */
    Stop,

    /** The preview is live. */
    Scanning,
}

/**
 * The scanner's rules, without a camera (Dart `_KitScannerState`): the host owns the camera and asks this class what to
 * do; [phase] is what [KitScanner] shows. Once a code is accepted the scanner latches: nothing more is delivered and the
 * camera stays stopped. A start that answers after a pause or a newer start changes nothing. The decoded value is
 * never kept (SEC-2): [code] hands it to the host's check and forgets it.
 */
public class KitScannerFlow {
    public var phase: KitScannerPhase = KitScannerPhase.Starting
        private set

    /** True once [code] accepted a value. */
    public var handled: Boolean = false
        private set

    private var attempt = 0

    /** A start attempt begins (the host then opens the camera and calls [opened] with this number). */
    public fun start(): Int {
        attempt++
        phase = KitScannerPhase.Starting
        return attempt
    }

    /** Start attempt [number] answered; [failed] when the camera could not open. */
    public fun opened(number: Int, failed: Boolean): KitScannerOpened {
        if (number != attempt) return KitScannerOpened.Ignore
        if (failed) {
            phase = KitScannerPhase.Failed
            return KitScannerOpened.Failed
        }
        if (phase == KitScannerPhase.Paused || handled) return KitScannerOpened.Stop
        phase = KitScannerPhase.Scanning
        return KitScannerOpened.Scanning
    }

    /** A decoded value: offered to [accept] only while scanning; true means the host stops the camera now. */
    public fun code(raw: String, accept: (String) -> Boolean): Boolean {
        if (handled || phase != KitScannerPhase.Scanning || raw.isEmpty()) return false
        if (!accept(raw)) return false
        handled = true
        return true
    }

    /** The app is inactive, hidden or paused; true means the host stops the camera. */
    public fun away(): Boolean {
        if (phase != KitScannerPhase.Scanning && phase != KitScannerPhase.Starting) return false
        phase = KitScannerPhase.Paused
        return !handled
    }

    /** The app is back; true means the host starts again ([start]). */
    public fun back(): Boolean = phase == KitScannerPhase.Paused && !handled

    /** The part is gone: any start still answering is ignored. */
    public fun dispose() {
        attempt++
    }
}

/** At 200 % text the text block scrolls rather than shrink the window below this side (Dart `_minWindow`). */
private const val MIN_WINDOW = 160 // F: lib/ui/kit/kit_scanner.dart:122

/** The window's side: the scanner window token, capped by the box less the gutters and the margins, in whole dp. */
internal fun scannerWindowSide(width: Float, height: Float, gutter: Float, margin: Float): Float =
    floor(max(0f, min(FlutterKitTokens.SCANNER_WINDOW.toFloat(), min(width - 2 * gutter, height - 2 * margin)))) // F: lib/ui/kit/kit_scanner.dart:435

/** The most the text block may take of [height] dp: it scrolls before the frame drops below its smallest side. */
internal fun scannerTextMax(height: Float): Float = max(height - (MIN_WINDOW + 2 * Space.S6), height / 2) // F: lib/ui/kit/kit_scanner.dart:248,250

/**
 * The camera frame that finds a pairing QR (Dart `KitScanner`, lib/ui/kit/kit_scanner.dart): the host's live [preview]
 * behind a square window with four corner brackets, one [instruction] line, and an in-place [rejected] line when the
 * code seen is not the one wanted. The part never opens a camera: the host owns it (with [KitScannerFlow]) and passes
 * its picture as [preview], shown only while [phase] is scanning. While starting, a waiting bar says the camera is
 * opening; once it has taken longer than the escalation wait (counted from [since]) the words change and [onSlow]
 * (at most two ways out) appear. Paused says so in place of the preview. Failed draws nothing: the host shows its own
 * state. In a short window the frame and the words sit side by side.
 *
 * States: loading (starting, slow), error (rejected), paused
 * Cap: Fill
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitScanner(
    phase: KitScannerPhase,
    instruction: String,
    modifier: Modifier = Modifier,
    rejected: String? = null,
    onSlow: List<KitAction> = emptyList(),
    since: TimeMark? = null,
    preview: @Composable () -> Unit,
) {
    require(onSlow.size <= 2) { "KitScanner.onSlow: at most two ways out (KitScanner.md)." } // F: lib/ui/kit/kit_scanner.dart:80
    if (phase == KitScannerPhase.Failed) return
    val tokens = LocalKitTokens.current
    val short = LocalKitWindow.current.short
    val frame: @Composable (Modifier) -> Unit = { ScannerFrame(phase, instruction, onSlow, since, it, preview) }
    val words: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = Layout.READING_WIDTH.dp)
                    .padding(start = tokens.gutter.dp, top = Space.S4.dp, end = tokens.gutter.dp, bottom = Space.S6.dp),
            ) {
                KitText(instruction, Modifier.fillMaxWidth(), role = TypeRole.Secondary, tone = TextTone.Secondary)
                if (rejected != null) {
                    Spacer(Modifier.height(Space.S3.dp))
                    // LOOK-4, LOOK-5: the failure tone's glyph is the error mark in text1, never danger or attention.
                    KitNotice(rejected, Modifier.fillMaxWidth(), tone = StatusTone.Failure, icon = KitIcons.error)
                }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (short) {
            Row(Modifier.fillMaxSize()) {
                frame(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.verticalScroll(rememberScrollState())) { words() }
                }
            }
        } else {
            val textMax = if (constraints.hasBoundedHeight) scannerTextMax(maxHeight.value).dp else Dp.Infinity
            Column(Modifier.fillMaxSize()) {
                frame(Modifier.weight(1f).fillMaxWidth())
                Box(Modifier.heightIn(max = textMax).verticalScroll(rememberScrollState())) { words() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScannerFrame(
    phase: KitScannerPhase,
    instruction: String,
    onSlow: List<KitAction>,
    since: TimeMark?,
    modifier: Modifier,
    preview: @Composable () -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val style = LocalKitStyle.current
    val scanning = phase == KitScannerPhase.Scanning
    val previewLabel = stringResource(Res.string.kitScannerPreview)
    val radius = StyleResolver.corner(FlutterKitTokens.PANEL_CORNER_RADIUS.toFloat(), style.knobs.corners, style.spec.tokens.sharpRadius)
    val ground = colors.ground
    val scrim: Color? = if (scanning) colors.scrim else null
    val bracket = if (scanning) colors.accent else colors.text3
    Box(
        modifier.clipToBounds().drawBehind { drawRect(ground) }.semantics {
            if (scanning) {
                role = Role.Image
                contentDescription = previewLabel
                stateDescription = instruction
                liveRegion = LiveRegionMode.Polite
            }
        },
    ) {
        if (scanning) Box(Modifier.fillMaxSize().clearAndSetSemantics {}) { preview() }
        Canvas(Modifier.fillMaxSize()) {
            val s = scannerWindowSide(size.width / density, size.height / density, tokens.gutter.toFloat(), Space.S6.toFloat()).dp.toPx()
            if (s <= 0f) return@Canvas
            val window = Rect(size.width / 2 - s / 2, size.height / 2 - s / 2, size.width / 2 + s / 2, size.height / 2 + s / 2) // F: lib/ui/kit/kit_scanner.dart:449
            val corner = min(radius.dp.toPx(), s / 2) // F: lib/ui/kit/kit_scanner.dart:449
            val outer = Path().apply { addRoundRect(RoundRect(window, CornerRadius(corner))) }
            if (scrim != null) {
                val dim = Path.combine(PathOperation.Difference, Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }, outer)
                drawPath(dim, scrim)
            }
            val thickness = Space.S1.dp.toPx()
            val inner = Path().apply { addRoundRect(RoundRect(window.deflate(thickness), CornerRadius(max(0f, corner - thickness)))) }
            val ring = Path.combine(PathOperation.Difference, outer, inner)
            val arm = min(FlutterKitTokens.SCANNER_BRACKET.dp.toPx(), s / 2) // F: lib/ui/kit/kit_scanner.dart:467
            val boxes = Path().apply {
                addRect(Rect(window.left, window.top, window.left + arm, window.top + arm))
                addRect(Rect(window.right - arm, window.top, window.right, window.top + arm))
                addRect(Rect(window.left, window.bottom - arm, window.left + arm, window.bottom))
                addRect(Rect(window.right - arm, window.bottom - arm, window.right, window.bottom))
            }
            drawPath(Path.combine(PathOperation.Intersect, ring, boxes), bracket)
        }
        val centre: (@Composable () -> Unit)? =
            when (phase) {
                KitScannerPhase.Starting -> { { Starting(onSlow, since) } }
                KitScannerPhase.Paused -> {
                    {
                        KitText(
                            stringResource(Res.string.kitScannerPaused),
                            role = TypeRole.Label,
                            tone = TextTone.Secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> null
            }
        if (centre != null) {
            Box(Modifier.fillMaxSize().padding(horizontal = tokens.gutter.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.verticalScroll(rememberScrollState())) { centre() }
            }
        }
    }
}

/** The waiting bar under the window's middle, and the ways out once the camera is slow (Dart `_starting`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Starting(onSlow: List<KitAction>, since: TimeMark?) {
    val escalate = MotionTokens.ESCALATE_AFTER.milliseconds
    var slow by remember(since) { mutableStateOf(since != null && since.elapsedNow() >= escalate) }
    if (since != null && !slow) {
        LaunchedEffect(since) {
            delay((escalate - since.elapsedNow()).coerceAtLeast(Duration.ZERO))
            slow = true
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width((FlutterKitTokens.SCANNER_WINDOW - 2 * Space.S4).dp).semantics { liveRegion = LiveRegionMode.Polite }) { // F: lib/ui/kit/kit_scanner.dart:331
            KitProgressView(
                KitProgress.Waiting(stringResource(if (slow) Res.string.kitScannerSlow else Res.string.kitScannerStarting)),
            )
        }
        if (slow && onSlow.isNotEmpty()) {
            Spacer(Modifier.height(Space.S3.dp))
            // Centred under the words, as tertiary buttons (48 dp targets).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S1.dp, Alignment.CenterHorizontally)) {
                for (action in onSlow) KitButton(action, role = KitButtonRole.Tertiary)
            }
        }
    }
}

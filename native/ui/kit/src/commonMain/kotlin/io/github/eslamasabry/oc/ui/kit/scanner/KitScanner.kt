package io.github.eslamasabry.oc.ui.kit.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.math.floor
import kotlin.math.min

/** Flutter kit_scanner.dart: a square opening with four brackets and a reading block below (beside in short windows).
 * The preview slot owns the camera. This part never sees, retains or announces a decoded value.
 */
@Composable
public fun KitScanner(
    instruction: String,
    previewLabel: String,
    modifier: Modifier = Modifier,
    rejected: String? = null,
    active: Boolean = true,
    preview: @Composable () -> Unit,
) {
    val tokens = LocalKitTokens.current
    val frame: @Composable (Modifier) -> Unit = { frameModifier ->
        ScannerFrame(previewLabel, active, frameModifier, preview)
    }
    val words: @Composable () -> Unit = {
        if (active) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = tokens.gutter.dp, top = Space.S4.dp, end = tokens.gutter.dp, bottom = Space.S6.dp)
            ) {
                KitText(instruction, role = TypeRole.Secondary, tone = TextTone.Secondary)
                if (rejected != null) {
                    Spacer(Modifier.height(Space.S3.dp))
                    KitNotice(rejected, tone = io.github.eslamasabry.oc.ui.design.color.StatusTone.Failure)
                }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (LocalKitWindow.current.short) {
            Row(Modifier.fillMaxSize()) {
                frame(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { words() }
            }
        } else {
            val textMax = maxOf(maxHeight - (160.dp + Space.S6.dp * 2), maxHeight / 2) // F: lib/ui/kit/kit_scanner.dart:140,247
            Column(Modifier.fillMaxSize()) {
                frame(Modifier.weight(1f).fillMaxWidth())
                Box(Modifier.heightIn(max = textMax)) { words() }
            }
        }
    }
}

internal fun scannerSide(width: Float, height: Float, gutter: Float, margin: Float): Float =
    floor(maxOf(0f, min(FlutterKitTokens.SCANNER_WINDOW.toFloat(), min(width - 2 * gutter, height - 2 * margin)))) // F: lib/ui/kit/kit_scanner.dart:426

@Composable
private fun ScannerFrame(label: String, active: Boolean, modifier: Modifier, preview: @Composable () -> Unit) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    Box(modifier.clipToBounds().graphicsLayer { alpha = if (active) 1f else 0f }.background(colors.ground).semantics {
        if (active) {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        }
    }) {
        Box(Modifier.fillMaxSize().clearAndSetSemantics {}) { preview() }
        Canvas(Modifier.fillMaxSize()) {
            val side = scannerSide(size.width, size.height, tokens.gutter.dp.toPx(), Space.S6.dp.toPx())
            if (side <= 0) return@Canvas
            val window = Rect((size.width - side) / 2, (size.height - side) / 2, (size.width + side) / 2, (size.height + side) / 2) // NV: centered square
            val corner = min(FlutterKitTokens.PANEL_CORNER_RADIUS.dp.toPx(), side / 2) // F: lib/ui/kit/kit_scanner.dart:449
            val thickness = Space.S1.dp.toPx()
            val outer = Path().apply { addRoundRect(RoundRect(window, CornerRadius(corner))) }
            val dim = Path.combine(PathOperation.Difference, Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }, outer)
            drawPath(dim, colors.scrim)
            val innerRadius = maxOf(0f, corner - thickness)
            val inner = Path().apply { addRoundRect(RoundRect(window.deflate(thickness), CornerRadius(innerRadius))) }
            val ring = Path.combine(PathOperation.Difference, outer, inner)
            val arm = min(FlutterKitTokens.SCANNER_BRACKET.dp.toPx(), side / 2) // F: lib/ui/kit/kit_scanner.dart:464
            val boxes = Path().apply {
                addRect(Rect(window.left, window.top, window.left + arm, window.top + arm))
                addRect(Rect(window.right - arm, window.top, window.right, window.top + arm))
                addRect(Rect(window.left, window.bottom - arm, window.left + arm, window.bottom))
                addRect(Rect(window.right - arm, window.bottom - arm, window.right, window.bottom))
            }
            drawPath(Path.combine(PathOperation.Intersect, ring, boxes), colors.accent)
        }
    }
}

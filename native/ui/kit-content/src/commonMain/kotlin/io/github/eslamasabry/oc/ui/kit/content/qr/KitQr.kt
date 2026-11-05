package io.github.eslamasabry.oc.ui.kit.content.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.foundation.contentAssert
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitQrTooLong
import org.jetbrains.compose.resources.stringResource

/** Test tag on the code's own box (the Dart `qrKey` default). */
public const val KIT_QR_TAG: String = "kit-qr"

/**
 * A QR code another device can scan (Dart `KitQr`; plan 10 §14.1). Dark modules on a light card with a four-module
 * quiet zone in every theme, because a scanner reads contrast, not palette. Every module is a whole number of physical
 * pixels (LOOK-21). The code takes the width it is given, up to 240 dp, and is square; there is no size parameter.
 * Error correction is fixed at M.
 *
 * [data] is never shown as text, logged or put in semantics (SEC-2): only [semanticsLabel] is spoken and the module
 * pattern is excluded. Modules are always drawn left to right, top to bottom: a mirrored code does not scan.
 *
 * States: code; too long (words, a polite live region)
 * Text: reading (the too-long notice)
 * Cap: Control (240 dp)
 * Motion: none
 * Hidden: none
 * Replaces: KitQr (lib/ui/kit/kit_qr.dart)
 */
@Composable
public fun KitQr(data: String, semanticsLabel: String, modifier: Modifier = Modifier) {
    val payload = remember(data) { QrPayload.of(data) }
    when (payload) {
        QrPayload.Empty -> contentAssert(false) { "KitQr: data must not be empty (B-KIT-026)" }
        QrPayload.TooLong ->
            Row(modifier.kitMaxWidth(CapRole.Control).semantics { liveRegion = LiveRegionMode.Polite }) {
                KitIcon(KitIcons.error, size = KitIconSize.Small)
                Spacer(Modifier.width(Space.S2.dp))
                KitText(stringResource(Res.string.kitQrTooLong), Modifier.weight(1f), role = TypeRole.Secondary)
            }
        is QrPayload.Ready -> QrCode(payload.matrix, semanticsLabel, modifier)
    }
}

/** Host helpers that do not draw. */
public object KitQrs {
    /** Whether [data] fits a code at error correction M, so a host can decide before showing the part. */
    public fun fits(data: String): Boolean = QrPayload.of(data) is QrPayload.Ready
}

@Composable
private fun QrCode(matrix: QrMatrix, label: String, modifier: Modifier) {
    val paper = remember { Graphite.of(false).toKitColors() }
    val hairline = LocalKitColors.current.hairline
    val density = LocalDensity.current
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Control).widthIn(max = QrRules.MAX_SIDE_DP.dp)) {
        val maxPx = with(density) { (if (constraints.hasBoundedWidth) maxWidth else QrRules.MAX_SIDE_DP.dp).toPx() }
        val sidePx = QrRules.sidePx(maxPx, matrix.size, density.density)
        val side: Dp = with(density) { sidePx.toDp() }
        val shape = KitShapeId.Code.shape()
        Canvas(
            Modifier
                .testTag(KIT_QR_TAG)
                .size(side)
                .clip(shape)
                .background(paper.surface2)
                .border(Dp.Hairline, hairline, shape)
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Image
                },
        ) {
            val total = matrix.size + QrRules.QUIET_MODULES * 2
            val module = size.width / total
            val quiet = module * QrRules.QUIET_MODULES
            val ink = paper.text1
            val paint = Paint().apply { color = ink; isAntiAlias = false }
            drawIntoCanvas { canvas ->
            for (row in 0 until matrix.size) {
                for (col in 0 until matrix.size) {
                    if (!matrix.isDark(row, col)) continue
                    canvas.drawRect(androidx.compose.ui.geometry.Rect(quiet + col * module, quiet + row * module, quiet + (col + 1) * module, quiet + (row + 1) * module), paint)
                }
            }
            }
        }
    }
}

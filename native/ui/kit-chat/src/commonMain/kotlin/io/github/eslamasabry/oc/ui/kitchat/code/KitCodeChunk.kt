package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor

/**
 * P12-CHAT-FEEL: one row of a long code block in the transcript. STREAM-FIX publishes a fence of more than 16 lines as
 * chunk rows; each draws its part of ONE box (no seam: [continued] drops the top rounding and padding, [continues]
 * the bottom), lines never wrap and scroll sideways (Flutter's code block), and the first chunk carries Copy at the
 * end of its first line, which copies the whole block ([onCopy] is handed the block by the caller). Displays the
 * redacted source (R18). Always left to right (R15).
 *
 * P-001: only the first line keeps clear of Copy; the other lines run the full width. P-002: the frame and the text
 * style are [codeChunkFrame] and [codeChunkStyle], which the streaming reply block draws with too, so a fence whose
 * rows settle at different moments never shows two differently styled boxes.
 */
@Composable
public fun KitCodeChunk(
    source: String,
    continued: Boolean,
    continues: Boolean,
    modifier: Modifier = Modifier,
    copyLabel: String? = null,
    onCopy: (() -> Unit)? = null,
    copiedLabel: String? = null,
) {
    val redactor = LocalKitRedactor.current
    val display = remember(source, redactor) { redactor.redact(source) }
    val fill = LocalKitColors.current.surface2
    val style = codeChunkStyle()
    val copying = !continued && copyLabel != null && onCopy != null
    val first = remember(display) { display.substringBefore('\n') }
    val rest = remember(display) { display.substringAfter('\n', missingDelimiterValue = "").takeIf { '\n' in display } }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.codeChunkFrame(fill, continued, continues).testTag(CODE_CHUNK_TAG)) {
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                BasicText(
                    first,
                    if (copying) Modifier.padding(end = codeCornerReserve(1).dp) else Modifier,
                    style = style,
                    softWrap = false,
                )
                if (rest != null) BasicText(rest, style = style, softWrap = false)
            }
            if (copying)
                CodeCorner(
                    fill = fill,
                    firstLine = codeFirstLine(style),
                    copyLabel = copyLabel,
                    copiedLabel = copiedLabel,
                    onCopy = onCopy,
                )
        }
    }
}

/**
 * The code surface every transcript code row draws on (P-002): `surface2`, the kit's 8 dp rounding, S3 sides and S2
 * top and bottom; a chunk that [continued] from the row above or [continues] into the row below drops its rounding and
 * padding on that side, so the rows of one fence read as one box. The same values as [KitCodeBlock].
 */
public fun Modifier.codeChunkFrame(fill: Color, continued: Boolean, continues: Boolean): Modifier {
    val top = if (continued) 0.dp else 8.dp
    val bottom = if (continues) 0.dp else 8.dp
    return fillMaxWidth()
        .background(fill, RoundedCornerShape(top, top, bottom, bottom))
        .padding(
            start = Space.S3.dp,
            end = Space.S3.dp,
            top = if (continued) 0.dp else Space.S2.dp,
            bottom = if (continues) 0.dp else Space.S2.dp,
        )
}

/** Code text in a transcript row (P-002): the kit's mono reading role in the primary ink, always left to right. */
@Composable
public fun codeChunkStyle(): TextStyle =
    LocalKitTypography.current.style(TypeRole.Mono).copy(
        color = LocalKitColors.current.text1,
        textDirection = TextDirection.Ltr,
    )

public const val CODE_CHUNK_TAG: String = "kit-code-chunk"

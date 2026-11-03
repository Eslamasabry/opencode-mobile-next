package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor

/**
 * P12-CHAT-FEEL: one row of a long code block in the transcript. STREAM-FIX publishes a fence of more than 16 lines as
 * chunk rows; each draws its part of ONE box (no seam: [continued] drops the top rounding and padding, [continues]
 * the bottom), lines never wrap and scroll sideways (Flutter's code block), and the first chunk carries Copy at its
 * end corner, which copies the whole block ([onCopy] is handed the block by the caller). Displays the redacted source
 * (R18). Always left to right (R15).
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
    val colors = LocalKitColors.current
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val top = if (continued) 0.dp else 8.dp
    val bottom = if (continues) 0.dp else 8.dp
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxWidth()
                .background(colors.surface2, RoundedCornerShape(top, top, bottom, bottom))
                .padding(
                    start = Space.S3.dp,
                    end = Space.S3.dp,
                    top = if (continued) 0.dp else Space.S2.dp,
                    bottom = if (continues) 0.dp else Space.S2.dp,
                )
                .testTag(CODE_CHUNK_TAG),
        ) {
            val copying = !continued && copyLabel != null && onCopy != null
            Box(
                Modifier.fillMaxWidth()
                    .then(if (copying) Modifier.padding(end = 56.dp) else Modifier)
                    .horizontalScroll(rememberScrollState()),
            ) {
                BasicText(display, style = mono.copy(color = colors.text1, textDirection = TextDirection.Ltr), softWrap = false)
            }
            if (copying) CopyCorner(copyLabel!!, copiedLabel) { onCopy!!() }
        }
    }
}

public const val CODE_CHUNK_TAG: String = "kit-code-chunk"

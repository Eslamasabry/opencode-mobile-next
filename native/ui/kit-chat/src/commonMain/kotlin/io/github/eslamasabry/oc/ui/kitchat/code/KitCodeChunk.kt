package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitChatWork
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.withContext

/**
 * P12-CHAT-FEEL: one row of a long code block in the transcript. STREAM-FIX publishes a fence of more than 16 lines as
 * chunk rows; each draws its part of ONE box (no seam: [continued] drops the top rounding, edge and inset, [continues]
 * the bottom), lines never wrap and scroll sideways (Flutter's code block), and the first chunk carries Copy at the
 * end of its first line, which copies the whole block ([onCopy] is handed the block by the caller). Displays the
 * redacted source (R18). Always left to right (R15); Copy keeps the reading direction's end side.
 *
 * C-006: the same look as [KitCodeBlock] (radius 14, `detailsSurface`, light hairline edge, 16 dp start inset, Copy
 * as a 24 dp icon centred on the first line with the text kept S3 clear of its column on every row of the fence).
 * C-019: a chunk of a closed fence is coloured in its [language] like the block (each chunk is tokenised on its own,
 * through the same cache); [open] (still streaming) and [CodeLanguage.Plain] draw plain.
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
    language: CodeLanguage = CodeLanguage.Plain,
    open: Boolean = false,
) {
    val redactor = LocalKitRedactor.current
    val display = remember(source, redactor) { redactor.redact(source) }
    val look = codeFrameLook()
    val colors = KitCodeColors.of(LocalKitColors.current)
    val style = codeChunkStyle()
    // Every row of a copyable fence keeps the control column's room, so the rows' text ends on one line.
    val copyable = copyLabel != null && onCopy != null
    val copying = copyable && !continued
    val tokenizer = LocalKitCodeTokenizer.current
    val work = LocalKitChatWork.current
    val colourable = !open && language != CodeLanguage.Plain
    val tokens by
        produceState<ImmutableList<CodeToken>>(persistentListOf(), display, colourable, language, tokenizer) {
            value =
                if (colourable) withContext(work) { tokenizer.tokenize(display, language) } else persistentListOf()
        }
    val text = remember(display, tokens, colors) { lineText(display, 0, tokens, colors) }
    val (insetTop, insetBottom) = codeLineInsets(codeLineHeight(style))
    Row(
        modifier.codeChunkFrame(look, continued, continues, copyable, insetTop, insetBottom).testTag(CODE_CHUNK_TAG),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                CodeOverflow(colors.fill, colors.gutter) {
                    Column { BasicText(text, style = style, softWrap = false) }
                }
            }
        }
        if (copyable) {
            Spacer(Modifier.width(Space.S3.dp))
            if (copying) {
                // The text's top inset already centres line one on the 48 dp target: lift the column by it.
                CodeControls(
                    copyLabel = copyLabel,
                    copiedLabel = copiedLabel,
                    onCopy = onCopy,
                    modifier = Modifier.offsetUp(insetTop),
                )
            } else {
                Spacer(Modifier.width(FlutterKitTokens.MIN_TARGET.dp))
            }
        }
    }
}

/**
 * The code surface every transcript code row draws on (P-002, C-006): [codeSurface] plus the insets of
 * [KitCodeBlock]: 16 dp at the start; a [copyable] fence ends 1 dp from the edge (its control column brings the rest)
 * and its first and last rows sit S1 plus the centring insets ([insetTop], [insetBottom]) from the edge, as the block
 * does with Copy on line one; otherwise 16 dp all round. A side that [continued] or [continues] has no inset, so the
 * rows of one fence read as one box.
 */
public fun Modifier.codeChunkFrame(
    look: CodeFrameLook,
    continued: Boolean,
    continues: Boolean,
    copyable: Boolean = false,
    insetTop: Float = 0f,
    insetBottom: Float = 0f,
): Modifier {
    val padding =
        PaddingValues(
            start = Space.S4.dp,
            end = (if (copyable) CODE_COPY_END else Space.S4).dp,
            top = if (continued) 0.dp else if (copyable) (Space.S1 + insetTop).dp else Space.S4.dp,
            bottom = if (continues) 0.dp else if (copyable) (Space.S1 + insetBottom).dp else Space.S4.dp,
        )
    return fillMaxWidth().codeSurface(look, continued, continues).padding(padding)
}

/** Code text in a transcript row (P-002): the kit's mono reading role in the primary ink, always left to right. */
@Composable
public fun codeChunkStyle(): TextStyle =
    LocalKitTypography.current.style(TypeRole.Mono).copy(
        color = LocalKitColors.current.text1,
        textDirection = TextDirection.Ltr,
    )

public const val CODE_CHUNK_TAG: String = "kit-code-chunk"

/** Draws a part [dp] higher and takes that much less room (the Copy column on a chunk's first line). */
private fun Modifier.offsetUp(dp: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val up = dp.dp.roundToPx()
    layout(placeable.width, (placeable.height - up).coerceAtLeast(0)) { placeable.place(0, -up) }
}

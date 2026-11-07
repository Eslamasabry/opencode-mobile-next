package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitWrapLines
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * C-006: the code glyphs are the kit's action size, 24 dp in a 48 dp target (`KitIconButton.copy`, size 24;
 * `_WrapToggle`, `lib/ui/kit/kit_code_block_parts.dart:247-250`).
 */
private const val CODE_GLYPH = 24

/**
 * The room after the Copy target so the copy glyph's ink ends S4 inside the block, on the rail its text starts on:
 * `KitOptical.endPad(copy, 24, S4 - (48 - 24) / 2)` = max(0, 4 - 24 × 0.125) = 1 dp
 * (`lib/ui/kit/kit_code_block.dart:606-611`, `lib/ui/kit/kit_optical.dart:32`).
 */
internal const val CODE_COPY_END: Int = 1

/**
 * C-006: how the code surface looks (`kit_code_block.dart:263-275`, `:615-633`): `detailsSurface` (dark: surface1;
 * light: the page's own ground) and, in light only, a hairline edge in text1 at 16 % so Copy and Wrap never float over
 * bare page. Corners are `codeRadius` (14).
 */
@Immutable
public data class CodeFrameLook(val fill: Color, val edge: Color, val edgeWidth: Dp) {
    public val radius: Dp
        get() = FlutterKitTokens.CODE_RADIUS.dp
}

/** The [CodeFrameLook] of the current theme. */
@Composable
public fun codeFrameLook(): CodeFrameLook {
    val c = LocalKitColors.current
    return CodeFrameLook(
        fill = codeFill(c),
        edge = if (c.dark) Color.Transparent else c.text1.copy(alpha = 0.16f),
        edgeWidth = kitHairline(),
    )
}

/**
 * The code surface with its rounding and light-mode edge. A part that [continued] from the row above or
 * [continues] into the row below leaves that side open (no rounding, no edge), so a fence drawn as several rows reads as one box.
 */
public fun Modifier.codeSurface(look: CodeFrameLook, continued: Boolean = false, continues: Boolean = false): Modifier {
    val top = if (continued) 0.dp else look.radius
    val bottom = if (continues) 0.dp else look.radius
    return background(look.fill, RoundedCornerShape(top, top, bottom, bottom)).codeEdge(look, continued, continues)
}

private fun Modifier.codeEdge(look: CodeFrameLook, continued: Boolean, continues: Boolean): Modifier =
    if (look.edge.alpha == 0f) {
        this
    } else {
        drawBehind {
            val w = look.edgeWidth.toPx().coerceAtLeast(0.5f)
            val r = look.radius.toPx()
            // An open side draws its round rect past the row and clips it: only the straight sides show there.
            val top = if (continued) -r - w else w / 2
            val bottom = if (continues) size.height + r + w else size.height - w / 2
            clipRect {
                drawRoundRect(
                    look.edge,
                    Offset(w / 2, top),
                    Size(size.width - w, bottom - top),
                    CornerRadius(r - w / 2),
                    style = Stroke(w),
                )
            }
        }
    }

/**
 * C-006: the room a block's text has when Copy (and Wrap) trail its first line, in dp: the block less its start inset
 * (S4), its end inset (S1), the 48 dp control column and the S3 gap before it. Flutter measures overflow against this
 * whether or not Copy shows (`_KitCodeBlockState._plan`, `kit_code_block.dart:704-712`).
 */
internal fun codeTextRoom(blockWidth: Float): Float =
    blockWidth - Space.S4 - Space.S1 - FlutterKitTokens.MIN_TARGET - Space.S3

/** Whether a block whose widest line is [longest] wide overflows [room] (both in the same unit; half-unit slack). */
internal fun codeOverflows(longest: Float, room: Float): Boolean = longest > room + 0.5f

/**
 * The inset above and below the lines while Copy sits on the first line, so one line is centred on the 48 dp target
 * and a one-line block is exactly one target tall (`kit_code_block.dart:1003-1013`): (top, bottom) in dp.
 */
internal fun codeLineInsets(lineHeight: Float): Pair<Float, Float> {
    val room = (FlutterKitTokens.MIN_TARGET - lineHeight).coerceAtLeast(0f)
    val top = kotlin.math.floor(room / 2)
    return top to room - top
}

/** The line height of [style] in dp (19 for mono at 1.0); 19 when the style has none. */
@Composable
internal fun codeLineHeight(style: TextStyle): Float =
    with(androidx.compose.ui.platform.LocalDensity.current) {
        style.lineHeight.takeIf { it.isSp }?.toDp()?.value
    } ?: 19f

/**
 * C-006: Copy at the end of the first line and Wrap **under** it, each a 24 dp glyph in a 48 dp target
 * (`kit_code_block.dart:584-592`). Copy draws in text1 and turns into a check for [COPIED_MS]; its name then says
 * [copiedLabel] (announced politely). Wrap is text2, accent while on. The column sits on the reading direction's end
 * side (it is outside the block's left-to-right text box, as in Flutter).
 */
@Composable
internal fun CodeControls(
    copyLabel: String?,
    copiedLabel: String?,
    onCopy: (() -> Unit)?,
    wrapOn: Boolean? = null,
    onWrap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val copying = copyLabel != null && onCopy != null
    val wrapping = wrapOn != null && onWrap != null
    if (!copying && !wrapping) return
    var taps by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        copied = true
        delay(COPIED_MS)
        copied = false
    }
    DisableSelection {
        Column(modifier.testTag(CODE_CORNER_TAG)) {
            if (copying) {
                val label = if (copied && copiedLabel != null) copiedLabel else copyLabel!!
                KitIconButton(
                    if (copied) KitIcons.check else KitIcons.copy,
                    label,
                    {
                        onCopy!!()
                        taps++
                    },
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    tone = TextTone.Primary,
                    size = CODE_GLYPH,
                )
            }
            if (wrapping) {
                KitIconButton(
                    KitIcons.wrapText,
                    stringResource(Res.string.kitWrapLines),
                    onWrap!!,
                    Modifier.semantics {
                        toggleableState = if (wrapOn == true) ToggleableState.On else ToggleableState.Off
                    },
                    tone = if (wrapOn == true) TextTone.Accent else TextTone.Secondary,
                    size = CODE_GLYPH,
                )
            }
        }
    }
}

/** `detailsSurface`: surface1 in dark, the page's ground in light (`FlutterKitTokenRoles.detailsSurface`). */
internal fun codeFill(c: io.github.eslamasabry.oc.ui.kit.theme.KitColors): Color = if (c.dark) c.surface1 else c.ground

/** How long Copy shows its check after a tap. */
internal const val COPIED_MS: Long = 2_000

/** The column holding Copy and Wrap, for tests. */
public const val CODE_CORNER_TAG: String = "kit-code-corner"

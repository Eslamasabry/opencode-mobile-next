package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlinx.coroutines.delay

/** The copy control's test handle (Dart `ValueKey('kit-code-copy')`, kit_code_block.dart:775). */
public const val CODE_COPY_TAG: String = "kit-code-copy"

/**
 * True for `KitMotion.copiedHold` after each [taps] change past zero: the copy glyph turns into a check for that long
 * (Dart `KitIconButton.copy`, kit_icon_button.dart: `_copiedTimer`).
 */
@Composable
private fun copiedFor(taps: Int): Boolean {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        copied = true
        delay(MotionTokens.COPIED_HOLD.toLong()) // the check's hold, KitMotion.copiedHold
        copied = false
    }
    return copied
}

/**
 * The block's Copy as an icon (Dart `_copyIcon`: `KitIconButton.copy`, a 24 dp glyph in `text1` on a 48 dp target; the
 * glyph turns into a check for `KitMotion.copiedHold`).
 * [label] names it (the tooltip and the spoken name); after a tap it says [copiedLabel] when given, politely.
 */
@Composable
internal fun CodeCopyIcon(label: String, copiedLabel: String?, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    var taps by remember { mutableIntStateOf(0) }
    val copied = copiedFor(taps)
    // The kit glyph is the Phosphor font glyph Flutter's `KitIconButton.copy` draws; `KitIconButton` itself still
    // draws the glyph's vector path, whose edges land a pixel off the font's.
    CodeGlyphTarget(
        if (copied) KitIcons.check else KitIcons.copy,
        if (copied && copiedLabel != null) copiedLabel else label,
        TextTone.Primary,
        {
            onCopy()
            taps++
        },
        modifier.testTag(CODE_COPY_TAG).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The header's labelled Copy (Dart `KitButton.fromAction(KitAction.copy(label: copyLabel), role: tertiary)`): the copy
 * glyph and the caller's words ("Copy commands"); the glyph turns into a check after a tap.
 */
@Composable
internal fun CodeCopyButton(words: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    var taps by remember { mutableIntStateOf(0) }
    val copied = copiedFor(taps)
    // The header does not pull the text action back to the block's edge: its words keep the button's own side
    // inset (`KitButton.tertiaryInset`).
    KitButton(
        words,
        {
            onCopy()
            taps++
        },
        modifier.padding(horizontal = Space.S2.dp).testTag(CODE_COPY_TAG),
        role = KitButtonRole.Tertiary,
        icon = if (copied) KitIcons.check else KitIcons.copy,
    )
}

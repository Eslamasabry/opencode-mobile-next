package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Constraints
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.defaultTone
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** The whole-word cut of [KitWordText] (Dart `KitWordText.cut`), with the line test handed in so it runs anywhere. */
public object KitWordCut {
    private val GAPS = Regex("\\s+") // F: lib/ui/kit/kit_word_text.dart:51
    private val TRAILING = Regex("[\\s,;:.!?·–—…-]+$") // F: lib/ui/kit/kit_word_text.dart:60

    /**
     * [text] cut after the last whole word that, with "…", still [fits]; [text] itself when it fits, or when no word
     * does (a single word wider than the line falls back to the framework's own ellipsis).
     */
    public fun cut(text: String, fits: (String) -> Boolean): String {
        if (fits(text)) return text
        val ends = GAPS.findAll(text).map { it.range.first }.filter { it > 0 }.toList()
        var low = 0
        var high = ends.size - 1
        var best: String? = null
        while (low <= high) {
            val mid = (low + high) ushr 1 // F: lib/ui/kit/kit_word_text.dart:58
            val candidate = text.substring(0, ends[mid]).replace(TRAILING, "") + "…"
            if (fits(candidate)) {
                best = candidate
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best ?: text
    }
}

/**
 * Words in a [KitText] role that end at a whole word when they do not fit [maxLines]: "…and retry when…", never
 * "…and retry w…" (owner, 2026-10-22; Dart `KitWordText`). A single word wider than the line falls back to the
 * framework's own ellipsis. A screen reader hears the words in full.
 *
 * States: none — it shows the words it is given. Cap: reading (720).
 */
@Composable
public fun KitWordText(
    text: String,
    maxLines: Int,
    modifier: Modifier = Modifier,
    role: TypeRole = TypeRole.Body,
    tone: TextTone = defaultTone(role),
) {
    require(maxLines > 0) // F: lib/ui/kit/kit_word_text.dart:19
    val measurer = rememberTextMeasurer()
    // Flutter lays the words in the ambient direction (kit_word_text.dart:83): an English line in an Arabic layout
    // is a right-to-left paragraph, its "…" at the start.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val style: TextStyle =
        LocalKitTypography.current.style(role).copy(
            color = LocalKitColors.current.tone(tone),
            textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr,
            textAlign = TextAlign.Start,
        )
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth()) {
        val width = constraints.maxWidth
        val shown =
            if (width == Constraints.Infinity) text
            else
                KitWordCut.cut(text) { candidate ->
                    !measurer.measure(candidate, style, maxLines = maxLines, constraints = Constraints(maxWidth = width))
                        .multiParagraph.didExceedMaxLines
                }
        val spoken = if (shown == text) Modifier else Modifier.clearAndSetSemantics { contentDescription = text }
        BasicText(shown, spoken.fillMaxWidth(), style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

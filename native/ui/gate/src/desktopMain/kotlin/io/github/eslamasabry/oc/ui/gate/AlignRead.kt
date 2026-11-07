package io.github.eslamasabry.oc.ui.gate

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnitType
import io.github.eslamasabry.oc.ui.gate.AlignRules.Align
import io.github.eslamasabry.oc.ui.gate.AlignRules.Box
import io.github.eslamasabry.oc.ui.gate.AlignRules.Kind
import io.github.eslamasabry.oc.ui.gate.AlignRules.Node
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.KitLayoutMarks

/**
 * Reads the alignment model ([AlignRules.Node]) from an unmerged Compose semantics tree. A KitIcon is a one-glyph
 * text node in the Phosphor font (a code point in the private use area); its asked-for size is the glyph's font size.
 */
internal object AlignRead {
    private const val PUA_FIRST = 0xE000 // NV: Unicode private use area, where the Phosphor fonts put their glyphs
    private const val PUA_LAST = 0xF8FF // NV: end of the private use area

    fun nodes(roots: List<SemanticsNode>): List<Node> {
        val out = ArrayList<Node>()
        fun walk(n: SemanticsNode, parent: Int?, d: Float) {
            out += node(n, parent, d)
            n.children.forEach { walk(it, n.id, d) }
        }
        // Window px over the root's density: a scene that scales a layer (FlutterCapture draws at 3x and scales by a
        // third) is measured where it lands, not in its own px.
        roots.forEach { walk(it, null, it.layoutInfo.density.density) }
        return out
    }

    private fun node(n: SemanticsNode, parent: Int?, d: Float): Node {
        val c = n.config
        val at = n.positionInWindow
        val coordinates = n.layoutInfo.coordinates
        val far = if (coordinates.isAttached) {
            coordinates.localToWindow(Offset(n.size.width.toFloat(), n.size.height.toFloat()))
        } else {
            Offset(at.x + n.size.width, at.y + n.size.height)
        }
        val box = Box(at.x / d, at.y / d, far.x / d, far.y / d)
        // window px per local px: 1 unless a layer scales the node
        val k = if (n.size.width > 0) (far.x - at.x) / n.size.width else 1f
        val words = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        val isText = SemanticsProperties.Text in c && SemanticsProperties.EditableText !in c
        val glyph = isText && words.length == 1 && words[0].code in PUA_FIRST..PUA_LAST
        val edge = c.getOrNull(KitLayoutMarks.Edge)
        val base = Node(
            id = n.id,
            parent = parent,
            box = box,
            shown = shown(n, d),
            kind = kindOf(isText, glyph),
            words = words.ifBlank { c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty() },
            tag = c.getOrNull(SemanticsProperties.TestTag),
            tap = SemanticsActions.OnClick in c,
            bleed = edge == KitEdge.Bleed,
            chrome = edge == KitEdge.Chrome,
            scroller = SemanticsProperties.VerticalScrollAxisRange in c || SemanticsProperties.CollectionInfo in c,
            sideScroller = SemanticsProperties.HorizontalScrollAxisRange in c,
            list = KitLayoutMarks.List in c,
            floating = KitLayoutMarks.Floating in c,
            field = SemanticsProperties.EditableText in c || SemanticsActions.SetText in c,
            contentEdge = KitLayoutMarks.ContentEdge in c,
        )
        val layout = if (isText) layoutOf(n) else null
        return if (layout == null) base else withText(base, layout, d / k, glyph)
    }

    private fun kindOf(isText: Boolean, glyph: Boolean): Kind = when {
        glyph -> Kind.Glyph
        isText -> Kind.Text
        else -> Kind.Other
    }

    /** The part of [n] inside its ancestors and the window, in dp; null when none of it shows. */
    private fun shown(n: SemanticsNode, d: Float): Box? {
        val v = n.boundsInWindow
        return if (v.width <= 0f || v.height <= 0f) null else Box(v.left / d, v.top / d, v.right / d, v.bottom / d)
    }

    /** [base] with its words' ink, first line, cut and (for a glyph) asked size from [l]. */
    private fun withText(base: Node, l: TextLayoutResult, d: Float, glyph: Boolean): Node {
        val lines = 0 until l.lineCount
        val box = base.box
        return base.copy(
            align = alignOf(l),
            inkL = lines.minOfOrNull { l.getLineLeft(it) }?.let { (box.l + it / d).coerceIn(box.l, box.r) },
            inkR = lines.maxOfOrNull { l.getLineRight(it) }?.let { (box.l + it / d).coerceIn(box.l, box.r) },
            line0 = if (l.lineCount > 0) box.t + l.getLineTop(0) / d to box.t + l.getLineBottom(0) / d else null,
            lines = l.lineCount,
            ellipsized = lines.any { l.isLineEllipsized(it) },
            glyph = if (glyph) glyphSize(l) else null,
        )
    }

    /** Right-to-left words (an Arabic shot, which the gate audits in a left-to-right window) start on the right. */
    private fun alignOf(l: TextLayoutResult): Align = when {
        l.layoutInput.style.textAlign == TextAlign.Center -> Align.Centre
        l.layoutInput.style.textAlign in listOf(TextAlign.End, TextAlign.Right) -> Align.End
        l.lineCount > 0 && l.getParagraphDirection(0) == ResolvedTextDirection.Rtl -> Align.End
        else -> Align.Start
    }

    /** The glyph's asked-for size in dp: its font size (KitIcon sets font size = box size, line height 1). */
    private fun glyphSize(l: TextLayoutResult): Float? {
        val size = l.layoutInput.style.fontSize
        return if (size.type == TextUnitType.Sp) size.value * l.layoutInput.density.fontScale else null
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult? {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val results = ArrayList<TextLayoutResult>()
        return if (action(results)) results.firstOrNull() else null
    }
}

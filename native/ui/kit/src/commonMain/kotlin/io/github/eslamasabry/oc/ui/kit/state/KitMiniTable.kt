package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlin.math.ceil
import kotlin.math.min

/** One row of a [KitMiniTable] (Dart `KitMiniTableRow`): a cell per column. A short row is padded. */
@Immutable
public data class KitMiniTableRow(val cells: List<String>)

/** The table's rules, apart from the drawing (Dart `KitMiniTable`). */
public object KitMiniTableRules {
    public const val MAX_COLUMNS: Int = 6 // F: lib/ui/kit/kit_mini_table.dart:36
    public const val MAX_ROWS: Int = 20 // F: lib/ui/kit/kit_mini_table.dart:37

    /** The widest a cell grows before its words wrap, in dp. */
    public const val MAX_CELL_WIDTH: Float = 200f // F: lib/ui/kit/kit_mini_table.dart:40

    private val NUMBER = Regex("^[\\s+\\-−]?[\\d.,]+\\s*[%a-zA-Z]{0,3}$") // F: lib/ui/kit/kit_mini_table.dart:61

    /** A column whose cells are all numbers ("12", "3.5 MB", "40%") aligns to the end with tabular figures. */
    public fun numeric(column: Int, rows: List<KitMiniTableRow>): Boolean =
        rows.isNotEmpty() && rows.all { column < it.cells.size && NUMBER.matches(it.cells[column].trim()) }

    /** A column's width from its widest words, never wider than a cell may be, plus the cell's side padding. */
    public fun columnWidth(widestWords: Float, cellPad: Float): Float =
        min(ceil(widestWords) + 1, MAX_CELL_WIDTH - cellPad) + cellPad // F: lib/ui/kit/kit_mini_table.dart:143-147

    /** A roomy place stretches the columns evenly; a tight one scrolls at natural width. */
    public fun stretch(natural: Float, available: Float): Float = if (natural < available) available / natural else 1f
}

/**
 * A small table (Dart `KitMiniTable`): a header row over up to 20 rows of up to 6 columns, for an agent's results
 * ("File · Added · Removed"). Cells size to their words (a long cell wraps at 200 dp); when the table is wider than its
 * place it scrolls sideways inside its own panel and never widens the page. A column whose cells are all numbers aligns
 * to the end with tabular figures. The header row is quiet text on a `surface2` band over a hairline.
 *
 * States: none (it draws the cells it is given)
 * Text: reading
 * Cap: List
 * Motion: none
 * Hidden: none
 * Replaces: KitMiniTable, KitMiniTableRow (lib/ui/kit/kit_mini_table.dart)
 */
@Composable
public fun KitMiniTable(
    columns: List<String>,
    rows: List<KitMiniTableRow>,
    modifier: Modifier = Modifier,
    semanticsLabel: String? = null,
) {
    val cols = columns.take(KitMiniTableRules.MAX_COLUMNS)
    if (cols.isEmpty()) return
    val body = rows.take(KitMiniTableRules.MAX_ROWS)
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val measurer = rememberTextMeasurer()
    val numeric = cols.indices.map { KitMiniTableRules.numeric(it, body) }
    val cellPad = Space.S3 * 2f // F: lib/ui/kit/kit_mini_table.dart:103
    fun styleOf(header: Boolean, column: Int) =
        typography.style(if (header) TypeRole.Label else TypeRole.Body)
            .let { if (numeric[column]) it.copy(fontFeatureSettings = "tnum") else it }
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun measure(text: String, header: Boolean, column: Int): Float =
        with(density) { measurer.measure(text, styleOf(header, column), maxLines = 1).size.width.toDp().value }
    val widths = cols.indices.map { c ->
        var widest = measure(cols[c], true, c)
        body.forEach { if (c < it.cells.size) widest = maxOf(widest, measure(it.cells[c], false, c)) }
        KitMiniTableRules.columnWidth(widest, cellPad)
    }
    val hairline = kitHairline()
    val rule = colors.hairline
    val band = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Square, SurfaceLevel.Surface2, ownFill = colors.surface2))

    @Composable
    fun Line(cells: List<String>, scale: Float, header: Boolean) {
        val ruled = Modifier.drawBehind {
            val y = size.height - hairline.toPx() * .5f // NV: half the stroke, so the rule sits on the row bottom edge (Dart Border bottom)
            drawLine(rule, Offset(0f, y), Offset(size.width, y), hairline.toPx())
        }
        Row(
            (if (header) Modifier.then(band.modifier) else Modifier).then(ruled).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Top,
        ) {
            cols.indices.forEach { c ->
                Cell(cells.getOrElse(c) { "" }, (widths[c] * scale).dp, header, numeric[c])
            }
        }
    }

    KitSurface(
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .semantics { semanticsLabel?.let { contentDescription = it } }
            .testTag("kit-mini-table"),
        padding = KitSurfacePadding.None,
        outlined = true,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val natural = widths.sum()
            val scale = KitMiniTableRules.stretch(natural, maxWidth.value)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Line(cols, scale, header = true)
                body.forEach { Line(it.cells, scale, header = false) }
            }
        }
    }
}

@Composable
private fun Cell(text: String, width: Dp, header: Boolean, numeric: Boolean) {
    // Start and end follow the page's direction, not the cell's words (a path in an Arabic table still sits right).
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    KitText(
        text,
        Modifier.width(width).padding(horizontal = Space.S3.dp, vertical = Space.S2.dp), // F: lib/ui/kit/kit_mini_table.dart:155-158
        role = if (header) TypeRole.Label else TypeRole.Body,
        tone = if (header) TextTone.Secondary else TextTone.Primary,
        textAlign = if (numeric != rtl) TextAlign.Right else TextAlign.Left,
        tabular = numeric,
    )
}

package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** Rows read to size the columns (Dart `_measuredRows`). */
private const val MEASURED_ROWS = 100 // F: lib/ui/kit/kit_viewer_bodies.dart:289

/** Characters of a cell read to size its column (Dart `_measuredChars`). */
private const val MEASURED_CHARS = 40 // F: lib/ui/kit/kit_viewer_bodies.dart:292

/** The widest a column's words get (Dart `KitLayout.readingWidth / 3`). */
private const val COLUMN_MAX = 720f / 3 // F: lib/ui/kit/kit_layout.dart:37 lib/ui/kit/kit_viewer_bodies.dart:322

/**
 * CSV/TSV cells as a table (Dart `_KitTable`, kit_viewer_bodies.dart): each column as wide as its longest of the
 * first 100 cells (40 characters read), held between two touch targets and a third of the reading width, plus the
 * cell's space3 sides; one row height minimum; the header in the label role, cells in the secondary role, two lines
 * at most; a hairline rule under each row. The header stays while the rows scroll; the whole table scrolls sideways
 * when it is wider than the viewer. Column order never mirrors; cell words keep their own direction.
 */
@Composable
public fun KitViewerTableBody(
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    header: Boolean = true,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val style = LocalKitTypography.current.style(TypeRole.Secondary)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widths = remember(rows, style, density) {
        val columns = rows.maxOfOrNull { it.size } ?: 0
        val longest = Array(columns) { "" }
        rows.take(MEASURED_ROWS).forEach { row ->
            row.forEachIndexed { c, cell ->
                val cut = if (cell.length > MEASURED_CHARS) cell.substring(0, MEASURED_CHARS) else cell
                if (cut.length > longest[c].length) longest[c] = cut
            }
        }
        val min = tokens.minTarget * 2f // F: lib/ui/kit/kit_viewer_bodies.dart:321
        longest.map { text ->
            val w = with(density) { measurer.measure(text, style, maxLines = 1).size.width.toDp().value }
            kotlin.math.ceil(w.coerceIn(min, COLUMN_MAX) + Space.S3 * 2f).dp // F: lib/ui/kit/kit_viewer_bodies.dart:334
        }
    }
    val natural = widths.fold(0.dp) { a, b -> a + b }
    val rule = colors.hairline
    val hairline = kitHairline()

    @Composable
    fun TableRow(cells: List<String>, isHeader: Boolean) {
        Row(
            Modifier.heightIn(min = tokens.rowHeightOne.dp) // F: lib/ui/kit/kit_viewer_bodies.dart:357
                .drawBehind {
                    val y = size.height - hairline.toPx() / 2
                    drawLine(rule, Offset(0f, y), Offset(size.width, y), hairline.toPx())
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            widths.forEachIndexed { c, width ->
                val cell = cells.getOrNull(c).orEmpty()
                Box(
                    Modifier.width(width)
                        .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp) // F: lib/ui/kit/kit_viewer_bodies.dart:366-369
                        .clearAndSetSemantics {
                            contentDescription = cell
                            if (isHeader) heading()
                        },
                ) {
                    KitText(
                        "⁨$cell⁩", // first-strong isolate: each cell shapes its own words
                        role = if (isHeader) TypeRole.Label else TypeRole.Secondary, // F: lib/ui/kit/kit_viewer_bodies.dart:376-378
                        tone = TextTone.Primary,
                        maxLines = 2, // F: lib/ui/kit/kit_viewer_bodies.dart:380
                    )
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.fillMaxSize().testTag("kit-viewer-table")) {
            val width = maxOf(maxWidth, natural)
            val height = maxHeight
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(width).height(height)) {
                    val hasHeader = header && rows.isNotEmpty()
                    if (hasHeader) TableRow(rows.first(), true)
                    val start = if (hasHeader) 1 else 0
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        items((rows.size - start).coerceAtLeast(0)) { TableRow(rows[it + start], false) }
                    }
                }
            }
        }
    }
}

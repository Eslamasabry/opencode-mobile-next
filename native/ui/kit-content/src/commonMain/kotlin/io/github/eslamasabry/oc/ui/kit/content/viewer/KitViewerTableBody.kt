package io.github.eslamasabry.oc.ui.kit.content.viewer

import io.github.eslamasabry.oc.ui.kit.frame.kitPageListState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlin.math.ceil

/**
 * CSV/TSV cells as a listing, with a fixed header and lazy rows. Column order never mirrors; cell
 * words keep bidi.
 */
@Composable
public fun KitViewerTableBody(
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    header: Boolean = true,
) {
    val columns = remember(rows) { rows.maxOfOrNull { it.size } ?: 0 }
    val widths =
        remember(rows) {
            List(columns) { c ->
                (rows.take(100).maxOfOrNull { it.getOrNull(c)?.length?.coerceAtMost(40) ?: 0 } ?: 0)
                    .times(8)
                    .coerceIn(96, 240)
                    .dp
            }
        }
    val colors = LocalKitColors.current
    @Composable
    fun TableRow(cells: List<String>, isHeader: Boolean) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (isHeader) Modifier.background(colors.surface2) else Modifier)
        ) {
            repeat(columns) { c ->
                KitText(
                    "⁨${cells.getOrNull(c).orEmpty()}⁩",
                    Modifier.width(widths[c])
                        .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp)
                        .then(if (isHeader) Modifier.semantics { heading() } else Modifier),
                    role = if (isHeader) TypeRole.Label else TypeRole.Body,
                )
            }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(widths.fold(0.dp) { a, b -> a + b })) {
                if (header && rows.isNotEmpty()) TableRow(rows.first(), true)
                LazyColumn(Modifier.weight(1f), state = kitPageListState()) {
                    val start = if (header && rows.isNotEmpty()) 1 else 0
                    items((rows.size - start).coerceAtLeast(0)) {
                        TableRow(rows[it + start], false)
                    }
                }
            }
        }
    }
}

/** How many rows are measured to size the columns (Dart `_measuredRows`). */
private const val MEASURED_ROWS = 100 // F: lib/ui/kit/kit_viewer_bodies.dart:293

/** Cells longer than this many characters do not widen their column (Dart `_measuredChars`). */
private const val MEASURED_CHARS = 40 // F: lib/ui/kit/kit_viewer_bodies.dart:296

/** A column is at most a third of the reading width (Dart `KitLayout.readingWidth / 3`). */
private const val MAX_COLUMN_SHARE = 3 // F: lib/ui/kit/kit_viewer_bodies.dart:322

/**
 * A delimited file as a table (Dart `_KitTable`): the file's column order left to right whatever the locale, each
 * column as wide as its longest measured cell within two touch targets and a third of the reading width, a rule under
 * every row, the header row fixed while the rows scroll, one sideways scroller for the whole table.
 */
@Composable
internal fun ViewerTable(rows: List<List<String>>, header: Boolean) {
    val tokens = LocalKitTokens.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val secondary = LocalKitTypography.current.style(TypeRole.Secondary)
    val widths =
        remember(rows, secondary, density) {
            val columns = rows.maxOfOrNull { it.size } ?: 0
            val longest = MutableList(columns) { "" }
            for (row in rows.take(MEASURED_ROWS)) {
                row.forEachIndexed { c, raw ->
                    val cell = if (raw.length > MEASURED_CHARS) raw.substring(0, MEASURED_CHARS) else raw
                    if (cell.length > longest[c].length) longest[c] = cell
                }
            }
            val min = tokens.minTarget * 2f // F: lib/ui/kit/kit_viewer_bodies.dart:321
            val max = Layout.READING_WIDTH / MAX_COLUMN_SHARE.toFloat()
            longest.map { text ->
                val w = with(density) { measurer.measure(text, secondary, softWrap = false, maxLines = 1).size.width.toDp().value }
                ceil(w.coerceIn(min, max) + Space.S3 * 2f).dp // F: lib/ui/kit/kit_viewer_bodies.dart:334
            }
        }
    val natural = widths.fold(0.dp) { sum, w -> sum + w }
    val head = if (header && rows.isNotEmpty()) rows.first() else null
    val body = if (head == null) rows else rows.drop(1)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = maxOf(maxWidth, natural)
            val height = maxHeight
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(width).height(height)) {
                    if (head != null) ViewerTableRow(head, widths, header = true)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), state = kitPageListState()) {
                        items(body.size) { ViewerTableRow(body[it], widths, header = false) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerTableRow(cells: List<String>, widths: List<androidx.compose.ui.unit.Dp>, header: Boolean) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val rule = kitHairline()
    Row(
        Modifier.fillMaxWidth().heightIn(min = tokens.rowHeightOne.dp).drawBehind {
            // The rule under a row is the style's divider at the hairline width (Dart `KitSurfaceLook.divider`).
            val h = rule.toPx()
            drawRect(colors.hairline, androidx.compose.ui.geometry.Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width, h))
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        widths.forEachIndexed { c, w ->
            val cell = cells.getOrElse(c) { "" }
            Box(
                Modifier.width(w).padding(horizontal = Space.S3.dp, vertical = Space.S2.dp)
                    .semantics(mergeDescendants = true) { if (header) heading() },
            ) {
                KitText(
                    "⁨$cell⁩", // KitBidi.auto: each cell isolated so its own words shape correctly
                    role = if (header) TypeRole.Label else TypeRole.Secondary,
                    tone = TextTone.Primary,
                    maxLines = 2, // F: lib/ui/kit/kit_viewer_bodies.dart:381
                )
            }
        }
    }
}

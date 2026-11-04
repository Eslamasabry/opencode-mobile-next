package io.github.eslamasabry.oc.ui.kit.content.viewer

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
                LazyColumn(Modifier.weight(1f)) {
                    val start = if (header && rows.isNotEmpty()) 1 else 0
                    items((rows.size - start).coerceAtLeast(0)) {
                        TableRow(rows[it + start], false)
                    }
                }
            }
        }
    }
}

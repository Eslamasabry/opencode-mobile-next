package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import kotlinx.collections.immutable.ImmutableList

/** Bounded, literal CSV/TSV cells: no markup or formulas execute and column order never mirrors. */
@Composable
public fun KitViewerTableBody(
    rows: ImmutableList<ImmutableList<String>>,
    modifier: Modifier = Modifier,
) {
    val columns = rows.maxOfOrNull { it.size } ?: 0
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SelectionContainer(modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                itemsIndexed(rows) { index, row ->
                    Row {
                        for (column in 0 until columns) {
                            KitText(
                                row.getOrNull(column).orEmpty(),
                                Modifier.width(200.dp).padding(Space.S2.dp),
                                role = if (index == 0) TypeRole.RowTitle else TypeRole.Body,
                            )
                        }
                    }
                }
            }
        }
    }
}

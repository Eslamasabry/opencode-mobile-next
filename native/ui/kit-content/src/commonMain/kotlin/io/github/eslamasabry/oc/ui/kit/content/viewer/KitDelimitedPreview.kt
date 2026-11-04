package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText

/**
 * Bounded delimited-file cells; the host supplies parsing, source switching and truncation copy.
 */
@Composable
public fun KitDelimitedPreview(
    headers: List<String>,
    rows: List<List<String>>,
    truncated: Boolean,
    modifier: Modifier = Modifier,
) {
    val columns = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtMost(32)
    Column(
        modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        DelimitedRow(headers, columns, true)
        rows.take(200).forEach { DelimitedRow(it, columns, false) }
    }
}

@Composable
private fun DelimitedRow(cells: List<String>, columns: Int, header: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        repeat(columns) { index ->
            KitText(
                cells.getOrElse(index) { "" },
                Modifier.width(160.dp)
                    .then(if (header) Modifier.semantics { heading() } else Modifier),
                role = if (header) TypeRole.Label else TypeRole.Body,
            )
        }
    }
}

package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth

private const val SINGLE_COLUMN_SCALE = 1.5f // F: lib/ui/screens/usage_screen.dart:925

/** Flutter usage_screen.dart's labelled figures: two columns, one from 1.5 text scale. */
@Composable
public fun KitMetricGrid(values: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val columns =
        if (LocalDensity.current.fontScale >= SINGLE_COLUMN_SCALE) 1
        else 2 // F: lib/ui/screens/usage_screen.dart:925
    Column(
        modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
    ) {
        values.chunked(columns).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S4.dp),
            ) {
                row.forEach { (label, value) ->
                    Column(
                        Modifier.weight(1f).semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
                    ) {
                        KitText(label, role = TypeRole.Secondary, tone = TextTone.Secondary)
                        KitText(value, role = TypeRole.Headline)
                    }
                }
                if (row.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
}

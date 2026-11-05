package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

public data class KitKeyValueRow(val label: String, val value: String)

public object KitKeyValueRules {
    public const val MAX_ROWS: Int = 20 // F: lib/ui/kit/kit_key_value.dart:36
    public fun isProse(value: String): Boolean = value.contains('\n') || (value.length > 48 && value.contains(' ')) // F: lib/ui/kit/kit_key_value.dart:95
}

/** Label/value facts, with prose and enlarged text stacked instead of cut. */
@Composable
public fun KitKeyValue(rows: List<KitKeyValueRow>, modifier: Modifier = Modifier) {
    require(rows.size <= KitKeyValueRules.MAX_ROWS)
    val stacked = LocalDensity.current.fontScale * 10 >= 15 // F: lib/ui/kit/kit_key_value.dart:52
    val labelStyle = LocalKitTypography.current.style(TypeRole.Secondary).copy(color = LocalKitColors.current.text2)
    val style = LocalKitTypography.current.style(TypeRole.Body).copy(color = LocalKitColors.current.text1, fontFeatureSettings = "tnum")
    Column(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth()) {
        rows.take(KitKeyValueRules.MAX_ROWS).forEachIndexed { i, fact ->
            if (i > 0) KitDivider()
            if (stacked || KitKeyValueRules.isProse(fact.value)) {
                Column(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp).semantics(mergeDescendants = true) {}) {
                    BasicText(fact.label, style = labelStyle)
                    BasicText(fact.value, style = style)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp).semantics(mergeDescendants = true) {}) {
                    BasicText(fact.label, Modifier.weight(2f), style = labelStyle) // F: lib/ui/kit/kit_key_value.dart:76
                    Spacer(Modifier.width(Space.S3.dp))
                    BasicText(fact.value, Modifier.weight(3f), style = style.copy(textAlign = TextAlign.End)) // F: lib/ui/kit/kit_key_value.dart:79
                }
            }
        }
    }
}

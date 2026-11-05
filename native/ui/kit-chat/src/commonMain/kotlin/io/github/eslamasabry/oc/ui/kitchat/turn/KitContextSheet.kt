package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.row.KitKeyValue
import io.github.eslamasabry.oc.ui.kit.row.KitKeyValueRow
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Flutter kit_context_sheet.dart's summary; no fabricated measurements or per-reply history. */
@Composable
public fun KitContextSheet(tokens: Long, limit: Long, modelName: String, percent: Int, level: KitContextLevel) {
    Box(Modifier.kitMaxWidth(CapRole.Sheet)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    KitContextRing(percent, level, stringResource(Res.string.kitContextRingSemantics, Numbers.format(percent)), size = 64.dp) // F: lib/ui/kit/kit_context_ring.dart:116
                    KitText(stringResource(Res.string.kitModelContext, Numbers.format(percent)), role = TypeRole.Label, tone = when (level) { KitContextLevel.Calm -> TextTone.Secondary; KitContextLevel.Warning -> TextTone.Attention; KitContextLevel.Full -> TextTone.Primary })
                }
                KitText(stringResource(Res.string.kitContextSheetTokens, Numbers.format(tokens), Numbers.format(limit)), Modifier.weight(1f), role = TypeRole.RowTitle)
            }
            KitKeyValue(listOf(
                KitKeyValueRow(stringResource(Res.string.kitContextSheetModel), modelName),
                KitKeyValueRow(stringResource(Res.string.kitContextSheetWindow), Numbers.format(limit)),
                KitKeyValueRow(stringResource(Res.string.kitContextSheetMeasured), stringResource(Res.string.kitContextSheetMeasuredValue)),
            ))
            KitText(stringResource(Res.string.kitContextSheetExplain), role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
    }
}

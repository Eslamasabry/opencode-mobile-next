package io.github.eslamasabry.oc.ui.kit.rb.o18

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouMark
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphMark
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphTaskMark

/**
 * A test-only stand-in for Dart `KitTaskCard` (lib/ui/kit/kit_task_card.dart:166-331), the card the board gallery
 * puts in its lanes. Order 08 owns `team/KitTaskCard.kt` and has not landed it; the board lane's own chrome (strip, lane
 * beds, paging, gaps) is what these shots gate. Replace with `KitTaskCard` once order 08 merges.
 */
@Composable
internal fun BoardTaskCardStandIn(title: String, mark: KitWorkGraphMark, meta: List<String>, needsYouFlag: Boolean) {
    val resting = mark == KitWorkGraphMark.Done || mark == KitWorkGraphMark.Stopped
    val maxLines = if (kitTextScale() >= 1.3f) 3 else 2
    KitSurface(padding = KitSurfacePadding.None) {
        KitTappable({}, Modifier.fillMaxWidth(), shape = KitShapeId.Panel) {
            Box(Modifier.fillMaxWidth().padding(start = Space.S2.dp, top = Space.S3.dp, end = Space.S3.dp, bottom = Space.S3.dp)) {
                Row {
                    if (mark == KitWorkGraphMark.NeedsYou) KitNeedsYouMark() else KitWorkGraphTaskMark(mark)
                    Spacer(Modifier.width(Space.S2.dp))
                    Column(Modifier.weight(1f).padding(top = Space.S1.dp)) {
                        KitText(title, role = TypeRole.RowTitle, tone = if (resting) TextTone.Secondary else TextTone.Primary, maxLines = maxLines)
                        Row(Modifier.padding(top = Space.S1.dp), verticalAlignment = Alignment.CenterVertically) {
                            meta.forEachIndexed { i, piece ->
                                KitText(piece, role = TypeRole.Secondary)
                                if (i < meta.lastIndex) KitText("·", Modifier.padding(horizontal = Space.S1.dp), role = TypeRole.Secondary)
                            }
                        }
                        if (needsYouFlag) {
                            Box(Modifier.padding(top = Space.S2.dp)) { KitText("Needs you", role = TypeRole.Label, tone = TextTone.Attention) }
                        }
                    }
                }
            }
        }
    }
}

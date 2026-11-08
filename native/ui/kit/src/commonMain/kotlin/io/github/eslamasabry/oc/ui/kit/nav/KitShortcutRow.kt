package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouBadge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** One place a [KitShortcutRow] opens: its glyph, its name, and how many requests wait there (the amber badge). */
public data class KitShortcut(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    /** Requests waiting there; 0 = no badge. */
    val needsYou: Int = 0,
)

/**
 * A short row of places to jump to (Search, B1: Needs you, Projects, Settings, Servers). Each is a bare 24 dp glyph over
 * its name, sharing the width equally: no tile, no box, the press ink alone shows the target (owner 2026-11-08: icons
 * in boxes read as decoration). A place with requests waiting carries the amber needs-you badge on its glyph, spoken
 * after its name. The row keeps the 16 dp gutter; each cell is at least 64 dp tall, a 48 dp target and more.
 *
 * States: default, badge
 * Text: frame (names stop growing at 1.3×; two lines from 1.3×)
 * Cap: List
 * Motion: the badge fades in and out (KitNeedsYouBadge); press ink only
 * Replaces: nothing (new; Search's place tiles)
 */
@Composable
public fun KitShortcutRow(items: List<KitShortcut>, modifier: Modifier = Modifier) {
    kitAssert(items.size in 1..5) { "KitShortcutRow: one to five places; more belong in a list" }
    val tokens = LocalKitTokens.current
    Row(
        modifier.kitMaxWidth(CapRole.List).fillMaxWidth().padding(horizontal = (tokens.gutter - Space.S2).dp).testTag("kit-shortcut-row"),
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        for (item in items) {
            KitTappable(
                onClick = item.onClick,
                modifier = Modifier.weight(1f).heightIn(min = (tokens.minTarget + Space.S4).dp).semantics { role = Role.Button },
                shape = KitShapeId.Panel,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Space.S3.dp, horizontal = Space.S1.dp).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    KitNeedsYouBadge(item.needsYou) {
                        KitIcon(item.icon, size = KitIconSize.Large, tone = TextTone.Primary, growsWithText = true)
                    }
                    KitText(
                        item.label,
                        role = TypeRole.Secondary,
                        tone = TextTone.Primary,
                        kind = KitTextKind.Frame,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

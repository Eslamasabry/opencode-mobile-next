package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitBrandMark
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.text.KitOptical
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.design.layout.WindowClass

/** The ⋯ glyph's size in the bar (Dart `AppIconography.actionSize`). */
private const val BAR_GLYPH = 24 // F: lib/ui/kit/kit_icon_button.dart:47

/**
 * The servers page as the root of the app (Dart `KitTopBar(brand: true)`, servers_state.dart:83-90): the product mark
 * and name at the gutter instead of a Back and a page title, and the page's few app-level actions behind one ⋯ at the
 * end. Arranged from kit parts: [KitBrandMark] (tile size), the name in the headline page-title role, and a [KitMenu]
 * on a [KitIconButton]. Without [menu] items the ⋯ is not drawn (a dead ⋯ is never shown, Dart `KitRowMenu`).
 *
 * Layout (kit_top_bar.dart): `minHeight = minTarget + 2 · space1` (:335), `space1` above and below (:504), the title
 * block centred on the touch target's line, the mark and name `space2` apart (:691-693), the ⋯'s ink ending on the
 * gutter (`_endRoom`, :529-557), held space1 from the window edge for the touch target.
 */
@Composable
internal fun ServersBrandBar(name: String, moreLabel: String, menu: List<KitMenuItem>) {
    val tokens = LocalKitTokens.current
    var open by remember { mutableStateOf(false) }
    val direction = LocalLayoutDirection.current
    val window = LocalKitWindow.current
    // On PC the bar is the pane's toolbar, flat with a hairline below (kit_top_bar.dart:407-409, :511-519).
    val toolbar = window.cls == WindowClass.Large || (window.cls == WindowClass.Expanded && window.finePointer)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = (tokens.minTarget + 2 * Space.S1).dp) // F: lib/ui/kit/kit_top_bar.dart:335
                .padding(vertical = Space.S1.dp) // F: lib/ui/kit/kit_top_bar.dart:504
                .testTag("servers-brand-bar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(tokens.gutter.dp)) // F: lib/ui/kit/kit_top_bar.dart:456-460 (no exit: the gutter)
            Row(
                Modifier.weight(1f).heightIn(min = tokens.minTarget.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitBrandMark()
                Box(Modifier.width(Space.S2.dp)) // F: lib/ui/kit/kit_top_bar.dart:692
                KitText(
                    name,
                    Modifier.semantics { heading() },
                    role = TypeRole.Headline, // F: lib/ui/kit/kit_top_bar.dart:695
                    kind = KitTextKind.Frame,
                    maxLines = 2,
                    pageTitle = true,
                )
            }
            if (menu.isNotEmpty()) {
                Box(Modifier.width(Space.S2.dp)) // F: lib/ui/kit/kit_top_bar.dart:475
                KitMenu(open, menu, { open = false }) {
                    KitIconButton(KitIcons.more, moreLabel, { open = true }, size = BAR_GLYPH)
                }
                val glyph = BAR_GLYPH.dp
                // Flutter lets the ⋯'s ink end on the gutter, which puts its 48 dp target on the window's edge; the native
                // layout rules keep every target space1 in from the edge (LAYOUT-RULES edge-tap, as KitRow's ⋯ does).
                Box(
                    Modifier.width(
                        maxOf(
                            Space.S1.dp,
                            KitOptical.endPad(KitIcons.more, glyph, tokens.gutter.dp - (tokens.minTarget.dp - glyph) / 2, direction),
                        )
                    )
                ) // F: lib/ui/kit/kit_top_bar.dart:552-557
            } else {
                Box(Modifier.width(Space.S1.dp)) // F: lib/ui/kit/kit_top_bar.dart:550 (no last glyph: space1)
            }
        }
        if (toolbar) KitDivider() // F: lib/ui/kit/kit_top_bar.dart:518
    }
}

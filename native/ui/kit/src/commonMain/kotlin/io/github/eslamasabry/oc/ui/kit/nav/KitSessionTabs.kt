package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.theme.*

@Immutable
public data class KitSessionTab(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val ended: Boolean = false,
)

/** Flutter kit_session_tabs.dart: sessions as word-hugging pills in a scrolling track. */
@Composable
public fun KitSessionTabs(
    tabs: List<KitSessionTab>,
    label: String,
    endedLabel: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val bed =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Track,
                KitShapeId.Pill,
                ownFill = colors.ground.copy(alpha = 0f),
            )
        ) // F: lib/ui/kit/kit_session_tabs.dart:79-84
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = (tokens.gutter - Space.S1).dp)
            .semantics {
                contentDescription = label
                selectableGroup()
            }
    ) {
        Row(Modifier.then(bed.modifier), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                val chosen =
                    kitSurfaceLook(
                        KitSurfaceInput(
                            SurfaceRole.Control,
                            KitShapeId.Pill,
                            level = SurfaceLevel.Ground,
                            ownFill = colors.accent,
                            color = colors.accent,
                            selected = true,
                        )
                    )
                KitTappable(
                    tab.onClick,
                    Modifier.padding(horizontal = Space.S1.dp)
                        .heightIn(min = tokens.minTarget.dp)
                        .semantics {
                            selected = tab.selected
                            contentDescription =
                                if (tab.ended) "${tab.label}, $endedLabel" else tab.label
                        },
                    role = KitTappableRole.Tab,
                    shape = KitShapeId.Pill,
                ) {
                    val face =
                        if (tab.selected) chosen.modifier
                        else
                            Modifier.border(
                                kitHairline(),
                                if (bed.paints) colors.hairline.copy(alpha = 0f)
                                else colors.hairline,
                                KitShapeId.Pill.shape(),
                            ) // F: lib/ui/kit/kit_session_tabs.dart:225-230
                    Box(
                        face
                            .padding(horizontal = Space.S4.dp, vertical = Space.S2.dp)
                            .clearAndSetSemantics {}
                    ) {
                        if (tab.selected) {
                            // The selected school's words-on-colour hook (Paper inverts the
                            // control).
                            androidx.compose.foundation.text.BasicText(
                                tab.label,
                                style =
                                    LocalKitTypography.current
                                        .style(TypeRole.Label)
                                        .copy(color = chosen.words ?: colors.onAccent),
                                maxLines = 1,
                            )
                        } else
                            KitText(
                                tab.label,
                                role = TypeRole.Label,
                                tone = if (tab.ended) TextTone.Tertiary else TextTone.Secondary,
                                maxLines = 1,
                            )
                    }
                }
            }
        }
    }
}

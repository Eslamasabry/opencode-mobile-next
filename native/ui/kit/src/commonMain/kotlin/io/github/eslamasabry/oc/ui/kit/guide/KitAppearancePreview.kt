package io.github.eslamasabry.oc.ui.kit.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitPaneInset
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * A small sample of the app as the Appearance in force draws it (owner 2026-11-10, Appearance redesign: "no preview of
 * what you are changing"): one card with a line a person asked ([ask], on the accent's soft tint at the end), the
 * reply's words ([reply]) and one filled action ([action]). It is a picture, not controls: nothing in it can be
 * tapped, and a screen reader hears [spoken] once. Every choice on the page changes it at once, because it is drawn
 * with the same theme, style, corners, spacing and text size as the rest of the app.
 *
 * Cap: reading
 */
@Composable
public fun KitAppearancePreview(ask: String, reply: String, action: String, spoken: String, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val card = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Content, shape = KitShapeId.Card, level = SurfaceLevel.Surface1, ownFill = colors.surface1))
    val pill = KitShapeId.Button.shape()
    Column(
        modifier
            .fillMaxWidth()
            .then(card.modifier)
            .padding(Space.S4.dp + kitPaneInset())
            .clearAndSetSemantics { contentDescription = spoken }
            .testTag("kit-appearance-preview"),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        Box(
            Modifier.align(Alignment.End)
                .widthIn(max = PREVIEW_ASK_MAX_DP.dp)
                .clip(KitShapeId.Panel.shape())
                .background(colors.accent.copy(alpha = PREVIEW_ASK_TINT))
                .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp)
        ) {
            KitText(ask, role = TypeRole.Body, maxLines = 2)
        }
        KitText(reply, role = TypeRole.Body, maxLines = 3)
        Box(
            Modifier.heightIn(min = PREVIEW_ACTION_DP.dp).clip(pill).background(colors.accent).padding(horizontal = Space.S4.dp),
            contentAlignment = Alignment.Center,
        ) {
            KitText(action, role = TypeRole.Label, tone = TextTone.OnAccent, kind = KitTextKind.Frame, maxLines = 1)
        }
    }
}

/** The asked line's bubble: at most this wide, on the accent at this alpha. */
private const val PREVIEW_ASK_MAX_DP = 280
private const val PREVIEW_ASK_TINT = .16f

/** The sample action's height: a picture of a button, smaller than a real target. */
private const val PREVIEW_ACTION_DP = 36

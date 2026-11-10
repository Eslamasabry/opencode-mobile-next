package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/**
 * One numbered step of a short how-to (Dart `KitRow.number` with `leadingAtTop` and `below`,
 * lib/ui/screens/guide_screen.dart `_Step`): the step's number in a soft disc at the top, its title
 * (three lines), its words (eight lines) and what the step hands over under them ([below]: a
 * command, a button). [spoken] names the step for a screen reader ("Step 1 of 3"); the disc itself
 * is not read.
 *
 * States: with and without [below] Text: reading Cap: List Motion: none Hidden: none
 */
@Composable
public fun KitNumberedStep(
    number: Int,
    title: String,
    spoken: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val scale = minOf(kitTextScale(), TextScalePolicy.ICON_CAP)
    Row(
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .padding(horizontal = kitRowGutter(), vertical = Space.S3.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size((tokens.iconTile * scale).dp).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size((28f * scale).dp).background(colors.surface3, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                KitText(number.toString(), role = TypeRole.Label, tone = TextTone.Accent, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f)) {
            KitText(
                title,
                Modifier.semantics {
                    heading()
                    contentDescription = "$spoken, $title"
                },
                role = TypeRole.RowTitle,
                maxLines = 3,
            )
            if (body != null) {
                Spacer(Modifier.height(Space.S1.dp))
                KitText(body, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 8)
            }
            if (below != null) {
                Spacer(Modifier.height(Space.S2.dp))
                Column(
                    Modifier.fillMaxWidth().padding(bottom = Space.S2.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    content = below,
                )
            }
        }
        Spacer(Modifier.width(0.dp))
    }
}

/**
 * One point of a short list (Dart `Bullet`, guide_screen.dart): a quiet dot and the words; the dot is not read.
 * The dot is drawn, not a "•" character (B-KIT-GLYPH-001), and sits in the middle of the first line of the words.
 */
@Composable
public fun KitBulletLine(text: String, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val scale = minOf(kitTextScale(), TextScalePolicy.ICON_CAP)
    val firstLine = with(LocalDensity.current) { LocalKitTypography.current.style(TypeRole.Body).lineHeight.toDp() } * kitTextScale()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.height(firstLine).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Box(Modifier.size((BULLET_DOT_DP * scale).dp).background(colors.text2, CircleShape))
        }
        KitText(text, Modifier.weight(1f))
    }
}

private const val BULLET_DOT_DP = 5f

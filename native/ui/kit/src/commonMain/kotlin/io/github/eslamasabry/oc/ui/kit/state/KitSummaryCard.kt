package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * The one card a screen may carry (Finora's summary card; direction §4.3): what needs the person now, in one place,
 * with its answer on the card. [kicker] is the quiet line on top ("Needs you", with the amber dot when [attention]);
 * [title] the question in the headline size; [supporting] where it comes from; [detail] the exact thing being allowed
 * (a command, in mono), which must be shown whenever [primary] allows something. [more] is the quiet link at the top end
 * ("1 more"). [onClick] makes the whole card open what it is about (the conversation). Shown only while there is
 * something to say: the host drops it, never leaves it empty.
 *
 * States: default
 * Text: reading
 * Cap: Reading
 * Motion: none of its own (the host reveals it)
 */
@Composable
public fun KitSummaryCard(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    attention: Boolean = false,
    supporting: String? = null,
    detail: String? = null,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    more: KitLineAction? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val shape = KitShapeId.Card.shape()
    Column(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .fillMaxWidth()
            .padding(horizontal = tokens.gutter.dp)
            .then(
                if (onClick != null) Modifier.kitTappable(onClick = onClick, onClickLabel = onClickLabel, role = KitTappableRole.Row, shape = KitShapeId.Card)
                else Modifier
            )
            .background(colors.surface2, shape)
            .padding(Space.S4.dp)
            .testTag("kit-summary-card"),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            if (attention) Box(Modifier.size(8.dp).background(colors.attention, CircleShape))
            KitText(kicker, Modifier.weight(1f), role = TypeRole.Label, tone = if (attention) TextTone.Attention else TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
            more?.let {
                KitTappable(onClick = it.onClick, shape = KitShapeId.Pill) {
                    KitText(it.label, Modifier.padding(horizontal = Space.S2.dp), role = TypeRole.Label, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
                }
            }
        }
        KitText(title, Modifier.semantics { heading() }, role = TypeRole.Headline, maxLines = 3)
        supporting?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 2) }
        detail?.let {
            Box(Modifier.fillMaxWidth().background(colors.surface1, KitShapeId.Code.shape()).padding(horizontal = Space.S3.dp, vertical = Space.S2.dp)) {
                KitText(it, role = TypeRole.Mono, maxLines = 3)
            }
        }
        if (primary != null || secondary != null) {
            Row(Modifier.padding(top = Space.S1.dp), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                primary?.let { KitButton(it, Modifier.weight(1f)) }
                secondary?.let { KitButton(it, Modifier.weight(1f), role = KitButtonRole.Secondary) }
            }
        }
    }
}

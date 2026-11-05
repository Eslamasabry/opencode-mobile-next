package io.github.eslamasabry.oc.ui.kit.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.theme.*

/** A group on the guide's start, or a question with its current answer. */
public data class KitGuideItem(
    val id: String,
    val label: String,
    val value: String,
    val done: Boolean = false,
    val next: Boolean = false,
    val held: Boolean = false,
)

/** Flutter kit_guide.dart's flat start list and responsive question/group cards. */
@Composable
public fun KitGuideRail(
    items: List<KitGuideItem>,
    onItem: (String) -> Unit,
    selected: String? = null,
    group: String? = null,
    allLabel: String = "",
    backLabel: String = "",
    onBack: (() -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val flat = group == null && LocalKitStyle.current.spec.tokens.flatLists
    val scale = LocalDensity.current.fontScale
    if (flat) {
        KitRowGroup {
            items.forEach { item ->
                KitTappable(
                    onClick = { onItem(item.id) },
                    modifier = Modifier.fillMaxWidth().testTag("guide-item-${item.id}"),
                    role = KitTappableRole.Row,
                    shape = KitShapeId.Square,
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = tokens.rowHeightTwo.dp)
                            .padding(start = tokens.gutter.dp, end = Space.S3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = Space.S2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                KitText(
                                    item.label,
                                    Modifier.weight(1f, fill = false),
                                    role = TypeRole.RowTitle,
                                    maxLines = 2,
                                ) // F: lib/ui/kit/kit_guide.dart:298
                                if (item.done) {
                                    Spacer(Modifier.width(Space.S1.dp))
                                    KitGlyph(KitIcons.check, colors.accent, tokens.smallIcon.dp)
                                }
                            }
                            KitText(
                                item.value,
                                role = TypeRole.Secondary,
                                tone = if (item.held) TextTone.Tertiary else TextTone.Secondary,
                                maxLines = 2,
                            ) // F: lib/ui/kit/kit_guide.dart:304
                        }
                        if (item.next) GuideDot()
                        Spacer(Modifier.width(Space.S1.dp))
                        KitGlyph(KitIcons.chevronRight, colors.text3, tokens.smallIcon.dp)
                    }
                }
            }
        }
        return
    }
    Column(Modifier.padding(horizontal = tokens.gutter.dp)) {
        if (group != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitTappable(onClick = { onBack?.invoke() }, onClickLabel = backLabel) {
                    Row(
                        Modifier.padding(start = Space.S1.dp, end = Space.S2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KitGlyph(KitIcons.back, colors.text2, tokens.smallIcon.dp)
                        Spacer(Modifier.width(Space.S1.dp))
                        KitText(allLabel, role = TypeRole.Secondary)
                    }
                }
                Spacer(Modifier.width(Space.S2.dp))
                KitText(group, Modifier.weight(1f).semantics { heading() }, role = TypeRole.Label)
                val at = items.indexOfFirst { it.id == selected }
                items.forEachIndexed { index, _ ->
                    Box(
                        Modifier.padding(start = Space.S1.dp)
                            .size(6.dp)
                            .background(
                                if (index <= at) colors.accent else colors.hairline,
                                CircleShape,
                            )
                            .clearAndSetSemantics {}
                    ) // F: lib/ui/kit/kit_guide.dart:455
                }
            }
            Spacer(Modifier.height(Space.S2.dp))
        }
        val columns =
            if (group == null) {
                if (scale >= 1.6f) 1 else 2 // F: lib/ui/kit/kit_guide.dart:326
            } else if (scale >= 1.3f) 1
            else items.size.coerceAtLeast(1) // F: lib/ui/kit/kit_guide.dart:349
        Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            items.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    row.forEach { item ->
                        GuideCard(
                            item,
                            item.id == selected,
                            { onItem(item.id) },
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun GuideCard(
    item: KitGuideItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Control,
                KitShapeId.Card,
                ownFill =
                    if (selected) colors.accent.copy(alpha = .14f).compositeOver(colors.ground)
                    else colors.surface1, // F: lib/ui/kit/kit_guide.dart:560
                selected = selected,
            )
        )
    val marked = selected || item.next
    KitTappable(
        onClick = onClick,
        shape = KitShapeId.Card,
        modifier = modifier.testTag("guide-item-${item.id}").semantics { this.selected = selected },
    ) {
        Box(
            Modifier.fillMaxSize()
                .then(look.modifier)
                .then(
                    if (marked)
                        Modifier.border(kitFocusRingWidth(), colors.accent, KitShapeId.Card.shape())
                    else Modifier
                )
        ) {
            Column(
                Modifier.heightIn(min = tokens.minTarget.dp)
                    .padding(
                        start = Space.S3.dp,
                        end = (Space.S3 + Space.S1).dp,
                        top = Space.S2.dp,
                        bottom = Space.S2.dp,
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitText(
                        item.label,
                        Modifier.weight(1f, fill = false),
                        role = TypeRole.RowTitle,
                        maxLines = 2,
                    ) // F: lib/ui/kit/kit_guide.dart:603
                    if (item.done) {
                        Spacer(Modifier.width(Space.S1.dp))
                        KitGlyph(KitIcons.check, colors.accent, tokens.smallIcon.dp)
                    }
                }
                KitText(
                    item.value,
                    role = TypeRole.Secondary,
                    tone =
                        if (item.held) TextTone.Tertiary
                        else if (selected) TextTone.Primary else TextTone.Secondary,
                    maxLines = 2,
                ) // F: lib/ui/kit/kit_guide.dart:639
            }
            if (item.next) Box(Modifier.align(Alignment.TopEnd).padding(Space.S2.dp)) { GuideDot() }
        }
    }
}

@Composable
private fun GuideDot() {
    Box(
        Modifier.size(7.dp)
            .background(LocalKitColors.current.accent, CircleShape)
            .clearAndSetSemantics {}
    ) // F: lib/ui/kit/kit_guide.dart:508
}

/** The next step stays at the foot of the guide; large text stacks its button below its count. */
@Composable
public fun KitGuideNextBar(
    count: String,
    label: String?,
    onNext: () -> Unit,
    ready: Boolean = false,
) {
    val tokens = LocalKitTokens.current
    val large = LocalDensity.current.fontScale >= 1.3f // F: lib/ui/kit/kit_guide.dart:843
    Column(Modifier.fillMaxWidth()) {
        KitDivider()
        val inset =
            Modifier.fillMaxWidth()
                .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp)
                .heightIn(min = tokens.minTarget.dp)
        val role = if (ready) KitButtonRole.Primary else KitButtonRole.Secondary
        if (large)
            Column(inset) {
                KitText(
                    count,
                    role = TypeRole.Secondary,
                    maxLines = 2,
                ) // F: lib/ui/kit/kit_guide.dart:832
                if (label != null) {
                    Spacer(Modifier.height(Space.S2.dp))
                    KitButton(label, onNext, role = role, fullWidth = true)
                }
            }
        else
            Row(inset, verticalAlignment = Alignment.CenterVertically) {
                KitText(
                    count,
                    Modifier.weight(1f),
                    role = TypeRole.Secondary,
                    maxLines = 2,
                ) // F: lib/ui/kit/kit_guide.dart:832
                if (label != null) {
                    Spacer(Modifier.width(Space.S2.dp))
                    Box(
                        Modifier.weight(2f),
                        contentAlignment = Alignment.CenterEnd,
                    ) { // F: lib/ui/kit/kit_guide.dart:882
                        KitButton(label, onNext, role = role)
                    }
                }
            }
    }
}

/** A guided question names its scope before the person changes it (kit_guide.dart _Header). */
@Composable
public fun KitGuideQuestionHeading(
    title: String,
    changesLabel: String,
    changes: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        KitText(title, Modifier.semantics { heading() }, role = TypeRole.Headline)
        Spacer(Modifier.height(Space.S1.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitText(changesLabel, role = TypeRole.Caption, tone = TextTone.Accent)
            KitText(changes, role = TypeRole.Secondary)
        }
    }
}

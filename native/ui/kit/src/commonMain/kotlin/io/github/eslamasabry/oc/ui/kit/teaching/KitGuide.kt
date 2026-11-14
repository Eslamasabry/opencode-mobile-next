package io.github.eslamasabry.oc.ui.kit.teaching

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitBottomInset
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.kitRowGutter
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitGuideAll
import io.github.eslamasabry.oc.ui.words.res.kitGuideBack
import io.github.eslamasabry.oc.ui.words.res.kitGuideChanges
import org.jetbrains.compose.resources.stringResource

/**
 * One chip of a [KitGuide] (Dart `KitGuideItem`): a group on its start, or one question inside a group. It shows what
 * is set now ([value]) under its [label]. [done]: answered on the guided path (a check after the label). [next]: the
 * guide's suggestion (an accent edge and a dot). [held]: waiting on another choice, the value reads quieter.
 */
@Immutable
public data class KitGuideItem(
    val id: String,
    val label: String,
    val value: String,
    val done: Boolean = false,
    val next: Boolean = false,
    val held: Boolean = false,
    val semanticsLabel: String? = null,
)

/** The group open in a [KitGuide] (Dart `KitGuideGroup`): its name, and the way back to every chip. */
@Immutable
public class KitGuideGroup(public val label: String, public val onBack: () -> Unit)

/** The one question a [KitGuide] shows (Dart `KitGuideQuestion`): its title, what it [changes], its control. */
@Immutable
public class KitGuideQuestion(
    public val id: String,
    public val title: String,
    public val changes: String,
    public val content: @Composable () -> Unit,
)

/** The guide's bar along the foot (Dart `KitGuideNext`): how far along, and the next step. */
@Immutable
public class KitGuideNext(
    public val count: String,
    public val label: String? = null,
    public val onPressed: (() -> Unit)? = null,
    public val ready: Boolean = false,
) {
    init {
        require(label == null || onPressed != null) { "KitGuideNext: a step needs an action" } // F: lib/ui/kit/kit_guide.dart:102
    }
}

/** Pure rules of [KitGuide] (kit_guide.dart). */
public object KitGuideRules {
    /** The start view's chips per row: two at ordinary text, one from 1.6× (`_Rail._chips`). */
    public fun startPerRow(scale: Float): Int = if (scale >= 1.6f) 1 else 2 // F: lib/ui/kit/kit_guide.dart:326

    /** One group's questions stack from 1.3× text, else they share one line. */
    public fun questionsStacked(scale: Float): Boolean = scale >= 1.3f // F: lib/ui/kit/kit_guide.dart:349

    /** The rail stays on top while the text is ordinary and the window is not short. */
    public fun railPinned(scale: Float, heightDp: Float): Boolean =
        scale < 1.3f && heightDp >= Layout.SHORT_HEIGHT // F: lib/ui/kit/kit_guide.dart:196,197

    /** The next bar stacks its step under the count from 1.3× text. */
    public fun nextStacked(scale: Float): Boolean = scale >= 1.3f // F: lib/ui/kit/kit_guide.dart:843

    /** A crumb dot is lit for every question up to the open one. */
    public fun dotLit(index: Int, at: Int): Boolean = index <= at

    /** A chip's value tone: quieter when held, primary when chosen. */
    public fun valueTone(item: KitGuideItem, selected: Boolean): TextTone =
        if (item.held) TextTone.Tertiary else if (selected) TextTone.Primary else TextTone.Secondary

    /** What a screen reader says for a chip by default: the label and the value. */
    public fun chipLabel(item: KitGuideItem): String = item.semanticsLabel ?: "${item.label}, ${item.value}"
}

private const val DOT = 7 // F: lib/ui/kit/kit_guide.dart:508,680

/**
 * A settings page that asks one decision at a time (Dart `KitGuide`, docs/ux-system/kit-api/KitGuide.md): a rail of
 * chips at the top that each show their current value, and below the rail either the open [question] (its title, a
 * "Changes" line naming what it affects, and its control) or the page's [home] words. On a guided first visit a [next]
 * bar along the foot suggests the next chip, which carries a dot; answered chips carry a check. The rail shows the
 * [items] given: the groups on the start view (in Solid, the page's own flat list), or the questions of one [group]
 * with a way back and its place as dots. Tapping a chip calls [onItem] with its id. The rail stays on top and the
 * question scrolls under it; with large text or a short window everything scrolls as one page.
 *
 * States: none (a frame around the controls it is given)
 * Text: reading
 * Cap: Fill
 * Motion: a new question fades and rises (STANDARD in, QUICK out), the rail cross-fades between the groups and one
 * group's questions, a check or a dot pops in (QUICK); Off = instant; nothing loops
 * Replaces: KitGuide, KitGuideItem, KitGuideGroup, KitGuideQuestion, KitGuideNext (lib/ui/kit/kit_guide.dart)
 */
@Composable
public fun KitGuide(
    items: List<KitGuideItem>,
    onItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    selected: String? = null,
    group: KitGuideGroup? = null,
    question: KitGuideQuestion? = null,
    home: (@Composable () -> Unit)? = null,
    notice: (@Composable () -> Unit)? = null,
    next: KitGuideNext? = null,
) {
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val reduced = KitMotion.state.reduced
    val scale = kitTextScale()
    val rail: @Composable () -> Unit = {
        val railPad = if (group == null) Modifier.padding(top = Space.S2.dp) else Modifier
        if (reduced) {
            Box(railPad) { Rail(items, selected, group, onItem) }
        } else {
            AnimatedContent(
                targetState = RailState(items, selected, group),
                modifier = railPad,
                transitionSpec = { fadeIn(specs.pace(KitPace.Standard)) togetherWith fadeOut(specs.pace(KitPace.Standard)) },
                contentKey = { it.group?.label ?: "_start" },
                label = "kit.guide.rail",
            ) { s -> Rail(s.items, s.selected, s.group, onItem) }
        }
    }
    val body: @Composable (Boolean) -> Unit = { scrolls -> Body(question, home, notice, scrolls) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bounded = constraints.hasBoundedHeight
        when {
            !bounded -> Column(Modifier.fillMaxWidth()) {
                rail()
                body(false)
                next?.let { NextBar(it) }
            }
            KitGuideRules.railPinned(scale, maxHeight.value) -> Column(Modifier.fillMaxSize()) {
                rail()
                Box(Modifier.fillMaxWidth().weight(1f)) { body(true) }
                next?.let { NextBar(it) }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = Space.S1.dp)) {
                    rail()
                    body(false)
                }
                next?.let { NextBar(it) }
            }
        }
    }
}

@Immutable
private data class RailState(val items: List<KitGuideItem>, val selected: String?, val group: KitGuideGroup?)

/** The chips: the groups two by two (a flat list in Solid), or one group's questions in a line under its name. */
@Composable
private fun Rail(items: List<KitGuideItem>, selected: String?, group: KitGuideGroup?, onItem: (String) -> Unit) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val scale = kitTextScale()
    if (group == null && LocalKitStyle.current.spec.tokens.flatLists) {
        // Flat lists (Solid): one row per group that opens it (a chevron), parted by hairlines at the words.
        KitRowGroup {
            items.forEach { item ->
                Box(Modifier.fillMaxWidth().testTag("guide-item-${item.id}")) {
                    if (item.held) {
                        KitRow(
                            title = item.label,
                            supporting = AnnotatedString(item.value, SpanStyle(color = colors.text3)),
                            trailing = KitRowTrailing.Chevron,
                            supportingMaxLines = 2, // F: lib/ui/kit/kit_guide.dart:304
                            onClick = { onItem(item.id) },
                        )
                    } else {
                        KitRow(
                            title = item.label,
                            supporting = item.value,
                            trailing = KitRowTrailing.Chevron,
                            titleMaxLines = 2, // F: lib/ui/kit/kit_guide.dart:298
                            supportingMaxLines = 2, // F: lib/ui/kit/kit_guide.dart:304
                            onClick = { onItem(item.id) },
                        )
                    }
                    // The guide's dot sits before the chevron (Dart `_GuideRowEnd`): the row's end gutter, the
                    // chevron, then one space step.
                    val chevron = tokens.smallIcon * minOf(scale, TextScalePolicy.FRAME_CAP)
                    Box(
                        Modifier.align(Alignment.CenterEnd)
                            .padding(end = kitRowGutter() + chevron.dp + Space.S1.dp),
                    ) { GuideDot(item.next) }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp)) {
        val chip: @Composable (KitGuideItem, Modifier) -> Unit = { item, m ->
            GuideChip(item, item.id == selected, { onItem(item.id) }, m)
        }
        if (group == null) {
            val perRow = KitGuideRules.startPerRow(scale)
            items.chunked(perRow).forEachIndexed { r, row ->
                if (r > 0) Spacer(Modifier.height(Space.S2.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEachIndexed { i, item ->
                        if (i > 0) Spacer(Modifier.width(Space.S2.dp))
                        chip(item, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            return@Column
        }
        val at = items.indexOfFirst { it.id == selected }
        Crumb(group, items.size, at)
        Spacer(Modifier.height(Space.S2.dp))
        if (KitGuideRules.questionsStacked(scale)) {
            items.forEachIndexed { i, item ->
                if (i > 0) Spacer(Modifier.height(Space.S2.dp))
                chip(item, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                items.forEachIndexed { i, item ->
                    if (i > 0) Spacer(Modifier.width((Space.S2 - 2).dp)) // F: lib/ui/kit/kit_guide.dart:366
                    chip(item, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** "‹ All", the group's name, and dots for the questions up to here (Dart `_Crumb`). */
@Composable
private fun Crumb(group: KitGuideGroup, count: Int, at: Int) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val specs = KitMotion.specs
    val back = stringResource(Res.string.kitGuideBack)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KitTappable(
            onClick = group.onBack,
            modifier = Modifier.semantics { contentDescription = back },
            shape = KitShapeId.Button,
        ) {
            Row(
                Modifier.heightIn(min = tokens.minTarget.dp)
                    .padding(start = Space.S1.dp, end = Space.S2.dp)
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitIcon(KitIcons.back, size = KitIconSize.Small, tone = TextTone.Secondary)
                Spacer(Modifier.width(Space.S1.dp))
                KitText(stringResource(Res.string.kitGuideAll), role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(Space.S2.dp))
        KitText(group.label, Modifier.weight(1f).semantics { heading() }, role = TypeRole.Label, maxLines = 1)
        Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            repeat(count) { i ->
                val color by animateColorAsState(
                    if (KitGuideRules.dotLit(i, at)) colors.accent else colors.hairline,
                    specs.pace(KitPace.Quick),
                    label = "kit.guide.crumb",
                )
                Box(
                    Modifier.padding(start = Space.S1.dp)
                        .size(6.dp) // F: lib/ui/kit/kit_guide.dart:455
                        .background(color, CircleShape),
                )
            }
        }
    }
}

/** The guide's accent dot: pops in from MARK_FROM when [shown] (Dart `AnimatedScale` + `AnimatedOpacity`). */
@Composable
private fun GuideDot(shown: Boolean) {
    val specs = KitMotion.specs
    val k by animateFloatAsState(if (shown) 1f else 0f, specs.pace(KitPace.Quick), label = "kit.guide.dot")
    val accent = LocalKitColors.current.accent
    Box(
        Modifier.size(DOT.dp)
            .graphicsLayer {
                val s = MotionTokens.MARK_FROM + (1f - MotionTokens.MARK_FROM) * k
                scaleX = s
                scaleY = s
                alpha = k
            }
            .background(accent, CircleShape)
            .clearAndSetSemantics {},
    )
}

/** One chip: its label (a check once answered), its value, and the dot when it is next (Dart `_GuideChip`). */
@Composable
private fun GuideChip(item: KitGuideItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val specs = KitMotion.specs
    val flat = LocalKitStyle.current.spec.tokens.flatLists
    val marked = selected || item.next
    val ownFill =
        if (selected) colors.accent.copy(alpha = .14f).compositeOver(colors.ground) // F: lib/ui/kit/kit_guide.dart:560
        else colors.surface1
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Control, KitShapeId.Card, ownFill = ownFill, selected = selected))
    // Solid draws the chip's own face: an accent edge when marked, none at rest under flat lists, else a hairline.
    // A style's own face keeps the accent edge the choice needs.
    val edge: Modifier =
        if (!look.paints) {
            val color = if (marked) colors.accent else if (flat) Color.Transparent else colors.hairline
            val width = if (selected) kitFocusRingWidth() else kitHairline()
            if (color == Color.Transparent) Modifier else Modifier.guideEdge(width, color, look.shape)
        } else if (marked) {
            Modifier.guideEdge(kitFocusRingWidth(), colors.accent, look.shape)
        } else {
            Modifier
        }
    val done by animateFloatAsState(if (item.done) 1f else 0f, specs.pace(KitPace.Quick), label = "kit.guide.done")
    KitTappable(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = KitGuideRules.chipLabel(item)
            this.selected = selected
        },
        shape = KitShapeId.Card,
    ) {
        Box(
            Modifier.fillMaxSize()
                .heightIn(min = tokens.minTarget.dp)
                .then(look.modifier)
                .then(edge)
                .clearAndSetSemantics {},
        ) {
            Column(
                Modifier.align(Alignment.CenterStart).padding(
                    start = Space.S3.dp,
                    end = (Space.S3 + Space.S1).dp,
                    top = Space.S2.dp,
                    bottom = Space.S2.dp,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitText(item.label, Modifier.weight(1f, fill = false), role = TypeRole.RowTitle, maxLines = 2) // F: lib/ui/kit/kit_guide.dart:603
                    if (item.done) {
                        Box(
                            Modifier.padding(start = Space.S1.dp).graphicsLayer {
                                val s = MotionTokens.MARK_FROM + (1f - MotionTokens.MARK_FROM) * done
                                scaleX = s
                                scaleY = s
                                alpha = done
                            },
                        ) { KitIcon(KitIcons.check, size = KitIconSize.Small, tone = TextTone.Accent) }
                    }
                }
                KitText(item.value, role = TypeRole.Secondary, tone = KitGuideRules.valueTone(item, selected), maxLines = 2) // F: lib/ui/kit/kit_guide.dart:639
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = Space.S2.dp, end = Space.S2.dp)) { GuideDot(item.next) }
        }
    }
}

/** Under the rail: the notice, then the open question (or the home words), sliding in when it changes. */
@Composable
private fun Body(
    question: KitGuideQuestion?,
    home: (@Composable () -> Unit)?,
    notice: (@Composable () -> Unit)?,
    scrolls: Boolean,
) {
    val specs = KitMotion.specs
    val reduced = KitMotion.state.reduced
    val page: @Composable (KitGuideQuestion?) -> Unit = { open ->
        val end = Space.S4.dp + kitBottomInset().bottom
        val m = if (scrolls) Modifier.fillMaxSize().verticalScroll(io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState()) else Modifier.fillMaxWidth()
        Column(m.padding(bottom = end)) {
            notice?.invoke()
            if (open != null) {
                val tokens = LocalKitTokens.current
                Box(Modifier.padding(start = tokens.gutter.dp, end = tokens.gutter.dp, top = Space.S4.dp)) { Header(open) }
                Spacer(Modifier.height(Space.S3.dp))
                open.content()
            } else {
                home?.invoke()
            }
        }
    }
    if (reduced) {
        page(question)
        return
    }
    AnimatedContent(
        targetState = question,
        transitionSpec = {
            (fadeIn(specs.pace(KitPace.Standard)) +
                slideInVertically(specs.pace(KitPace.Standard)) { (it * .02f).toInt() }) togetherWith // F: lib/ui/kit/kit_guide.dart:761
                fadeOut(specs.pace(KitPace.Quick))
        },
        contentKey = { it?.id ?: "_home" },
        label = "kit.guide.body",
    ) { open -> page(open) }
}

/** The question in words, and what it changes (Dart `_Header`). */
@Composable
private fun Header(question: KitGuideQuestion) {
    val colors = LocalKitColors.current
    val type = LocalKitTypography.current
    val changesWord = stringResource(Res.string.kitGuideChanges)
    val caption = type.style(TypeRole.Caption)
    val secondary = type.style(TypeRole.Secondary)
    Column(Modifier.fillMaxWidth()) {
        KitText(question.title, Modifier.semantics { heading() }, role = TypeRole.Headline)
        Spacer(Modifier.height(Space.S1.dp))
        val line = buildAnnotatedString {
            withStyle(caption.toSpanStyle().copy(color = colors.accent)) { append(changesWord) }
            append("  ")
            append(question.changes)
        }
        BasicText(
            line,
            Modifier.semantics(mergeDescendants = true) {},
            style = secondary.copy(color = colors.text2, textDirection = TextDirection.Content),
        )
    }
}

/** How far along, and the next step (Dart `_NextBar`). */
@Composable
private fun NextBar(next: KitGuideNext) {
    val tokens = LocalKitTokens.current
    val label = next.label
    val role = if (next.ready) KitButtonRole.Primary else KitButtonRole.Secondary
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxWidth()) {
        KitDivider()
        val inset = Modifier.fillMaxWidth()
            .padding(start = tokens.gutter.dp, end = tokens.gutter.dp, top = Space.S2.dp, bottom = Space.S2.dp + bottom)
            .heightIn(min = tokens.minTarget.dp)
        if (KitGuideRules.nextStacked(kitTextScale())) {
            Column(inset) {
                KitText(next.count, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 2) // F: lib/ui/kit/kit_guide.dart:832
                if (label != null) {
                    Spacer(Modifier.height(Space.S2.dp))
                    KitButton(label, { next.onPressed?.invoke() }, role = role, fullWidth = true)
                }
            }
        } else {
            Row(inset, verticalAlignment = Alignment.CenterVertically) {
                KitText(next.count, Modifier.weight(1f), role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 2) // F: lib/ui/kit/kit_guide.dart:832
                if (label != null) {
                    Spacer(Modifier.width(Space.S2.dp))
                    Box(Modifier.weight(2f), contentAlignment = Alignment.CenterEnd) { // F: lib/ui/kit/kit_guide.dart:882
                        KitButton(label, { next.onPressed?.invoke() }, role = role)
                    }
                }
            }
        }
    }
}

/**
 * A box edge inside the outline, at its exact width: Flutter's `Border` lays a third-of-a-pixel hairline inside the
 * box, where `Modifier.border` would round the stroke up to a whole pixel.
 */
private fun Modifier.guideEdge(width: Dp, color: Color, shape: Shape): Modifier =
    drawWithContent {
        drawContent()
        val w = width.toPx()
        inset(w / 2f) { drawOutline(shape.createOutline(size, layoutDirection, this), color, style = Stroke(w)) } // NV: half the stroke, so it lies inside the box
    }

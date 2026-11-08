package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitMonoCut
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.turn.KitLiveSweep
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitToolOpenDetails
import org.jetbrains.compose.resources.stringResource

/** The title's share of the line when a path or detail follows it (Dart `_buildTimelineStep`). */
private const val TITLE_SHARE = .6f // F: lib/ui/kit/chat/kit_tool_row.dart:511

/** The end words' share of the line. */
private const val WORDS_SHARE = .3f // F: lib/ui/kit/chat/kit_tool_row.dart:576

/**
 * A step inside an opened [KitStepTimeline] (Dart `_KitToolRowState._buildTimelineStep`, kit_tool_row.dart:400-709):
 * ONE line, 32 dp (taller only for larger text). A shell step's line is its command in mono; any other step reads as
 * one sentence, "Read lib/x.dart", "Edit checkout_test.dart +3 −1". Its node (the step's glyph in a small tile; the
 * light while it runs) sits on the rail and nothing else marks the line: no frame, no fill, no chevron. The end says
 * how the step stands: the running time, how long it took, or the status word. A tap opens it; the opened body sits at
 * the text's own x under the line and fades in over QUICK. A step that opens another page keeps a forward chevron.
 */
@Composable
internal fun TimelineStep(
    view: KitToolView,
    opened: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    body: (@Composable () -> Unit)?,
    onOpen: (() -> Unit)?,
) {
    val m = railMeasures()
    val tokens = LocalKitTokens.current
    val status = view.status
    val live = status == KitToolStatus.Running
    val wide = m.wide
    // F: lib/ui/kit/chat/kit_tool_row.dart:430 the first line sits in the middle of the 32 dp
    val pad = (m.stepLine - m.first) / 2 // F: lib/ui/kit/chat/kit_tool_row.dart:430
    // F: lib/ui/kit/chat/kit_tool_row.dart:432-438 (native folds not-run into Stopped, read as quiet)
    val tone = if (live || status == KitToolStatus.Failed) TextTone.Primary else TextTone.Secondary
    val subject = view.subject?.takeIf { it.isNotEmpty() }
    val detail = view.detail?.takeIf { it.isNotEmpty() }
    val shellLine = view.kind == KitToolKind.Shell && subject != null
    val hasCounts = view.added != null || view.removed != null
    val open = opened && body != null

    // Opening fades the body in over QUICK (never on first composition: a row scrolled back shows at once); no size
    // motion (MOT-5); reduced motion shows it at once.
    val reduced = KitMotion.state.reduced
    val quick = KitMotion.specs.pace<Float>(KitPace.Quick)
    var wasOpen by remember { mutableStateOf(open) }
    val fade = remember(open) { Animatable(if (open && !wasOpen && !reduced) 0f else 1f) }
    LaunchedEffect(open) {
        wasOpen = open
        fade.animateTo(1f, quick)
    }

    val second: (@Composable () -> Unit)? =
        when {
            shellLine -> null
            subject != null -> {
                { KitMonoText(redacted(subject), tone = tone, cut = KitMonoCut.Middle, maxLines = 1) }
            }
            detail != null -> {
                {
                    KitText(redacted(detail), role = TypeRole.Secondary, tone = TextTone.Tertiary, maxLines = if (wide) 3 else 1) // F: lib/ui/kit/chat/kit_tool_row.dart:454
                }
            }
            else -> null
        }
    val title: @Composable () -> Unit = {
        KitLiveSweep(working = live, step = true) {
            KitText(view.title, role = TypeRole.Secondary, tone = tone, maxLines = if (wide) 3 else 1) // F: lib/ui/kit/chat/kit_tool_row.dart:482
        }
    }

    val sentence: @Composable () -> Unit = {
        when {
            shellLine ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        KitLiveSweep(working = live, step = true) {
                            KitMonoText(
                                redacted(subject.orEmpty()),
                                tone = tone,
                                cut = if (wide) KitMonoCut.Wrap else KitMonoCut.End,
                                maxLines = if (wide) 3 else 1, // F: lib/ui/kit/chat/kit_tool_row.dart:467
                            )
                        }
                    }
                    if (hasCounts) {
                        Spacer(Modifier.width(Space.S2.dp)) // F: lib/ui/kit/chat/kit_tool_row.dart:529
                        Counts(view.added, view.removed)
                    }
                }
            wide ->
                Column {
                    title()
                    second?.invoke()
                    if (hasCounts) Counts(view.added, view.removed)
                }
            else ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (second == null) Box(Modifier.weight(1f, fill = false)) { title() }
                    else Box(Modifier.share(TITLE_SHARE)) { title() }
                    if (second != null) {
                        Spacer(Modifier.width(Space.S1.dp)) // F: lib/ui/kit/chat/kit_tool_row.dart:516
                        Box(Modifier.weight(1f)) { second() }
                    }
                    if (hasCounts) {
                        Spacer(Modifier.width(Space.S2.dp)) // F: lib/ui/kit/chat/kit_tool_row.dart:519
                        Counts(view.added, view.removed)
                    }
                }
        }
    }

    val duration = view.durationLabel?.takeIf { it.isNotEmpty() }
    val wordLines = if (wide) 2 else 1 // F: lib/ui/kit/chat/kit_tool_row.dart:556
    // F: lib/ui/kit/chat/kit_tool_row.dart:536-556 and 1216-1244 _stepWords
    val words: (@Composable () -> Unit)? =
        when (status) {
            KitToolStatus.Running ->
                if (duration == null) null
                else {
                    {
                        KitText(duration, Modifier.testTag("kit-step-counter"), role = TypeRole.Caption, tone = TextTone.Secondary, maxLines = 1, tabular = true)
                    }
                }
            KitToolStatus.Done ->
                if (duration == null) null
                else {
                    {
                        KitText(duration, Modifier.testTag("kit-tool-duration"), role = TypeRole.Caption, tone = TextTone.Tertiary, maxLines = wordLines)
                    }
                }
            // LOOK-5: a failed step's word is plain text1, never a danger colour.
            KitToolStatus.Failed -> {
                { KitText(view.statusLabel, role = TypeRole.Secondary, tone = TextTone.Primary, maxLines = wordLines) }
            }
            // F: lib/ui/kit/chat/kit_tool_row.dart:1240 waitingForYou shows its word in secondary
            KitToolStatus.Pending,
            KitToolStatus.WaitingForYou,
            KitToolStatus.Stopped -> {
                { KitText(view.statusLabel, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = wordLines) }
            }
        }
    // Only the group's line has a chevron; a step that opens another page keeps its forward arrow (it leaves this list).
    val forward = onOpen != null && body == null

    val lineBox: @Composable BoxScope.() -> Unit = {
        Box(Modifier.fillMaxWidth().heightIn(min = m.stepLine).align(Alignment.CenterStart).testTag("kit-step-line"), contentAlignment = Alignment.CenterStart) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = pad),
                verticalAlignment = if (wide) Alignment.Top else Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { sentence() }
                if (words != null) {
                    Spacer(Modifier.width(Space.S2.dp)) // F: lib/ui/kit/chat/kit_tool_row.dart:573
                    Box(Modifier.share(WORDS_SHARE).testTag("kit-tool-end")) { words() }
                }
                if (forward) {
                    Spacer(Modifier.width(Space.S1.dp)) // F: lib/ui/kit/chat/kit_tool_row.dart:581
                    // F: lib/ui/kit/chat/kit_tool_row.dart:1357-1371 _ForwardChevron, 20 dp text2, mirrored in RTL
                    KitIcon(KitIcons.chevronRight, Modifier.testTag("kit-tool-forward"), size = KitIconSize.Small, tone = TextTone.Secondary)
                }
            }
        }
    }

    // The line is 32 dp, but a line that taps keeps the kit's 48 dp target, centred on the line and reaching 8 dp over
    // each neighbour's edge; from 1.3x text the words wrap and the row is taller than 48 on its own.
    @Composable
    fun Target(tappable: @Composable (Modifier) -> Unit) {
        if (wide) tappable(Modifier.fillMaxWidth())
        else
            Box(Modifier.fillMaxWidth().height(m.stepLine), contentAlignment = Alignment.Center) {
                tappable(Modifier.fillMaxWidth().requiredHeight(tokens.minTarget.dp)) // F: lib/ui/kit/chat/kit_tool_row.dart:600
            }
    }

    val spoken = Modifier.semantics { stateDescription = view.statusLabel }
    RailNode(
        box = m.stepLine,
        top = 0.dp,
        modifier = modifier.widthIn(max = 700.dp), // F: lib/ui/kit/kit_layout.dart:48 paneDetailMaxWidth
        node = { KitToolNode(view, m.glyph, onRail = true) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            when {
                body != null ->
                    Target { target ->
                        KitTappable(
                            onClick = onToggle,
                            modifier =
                                target.then(spoken).semantics {
                                    if (open) collapse { onToggle(); true } else expand { onToggle(); true }
                                },
                            role = KitTappableRole.Row,
                            shape = KitShapeId.Tile,
                            content = lineBox,
                        )
                    }
                onOpen != null ->
                    Target { target ->
                        KitTappable(
                            onClick = onOpen,
                            modifier = target.then(spoken),
                            role = KitTappableRole.Row,
                            onClickLabel = stringResource(Res.string.kitToolOpenDetails),
                            shape = KitShapeId.Tile,
                            content = lineBox,
                        )
                    }
                else -> Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = view.statusLabel }, content = lineBox)
            }
            if (open) {
                // F: lib/ui/kit/chat/kit_tool_row.dart:1287-1311 _Body: one step down, two under, two between parts
                Column(
                    Modifier.fillMaxWidth()
                        .padding(top = Space.S1.dp, bottom = Space.S2.dp)
                        .graphicsLayer { alpha = fade.value }
                        .testTag("kit-tool-body"),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    CompositionLocalProvider(LocalKitStepBody provides true) { body?.invoke() }
                }
            }
        }
    }
}

/** At most [share] of the line's width (a child of a Row measured before the weighted ones). */
private fun Modifier.share(share: Float): Modifier = layout { measurable, constraints ->
    val placeable =
        measurable.measure(constraints.copy(minWidth = 0, maxWidth = (constraints.maxWidth * share).toInt()))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * A fold on the rail (Dart `_Fold` on a timeline, kit_message.dart:849-982): a thought or a notice as one line like
 * every step, 32 dp (more only for larger text), a quiet dot on the rail, [title] in the secondary role and tone, no
 * chevron (a tap opens it) and the 48 dp target centred on the line. Opened, [body] sits at the text's own x under the
 * line and fades in over QUICK. Outside a timeline nothing is drawn: the host keeps its own row there.
 */
@Composable
public fun KitStepFold(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    if (!kitOnStepRail()) return
    val m = railMeasures()
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val pad = (m.stepLine - m.first) / 2 // F: lib/ui/kit/chat/kit_message.dart:862-864
    val reduced = KitMotion.state.reduced
    val quick = KitMotion.specs.pace<Float>(KitPace.Quick)
    var wasOpen by remember { mutableStateOf(expanded) }
    val fade = remember(expanded) { Animatable(if (expanded && !wasOpen && !reduced) 0f else 1f) }
    LaunchedEffect(expanded) {
        wasOpen = expanded
        fade.animateTo(1f, quick)
    }
    val line: @Composable BoxScope.() -> Unit = {
        Box(
            Modifier.fillMaxWidth().heightIn(min = m.stepLine).align(Alignment.CenterStart),
            contentAlignment = if (m.wide) Alignment.TopStart else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(vertical = pad)) {
                KitText(title, role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
        }
    }
    RailNode(
        box = m.stepLine,
        top = 0.dp,
        modifier = modifier.widthIn(max = 700.dp), // F: lib/ui/kit/kit_layout.dart:48 paneDetailMaxWidth
        half = 3.dp,
        node = {
            // F: lib/ui/kit/chat/kit_step_node.dart:63-68 a 6 dp dot in text3
            Box(Modifier.size(6.dp).background(colors.text3, CircleShape)) // F: lib/ui/kit/chat/kit_step_node.dart:38
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            val tap: @Composable (Modifier) -> Unit = { target ->
                KitTappable(
                    onClick = onToggle,
                    modifier = target.semantics { if (expanded) collapse { onToggle(); true } else expand { onToggle(); true } },
                    role = KitTappableRole.Row,
                    shape = KitShapeId.Tile,
                    content = line,
                )
            }
            if (m.wide) tap(Modifier.fillMaxWidth())
            else
                Box(Modifier.fillMaxWidth().height(m.stepLine), contentAlignment = Alignment.Center) {
                    tap(Modifier.fillMaxWidth().requiredHeight(tokens.minTarget.dp))
                }
            if (expanded) {
                // F: lib/ui/kit/chat/kit_message.dart:985-1001 _Body: one step down, two under
                Box(
                    Modifier.fillMaxWidth()
                        .padding(top = Space.S1.dp, bottom = Space.S2.dp)
                        .graphicsLayer { alpha = fade.value }
                        .testTag("kit-step-fold-body")
                ) {
                    CompositionLocalProvider(LocalKitStepBody provides true) { body() }
                }
            }
        }
    }
}

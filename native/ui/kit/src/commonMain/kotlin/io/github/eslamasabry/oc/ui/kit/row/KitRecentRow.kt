package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitProminentRule
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowSource
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowTitle
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitPinned
import org.jetbrains.compose.resources.stringResource

/** The roomy conversation row's rules, apart from the drawing. */
public object KitRecentRowRules {
    /** The agent's mark, drawn bare at the row's start (no tile, no container: owner 2026-11-08). */
    public const val MARK_DP: Int = 28

    /** From the mark's end to the words. */
    public const val MARK_GAP_DP: Int = 14

    /** The row's least height: two lines of words with room above and below (A1: about 76 dp). */
    public const val MIN_HEIGHT_DP: Int = 76

    /** The project dot beside the project's name. */
    public const val DOT_DP: Int = 7

    /**
     * The most of the row's width its end (the time over the state word) takes: a long state word is cut there with
     * an ellipsis, so it never crushes the title and the project to a few letters (issue 147: "Build …" at 360 dp).
     */
    public const val END_SHARE: Float = 0.38f

    /** Where the hairline between two rows starts: past the mark, on the words' edge. */
    public fun textStartDp(gutter: Int): Int = gutter + MARK_DP + MARK_GAP_DP

    /**
     * Which of the theme's [count] palette colours a project takes: decided by its name alone (FNV-1a over the
     * lower-cased name), so the same project keeps its colour on every row, start and phone. -1 when there is none.
     */
    public fun paletteSlot(project: String, count: Int): Int {
        if (count <= 0 || project.isBlank()) return -1
        var hash = 2166136261u
        for (c in project.trim().lowercase()) {
            hash = hash xor c.code.toUInt()
            hash *= 16777619u
        }
        return (hash % count.toUInt()).toInt()
    }

    /**
     * The theme's own colours a project can take: the partner and the code colours, picked per pack for the page's
     * ground. Never the accent, amber or green: those say Working, Needs you and done on the same row.
     */
    public fun palette(colors: KitColors): List<Color> =
        listOf(colors.partner, colors.codeKeyword, colors.codeString, colors.codeType).distinct()

    /** The second line: the preview when there is one, else the quiet [meta] (server and the like). */
    public fun line(preview: String?, meta: String?): String? =
        preview?.trim()?.takeIf { it.isNotEmpty() } ?: meta?.trim()?.takeIf { it.isNotEmpty() }

    /** What a screen reader says, in order: title, pinned, agent, state, project, the line, time. */
    public fun spoken(
        title: String,
        pinned: String?,
        agent: String?,
        state: String?,
        project: String?,
        line: String?,
        time: String?,
    ): String = listOfNotNull(title, pinned, agent, state, project, line, time).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * One conversation as a roomy row in a [KitRowPanel] (the Conversations home, A1). Leading: the [agent]'s monochrome
 * mark, bare, 28 dp (a neutral agent glyph when its mark is unknown). Then the bold [title] on one line and, under it,
 * one line cut with an ellipsis: the project's dot and name ([project], null when the list holds one project) and the
 * [preview] of the last message, or the quiet [meta] when there is none. At the end: the [time] on top and the
 * [state] word under it in plain coloured text (Working green with a small dot, Needs you amber, the rest quiet). No
 * tile, no badge, no pill. [below] holds the row's own acts (a waiting request's Allow / Reject) on the words' edge.
 *
 * States: default, running, needs you, failed, done, pinned, untappable, waiting ([KitRowState.waiting]: the working
 *   mark alone in the time's place)
 * Text: reading (title 1 line, 2 at large text; the second line 1 line)
 * Cap: List
 * Motion: none of its own (the panel eases its height)
 * Hidden: long-press (the row's menu; the same acts are on the conversation's own page)
 * Replaces: none
 */
@Composable
public fun KitRecentRow(
    title: String,
    agent: KitFeedAgent,
    time: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    preview: String? = null,
    meta: String? = null,
    project: String? = null,
    state: KitRowState? = null,
    pinned: Boolean = false,
    growKey: Any? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    below: (@Composable () -> Unit)? = null,
    /** False when every row is the same agent: its mark still shows, its name is not said on each row. */
    speakAgent: Boolean = true,
    /** How many lines the [preview] may take: 1 for a quiet line, more where the words decide something (a command to allow). */
    previewLines: Int = 1,
) {
    val colors = LocalKitColors.current
    val gutter = kitRowGutter()
    val large = LocalDensity.current.fontScale > LARGE_TEXT
    val line = KitRecentRowRules.line(preview, meta)
    val shownProject = project?.trim()?.takeIf { it.isNotEmpty() }
    val pinnedWord = if (pinned) stringResource(Res.string.kitPinned) else null
    // A waiting row shows the working mark in the time's place: the time is neither drawn nor said, the word is said.
    val waiting = state?.waiting == true
    val shownTime = time.takeUnless { waiting }
    val words = KitRecentRowRules.spoken(title, pinnedWord, agent.name.takeIf { speakAgent }, state?.word, shownProject, line, shownTime)
    val m =
        Modifier.fillMaxWidth()
            .kitGrowSource(growKey)
            .testTag("kit-recent-row")
            .semantics(mergeDescendants = true) { contentDescription = words }
    val body: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val endMax = maxWidth * KitRecentRowRules.END_SHARE
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = KitRecentRowRules.MIN_HEIGHT_DP.dp)
                    .padding(start = gutter, end = gutter, top = Space.S3.dp, bottom = if (below == null) Space.S3.dp else Space.S2.dp)
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mark(agent)
                Spacer(Modifier.width(KitRecentRowRules.MARK_GAP_DP.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        BasicText(
                            title,
                            Modifier.weight(1f, fill = false).kitGrowTitle(growKey),
                            style = LocalKitTypography.current.style(TypeRole.RowTitle).copy(color = colors.text1, fontWeight = FontWeight.SemiBold),
                            maxLines = if (large) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pinned) KitGlyph(KitIcons.pin, colors.text2, 14.dp)
                    }
                    if (line != null || shownProject != null) SecondLine(shownProject, line, previewLines)
                }
                if (waiting) {
                    Spacer(Modifier.width(Space.S3.dp))
                    WaitingEnd()
                } else if (time != null || state?.word != null) {
                    Spacer(Modifier.width(Space.S3.dp))
                    End(time, state, Modifier.widthIn(max = endMax))
                }
            }
        }
    }
    // The acts under the words are their own targets, outside the row's one tap and its one spoken sentence.
    Column(modifier.fillMaxWidth().kitMaxWidth(CapRole.List)) {
        if (onClick == null) Box(m) { body() }
        else KitTappable(onClick, m, role = KitTappableRole.Row, shape = KitShapeId.Square, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel) { body() }
        below?.let {
            Box(
                Modifier.fillMaxWidth()
                    .padding(start = KitRecentRowRules.textStartDp(gutter.value.toInt()).dp, end = gutter, bottom = Space.S3.dp)
            ) { it() }
        }
    }
}

/** The agent's mark, bare; a neutral agent glyph in the quiet ink when its mark is unknown. */
@Composable
private fun Mark(agent: KitFeedAgent) {
    val size = KitRecentRowRules.MARK_DP.dp
    if (agent.id.isNotBlank()) KitProviderLogo(agent.id, agent.name, agent = true, Modifier.size(size), markSize = size)
    else Box(Modifier.size(size), contentAlignment = Alignment.Center) { KitGlyph(KitIcons.agent, LocalKitColors.current.text2, size - 6.dp) }
}

/** The project's dot and name (when shown), then the preview or the quiet words, on one line cut with an ellipsis. */
@Composable
private fun SecondLine(project: String?, line: String?, previewLines: Int) {
    val colors = LocalKitColors.current
    val style = LocalKitTypography.current.style(TypeRole.Secondary).copy(color = colors.text2)
    if (previewLines > 1 && line != null) {
        // Words that decide something (a command to allow) read in full on their own lines, under the project.
        Column(Modifier.fillMaxWidth()) {
            if (project != null) {
                val palette = KitRecentRowRules.palette(colors)
                val slot = remember(project, palette.size) { KitRecentRowRules.paletteSlot(project, palette.size) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(KitRecentRowRules.DOT_DP.dp)
                            .clip(CircleShape)
                            .background(palette.getOrElse(slot) { colors.text3 })
                            .testTag("kit-recent-project-dot")
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicText(
                        "\u2068$project\u2069",
                        style = style.copy(color = colors.text1.copy(alpha = PROJECT_ALPHA)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            BasicText(line, Modifier.fillMaxWidth(), style = style, maxLines = previewLines, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A project name next to a preview takes at most 40 % of the line, so the preview always reads.
        val projectMax = if (line != null) maxWidth * PROJECT_SHARE else maxWidth
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (project != null) {
                val palette = KitRecentRowRules.palette(colors)
                val slot = remember(project, palette.size) { KitRecentRowRules.paletteSlot(project, palette.size) }
                Box(
                    Modifier.size(KitRecentRowRules.DOT_DP.dp)
                        .clip(CircleShape)
                        .background(palette.getOrElse(slot) { colors.text3 })
                        .testTag("kit-recent-project-dot")
                )
                Spacer(Modifier.width(6.dp))
                BasicText(
                    "\u2068$project\u2069",
                    Modifier.widthIn(max = projectMax),
                    style = style.copy(color = colors.text1.copy(alpha = PROJECT_ALPHA)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line != null) BasicText("\u00a0·\u00a0", style = style, maxLines = 1)
            }
            if (line != null)
                BasicText(line, Modifier.weight(1f), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The time on top; the state word under it in its colour (Working with a small green dot). [modifier] caps its width
 * ([KitRecentRowRules.END_SHARE]); a longer word is cut with an ellipsis.
 */
@Composable
private fun End(time: String?, state: KitRowState?, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        if (time != null)
            KitText(time.replace(' ', ' '), role = TypeRole.Caption, tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary, maxLines = 1)
        val word = state?.word
        if (state != null && word != null) {
            val tone = state.tone
            val color = stateColor(colors, tone)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (tone == KitRowStateTone.Running)
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color).testTag("kit-recent-live-dot"))
                BasicText(
                    word,
                    Modifier.weight(1f, fill = false),
                    style =
                        typography.style(TypeRole.Secondary).copy(
                            color = color,
                            fontWeight = if (tone == KitRowStateTone.NeedsYou || tone == KitRowStateTone.Running) FontWeight.SemiBold else null,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The end of a row that waits on something that ends by itself: the kit's working mark alone, in the quiet ink, at the
 * time's size (a still dot under reduced motion, in tests and on a hidden page, as every kit working mark).
 */
@Composable
private fun WaitingEnd() {
    KitWorkingMark(LocalKitColors.current.text2, size = WAITING_MARK_DP.dp)
}

private const val WAITING_MARK_DP = 16

/** Working is green (the theme's success), Needs you amber (attention); every other word stays quiet. */
private fun stateColor(colors: KitColors, tone: KitRowStateTone): Color =
    when (tone) {
        KitRowStateTone.NeedsYou -> colors.attention
        KitRowStateTone.Running -> colors.success
        else -> colors.text2
    }

/**
 * Rows grouped on one rounded panel that follows the Style in force (Glass frosted, Solid's surface, Soft's raised
 * face, …) and the Corners knob, on the page gutter, under an optional [label]. Unlike [KitRowGroup], it frames its
 * rows in every style (Solid included): the home's sections read as groups. Hairlines between rows start on the words'
 * edge past a [KitRecentRow]'s mark ([leadingMarks]) or on the row's gutter. The panel eases its height when a row
 * comes or goes (still under reduced motion).
 *
 * States: default, with label
 * Text: none (its rows bring their own)
 * Cap: List
 * Motion: height eases (Standard pace); none under reduced motion
 * Hidden: none
 * Replaces: none
 */
@Composable
public fun KitRowPanel(
    modifier: Modifier = Modifier,
    label: String? = null,
    leadingMarks: Boolean = true,
    gapBefore: Dp? = null,
    /** A line of at least 1 dp between rows (B-173, Flutter #129): the home list on dark screens. */
    prominentDividers: Boolean = false,
    /**
     * Quiet words at the end of the label's line that say what is happening for this group right now ("Starting
     * Linux…"); null when nothing is. They fade in and out; the label and the rows never move. A screen reader hears
     * them once when they change.
     */
    labelStatus: String? = null,
    /**
     * The one act that belongs to [labelStatus] ("Download on mobile data"): the section label's own end act, in its
     * 48 dp target. The status words may then run to two lines at large text instead of being cut.
     */
    labelAction: KitLineAction? = null,
    rows: @Composable () -> Unit,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Panel, SurfaceLevel.Surface1, ownFill = colors.surface1))
    val grow =
        if (KitMotion.state.reduced) Modifier
        else Modifier.animateContentSize(KitMotion.specs.pace(KitPace.Standard))
    Column(modifier.fillMaxWidth().kitMaxWidth(CapRole.List).testTag("kit-row-panel")) {
        label?.let { KitSectionLabel(it, gapBefore = gapBefore, trailing = kitLabelStatus(labelStatus, labelAction)) }
        Box(
            Modifier.fillMaxWidth()
                .padding(horizontal = tokens.gutter.dp)
                .then(look.modifier)
                .clip(look.shape)
                .then(grow)
        ) {
            // A panel is a surface of its own: its rows keep their gutter inside it.
            CompositionLocalProvider(LocalKitOnRail provides false) {
                val inset = if (leadingMarks) KitRecentRowRules.textStartDp(tokens.gutter) else tokens.gutter
                // The lines stop short of the end edge where the style asks (Glass: one gutter, clear of the rim).
                val endInset = tokens.gutter * LocalKitStyle.current.spec.tokens.dividerEndGutters
                KitHairlineColumn(inset.dp, endInset.dp, colors.hairline, if (prominentDividers) kitProminentRule() else null, rows)
            }
        }
    }
}

/**
 * [KitRowPanel]'s status words: null until the first words arrive (a panel that never has any keeps its plain label);
 * after that the last words stay while they fade out.
 */
@Composable
private fun kitLabelStatus(status: String?, action: KitLineAction? = null): (@Composable () -> Unit)? {
    val held = remember { mutableStateOf<String?>(null) }
    if (status != null && held.value != status) held.value = status
    val words = held.value ?: return null
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = status != null
    val reduced = KitMotion.state.reduced
    val specs = KitMotion.specs
    return {
        AnimatedVisibility(
            visibleState = visible,
            enter = if (reduced) EnterTransition.None else fadeIn(specs.enter()),
            exit = if (reduced) ExitTransition.None else fadeOut(specs.exit()),
        ) {
            Column(if (action != null) Modifier.fillMaxWidth() else Modifier) {
                KitText(
                    words,
                    Modifier.testTag("kit-row-panel-status").semantics { liveRegion = LiveRegionMode.Polite },
                    role = TypeRole.Label,
                    tone = TextTone.Secondary,
                    maxLines = if (action != null) 2 else 1,
                )
                // The status's own act, under its words and level with them: a 48 dp target, the accent so it reads as
                // something to tap (never a bar or a row of its own).
                if (status != null && action != null)
                    KitTappable(
                        onClick = action.onClick,
                        enabled = action.enabled,
                        disabledReason = action.disabledReason,
                        modifier = Modifier.heightIn(min = LocalKitTokens.current.minTarget.dp).testTag("kit-row-panel-status-action"),
                    ) {
                        Box(Modifier.heightIn(min = LocalKitTokens.current.minTarget.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                            KitText(action.label, role = TypeRole.Label, tone = TextTone.Accent, maxLines = 2)
                        }
                    }
            }
        }
    }
}

/**
 * The way from a short list to the whole one: [label] and its [count] ("All conversations  11 ›") as one row on its own
 * [KitRowPanel], the chevron mirrored in right-to-left. The whole row is one target; a screen reader hears "label, count".
 *
 * States: default
 * Text: frame (label), tabular count
 * Cap: List
 * Motion: the kit press only
 * Hidden: none
 * Replaces: none
 */
@Composable
public fun KitPanelLinkRow(label: String, count: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val gutter = kitRowGutter()
    KitTappable(
        onClick,
        modifier.fillMaxWidth().kitMaxWidth(CapRole.List).testTag("kit-panel-link-row").semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(label, count).joinToString(", ")
        },
        role = KitTappableRole.Row,
        shape = KitShapeId.Square,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = LINK_ROW_DP.dp).padding(horizontal = gutter).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitText(label, Modifier.weight(1f), role = TypeRole.RowTitle, maxLines = 1)
            if (count != null) {
                Spacer(Modifier.width(Space.S2.dp))
                KitText(count, role = TypeRole.Secondary, tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary, maxLines = 1, tabular = true)
            }
            Spacer(Modifier.width(Space.S2.dp))
            KitGlyph(KitIcons.chevronRight, colors.text2, 18.dp)
        }
    }
}

private const val LINK_ROW_DP = 56
private const val LARGE_TEXT = 1.3f
private const val PROJECT_ALPHA = 0.86f
private const val PROJECT_SHARE = 0.4f

package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitInlineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitMayTruncate
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowSource
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowTitle
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** The supporting lines a row allows (Dart A11Y-8): 1 at normal size, 2 from 1.3×, 3 from 2.0×. */
public fun kitRowSupportingLines(textScale: Float, asked: Int = 1): Int =
    when {
        textScale >= 2.0f -> maxOf(asked, 3)
        TextScalePolicy.isLargeText(textScale) -> maxOf(asked, 2)
        else -> asked
    }

/** A row title: one line below 1.3×, two from 1.3× (the full value stays in semantics). */
public fun kitRowTitleLines(textScale: Float, asked: Int = 1): Int = if (TextScalePolicy.isLargeText(textScale)) maxOf(asked, 2) else asked

/**
 * A file name that wraps only after `_`, `-` or before the extension's dot, never mid-word (Dart `kitBreakableFileName`).
 * The break chances are zero-width spaces; semantics read the plain name.
 */
public fun kitBreakableFileName(name: String): String {
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: -1
    val out = StringBuilder()
    name.forEachIndexed { i, c ->
        if (i == dot) out.append('​')
        out.append(c)
        if (c == '_' || c == '-') out.append('​')
    }
    return out.toString()
}

/**
 * The one list row (Dart `KitRow`; plan 06 §16.2): what it is, one supporting line, and its value, chevron or one act.
 * 56 dp (one line) or 64 dp (two lines), growing with text; the title wraps from 1.3× and the supporting line takes 2
 * lines from 1.3× and 3 from 2.0×. A disabled row says why: [disabledReason] replaces the supporting line and is told
 * to TalkBack once; title and glyph go to `text3` (never through alpha). [selected] is the row shown in the detail
 * pane. [server] prefixes the supporting line ("laptop · …"). A long-press opens the row's menu (S1-KITA2's
 * `KitMenu`); every menu item must also be reachable another way.
 *
 * States: default, two-line, disabled, selected
 * (A destructive row goes in `KitRowGroup`'s destructive slot, last; its words name the act — no red, LOOK-5.)
 * Text: reading
 * Cap: List
 * Motion: pressed fill ≥ PRESS_HOLD (KitTappable); with [growKey] the opened page grows out of the row (Full only)
 * Hidden: long-press when [onLongClick] is set (the caller registers its tip and its visible twin)
 * Feed rows (Dart `KitFeedItem`): [state] puts its word in its colour at the start of the supporting line ("Needs
 * you · shopfront · Allow running the tests?") and, for needs-you and running, a mark in the lead column (an amber
 * dot, a mint ring), so a list of feed rows keeps one aligned lead column. [meta] is the row's time or count, in the
 * 12 sp meta size at the end of the title line, so times line up in one column.
 *
 * Unavailable (Dart `KitRow.unavailable`): [unavailableReason] dims the title and glyph and wraps the reason whole;
 * [enable] trails as the way forward, and a tap anywhere on the row runs it. [menu] opens on a long-press (Dart
 * `KitRow.menu`); every item must also be reachable another way. [leadingTone] inks a bare leading glyph in another
 * tone (Dart `KitRow.icon(color:)`; a destructive row's glyph in `text1`).
 *
 * Replaces: KitRow (lib/ui/kit/kit_row.dart), KitFeedItem (lib/ui/kit/kit_feed_item.dart)
 */
@Composable
public fun KitRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: KitRowLeading? = null,
    trailing: KitRowTrailing = KitRowTrailing.None,
    server: String? = null,
    titleIsFileName: Boolean = false,
    supportingMaxLines: Int = 1,
    enabled: Boolean = true,
    disabledReason: String? = null,
    selected: Boolean = false,
    growKey: Any? = null,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    state: KitRowState? = null,
    meta: String? = null,
    titleMaxLines: Int = 1,
    titleAccent: Boolean = false,
    unavailableReason: String? = null,
    enable: KitLineAction? = null,
    menu: List<KitMenuItem> = emptyList(),
    leadingTone: TextTone? = null,
    padding: PaddingValues? = null,
    /** Search's matched words, marked in the title and the supporting line in the accent ink ([KitHighlight]). */
    highlight: String? = null,
    onClick: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val longClick = if (menu.isNotEmpty()) ({ menuOpen = true }) else onLongClick
    val unavailable = unavailableReason != null
    val body = @Composable {
    RowBody(
        title = title, modifier = modifier, supporting = if (unavailable) unavailableReason else supporting, leading = leading,
        trailingGiven = if (unavailable && enable != null) KitRowTrailing.Action(enable) else trailing,
        server = server, titleIsFileName = titleIsFileName, supportingMaxLines = supportingMaxLines,
        enabled = enabled, disabledReason = disabledReason, selected = selected, growKey = growKey,
        onClickLabel = onClickLabel, onLongClick = longClick, onLongClickLabel = onLongClickLabel,
        state = state, meta = meta, onClick = if (unavailable) enable?.onClick else onClick,
        titleMaxLines = titleMaxLines, titleAccent = titleAccent, padding = padding,
        unavailable = unavailable,
        leadingTone = leadingTone,
        highlight = highlight,
    )
    }
    if (menu.isEmpty()) body()
    else KitMenu(expanded = menuOpen, items = menu, onDismissRequest = { menuOpen = false }) { body() }
}

@Composable
public fun KitRow(
    title: String,
    supporting: AnnotatedString,
    modifier: Modifier = Modifier,
    leading: KitRowLeading? = null,
    trailing: KitRowTrailing = KitRowTrailing.None,
    supportingMaxLines: Int = 1,
    enabled: Boolean = true,
    disabledReason: String? = null,
    selected: Boolean = false,
    state: KitRowState? = null,
    onClick: (() -> Unit)? = null,
) {
    RowBody(
        title = title,
        modifier = modifier,
        supporting = supporting.text,
        leading = leading,
        trailingGiven = trailing,
        supportingMaxLines = supportingMaxLines,
        enabled = enabled,
        disabledReason = disabledReason,
        selected = selected,
        state = state,
        onClick = onClick,
        supportingSpan = supporting,
    )
}

@Composable
private fun RowBody(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: KitRowLeading? = null,
    trailingGiven: KitRowTrailing = KitRowTrailing.None,
    server: String? = null,
    titleIsFileName: Boolean = false,
    supportingMaxLines: Int = 1,
    enabled: Boolean = true,
    disabledReason: String? = null,
    selected: Boolean = false,
    growKey: Any? = null,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    state: KitRowState? = null,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    titleMaxLines: Int = 1,
    titleAccent: Boolean = false,
    supportingSpan: AnnotatedString? = null,
    unavailable: Boolean = false,
    leadingTone: TextTone? = null,
    padding: PaddingValues? = null,
    highlight: String? = null,
) {
    // C04: a chosen row says so with a check at its trailing edge when it has nothing else there.
    val trailing = if (selected && trailingGiven == KitRowTrailing.None) KitRowTrailing.Check else trailingGiven
    kitAssert(title.isNotBlank()) { "KitRow: a row needs a title" }
    kitAssert(enabled || !disabledReason.isNullOrBlank()) { "KitRow: a disabled row must say why (B-KIT-051)" }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val onRail = LocalKitOnRail.current
    val glassChosen = if (selected) io.github.eslamasabry.oc.ui.kit.effects.kitGlassChosenRow() else null
    val scale = kitTextScale()
    val large = TextScalePolicy.isLargeText(scale)
    val supportLine =
        when {
            !enabled -> disabledReason
            server != null && supporting != null -> "$server · $supporting"
            server != null -> server
            else -> supporting
        }
    val value = trailing as? KitRowTrailing.Value
    val action = (trailing as? KitRowTrailing.Action)?.action?.let {
        if (enabled) it else it.copy(enabled = false, disabledReason = disabledReason, onClick = {})
    }
    val iconAction = (trailing as? KitRowTrailing.IconAction)?.action?.let {
        if (enabled) it else it.copy(enabled = false, disabledReason = disabledReason, onClick = {})
    }
    val actionBelow = action != null && large
    // A caret ends its ink on the row's end rail (KitChevron in a row, kit_row_parts.dart:181-198): the row pads its
    // end by space1 and the caret's slot leaves the rest of the rail minus the glyph's own ink inset.
    val chevronEnds = (trailing == KitRowTrailing.Chevron || (value != null && value.chevron)) && kitRowGutter() > 0.dp
    val tappable = onClick != null || onLongClick != null
    val base =
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .kitGrowSource(growKey)
            .then(
                if (tappable) {
                    Modifier.kitTappable(
                        onClick = onClick ?: {},
                        enabled = enabled,
                        disabledReason = disabledReason,
                        role = KitTappableRole.Row,
                        onClickLabel = onClickLabel,
                        onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel,
                        shape = KitShapeId.Square,
                    )
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                if (selected) this.selected = true
                if (titleIsFileName) contentDescription = title
            }
            .then(
                if (!selected) Modifier
                // On a flat list (Solid) the chosen row is a rounded plate inset space2 from the row's ends, never a
                // square band across the page (kit_row.dart:672-690).
                else if (LocalKitStyle.current.spec.tokens.flatLists) Modifier.kitChosenPlate(colors.accent.copy(alpha = CHOSEN_ROW_TINT))
                // Glass: accent-tinted frost with the accent rim, its corners following the group's (owner 2026-11-08).
                else glassChosen ?: Modifier.kitSelectedFill(colors.accent.copy(alpha = CHOSEN_ROW_TINT), onRail)
            )
            .heightIn(min = (if (supportLine != null || state != null) tokens.rowHeightTwo else tokens.rowHeightOne).dp)
            // A row's 48 dp glyph target (its ⋯ menu or a labelled glyph) ends near the edge, so its glyph lines up
            // with the chevrons of the rows around it and with the top bar's ⋯ (P-112: Files' row ⋯ sat 12 dp in).
            // Dart `KitRow.padding`: a row with its own padding lays out as given (no rails, no space2 around the words),
            // e.g. a sheet's body rows `EdgeInsets.symmetric(vertical: 8)` (kit_row.dart:491-513).
            .then(if (padding != null) Modifier.padding(padding) else Modifier.padding(
                start = kitRowGutter(),
                // A 48 dp target (⋯, a glyph act, a text act) reaches to space1 from the end; a chevron or a value ends its
                // ink on the row's end rail itself (Dart `trailingRailOf`), so the row keeps the gutter for them.
                end = if (rowTargetOnEdge(trailing) || action != null || chevronEnds) Space.S1.dp else kitRowGutter(),
            ))
            .testTag("kit-row")
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        // Dart `padded`: the leading mark and the words keep space 2 above and below only on a row without its own
        // padding; a row with its own padding lays out as given (kit_row.dart:491-498).
        val own = padding == null
        val lead = if (own) Modifier.padding(vertical = Space.S2.dp) else Modifier
        when {
            leading is KitRowLeading.Glyph && !leading.tile -> {
                Box(lead.size((tokens.iconTile * minOf(scale, TextScalePolicy.ICON_CAP)).dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    // The platform's list icon (KitRowIconBare.size, kit_row.dart:1085) drawn from the glyph font as
                    // Flutter's Icon is, growing with the text up to the icon cap.
                    io.github.eslamasabry.oc.ui.kit.icon.KitIcon(
                        leading.icon,
                        size = io.github.eslamasabry.oc.ui.kit.icon.KitIconSize.Large,
                        tone = if (!enabled || unavailable) TextTone.Tertiary else if (leading.current) TextTone.Accent else leadingTone ?: TextTone.Secondary,
                        growsWithText = true,
                    )
                }
            }
            leading is KitRowLeading.Logo && leading.bare -> {
                // A bare mark (Search's rows): the agent's or provider's mark at the list icon size, no tile behind it.
                val grow = minOf(scale, TextScalePolicy.ICON_CAP)
                val icon = (KitIconSize.Large.dp * grow).dp
                Box(lead.size((tokens.iconTile * grow).dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    KitProviderLogo(leading.id, leading.name, leading.agent, Modifier.size(icon), markSize = icon)
                }
            }
            leading != null -> Box(lead) { RowLeadingTile(leading, dim = !enabled || unavailable, selected = selected) }
            state != null -> Box(lead) { RowStateMark(state.tone) }
        }
        if (leading != null || state != null) Spacer(Modifier.width(Space.S3.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            val measurer = rememberTextMeasurer()
            val typography = LocalKitTypography.current
            val density = LocalDensity.current
            // Budget the actual title, value, gap and chevron after the leading tile has been measured.
            val valueBelow = value != null && (large || run {
                val titleWidth = measurer.measure(
                    title, style = typography.style(TypeRole.RowTitle).copy(textDirection = TextDirection.Content),
                    softWrap = false, maxLines = 1,
                ).size.width
                val valueWidth = measurer.measure(
                    value.text, style = typography.style(if (value.prominent) TypeRole.Body else TypeRole.Secondary).copy(textDirection = TextDirection.Content),
                    softWrap = false, maxLines = 1,
                ).size.width
                val chevron = if (value.chevron) {
                    (Space.S1 + tokens.smallIcon * minOf(scale, TextScalePolicy.FRAME_CAP)).dp
                } else 0.dp
                val gapsAndChevron = with(density) { (Space.S3.dp + chevron).roundToPx() }
                titleWidth.toLong() + valueWidth + gapsAndChevron > constraints.maxWidth
            })
            // The text never runs into what trails it: space2 before the trailing part (kit_row.dart:640).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                Column(
                    Modifier.weight(1f).then(if (own) Modifier.padding(vertical = Space.S2.dp) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(2.dp), // F: lib/ui/kit/kit_tokens.dart:398
                ) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                        KitText(
                            if (titleIsFileName) kitBreakableFileName(title) else title,
                            Modifier.weight(1f).kitGrowTitle(growKey).testTag("kit-row-title"),
                            role = TypeRole.RowTitle,
                            tone = if (!enabled || unavailable) TextTone.Tertiary else if (titleAccent) TextTone.Accent else TextTone.Primary,
                            maxLines = if (titleIsFileName && large) Int.MAX_VALUE else kitRowTitleLines(scale, titleMaxLines),
                            highlight = highlight,
                            // Flutter lays a row title out in the ambient direction, so an Arabic title in an English
                            // list starts on the reading edge (kit_sheet_default: "العربية"); never at the row's end.
                            textAlign = if (LocalLayoutDirection.current == LayoutDirection.Ltr) TextAlign.Left else TextAlign.Right,
                        )
                        meta?.let {
                            KitText(
                                it,
                                Modifier.padding(top = 2.dp).testTag("kit-row-meta"),
                                role = TypeRole.Caption,
                                tone = TextTone.Tertiary,
                                kind = KitTextKind.Frame,
                                maxLines = 1,
                            )
                        }
                    }
                    val stateWord = state?.takeIf { enabled }
                    val word = stateWord?.word
                    if (enabled && supportingSpan != null) {
                        BasicText(
                            supportingSpan,
                            Modifier.kitMaxWidth(CapRole.Reading).testTag("kit-row-supporting")
                                .kitMayTruncate("row-supporting"),
                            style = LocalKitTypography.current.style(TypeRole.Secondary).copy(
                                color = colors.text2, textDirection = TextDirection.Content,
                            ),
                            maxLines = kitRowSupportingLines(scale, supportingMaxLines),
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (stateWord != null && word != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KitText(
                                word,
                                Modifier.testTag("kit-row-state"),
                                role = TypeRole.Secondary,
                                tone =
                                    when (stateWord.tone) {
                                        KitRowStateTone.NeedsYou -> TextTone.Attention
                                        KitRowStateTone.Running -> TextTone.Accent
                                        else -> TextTone.Secondary
                                    },
                                maxLines = 1,
                            )
                            supportLine?.let {
                                KitText(
                                    " · $it",
                                    Modifier.weight(1f, fill = false).testTag("kit-row-supporting")
                                        .kitMayTruncate("row-supporting"),
                                    role = TypeRole.Secondary,
                                    tone = TextTone.Secondary,
                                    maxLines = 1,
                                    highlight = highlight,
                                )
                            }
                        }
                    } else {
                        supportLine?.let {
                            KitText(
                                it,
                                Modifier.testTag("kit-row-supporting").kitMayTruncate("row-supporting"),
                                role = TypeRole.Secondary,
                                tone = TextTone.Secondary,
                                maxLines = if (!enabled || unavailable) Int.MAX_VALUE else kitRowSupportingLines(scale, supportingMaxLines),
                                highlight = highlight.takeIf { enabled && !unavailable },
                            )
                        }
                    }
                    if (valueBelow) RowValue(value)
                    if (actionBelow) KitInlineAction(action)
                }
                when {
                    iconAction != null -> KitIconButton(
                        icon = requireNotNull(iconAction.icon),
                        label = iconAction.semanticsLabel ?: iconAction.label,
                        onClick = iconAction.onClick,
                        modifier = Modifier.testTag(iconAction.testTag ?: "kit-row-icon-action"),
                        enabled = KitEnabled.of(iconAction.enabled, iconAction.disabledReason),
                    )
                    trailing is KitRowTrailing.Menu -> RowMenu(trailing, enabled, disabledReason)
                    value != null && !valueBelow ->
                        if (chevronEnds) Box(Modifier.padding(end = chevronEndPad(tokens.smallIcon * minOf(scale, TextScalePolicy.FRAME_CAP)))) { RowValue(value) }
                        else RowValue(value)
                    // One tertiary button, trailing (Dart `KitButton.fromAction(enable, role: tertiary, expand: false)`); it
                    // keeps its own side padding beside the words.
                    action != null && !actionBelow -> Box(Modifier.padding(horizontal = Space.S2.dp)) {
                        io.github.eslamasabry.oc.ui.kit.KitButton(
                            io.github.eslamasabry.oc.ui.kit.KitAction(
                                action.label, action.onClick,
                                enabled = io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled.of(action.enabled, action.disabledReason),
                                icon = action.icon, semanticsLabel = action.semanticsLabel,
                            ),
                            Modifier.testTag(action.testTag ?: "kit-row-action"),
                            io.github.eslamasabry.oc.ui.kit.KitButtonRole.Tertiary,
                        )
                    }
                    trailing is KitRowTrailing.Locked -> RowLockedValue(trailing)
                    trailing is KitRowTrailing.Copy && enabled ->
                        io.github.eslamasabry.oc.ui.kit.action.KitIconButtonCopy(
                            text = { trailing.text },
                            label = trailing.label,
                        )
                    trailing is KitRowTrailing.Chip ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), verticalAlignment = Alignment.CenterVertically) {
                            io.github.eslamasabry.oc.ui.kit.chip.KitChip(trailing.label, icon = trailing.icon)
                            if (trailing.checked)
                                io.github.eslamasabry.oc.ui.kit.icon.KitIcon(
                                    io.github.eslamasabry.oc.ui.kit.icon.KitIcons.check,
                                    Modifier.testTag("kit-row-check"),
                                    size = io.github.eslamasabry.oc.ui.kit.icon.KitIconSize.Small,
                                )
                        }
                    trailing == KitRowTrailing.Chevron ->
                        if (chevronEnds) Box(Modifier.padding(end = chevronEndPad(tokens.smallIcon * minOf(scale, TextScalePolicy.FRAME_CAP)))) { RowChevron() }
                        else RowChevron()
                    trailing == KitRowTrailing.Check -> io.github.eslamasabry.oc.ui.kit.icon.KitIcon(
                        io.github.eslamasabry.oc.ui.kit.icon.KitIcons.check,
                        Modifier.testTag("kit-row-check"),
                        size = io.github.eslamasabry.oc.ui.kit.icon.KitIconSize.Small,
                        // Glass marks the chosen row in the accent: its frost, its rim and its check.
                        tone = if (glassChosen != null) TextTone.Accent else TextTone.Primary,
                    )
                    else -> Unit
                }
            }
        }
    }
}

/** [KitRowTrailing.Menu]: the ⋯ button and its menu, owned by the row so the highlight covers both. */
@Composable
private fun RowMenu(menu: KitRowTrailing.Menu, enabled: Boolean, disabledReason: String?) {
    var open by remember { mutableStateOf(false) }
    KitMenu(expanded = open, items = menu.items, onDismissRequest = { open = false }, title = menu.title) {
        KitIconButton(
            KitIcons.more,
            menu.label,
            { open = true },
            Modifier.testTag("kit-row-menu"),
            enabled = KitEnabled.of(enabled, disabledReason),
        )
    }
}

/**
 * A selected row's fill (E20-S08). On the page it is the row's box, which bleeds to the window edges. On a sheet's rail
 * the row sits 20 dp in from the sheet's edges, so the fill reaches past the rail to them too: the highlight starts
 * on the sheet's edge, as a page row's does on the window's, not on a third edge of its own.
 */
private fun Modifier.kitSelectedFill(color: androidx.compose.ui.graphics.Color, onRail: Boolean): Modifier =
    if (!onRail) background(color)
    else
        drawBehind {
            val rail = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.RAIL.dp.toPx()
            drawRect(
                color,
                topLeft = androidx.compose.ui.geometry.Offset(-rail, 0f),
                size = androidx.compose.ui.geometry.Size(size.width + 2 * rail, size.height),
            )
        }

/**
 * The room after a caret's box so its ink ends on the row's end rail (Dart `KitOptical.endPad` with the row's
 * `trailingRail` = space4 - space1): the caret-right glyph's ink ends .28125 of its size short of its box.
 */
internal fun chevronEndPad(size: Float): androidx.compose.ui.unit.Dp =
    maxOf(0f, (Space.S4 - Space.S1) - CARET_INK_END * size).dp // F: lib/ui/kit/kit_optical.dart:53 lib/ui/kit/kit_row.dart:664

/** A chosen row's wash: a breath of the accent, never a slab (audit 2026-11-09, C04). */
internal const val CHOSEN_ROW_TINT: Float = 0.06f

private const val CARET_INK_END = .28125f // F: lib/ui/kit/kit_optical.dart:26

/** The chosen row's plate on a flat list: surface3, card corners, inset space2 from both ends (kit_row.dart:676-686). */
private fun Modifier.kitChosenPlate(color: androidx.compose.ui.graphics.Color): Modifier =
    drawBehind {
        val inset = Space.S2.dp.toPx()
        val r = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.CARD_RADIUS.dp.toPx()
        drawRoundRect(
            color,
            topLeft = androidx.compose.ui.geometry.Offset(inset, 0f),
            size = androidx.compose.ui.geometry.Size(size.width - inset - inset, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
    }

/** True when [trailing] is a 48 dp glyph target that ends near the row's edge, on the top bar's ⋯ line (P-112). */
internal fun rowTargetOnEdge(trailing: KitRowTrailing?): Boolean =
    trailing is KitRowTrailing.Menu || trailing is KitRowTrailing.IconAction

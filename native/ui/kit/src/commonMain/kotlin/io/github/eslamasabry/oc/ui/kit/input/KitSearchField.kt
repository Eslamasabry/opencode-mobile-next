package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.words.res.kitSearchFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitPresence
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitSearchClear
import io.github.eslamasabry.oc.ui.words.res.kitSearchFilterActive
import io.github.eslamasabry.oc.ui.words.res.kitSearchNoMatch
import io.github.eslamasabry.oc.ui.words.res.kitSearchNoMatchIn
import io.github.eslamasabry.oc.ui.words.res.kitSearchPartial
import io.github.eslamasabry.oc.ui.words.res.kitSearchResults
import io.github.eslamasabry.oc.ui.words.res.kitSearchResults__eq0
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What Esc (and the back gesture) does in a search field. */
public enum class SearchEscape { Clear, Leave }

/** Dart `KitSearchField` rules (B-KIT-030). */
public object SearchRules {
    /** A search earns its place when the list can pass 8 items (kit-v2 §1.5). Hosts ask this; no literal. */
    public const val SHOW_ABOVE: Int = 8 // F: lib/ui/kit/kit_search_field.dart:116

    /** The debounce, and the wait before the count is announced (`KitMotion.typingSettle`). */
    public const val SETTLE_MS: Long = MotionTokens.TYPING_SETTLE.toLong()

    public fun worthShowing(itemCount: Int): Boolean = itemCount > SHOW_ABOVE

    /** Esc clears a query first; with nothing typed it is not consumed, so the screen or modal leaves. */
    public fun escape(query: String): SearchEscape = if (query.isEmpty()) SearchEscape.Leave else SearchEscape.Clear
}

/**
 * The one search field for lists, sheets and viewers (Dart `KitSearchField`; plan 06 §7.5). The host puts it in
 * `KitScreen`'s search slot or at the top of a sheet body. [onSearch] runs once typing settles (300 ms), at once on
 * Clear, and at once on Enter (then [onSubmit]). [resultCount] is announced politely once typing settles, never per
 * keystroke; [partial] says "12 loaded · searching the server…" (STATE-11). An active filter always shows as words in a
 * removable chip, so an empty result is never caused by a hidden filter. Esc and the back gesture clear the query
 * first. The query is plain text: the field sends it nowhere itself and keeps nothing across screens.
 *
 * States: empty, typing, results, partial, filtered, disabled (no-match: [KitSearchNoMatch])
 * Text: reading (the query), frame (the count line)
 * Cap: List
 * Motion: Clear fades QUICK (KitPresence); count line and filter chip unfold (KitReveal); Off = instant (class Fade)
 * Replaces: KitSearchField (lib/ui/kit/kit_search_field.dart)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitSearchField(
    label: String,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: TextFieldState = rememberTextFieldState(),
    onSubmit: ((String) -> Unit)? = null,
    resultCount: Int? = null,
    partial: Boolean = false,
    activeFilter: String? = null,
    removeFilterLabel: String? = null,
    onClearFilter: (() -> Unit)? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    filters: List<KitMenuItem> = emptyList(),
    prominent: Boolean = false,
) {
    kitAssert(label.isNotBlank()) { "KitSearchField: a search field names what it searches" }
    kitAssert(enabled || !disabledReason.isNullOrBlank()) { "KitSearchField: a disabled field says why (STATE-8)" }
    kitAssert(activeFilter == null || (onClearFilter != null && !removeFilterLabel.isNullOrBlank())) {
        "KitSearchField: an active filter needs onClearFilter and its remove label"
    }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    var filterOpen by remember { mutableStateOf(false) }
    val wide = LocalKitWindow.current.cls != WindowClass.Compact
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val search by rememberUpdatedState(onSearch)
    var sent by remember { mutableStateOf(state.text.toString()) }
    var settled by remember { mutableStateOf(state.text.toString()) }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.drop(1).collectLatest { q ->
            if (q.isNotEmpty()) delay(SearchRules.SETTLE_MS)
            settled = q
            if (q != sent) {
                sent = q
                search(q)
            }
        }
    }
    val query = state.text.toString()
    // The back gesture behaves as Esc: clears first, then lets the screen leave.
    if (query.isNotEmpty() && LocalNavigationEventDispatcherOwner.current != null) {
        val back = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(state = back, isBackEnabled = true, onBackCompleted = { state.clearText() })
    }
    // A filled, fully rounded field with no outline (the Flutter search look): the magnifier and the words carry it.
    val shapeId = if (prominent) KitShapeId.Pill else KitShapeId.Button
    val shape = shapeId.shape()
    val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Field, shape = shapeId, ownFill = colors.surface1, selected = focused))
    val edge = if (focused) colors.accent else if (prominent) colors.surface1 else colors.hairline
    val plain = LocalKitStyle.current.style == Style.Solid
    val hairline = (1f / LocalDensity.current.density).dp
    val lineHeight = with(LocalDensity.current) { LocalKitTypography.current.style(TypeRole.Body).lineHeight.toDp() }
    val lineBox = maxOf(tokens.minTarget.dp, lineHeight)
    val vertical = ((tokens.buttonHeight - tokens.minTarget) / 2).dp // F: lib/ui/kit/kit_search_field.dart:299

    val text = LocalKitTypography.current.style(TypeRole.Body).copy(color = if (enabled) colors.text1 else colors.text3)
    Column(modifier.kitMaxWidth(CapRole.List).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = tokens.buttonHeight.dp)
                .then(look.modifier)
                .then(if (plain) Modifier.border(if (focused) hairline * 2 else hairline, edge, shape) else Modifier), // F: lib/ui/kit/kit_tokens.dart:324-325
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            KitGlyph(KitIcons.search, colors.text3, tokens.smallIcon.dp * minOf(kitTextScale(), tokens.maxIconScale), Modifier.padding(start = Space.S4.dp, end = Space.S3.dp))
            Box(Modifier.weight(1f).padding(end = Space.S2.dp, top = vertical, bottom = vertical).heightIn(min = lineBox), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    state = state,
                    interactionSource = interactions,
                    decorator = TextFieldDecorator { inner -> Box(Modifier.heightIn(min = lineBox), contentAlignment = Alignment.CenterStart) { inner() } },
                    enabled = enabled,
                    textStyle = text,
                    cursorBrush = SolidColor(colors.accent),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = {
                        val q = state.text.toString()
                        settled = q
                        if (q != sent) {
                            sent = q
                            search(q)
                        }
                        onSubmit?.invoke(q)
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .testTag("kit-search-field")
                            .semantics { contentDescription = label }
                            .onPreviewKeyEvent { e ->
                                val esc = e.type == KeyEventType.KeyDown && e.key == Key.Escape
                                if (esc && SearchRules.escape(state.text.toString()) == SearchEscape.Clear) {
                                    state.clearText()
                                    true
                                } else if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                                    focusManager.moveFocus(FocusDirection.Down)
                                    true
                                } else {
                                    false
                                }
                            },
                )
                if (query.isEmpty()) {
                    KitText(label, role = TypeRole.Body, tone = TextTone.Tertiary, kind = KitTextKind.Reading, maxLines = if (LocalDensity.current.fontScale >= 1.3f) 2 else 1) // F: lib/ui/kit/kit_search_field.dart:337
                }
            }
            if (filters.isNotEmpty()) {
                KitMenu(expanded = filterOpen, items = filters, onDismissRequest = { filterOpen = false }, title = stringResource(Res.string.kitSearchFilter), anchor = {
                    if (wide) KitButton(label = stringResource(Res.string.kitSearchFilter), onClick = { filterOpen = true }, role = KitButtonRole.Tertiary, icon = KitIcons.filter, enabled = if (enabled) KitEnabled.Enabled else KitEnabled.Disabled(disabledReason.orEmpty()))
                    else KitIconButton(icon = KitIcons.filter, label = stringResource(Res.string.kitSearchFilter), onClick = { filterOpen = true }, enabled = if (enabled) KitEnabled.Enabled else KitEnabled.Disabled(disabledReason.orEmpty()), tone = TextTone.Tertiary)
                })
            }
            KitPresence(visible = query.isNotEmpty()) {
                KitIconButton(
                    icon = KitIcons.close,
                    label = stringResource(Res.string.kitSearchClear),
                    onClick = { state.clearText() },
                    enabled = if (enabled) KitEnabled.Enabled else KitEnabled.Disabled(disabledReason.orEmpty()),
                    tone = TextTone.Secondary,
                )
            }
        }

        val countLine =
            when {
                query.isEmpty() || resultCount == null -> null
                partial -> stringResource(Res.string.kitSearchPartial, resultCount)
                resultCount == 0 -> stringResource(Res.string.kitSearchResults__eq0)
                else -> pluralStringResource(Res.plurals.kitSearchResults, resultCount, Numbers.format(resultCount))
            }
        KitReveal(visible = countLine != null || activeFilter != null) {
            FlowRow(Modifier.padding(top = Space.S2.dp), horizontalArrangement = Arrangement.spacedBy(Space.S3.dp), verticalArrangement = Arrangement.spacedBy(Space.S2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                if (activeFilter != null) KitChip(label = activeFilter, kind = KitChipKind.Removable, removeLabel = removeFilterLabel, onRemove = onClearFilter)
                if (countLine != null) KitText(countLine, modifier = Modifier.semantics { if (settled == query) liveRegion = LiveRegionMode.Polite }, role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
        }

        if (!enabled && disabledReason != null) {
            KitText(disabledReason, Modifier.padding(top = Space.S2.dp), role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
        }
    }
}

/**
 * The inline "nothing matches" state under a search (Dart `KitSearchNoMatch`): the person's words isolated, Clear search,
 * and at most one more way forward ([action]). Announced once when it replaces the list.
 *
 * States: one
 * Text: frame
 * Cap: Reading
 * Motion: none of its own (the host swaps it in)
 * Replaces: KitSearchNoMatch (lib/ui/kit/kit_search_field.dart)
 */
@Composable
public fun KitSearchNoMatch(
    query: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    what: String? = null,
    action: KitAction? = null,
) {
    val q = "“⁨$query⁩”"
    val title = if (what == null) stringResource(Res.string.kitSearchNoMatch, q) else stringResource(Res.string.kitSearchNoMatchIn, "⁨$what⁩", q)
    KitStateView(
        kind = KitStateKind.Empty,
        title = title,
        modifier = modifier.kitMaxWidth(CapRole.State).semantics { liveRegion = LiveRegionMode.Polite },
        icon = KitIcons.search,
        primary = KitAction(label = stringResource(Res.string.kitSearchClear), onClick = onClear),
        secondary = action,
    )
}

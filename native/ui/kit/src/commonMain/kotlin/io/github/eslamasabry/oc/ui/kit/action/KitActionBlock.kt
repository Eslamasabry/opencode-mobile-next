package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.LocalKitStackedAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMore
import org.jetbrains.compose.resources.stringResource

/** Plan 06 §10.1: below 600 dp the window is compact and the block stacks. */
internal const val COMPACT_MAX_DP: Int = 600

/** Dart `KitActionBlock`: at most two tertiary actions are shown; the rest go into "More". */
internal const val SHOWN_TERTIARY: Int = 2

/** How a block lays out (pure; B-KIT-011). */
public enum class KitActionLayout { Stack, Row }

/** Pure rules of [KitActionBlock] (Dart `KitActionBlock._buildContent`). */
public object ActionBlockRules {
    /** A destructive tertiary forces the stack on every window (LAY-14, §2.7); compact windows stack. */
    public fun layout(widthDp: Float, tertiary: List<KitAction>): KitActionLayout =
        if (tertiary.any { it.destructive } || widthDp < COMPACT_MAX_DP) KitActionLayout.Stack else KitActionLayout.Row

    /** The tertiary actions shown as buttons, and the ones that go into "More". */
    public fun split(tertiary: List<KitAction>): Pair<List<KitAction>, List<KitAction>> =
        tertiary.take(SHOWN_TERTIARY) to tertiary.drop(SHOWN_TERTIARY)
}

/**
 * The one action hierarchy (Dart `KitActionBlock`; design standard §2): [primary], then [secondary], then up to two
 * [tertiary] actions, with the rest in "More" (`kit-actions-more`). A disabled action's reason shows as a muted line
 * under it (STATE-8). Compact windows stack full-width buttons (primary first) and wrap the tertiary actions side by
 * side, pulled back to the column's edge ([KitInset]); a destructive tertiary or a reason to show puts each tertiary
 * action on its own line, and a destructive tertiary forces the stack on every window (LAY-14). From medium up the
 * block is one end-aligned row, primary at the end, its reasons collected under it. [pair]: a primary with one short
 * secondary beside it on a phone, while both fit.
 *
 * States: default, disabled reasons, working
 * Text: frame
 * Cap: Reading
 * Motion: none of its own
 * Replaces: KitActionBlock (lib/ui/kit/kit_buttons.dart)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitActionBlock(
    modifier: Modifier = Modifier,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    tertiary: List<KitAction> = emptyList(),
    pair: Boolean = false,
) {
    if (primary == null && secondary == null && tertiary.isEmpty()) return
    val (shown, overflow) = ActionBlockRules.split(tertiary)
    val gap = Space.S2.dp
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Reading)) {
        val stacked = ActionBlockRules.layout(maxWidth.value, tertiary) == KitActionLayout.Stack
        val large = LocalDensity.current.fontScale > PAIR_TEXT_LIMIT
        val paired =
            pair && stacked && primary != null && secondary != null && tertiary.isEmpty() &&
                primary.enabled.reason == null && secondary.enabled.reason == null
        when {
            paired && !large && maxWidth.value >= Layout.PAIR_ROW_MIN_WIDTH ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    KitButton(primary!!, Modifier.weight(1f), KitButtonRole.Primary)
                    KitButton(secondary!!, role = KitButtonRole.Secondary, expand = false)
                }
            !stacked -> {
                // STATE-8: reasons collect under the row, end-aligned, in slot order.
                val reasons = listOfNotNull(primary?.enabled?.reason, secondary?.enabled?.reason) + shown.mapNotNull { it.enabled.reason }
                Column(horizontalAlignment = Alignment.End) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompositionLocalProvider(LocalKitStackedAction provides true) {
                            shown.forEach { KitButton(it, role = KitButtonRole.Tertiary, expand = false) }
                        }
                        if (overflow.isNotEmpty()) MoreActions(overflow)
                        secondary?.let { KitButton(it, role = KitButtonRole.Secondary, expand = false) }
                        primary?.let { KitButton(it, role = KitButtonRole.Primary, expand = false) }
                    }
                    if (reasons.isNotEmpty()) {
                        Column(Modifier.padding(top = Space.S1.dp), horizontalAlignment = Alignment.End) {
                            reasons.forEach { ActionReason(it) }
                        }
                    }
                }
            }
            else -> {
                val oneOnEachLine = tertiary.any { it.destructive } || shown.any { it.enabled.reason != null }
                val hasTertiaryLine = shown.isNotEmpty() || overflow.isNotEmpty()
                // Flutter stretches the column; each button holds its control cap centred (KitMaxWidth.control).
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    primary?.let { Stacked(it, KitButtonRole.Primary) }
                    if (primary != null && secondary != null) Spacer(Modifier.height(gap))
                    secondary?.let { Stacked(it, KitButtonRole.Secondary) }
                    if (hasTertiaryLine && (primary != null || secondary != null)) Spacer(Modifier.height(gap))
                    if (hasTertiaryLine) {
                        CompositionLocalProvider(LocalKitStackedAction provides true) {
                            if (oneOnEachLine) {
                                Column {
                                    shown.forEachIndexed { i, action ->
                                        KitInset { KitButton(action, role = KitButtonRole.Tertiary, expand = false) }
                                        action.enabled.reason?.let {
                                            Spacer(Modifier.height(Space.S1.dp))
                                            ActionReason(it, Modifier.fillMaxWidth())
                                        }
                                        if (i != shown.lastIndex || overflow.isNotEmpty()) Spacer(Modifier.height(gap))
                                    }
                                    if (overflow.isNotEmpty()) KitInset { MoreActions(overflow) }
                                }
                            } else {
                                KitInset {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
                                        itemVerticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        shown.forEach { KitButton(it, role = KitButtonRole.Tertiary, expand = false) }
                                        if (overflow.isNotEmpty()) MoreActions(overflow)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dart `KitActionBlock`: above 1.3× text a pair stacks, as two labels side by side would wrap. */
private const val PAIR_TEXT_LIMIT: Float = 1.3f // F: lib/ui/kit/kit_buttons.dart:1057

/** A stacked primary or secondary: full width, then its reason. */
@Composable
private fun Stacked(action: KitAction, role: KitButtonRole) {
    KitButton(action, Modifier.fillMaxWidth(), role)
    action.enabled.reason?.let {
        Spacer(Modifier.height(Space.S1.dp))
        ActionReason(it, Modifier.fillMaxWidth())
    }
}

/** A disabled action's reason (STATE-8): one muted line in the note style, found in tests by its text. */
@Composable
private fun ActionReason(text: String, modifier: Modifier = Modifier) {
    KitText(
        text,
        modifier.testTag("kit-action-reason"),
        role = TypeRole.Secondary, // Dart KitTokens.note: the secondary role in text2
        tone = TextTone.Secondary,
        kind = KitTextKind.Frame,
    )
}

/**
 * Pulls a row of tertiary buttons back by their side padding, so their words line up with the text above them (Dart
 * `KitInset`; start edge, either direction).
 */
@Composable
public fun KitInset(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset(x = -Space.S2.dp)) { content() }
    }
}

@Composable
private fun MoreActions(overflow: List<KitAction>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val items =
        overflow.map { a ->
            KitMenuItem(
                label = a.label,
                onSelect = { if (!a.working && a.runnable) a.onClick?.invoke() },
                icon = a.icon,
                destructive = a.destructive,
                enabled = if (a.working || a.onClick == null) KitEnabled.Disabled(a.enabled.reason ?: a.label) else a.enabled,
            )
        }
    KitMenu(expanded = open, items = items, onDismissRequest = { open = false }, modifier = modifier) {
        KitIconButton(
            icon = KitPartGlyphs.More,
            label = stringResource(Res.string.kitMore),
            onClick = { open = true },
            modifier = Modifier.testTag("kit-actions-more"),
        )
    }
}

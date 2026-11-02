package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
 * under its button (STATE-8). Compact windows stack full-width buttons (primary first); from medium up the block is one
 * end-aligned row with the primary at the end; a destructive tertiary forces the stack on every window. The type
 * carries "at most one primary".
 *
 * States: default, disabled reasons, working
 * Text: frame
 * Cap: Reading
 * Motion: none of its own
 * Replaces: KitActionBlock, KitActionStack (lib/ui/kit/kit_buttons.dart, kit_action_stack.dart)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitActionBlock(
    modifier: Modifier = Modifier,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    tertiary: List<KitAction> = emptyList(),
) {
    if (primary == null && secondary == null && tertiary.isEmpty()) return
    val (shown, overflow) = ActionBlockRules.split(tertiary)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        when (ActionBlockRules.layout(maxWidth.value, tertiary)) {
            KitActionLayout.Stack ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    primary?.let { ActionWithReason(it, KitButtonRole.Primary, Modifier.fillMaxWidth()) }
                    secondary?.let { ActionWithReason(it, KitButtonRole.Secondary, Modifier.fillMaxWidth()) }
                    shown.forEach { ActionWithReason(it, KitButtonRole.Tertiary, Modifier.fillMaxWidth()) }
                    if (overflow.isNotEmpty()) MoreActions(overflow, Modifier.align(Alignment.End))
                }
            KitActionLayout.Row ->
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    shown.forEach { ActionWithReason(it, KitButtonRole.Tertiary) }
                    if (overflow.isNotEmpty()) MoreActions(overflow)
                    secondary?.let { ActionWithReason(it, KitButtonRole.Secondary) }
                    primary?.let { ActionWithReason(it, KitButtonRole.Primary) }
                }
        }
    }
}

@Composable
private fun ActionWithReason(action: KitAction, role: KitButtonRole, modifier: Modifier = Modifier) {
    Column(modifier) {
        KitButton(action, Modifier.then(modifier), role)
        val reason = action.enabled.reason
        if (reason != null && reason.isNotBlank()) {
            KitText(
                reason,
                Modifier.padding(top = Space.S1.dp, start = Space.S1.dp, end = Space.S1.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
            )
        }
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

package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitChoiceCurrent
import io.github.eslamasabry.oc.ui.words.res.kitChoiceRecommended
import io.github.eslamasabry.oc.ui.words.res.kitChoiceSelectedCount
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Plan 06 §7.5: skeleton rows while choices load. */
private const val LOADING_ROWS = 3

/** One choice (Dart `KitChoice`): a value, a title, one supporting line, and its own disabled reason. */
@Immutable
public data class KitChoice<T>(
    val value: T,
    val title: String,
    val supporting: String? = null,
    val recommended: Boolean = false,
    val enabled: KitEnabled = KitEnabled.Enabled,
)

/** The mark at a choice row's start (Dart `KitChoiceMark`). */
public enum class KitChoiceMark { Radio, Check, None }

/** Pure rules of the choice list (B-KIT-014). */
public object ChoiceRules {
    /** Multi: tapping toggles exactly that value. */
    public fun <T> toggle(selected: Set<T>, value: T): Set<T> = if (value in selected) selected - value else selected + value

    /** "Current" shows on the in-use row only while the selection differs from it. */
    public fun <T> showsCurrent(value: T, current: T?, selected: T?): Boolean = current != null && value == current && selected != current
}

/**
 * One answer from a short list (Dart `KitChoiceList.single`; plan 06 §7.5): radio rows, one tap = one [onSelect] call
 * (there is no Apply; the host closes its sheet or sends). Disabled choices state their reason on their own line.
 * [sends] plays `Send` through the kit seam (the tap sends the person's answer). While [loading], skeleton rows; with no
 * choices, the [empty] slot (a `KitStateView`). "Current" marks the value in use while the selection differs from it.
 * Replaces dropdowns.
 *
 * States: default, selected, disabled, loading, empty
 * Text: reading (titles), frame (tags)
 * Cap: List
 * Motion: none of its own
 * Replaces: KitChoiceList.single, KitChoiceRow, KitChoiceMark (lib/ui/kit/kit_choice_list.dart)
 */
@Composable
public fun <T> KitChoiceList(
    choices: List<KitChoice<T>>,
    selected: T?,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    current: T? = null,
    sends: Boolean = false,
    loading: Boolean = false,
    empty: (@Composable () -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    kitAssert(loading || choices.isNotEmpty() || empty != null) { "KitChoiceList: an empty list needs its empty state" }
    Column(modifier.fillMaxWidth().semantics { contentDescription = semanticsLabel }.selectableGroup()) {
        when {
            loading -> KitSkeletonRows(count = LOADING_ROWS)
            choices.isEmpty() -> empty?.invoke()
            else ->
                choices.forEach { c ->
                    KitChoiceRow(
                        title = c.title,
                        selected = c.value == selected,
                        supporting = c.supporting,
                        mark = KitChoiceMark.Radio,
                        tag =
                            when {
                                ChoiceRules.showsCurrent(c.value, current, selected) -> stringResource(Res.string.kitChoiceCurrent)
                                c.recommended -> stringResource(Res.string.kitChoiceRecommended)
                                else -> null
                            },
                        enabled = c.enabled,
                        haptic = if (sends) KitHaptic.Send else null,
                        onClick = { onSelect(c.value) },
                    )
                }
        }
    }
}

/**
 * Several answers (Dart `KitChoiceList.multi`): check rows, each tap toggles one value and calls [onChange] once; the
 * host's primary applies them. [showCount] puts "N selected" above the list.
 */
@Composable
public fun <T> KitChoiceListMulti(
    choices: List<KitChoice<T>>,
    selected: Set<T>,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    showCount: Boolean = true,
    loading: Boolean = false,
    empty: (@Composable () -> Unit)? = null,
    onChange: (Set<T>) -> Unit,
) {
    kitAssert(loading || choices.isNotEmpty() || empty != null) { "KitChoiceList: an empty list needs its empty state" }
    Column(modifier.fillMaxWidth().semantics { contentDescription = semanticsLabel }) {
        if (showCount && !loading && choices.isNotEmpty()) {
            KitText(
                pluralStringResource(Res.plurals.kitChoiceSelectedCount, selected.size, Numbers.format(selected.size)),
                Modifier.padding(bottom = Space.S1.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
            )
        }
        when {
            loading -> KitSkeletonRows(count = LOADING_ROWS)
            choices.isEmpty() -> empty?.invoke()
            else ->
                choices.forEach { c ->
                    KitChoiceRow(
                        title = c.title,
                        selected = c.value in selected,
                        supporting = c.supporting,
                        mark = KitChoiceMark.Check,
                        tag = if (c.recommended) stringResource(Res.string.kitChoiceRecommended) else null,
                        enabled = c.enabled,
                        onClick = { onChange(ChoiceRules.toggle(selected, c.value)) },
                    )
                }
        }
    }
}

/**
 * One choice row (Dart `KitChoiceRow`): the mark at the start, the title (and one supporting line or the disabled
 * reason), an optional tag word at the end. Radio rows are `RadioButton`s, check rows `Checkbox`es, to a screen reader.
 */
@Composable
public fun KitChoiceRow(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    mark: KitChoiceMark = KitChoiceMark.Radio,
    tag: String? = null,
    enabled: KitEnabled = KitEnabled.Enabled,
    haptic: KitHaptic? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalKitTokens.current
    val on = enabled.isEnabled
    Row(
        modifier.fillMaxWidth()
            .heightIn(min = tokens.choiceRowMin.dp)
            .kitSelectTap(
                selected = selected,
                role = if (mark == KitChoiceMark.Check) Role.Checkbox else Role.RadioButton,
                enabled = enabled,
                haptic = haptic,
                onClick = onClick,
            )
            .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        if (mark != KitChoiceMark.None) ChoiceMarkGlyph(mark, selected, on)
        Column(Modifier.weight(1f)) {
            KitText(title, role = TypeRole.RowTitle, tone = if (on) TextTone.Primary else TextTone.Tertiary)
            val line = enabled.reason ?: supporting
            line?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 3) }
        }
        tag?.let { KitText(it, role = TypeRole.Caption, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1) }
    }
}

@Composable
private fun ChoiceMarkGlyph(mark: KitChoiceMark, selected: Boolean, on: Boolean) {
    val colors = LocalKitColors.current
    val size = 22.dp
    val edge = if (!on) colors.text3 else if (selected) colors.accent else colors.text2
    val shape = if (mark == KitChoiceMark.Radio) KitShapeId.Circle.toShape() else KitShapeId.Square.toShape()
    if (mark == KitChoiceMark.Check && selected) {
        Box(Modifier.size(size).clip(KitShapeId.Code.toShape()).background(edge), contentAlignment = Alignment.Center) {
            KitGlyph(KitPartGlyphs.Check, colors.onAccent, 16.dp)
        }
        return
    }
    Box(
        Modifier.size(size).clip(if (mark == KitChoiceMark.Check) KitShapeId.Code.toShape() else shape).border(2.dp, edge, if (mark == KitChoiceMark.Check) KitShapeId.Code.toShape() else shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).clip(KitShapeId.Circle.toShape()).background(edge))
    }
}

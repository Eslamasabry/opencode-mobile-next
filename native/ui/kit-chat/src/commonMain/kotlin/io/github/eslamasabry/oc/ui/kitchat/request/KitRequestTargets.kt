package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.action.rememberKitCopyAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.tool.KitMonoLtr
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetailsHide
import io.github.eslamasabry.oc.ui.words.res.kitRequestMoreTargets
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The request's exact command or file, with the change size when it was supplied by the server. */
public data class KitRequestTarget(val text: String, val change: String? = null)

/**
 * Three targets in place; the visible Details action reveals every remaining target inside the
 * bounded card.
 */
@Composable
public fun KitRequestTargets(
    targets: List<KitRequestTarget>,
    modifier: Modifier = Modifier,
    copyLabel: String? = null,
    copiedLabel: String? = null,
) {
    if (targets.isEmpty()) return
    var expanded by remember(targets) { mutableStateOf(false) }
    val colors = LocalKitColors.current
    val shape = KitShapeId.Code.toShape()
    val hidden = (targets.size - TARGET_LIMIT).coerceAtLeast(0)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.ground)
            .border(1.dp, colors.hairline, shape)
            .padding(Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        // Copies every command or path exactly as asked (the full text, not the shown lines): a compact copy mark in
        // the box's corner beside the first line, like a code block's, never a long button under the command.
        val copy =
            if (copyLabel != null && copiedLabel != null)
                rememberKitCopyAction(copyLabel, copiedLabel, { targets.joinToString("\n") { it.text } })
            else null
        (if (expanded) targets else targets.take(TARGET_LIMIT)).forEachIndexed { index, target ->
            if (index == 0 && copy != null) {
                // One line sits level with the copy mark (it sat high, over an empty lower half); several lines keep
                // the mark at the first line's corner.
                val oneLine = '\n' !in target.text
                Row(verticalAlignment = if (oneLine) Alignment.CenterVertically else Alignment.Top) {
                    KitMonoLtr(
                        target.text,
                        Modifier.weight(1f).padding(top = if (oneLine) 0.dp else Space.S2.dp),
                        TextTone.Primary,
                    )
                    KitIconButton(
                        icon = if (copy.label == copiedLabel) KitIcons.check else KitIcons.copy,
                        label = copy.label,
                        onClick = copy.onClick ?: {},
                        tone = TextTone.Secondary,
                    )
                }
            } else KitMonoLtr(target.text, Modifier.fillMaxWidth(), TextTone.Primary)
            target.change?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
        }
        if (hidden > 0) {
            if (!expanded)
                KitText(
                    pluralStringResource(Res.plurals.kitRequestMoreTargets, hidden, hidden),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            KitButton(
                stringResource(if (expanded) Res.string.kitDetailsHide else Res.string.kitDetails),
                { expanded = !expanded },
                role = KitButtonRole.Tertiary,
            )
        }
    }
}

private const val TARGET_LIMIT: Int = 3

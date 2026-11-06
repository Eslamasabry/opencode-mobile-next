package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.roundToInt

/** Flutter's choice-list panel and text-rail seams (`kit_choice_list.dart:426-457`). */
@Composable
public fun <T> KitChoiceGroup(
    choices: List<KitChoice<T>>,
    selected: Set<T>,
    semanticsLabel: String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    multiple: Boolean = false,
) {
    val tokens = LocalKitTokens.current
    val iconScale = LocalDensity.current.fontScale.coerceIn(1f, tokens.maxIconScale)
    val inset = (tokens.gutter + (tokens.smallIcon * iconScale).roundToInt() + Space.S3).dp
    KitSurface(
        modifier = modifier.kitMaxWidth(CapRole.List).fillMaxWidth(),
        padding = KitSurfacePadding.None,
        flatInLists = true,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .testTag("kit-choice-group")
                .semantics { contentDescription = semanticsLabel }
                .then(if (multiple) Modifier else Modifier.selectableGroup())
        ) {
            choices.forEachIndexed { index, choice ->
                if (index > 0) KitDivider(Modifier.padding(start = inset, end = tokens.gutter.dp))
                KitChoiceRow(
                    title = choice.title,
                    supporting = choice.supporting,
                    selected = choice.value in selected,
                    enabled = choice.enabled,
                    mark = if (multiple) KitChoiceMark.Check else KitChoiceMark.Radio,
                    onClick = { onSelect(choice.value) },
                )
            }
        }
    }
}

package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The scalar, non-tappable KitProgressRow from kit_progress_row.dart. Null is an empty skeleton track. */
@Composable
public fun KitProgressRow(title: String, value: Float?, modifier: Modifier = Modifier, valueLabel: String? = null) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val amount = value?.coerceIn(0f, 1f)
    val limit = when {
        value == null -> stringResource(Res.string.kitProgressRowLoading)
        value >= 1f -> stringResource(Res.string.kitProgressRowAtLimit) // F: lib/ui/kit/kit_progress_row.dart:136
        value >= .8f -> stringResource(Res.string.kitProgressRowNearLimit) // F: lib/ui/kit/kit_progress_row.dart:135
        else -> null
    }
    val words = listOfNotNull(valueLabel?.takeIf(String::isNotBlank), limit).joinToString(" · ")
    Column(modifier.fillMaxWidth().heightIn(min = tokens.rowHeightTwo.dp)
        .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp)
        .semantics(mergeDescendants = true) {}) {
        KitText(title, role = TypeRole.RowTitle)
        if (words.isNotEmpty()) {
            Spacer(Modifier.height(Space.S1.dp))
            KitText(words, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        Spacer(Modifier.height(Space.S2.dp))
        if (amount != null) key(amount) {
            // A quota may go down when a window resets; it is not a monotonically advancing job.
            KitProgressView(KitProgress.Known(amount), semanticsLabel = title,
                tone = if (amount >= 1f) KitProgressTone.Failed else KitProgressTone.Moving) // F: lib/ui/kit/kit_progress_row.dart:136
        }
        else Canvas(Modifier.fillMaxWidth().height(tokens.progressBar.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }) { drawRoundRect(colors.surface3, cornerRadius = CornerRadius(size.height / 2)) } // NV: circular ends use half the track height
    }
}

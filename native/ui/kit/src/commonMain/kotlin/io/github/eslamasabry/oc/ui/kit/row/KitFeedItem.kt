package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.*

/** Flutter kit_feed_item.dart: title, then one inline state/project/time line, with no leading column. */
@Composable
public fun KitFeedItem(
    title: String,
    project: String,
    time: String?,
    state: KitRowState?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    done: Boolean = false,
) {
    if (LocalKitStyle.current.style == Style.Signal) {
        KitRow(title, modifier, supporting = project.ifBlank { null }, state = state, meta = time, onClick = onClick)
        return
    }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val large = LocalDensity.current.fontScale > 1.3f // F: lib/ui/kit/kit_feed_item.dart:365
    val word = state?.word
    val statusColor = when (state?.tone) {
        KitRowStateTone.NeedsYou -> colors.attention
        KitRowStateTone.Running -> colors.accent
        else -> colors.text2
    }
    val meta = buildAnnotatedString {
        if (word != null) {
            if (done) append("✓ ")
            withStyle(SpanStyle(color = statusColor, fontWeight = if (state?.tone == KitRowStateTone.NeedsYou || state?.tone == KitRowStateTone.Running) FontWeight.SemiBold else null)) { append(word) }
        }
        if (project.isNotBlank()) { if (length > 0) append("\u00a0· "); append("\u2068$project\u2069") }
        if (time != null) { if (length > 0) append("\u00a0· "); append(time.replace(' ', '\u00a0')) }
    }
    val words = listOfNotNull(title, word, project.takeIf { it.isNotBlank() }, time).joinToString(", ")
    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp, vertical = Space.S3.dp).clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            KitText(title, role = TypeRole.RowTitle, maxLines = if (large) 2 else 1) // F: lib/ui/kit/kit_feed_item.dart:490
            if (meta.isNotEmpty()) BasicText(meta, style = LocalKitTypography.current.style(TypeRole.Secondary).copy(color = colors.text2), maxLines = if (large) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val m = modifier.kitMaxWidth(CapRole.List).semantics(mergeDescendants = true) { contentDescription = words }
    if (onClick == null) Box(m) { body() }
    else KitTappable(onClick, m, role = KitTappableRole.Row) { body() }
}

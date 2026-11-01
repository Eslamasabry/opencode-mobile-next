package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor

/** What a mono surface displays: the source through the app's redactor (plan 09 R18, §9.5). */
@Composable
internal fun redacted(source: String): String {
    val redactor = LocalKitRedactor.current
    return remember(redactor, source) { redactor.redact(source) }
}

/**
 * Mono text that reads left to right in any interface language (plan 09 R15, §9.7): its own paragraph, so it is
 * isolated from the RTL prose around it, aligned to the left edge. Displays [source] redacted. Reading text (follows
 * the person's text size); a frame part caps it by wrapping it in `KitFrameText`.
 */
@Composable
internal fun KitMonoLtr(
    source: String,
    modifier: Modifier = Modifier,
    tone: TextTone = TextTone.Primary,
    maxLines: Int = Int.MAX_VALUE,
) {
    val style =
        LocalKitTypography.current.style(TypeRole.Mono).copy(
            color = LocalKitColors.current.tone(tone),
            textDirection = TextDirection.Ltr,
            textAlign = TextAlign.Left,
        )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicText(redacted(source), modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

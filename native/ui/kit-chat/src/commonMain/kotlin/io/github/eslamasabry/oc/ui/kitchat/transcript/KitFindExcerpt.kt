package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask

/**
 * The current find result in view even when its source is folded. Technical matches stay redacted.
 */
@Composable
public fun KitFindExcerpt(text: String, query: String, modifier: Modifier = Modifier) {
    val mask = rememberKitMask()
    val safe = mask(text)
    val colors = LocalKitColors.current
    val marked =
        remember(safe, query, colors) {
            val out = AnnotatedString.Builder(safe)
            if (query.isNotBlank()) {
                var at = safe.indexOf(query, ignoreCase = true)
                while (at >= 0) {
                    out.addStyle(
                        SpanStyle(
                            background = colors.surface3,
                            color = colors.text1,
                            fontWeight = FontWeight.Bold,
                        ),
                        at,
                        at + query.length,
                    )
                    at = safe.indexOf(query, at + query.length, ignoreCase = true)
                }
            }
            out.toAnnotatedString()
        }
    BasicText(
        marked,
        modifier.testTag("transcript-find-excerpt"),
        style = LocalKitTypography.current.style(TypeRole.Secondary).copy(color = colors.text2),
    )
}

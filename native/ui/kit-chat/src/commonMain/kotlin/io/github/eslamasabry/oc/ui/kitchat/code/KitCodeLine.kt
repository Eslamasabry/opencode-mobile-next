package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitChatWork
import kotlinx.coroutines.withContext

/**
 * A lazy source line, without a second code-block frame; existing find marks survive syntax
 * colouring.
 */
@Composable
public fun KitCodeLine(
    text: AnnotatedString,
    language: CodeLanguage,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
    highlight: Boolean = true,
) {
    val tokenizer = LocalKitCodeTokenizer.current
    val work = LocalKitChatWork.current
    val tokens by
        produceState<List<CodeToken>>(emptyList(), text.text, language, highlight, tokenizer) {
            value =
                if (highlight && text.length <= 1000)
                    withContext(work) { tokenizer.tokenize(text.text, language) }
                else emptyList()
        }
    val colors = KitCodeColors.of(LocalKitColors.current)
    val annotated =
        remember(text, tokens, colors) {
            buildAnnotatedString {
                append(text)
                tokens.forEach { token ->
                    addStyle(
                        SpanStyle(color = colors[token.role]),
                        token.start.coerceIn(0, text.length),
                        token.end.coerceIn(token.start.coerceIn(0, text.length), text.length),
                    )
                }
            }
        }
    BasicText(
        annotated,
        modifier,
        style = LocalKitTypography.current.style(TypeRole.Mono).copy(color = colors.text),
        softWrap = wrap,
    )
}

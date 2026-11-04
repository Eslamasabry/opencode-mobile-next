package io.github.eslamasabry.oc.ui.kitchat.diff

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitChatWork
import io.github.eslamasabry.oc.ui.kitchat.code.*
import kotlinx.coroutines.withContext

/**
 * Lazy per-line syntax; large files/lines remain plain. Every colour meets 4.5:1 across all
 * row/selection fills.
 */
@Composable
internal fun DiffCodeText(
    text: String,
    language: CodeLanguage,
    enabled: Boolean,
    modifier: Modifier,
    style: TextStyle,
    wrap: Boolean,
) {
    val tokenizer = LocalKitCodeTokenizer.current
    val work = LocalKitChatWork.current
    val tokens by
        produceState<List<CodeToken>>(emptyList(), text, language, enabled, tokenizer) {
            value =
                if (enabled && text.length <= 1000)
                    withContext(work) {
                        tokenizer.tokenize(
                            text,
                            if (language == CodeLanguage.Json) CodeLanguage.JavaScript
                            else language,
                        )
                    }
                else emptyList()
        }
    val colors = LocalKitColors.current
    val syntax = KitCodeColors.of(colors)
    val grounds =
        remember(colors) {
            listOf(
                    Color.Transparent,
                    colors.success.copy(alpha = .12f),
                    colors.attention.copy(alpha = .12f),
                )
                .flatMap { tint ->
                    val ground = tint.compositeOver(colors.ground)
                    listOf(
                        Argb(ground.toArgb()),
                        Argb(colors.accent.copy(alpha = .2f).compositeOver(ground).toArgb()),
                    )
                }
        }
    val annotated =
        remember(text, tokens, syntax, grounds) {
            buildAnnotatedString {
                append(text.ifEmpty { " " })
                for (token in tokens) {
                    val start = token.start.coerceIn(0, text.length)
                    val end = token.end.coerceIn(start, text.length)
                    val legible =
                        RoleDerivation.readableOn(
                            Argb(syntax[token.role].toArgb()),
                            grounds,
                            4.5,
                            Argb(colors.text1.toArgb()),
                        )
                    addStyle(SpanStyle(color = Color(legible.v)), start, end)
                }
            }
        }
    BasicText(annotated, modifier, style = style, softWrap = wrap)
}

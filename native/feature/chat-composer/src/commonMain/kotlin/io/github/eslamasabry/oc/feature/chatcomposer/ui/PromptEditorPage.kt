package io.github.eslamasabry.oc.feature.chatcomposer.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable

/**
 * S24-CHAT story 4: the prompt editor for the live composer. The app binds it to `:feature:chat`'s
 * `ComposerPromptEditor`, so the chat's composer opens it over its own [field] (Done keeps the
 * words, Cancel asks before it puts them back).
 */
@Composable
public fun PromptEditorPage(visible: Boolean, field: TextFieldState, onClose: () -> Unit): Unit =
    PromptEditorScreen(visible, field, onClose)

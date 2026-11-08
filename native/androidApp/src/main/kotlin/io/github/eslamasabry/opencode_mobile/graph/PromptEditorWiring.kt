package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.feature.chat.composer.ComposerPromptEditor
import io.github.eslamasabry.oc.feature.chatcomposer.ui.PromptEditorPage

/*
 * S24-CHAT story 4: the chat's composer opens :feature:chat-composer's full-screen prompt editor
 * (features may not depend on each other, so the app joins them here).
 */
@ContributesIntoSet(AppScope::class, binding = binding<ComposerPromptEditor>())
@Inject
class AppPromptEditor : ComposerPromptEditor {
    @Composable
    override fun Editor(visible: Boolean, field: TextFieldState, onClose: () -> Unit) =
        PromptEditorPage(visible, field, onClose)
}

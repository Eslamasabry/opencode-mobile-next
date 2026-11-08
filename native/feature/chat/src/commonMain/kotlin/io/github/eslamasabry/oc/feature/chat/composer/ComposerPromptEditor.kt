package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * S24-CHAT story 4 (Flutter `prompt_editor.dart`, `/editor`): the full-screen prompt editor over the
 * composer's own field. It lives in `:feature:chat-composer`, which this feature may not depend on,
 * so the app binds it here; with no binding (desktop, tests) the editor is simply not offered.
 * [onClose] runs after Done, and after Cancel once the field is back as it was.
 */
public interface ComposerPromptEditor {
    @Composable public fun Editor(visible: Boolean, field: TextFieldState, onClose: () -> Unit)
}

@ContributesTo(AppScope::class)
public interface ComposerPromptEditorBindings {
    @Multibinds(allowEmpty = true) public val composerPromptEditors: Set<ComposerPromptEditor>
}

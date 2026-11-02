package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.security.Redactor

/** Supplies the graph's registered redactor without exposing security services to feature code. */
@Inject
public class KitChatEnvironment(private val redactor: Redactor) {
    @Composable
    public fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitRedactor provides redactor, content = content)
    }
}

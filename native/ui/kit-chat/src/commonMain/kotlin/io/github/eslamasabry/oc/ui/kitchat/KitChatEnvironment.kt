package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Supplies the graph's registered redactor without exposing security services to feature code, and (P12-CHAT-FEEL)
 * the app's default context for kit-chat's pure work, so code colouring never runs on the main thread.
 */
public class KitChatEnvironment private constructor(private val redactor: Redactor, private val work: CoroutineContext) {
    /** The app graph's environment. */
    @Inject
    public constructor(redactor: Redactor, dispatchers: AppDispatchers) : this(redactor, dispatchers.default)

    /** Tests and previews: pure work in the composition's own context. */
    public constructor(redactor: Redactor) : this(redactor, EmptyCoroutineContext)

    @Composable
    public fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitRedactor provides redactor, LocalKitChatWork provides work, content = content)
    }
}

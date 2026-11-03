package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor

/**
 * Code and other mono parts outside the app graph (feature screen tests, catalog previews): a fresh [Redactor] that
 * still masks every pattern it knows (keys, tokens, passwords in addresses); only the graph's registered secrets are
 * absent. The app always provides the graph's instance through `KitChatEnvironment`.
 */
@Composable
public fun KitStandaloneRedaction(content: @Composable () -> Unit) {
    val redactor = remember { Redactor() }
    CompositionLocalProvider(LocalKitRedactor provides redactor, content = content)
}

package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The app's one [Redactor] (plan 09 §9.2 and R18): every mono surface of the chat (code, commands, output, diffs, tool
 * subjects) **displays** `redact(source)`. No default on purpose: a part drawn without it fails loudly instead of
 * showing a secret. `:feature:chat` (and the shell) provide the graph's instance; tests provide their own.
 */
public val LocalKitRedactor: ProvidableCompositionLocal<Redactor> =
    staticCompositionLocalOf { error("LocalKitRedactor is not provided: mono chat parts never draw unredacted text") }

/**
 * Where kit-chat parts run their pure work (tokenising code, preparing diffs): the app provides its `AppDispatchers`
 * default context here (rule 6: no dispatcher singletons outside `:core:common`). Empty = the composition's own context,
 * which tests and previews use.
 */
public val LocalKitChatWork: ProvidableCompositionLocal<CoroutineContext> = staticCompositionLocalOf { EmptyCoroutineContext }

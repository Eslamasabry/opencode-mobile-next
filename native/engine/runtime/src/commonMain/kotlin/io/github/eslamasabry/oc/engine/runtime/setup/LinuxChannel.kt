package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The built-in Linux method channel as a port (port of lib/builtin/builtin_linux.dart `_invoke`). The Android binding
 * implements it over BuiltinLinux.kt with exactly the Flutter method names and argument keys, so the native side keeps
 * answering the same contract. Argument maps leave out keys that have no value (Dart `'port': ?port`).
 *
 * Replies are Dart-channel shaped values: null, Boolean, Int/Long, Double, String, List, Map. A missing plugin or an
 * unsupported platform answers `Failure.Unavailable(BUILTIN_LINUX)`; a native error answers `Failure.Runtime` with the
 * native text only in the Details fold. Never put a script's output or a credential into a failure.
 */
public fun interface LinuxChannel {
    public suspend fun invoke(method: String, arguments: Map<String, Any?>?): Outcome<Any?>

    public companion object {
        /** BuiltinLinux.channelName. */
        public const val CHANNEL_NAME: String = "io.github.eslamasabry.opencode_mobile/builtin_linux"

        /** The feature an absent channel names (`missing_plugin`, `unsupported_platform`). */
        public val BUILTIN_LINUX: FeatureId = FeatureId("builtinLinux")
    }
}

/**
 * Bumped whenever a setup job ends or a removal attempts mutation (port of `setupToolsChanged`): what is on the phone
 * changed for good, so the pages that list installed tools read the phone again. Not tied to anyone watching progress.
 */
public class ToolsChanged {
    private val state = MutableStateFlow(0L)

    public val revision: StateFlow<Long> = state.asStateFlow()

    public fun bump() {
        state.update { it + 1 }
    }
}

/** Dart `value is int`: a whole channel number, never a Double, a Boolean or a numeric String. */
internal fun channelInt(value: Any?): Long? =
    when (value) {
        is Int -> value.toLong()
        is Long -> value
        is Short -> value.toLong()
        is Byte -> value.toLong()
        else -> null
    }

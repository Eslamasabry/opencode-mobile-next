package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem

/**
 * The built-in Linux method channel `io.github.eslamasabry.opencode_mobile/builtin_linux` as a port (port of the
 * MethodChannel half of lib/builtin/builtin_linux.dart). The Android binding implements [invoke] by dispatching
 * [method] with exactly these argument keys to `BuiltinLinux.kt` (native/android/linux), and answers with what that
 * method returns: maps as `Map<String, Any?>`, lists as `List<Any?>`, booleans, `Int`/`Long`/`Double` numbers,
 * strings or null.
 *
 * The binding never throws (cancellation aside). A refusal is [ChannelReply.Error]; a build without the channel is
 * [ChannelReply.Missing]. The native message is never carried: copy lives in `:ui:words`, keyed by the
 * [RuntimeProblem] the bridge derives from the code.
 */
public interface BuiltinLinuxChannel {
    public suspend fun invoke(method: String, arguments: Map<String, Any?>? = null): ChannelReply
}

/** One answer of [BuiltinLinuxChannel.invoke]. */
public sealed interface ChannelReply {
    /** The method returned [value] (null for a `void` method). */
    public data class Value(val value: Any?) : ChannelReply

    /**
     * The phone refused (a `PlatformException`): [code] is the native snake_case code, [details] the phone's own
     * description for the Details fold, already redacted by the binding. Never shown as the message.
     */
    public data class Error(val code: String, val details: Redacted? = null) : ChannelReply

    /** This build of the app has no built-in Linux channel (`missing_plugin`). */
    public data object Missing : ChannelReply
}

/** The capability every refusal of a runner without the channel names (`PlatformCapabilities.builtinLinux`). */
public val BuiltinLinuxFeature: FeatureId = FeatureId("builtinLinux")

/**
 * The failure of a native refusal: the snake_case code read as its [RuntimeProblem] (`work_lease_invalid` →
 * [RuntimeProblem.WorkLeaseInvalid]), any other code as [RuntimeProblem.Generic]; the phone's details stay for Details.
 */
public fun ChannelReply.Error.toFailure(): Failure = Failure.Runtime(runtimeProblemFor(code), details)

/** The [RuntimeProblem] whose snake_case name is [code], or [RuntimeProblem.Generic]. */
public fun runtimeProblemFor(code: String): RuntimeProblem =
    RuntimeProblem.entries.firstOrNull { snakeCase(it.name) == code } ?: RuntimeProblem.Generic

private fun snakeCase(name: String): String =
    buildString {
        name.forEachIndexed { index, c ->
            if (c.isUpperCase()) {
                if (index > 0) append('_')
                append(c.lowercaseChar())
            } else {
                append(c)
            }
        }
    }

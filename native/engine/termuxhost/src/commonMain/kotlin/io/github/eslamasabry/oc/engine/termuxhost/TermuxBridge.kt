package io.github.eslamasabry.oc.engine.termuxhost

import io.github.eslamasabry.oc.core.security.Redactor

/** What the `oc/termux` method channel answered (the Android half lives in the app's Termux binding). */
public sealed interface ChannelReply {
    public data class Value(val value: Any?) : ChannelReply

    /** No handler registered for `oc/termux` (Dart MissingPluginException): a bare device, never a crash. */
    public data object MissingHandler : ChannelReply

    /** The platform side refused (Dart PlatformException). */
    public data class PlatformError(val code: String, val message: String?) : ChannelReply
}

/** The `oc/termux` channel port. */
public fun interface TermuxChannel {
    public suspend fun invoke(method: String, arguments: Map<String, Any?>?): ChannelReply
}

/** The pause before the single service-shutdown retry (bound to `delay` by the app; virtual time in tests). */
public fun interface TermuxRetryWait {
    public suspend fun pause(millis: Long)
}

/**
 * Port of lib/termux/bridge.dart's channel half (B-LINUX-228, B-LINUX-246). Every entry point answers instead of
 * throwing: where there is no bridge ([supported] false, or no handler) capabilities report unavailable, boolean
 * entry points answer false and commands fail as a handled [TermuxBridgeError] with [UNSUPPORTED_PLATFORM].
 */
public class TermuxBridge(
    public val supported: Boolean,
    private val channel: TermuxChannel,
    private val redactor: Redactor,
    private val wait: TermuxRetryWait,
    private val serviceShutdownRetryDelayMillis: Long = 1_500,
) {
    public companion object {
        public const val UNSUPPORTED_PLATFORM: String = "unsupported_platform"

        public val Unsupported: TermuxBridgeError =
            TermuxBridgeError(
                "Termux runs on Android. This desktop build connects to an OpenCode server you start yourself.",
                UNSUPPORTED_PLATFORM,
            )

        private val JOB_ID = Regex("^[A-Za-z0-9_-]{1,96}$")
        private val COMPONENT_ID = Regex("^[a-z][a-z0-9_-]{0,63}$")
        private val SETUP_VERSION = Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$")
        private val ADDING = Regex("^[a-z][a-z0-9_-]*(?:,[a-z][a-z0-9_-]*)*$")
        private val ALLOWED_PARAMS = mapOf("opencode" to setOf("runtime", "version"), "_job" to setOf("first", "adding"))
        private fun invalid(message: String) = TermuxAnswer.Failed(TermuxBridgeError(message, "invalid_setup"))
    }

    public suspend fun capabilities(): TermuxCapabilities {
        if (!supported) return TermuxCapabilities.Unavailable
        return when (val reply = channel.invoke("getCapabilities", null)) {
            is ChannelReply.Value -> TermuxCapabilities.fromMap(reply.value.asStringMap())
            // A runner with no `oc/termux` handler: honestly nothing installed.
            ChannelReply.MissingHandler -> TermuxCapabilities.Unavailable
            // Dart lets a PlatformException through here; the native port answers "unavailable" instead.
            is ChannelReply.PlatformError -> TermuxCapabilities.Unavailable
        }
    }

    public suspend fun requestPermission(): Boolean = invokeFlag("requestRunCommandPermission")

    public suspend fun openTermux(): Boolean = invokeFlag("openTermux")

    public suspend fun openAppSettings(): Boolean = invokeFlag("openAppSettings")

    /** Android's answer to the "Run commands" question in words, or [ChannelReply.MissingHandler] semantics as null. */
    internal suspend fun requestRunCommandAccess(): ChannelReply = channel.invoke("requestRunCommandAccess", null)

    private suspend fun invokeFlag(method: String): Boolean {
        if (!supported) return false
        return when (val reply = channel.invoke(method, null)) {
            is ChannelReply.Value -> reply.value == true
            ChannelReply.MissingHandler -> false
            is ChannelReply.PlatformError -> false
        }
    }

    /** Runs [script] in Termux; one retry after the Termux service shutdown kills the execution. */
    public suspend fun run(
        script: String,
        background: Boolean = true,
        workdir: String? = null,
        timeoutMillis: Long = 30_000,
    ): TermuxAnswer<TermuxCommandResult> {
        if (!supported) return TermuxAnswer.Failed(Unsupported)
        var command =
            when (val first = invoke(script, background, workdir, timeoutMillis)) {
                is TermuxAnswer.Failed -> return first
                is TermuxAnswer.Done -> first.value
            }
        if (command.killedByServiceShutdown) {
            wait.pause(serviceShutdownRetryDelayMillis)
            command =
                when (val second = invoke(script, background, workdir, timeoutMillis)) {
                    is TermuxAnswer.Failed -> return second
                    is TermuxAnswer.Done -> second.value
                }
        }
        if (!command.successful) return TermuxAnswer.Failed(TermuxBridgeError(command.failureMessage, "command_failed"))
        return TermuxAnswer.Done(command)
    }

    private suspend fun invoke(
        script: String,
        background: Boolean,
        workdir: String?,
        timeoutMillis: Long,
    ): TermuxAnswer<TermuxCommandResult> {
        val reply =
            channel.invoke(
                "runInTermux",
                mapOf(
                    "script" to script,
                    "background" to background,
                    "workdir" to (workdir ?: TermuxScripts.HOME),
                    "timeoutMs" to timeoutMillis,
                ),
            )
        return when (reply) {
            is ChannelReply.Value -> TermuxAnswer.Done(TermuxCommandResult.fromMap(reply.value.asStringMap()))
            ChannelReply.MissingHandler -> TermuxAnswer.Failed(Unsupported)
            is ChannelReply.PlatformError ->
                TermuxAnswer.Failed(TermuxBridgeError(reply.message ?: "Termux command failed.", reply.code))
        }
    }

    public suspend fun verifyBridge(): TermuxAnswer<Unit> =
        when (val result = run("printf 'opencode-bridge-ok'")) {
            is TermuxAnswer.Failed -> result
            is TermuxAnswer.Done ->
                if (result.value.stdout.trim() == "opencode-bridge-ok") {
                    TermuxAnswer.Done(Unit)
                } else {
                    TermuxAnswer.Failed(TermuxBridgeError("Termux returned an unexpected bridge response.", "invalid_probe"))
                }
        }

    public suspend fun ensureWakeLock(): TermuxAnswer<Unit> =
        when (val result = run(TermuxScripts.ensureWakeLockScript, timeoutMillis = 10_000)) {
            is TermuxAnswer.Failed -> result
            is TermuxAnswer.Done -> TermuxAnswer.Done(Unit)
        }

    public suspend fun status(): TermuxAnswer<TermuxSetupStatus> =
        when (val result = run(TermuxScripts.statusScript())) {
            is TermuxAnswer.Failed -> result
            is TermuxAnswer.Done ->
                TermuxSetupStatus.parse(result.value.stdout)?.let { TermuxAnswer.Done(it) }
                    ?: TermuxAnswer.Failed(TermuxRuntime.InvalidRuntime)
        }

    public suspend fun setupSnapshot(): TermuxAnswer<TermuxSetupSnapshot> =
        when (val result = run(TermuxScripts.setupSnapshotScript())) {
            is TermuxAnswer.Failed -> result
            is TermuxAnswer.Done ->
                TermuxSetupSnapshot.parse(result.value.stdout)?.let { TermuxAnswer.Done(it) }
                    ?: TermuxAnswer.Failed(TermuxRuntime.InvalidRuntime)
        }

    public suspend fun diagnostics(): TermuxAnswer<String> =
        when (val result = run(TermuxScripts.diagnosticsScript())) {
            is TermuxAnswer.Failed -> result
            is TermuxAnswer.Done -> TermuxAnswer.Done(result.value.stdout.trim())
        }

    public suspend fun inspectInstallation(): TermuxAnswer<TermuxInstallation> =
        when (val result = run(TermuxScripts.installationScript(), timeoutMillis = 25_000)) {
            is TermuxAnswer.Failed -> result
            is TermuxAnswer.Done ->
                TermuxInstallation.parse(result.value.stdout)?.let { TermuxAnswer.Done(it) }
                    ?: TermuxAnswer.Failed(TermuxInstallation.Unreadable)
        }

    // ---- Durable v2 setup (B-LINUX-246): metadata is allowlisted and redacted before the native host stores it.

    /** One setup component as the setup engine describes it. */
    public data class SetupComponent(
        val id: String,
        val script: String? = null,
        val native: Boolean = false,
        val step: Boolean = false,
        val skipped: Boolean = false,
        val weight: Int? = null,
        val version: String? = null,
        val data: Map<String, Any?> = emptyMap(),
    )

    /**
     * Sends only allowlisted, redacted metadata; the native component always runs the pinned [TermuxScripts.setupBaseScript].
     * Display [texts] never cross the channel. Unsupported or credential parameters fail before anything is sent.
     */
    public suspend fun startSetup(
        jobId: String,
        components: List<SetupComponent>,
        params: Map<String, Map<String, String>> = emptyMap(),
        @Suppress("UNUSED_PARAMETER") texts: Map<String, String> = emptyMap(),
    ): TermuxAnswer<Unit> {
        if (!JOB_ID.matches(jobId)) return TermuxAnswer.Failed(TermuxBridgeError("Invalid setup job id.", "invalid_setup"))
        val safeParams = linkedMapOf<String, Map<String, String>>()
        for ((group, values) in params) {
            val keys = ALLOWED_PARAMS[group]
            if (keys == null || values.keys.any { it !in keys }) return invalid("Unsupported setup parameter.")
            for ((key, value) in values) {
                val valid =
                    when (key) {
                        "runtime" -> value == "opencode2"
                        "version" -> SETUP_VERSION.matches(value)
                        "first" -> value == "0" || value == "1"
                        "adding" -> value.isEmpty() || ADDING.matches(value)
                        else -> false
                    }
                if (!valid || redactor.containsSecret(value)) return invalid("Invalid setup parameter.")
            }
            safeParams[group] = values.filterKeys { it in keys }.mapValues { redactor.redact(it.value) }
        }
        val safeComponents = mutableListOf<Map<String, Any?>>()
        for (component in components) {
            if (!COMPONENT_ID.matches(component.id)) return invalid("Invalid setup component.")
            if (component.native && component.id != "linux") return invalid("Unsupported native component.")
            safeComponents +=
                mapOf(
                    "id" to component.id,
                    "script" to if (component.native) TermuxScripts.setupBaseScript else component.script,
                    "native" to component.native,
                    "step" to component.step,
                    "skipped" to component.skipped,
                    "weight" to component.weight,
                    "version" to component.version?.let(redactor::redact),
                    "data" to
                        listOf("runtime", "openCodeChanged")
                            .mapNotNull { key -> (component.data[key] as? String)?.let { key to redactor.redact(it) } }
                            .toMap(),
                )
        }
        return setupInvoke("startSetup", mapOf("jobId" to jobId, "components" to safeComponents, "params" to safeParams))
    }

    public suspend fun cancelSetup(): TermuxAnswer<Unit> = setupInvoke("cancelSetup", null)

    /** Native persists fixed failure copy; arbitrary [error] text never crosses the channel. */
    public suspend fun completeSetupStep(
        jobId: String,
        id: String,
        ok: Boolean,
        @Suppress("UNUSED_PARAMETER") error: String? = null,
        version: String? = null,
    ): TermuxAnswer<Unit> =
        setupInvoke(
            "completeSetupStep",
            mapOf("jobId" to jobId, "id" to id, "ok" to ok, "version" to version?.let(redactor::redact)),
        )

    private suspend fun setupInvoke(method: String, arguments: Map<String, Any?>?): TermuxAnswer<Unit> {
        if (!supported) return TermuxAnswer.Failed(Unsupported)
        return when (val reply = channel.invoke(method, arguments)) {
            is ChannelReply.Value -> TermuxAnswer.Done(Unit)
            ChannelReply.MissingHandler -> TermuxAnswer.Failed(Unsupported)
            is ChannelReply.PlatformError ->
                TermuxAnswer.Failed(TermuxBridgeError(redactor.redact(reply.message ?: "Termux setup failed."), reply.code))
        }
    }
}

internal fun Any?.asStringMap(): Map<String, Any?> =
    (this as? Map<*, *>)?.entries?.associate { (key, value) -> key.toString() to value } ?: emptyMap()

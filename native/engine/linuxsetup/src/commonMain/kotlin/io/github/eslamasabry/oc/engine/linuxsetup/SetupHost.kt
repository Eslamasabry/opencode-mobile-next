package io.github.eslamasabry.oc.engine.linuxsetup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A host answer: the value, or a refusal with the native code (never shown as text; Details only, redacted). */
public sealed interface HostReply<out T> {
    public data class Ok<out T>(val value: T) : HostReply<T>

    public data class Refused(val message: String, val code: String? = null) : HostReply<Nothing>
}

public data class SetupLinuxStatus(val installed: Boolean, val serverRunning: Boolean = false)

public data class SetupRunResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0
}

/** The native side of setup (BuiltinLinux / the Termux setup host), as a port. */
public interface SetupLinuxHost {
    public suspend fun status(): HostReply<SetupLinuxStatus>

    public suspend fun run(script: String): HostReply<SetupRunResult>

    /** setup.json's text, or null when there is no job. */
    public suspend fun setupStatus(): HostReply<String?>

    public suspend fun startSetup(
        jobId: String,
        components: List<SetupRunnerSpec>,
        params: Map<String, Map<String, String>>,
        texts: SetupNotificationTexts,
    ): HostReply<Unit>
}

/**
 * Bumped whenever a setup job ends or a removal attempts mutation: pages that list installed tools read the phone
 * again (port of `setupToolsChanged`).
 */
public class SetupToolsChanged {
    private val revisions = MutableStateFlow(0)
    public val revision: StateFlow<Int> = revisions.asStateFlow()

    public fun bump() {
        revisions.update { it + 1 }
    }
}

/** Where finished components and jobs are recorded as timed spans (PerfTrace in Flutter). */
public fun interface SetupTrace {
    public fun recordDuration(name: String, millis: Long, attrs: Map<String, String>, failed: Boolean)

    public companion object {
        public val None: SetupTrace = SetupTrace { _, _, _, _ -> }
    }
}

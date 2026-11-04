package io.github.eslamasabry.oc.engine.runtime.projects

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.setup.LinuxChannel
import io.github.eslamasabry.oc.engine.runtime.setup.ToolsChanged
import io.github.eslamasabry.oc.engine.runtime.setup.channelInt

/**
 * Fresh, measured logical sizes of the two removal choices (port of builtin_linux.dart `BuiltinProjectStorage`).
 * [runtimeBytes] is Ubuntu, tools, runtime settings and logs and any downloaded Ubuntu archive; [projectsBytes] is
 * everything beneath `/root/projects`, hidden files and repositories included, links not followed.
 */
public data class ProjectStorage(val runtimeBytes: Long, val projectsBytes: Long, val measuredAtMilliseconds: Long) {
    public val keepProjectsFreedBytes: Long get() = runtimeBytes

    public val deleteEverythingFreedBytes: Long get() = runtimeBytes + projectsBytes

    public companion object {
        /**
         * The native `projectStorage` reply, or null when it is not a map or any of the three numbers is missing,
         * negative, fractional, a string or a Boolean. Unknown storage is never shown as zero bytes free.
         */
        public fun fromReply(reply: Any?): ProjectStorage? {
            val map = reply as? Map<*, *> ?: return null
            fun read(key: String): Long? = channelInt(map[key])?.takeIf { it >= 0 }
            return ProjectStorage(
                runtimeBytes = read("runtimeBytes") ?: return null,
                projectsBytes = read("projectsBytes") ?: return null,
                measuredAtMilliseconds = read("measuredAtMilliseconds") ?: return null,
            )
        }
    }
}

/**
 * Removing the in-app Linux (port of builtin_linux.dart `remove` / `uninstall` / `projectStorage`; B-LINUX-029,
 * B-LINUX-030). Projects are kept unless the person explicitly chose to delete everything and typed the app name
 * exactly ([DELETION_CONFIRMATION_NAME]); a wrong confirmation sends nothing. Removal uses its own native method,
 * `removeRuntime`, so an older app whose `uninstall` ignores the preservation argument never receives a destructive
 * call. Native text never reaches a failure: measuring fails as `Runtime(StorageUnavailable)`, removing as
 * `Runtime(RemovalFailed)`, an absent channel stays `Unavailable`. Every removal attempt bumps [toolsChanged].
 */
public class ProjectRemoval(private val channel: LinuxChannel, private val toolsChanged: ToolsChanged? = null) {
    /** Measures both removal choices afresh. */
    public suspend fun measure(): Outcome<ProjectStorage> =
        when (val reply = channel.invoke("projectStorage", null)) {
            is Outcome.Failed -> safe(reply.failure, RuntimeProblem.StorageUnavailable).failed()
            is Outcome.Ok -> ProjectStorage.fromReply(reply.value)?.ok() ?: Failure.Runtime(RuntimeProblem.StorageUnavailable).failed()
        }

    /**
     * Removes the runtime, keeping projects by default. Native code repeats the confirmation check and refuses while
     * setup runs. A failure means: measure again, never assume the space was freed.
     */
    public suspend fun remove(alsoDeleteProjects: Boolean = false, confirmationName: String? = null): Outcome<Unit> {
        if (alsoDeleteProjects && confirmationName != DELETION_CONFIRMATION_NAME) {
            return Failure.Runtime(RuntimeProblem.ConfirmationRequired).failed()
        }
        val arguments = buildMap<String, Any?> {
            put("alsoDeleteProjects", alsoDeleteProjects)
            if (alsoDeleteProjects) put("confirmationName", DELETION_CONFIRMATION_NAME)
        }
        return try {
            when (val reply = channel.invoke("removeRuntime", arguments)) {
                is Outcome.Failed -> safe(reply.failure, RuntimeProblem.RemovalFailed).failed()
                is Outcome.Ok -> Unit.ok()
            }
        } finally {
            toolsChanged?.bump()
        }
    }

    /** The legacy entry point: the safe default, projects kept. */
    public suspend fun uninstall(): Outcome<Unit> = remove()

    private fun safe(failure: Failure, problem: RuntimeProblem): Failure =
        if (failure is Failure.Unavailable) Failure.Unavailable(failure.feature) else Failure.Runtime(problem)

    public companion object {
        /** The literal app name the destructive confirmation asks the person to type. */
        public const val DELETION_CONFIRMATION_NAME: String = "OpenCode"
    }
}

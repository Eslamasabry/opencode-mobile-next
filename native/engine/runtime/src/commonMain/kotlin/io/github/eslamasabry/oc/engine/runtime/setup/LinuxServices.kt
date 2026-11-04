package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.map

/**
 * Named long-running services on the Linux channel (port of builtin_linux.dart `startService` / `stopService` /
 * `serviceLog` and `BuiltinLinuxStatus.services`; B-LINUX-008, amended: team discovery is deferred by D6). Each service
 * runs in its own proot, owned by the app like the OpenCode server (the service [SERVER]). A service that runs already
 * is restarted by the native side. Never start a long-running program through a one-shot `run` instead: its proot
 * ends with the script and takes everything it started with it.
 */
public class LinuxServices(private val channel: LinuxChannel) {
    /**
     * `startService {name, script, port?, notice?}`. [notice] is what the ongoing notification says while it runs
     * (words from `:ui:words`). Without a port or a notice the key is left out, never sent as null.
     */
    public suspend fun start(name: String, script: String, port: Int? = null, notice: String? = null): Outcome<Unit> {
        val arguments = buildMap<String, Any?> {
            put("name", name)
            put("script", script)
            if (port != null) put("port", port)
            if (notice != null) put("notice", notice)
        }
        return channel.invoke("startService", arguments).map { }
    }

    /** `stopService {name}`. */
    public suspend fun stop(name: String): Outcome<Unit> = channel.invoke("stopService", mapOf("name" to name)).map { }

    /** `serviceLog {name, tailBytes}`: the end of the service's log, for a failure's Details only. No reply reads "". */
    public suspend fun log(name: String, tailBytes: Int = DEFAULT_LOG_TAIL_BYTES): Outcome<String> =
        channel.invoke("serviceLog", mapOf("name" to name, "tailBytes" to tailBytes)).map { it as? String ?: "" }

    /** The services running now, from `status`. */
    public suspend fun running(): Outcome<List<String>> =
        channel.invoke("status", null).map { reply -> servicesOf((reply as? Map<*, *>)?.get("services")) }

    public companion object {
        /** The OpenCode server's name among the services (BuiltinLinux.kt SERVER). */
        public const val SERVER: String = "server"

        public const val DEFAULT_LOG_TAIL_BYTES: Int = 16384

        /** `status.services`: only the names that are strings; anything else in the list, or no list, is left out. */
        public fun servicesOf(raw: Any?): List<String> = (raw as? List<*>).orEmpty().filterIsInstance<String>()

        public fun serviceRunning(services: List<String>, name: String): Boolean = name in services
    }
}

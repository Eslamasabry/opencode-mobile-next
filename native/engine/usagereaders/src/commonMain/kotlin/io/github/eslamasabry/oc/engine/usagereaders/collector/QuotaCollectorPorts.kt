package io.github.eslamasabry.oc.engine.usagereaders.collector

import io.github.eslamasabry.oc.core.common.Outcome

/** In-app Ubuntu as the collector host sees it. [ready] = installed and in its ready phase. */
public data class LinuxStatus(val installed: Boolean, val ready: Boolean, val services: List<String>)

/** What one script printed. Holds counts and fixed codes only (the scripts never print a value). */
public data class LinuxRun(val exitCode: Int, val output: String) {
    override fun toString(): String = "LinuxRun($exitCode, ${output.length} chars)"
}

/**
 * PORT. The app's own In-app Ubuntu (proot) shell (Flutter `BuiltinLinux`). Scripts never hold a token or a key.
 */
public interface InAppLinux {
    public suspend fun status(): Outcome<LinuxStatus>

    public suspend fun run(script: String, timeoutMs: Long): Outcome<LinuxRun>

    /** Starts (or restarts) a named supervised service running [script], listening on [port]. */
    public suspend fun startService(name: String, script: String, port: Int?): Outcome<Unit>

    public suspend fun stopService(name: String): Outcome<Unit>

    /** The end of a service's log, for Details. */
    public suspend fun serviceLog(name: String): Outcome<String>
}

/**
 * PORT. Files inside In-app Ubuntu's root file system. [path] is the path as Ubuntu sees it (`/root/...`); the adapter
 * puts it under the rootfs folder and creates missing parent folders on write.
 */
public interface RootfsFiles {
    /** Null when the file is missing. */
    public suspend fun read(path: String): ByteArray?

    /** True when written. */
    public suspend fun write(path: String, bytes: ByteArray): Boolean
}

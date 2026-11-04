package io.github.eslamasabry.oc.engine.integrations.host

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The built-in Ubuntu's shell (Flutter `ByoHostLocalShell`). Native output is deliberately not returned: every
 * operation redirects output to an app-private file before invoking a program that may print credentials.
 */
public interface HostLocalShell {
    public suspend fun available(): Boolean

    /** Runs [script]; only the exit code comes back. */
    public suspend fun run(script: String, timeoutMs: Long): Int

    /** Starts the long-running service [name] (the SSH tunnel) owning local [port]. */
    public suspend fun start(name: String, script: String, port: Int)

    public suspend fun stop(name: String)

    public suspend fun running(name: String): Boolean
}

/** What is at a path, without following symlinks. */
public enum class HostEntryKind { Missing, Directory, File, Link }

/**
 * The app-private files the shell shares with the phone. Paths are absolute local paths; nothing here follows a link.
 * `/tmp/x` inside Ubuntu is `<supportDirectory>/linux/ubuntu/tmp/x` here.
 */
public interface HostPrivateFiles {
    public suspend fun supportDirectory(): String

    public suspend fun kind(path: String): HostEntryKind

    /** Child leaf names of the directory [path]. */
    public suspend fun list(path: String): List<String>

    /** Deletes [path] and everything below it. */
    public suspend fun deleteTree(path: String)

    public suspend fun write(path: String, text: String)

    public suspend fun size(path: String): Long

    public suspend fun read(path: String): String
}

/** Loopback TCP helpers for the tunnel's local end. */
public interface LoopbackPorts {
    /** A free 127.0.0.1 port (bind 0, read, close). */
    public suspend fun freePort(): Int

    /** True when something accepts a connection on 127.0.0.1:[port] within a short wait. */
    public suspend fun canConnect(port: Int): Boolean
}

/** One request to the supervisor over the tunnel. */
public class LoopbackRequest(
    public val method: String,
    public val port: Int,
    public val path: String,
    /** The full `Authorization` header value. Never logged. */
    public val authorization: String,
) {
    override fun toString(): String = "LoopbackRequest($method $path)"
}

public class LoopbackReply(public val status: Int, public val body: ByteArray)

/**
 * Plain HTTP to `127.0.0.1:<port>` with no proxy. Contract: a redirect is NEVER followed (the reply carries its 3xx
 * status), so the Basic credentials can only ever reach the loopback tunnel. Bodies above [maxBytes] are refused.
 */
public fun interface LoopbackHttp {
    public suspend fun exchange(request: LoopbackRequest, maxBytes: Int): LoopbackReply
}

/** Random bytes from a secure source. */
public fun interface HostRandom {
    public fun bytes(count: Int): ByteArray

    public companion object {
        /** `Uuid.random()` draws from the platform's secure generator. */
        @OptIn(ExperimentalUuidApi::class)
        public val Secure: HostRandom =
            HostRandom { count ->
                val out = ArrayList<Byte>(count + 16)
                while (out.size < count) out.addAll(Uuid.random().toByteArray().toList())
                out.take(count).toByteArray()
            }
    }
}

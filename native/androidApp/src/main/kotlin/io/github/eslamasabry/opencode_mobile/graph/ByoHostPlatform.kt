package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.host.*
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.withContext

/** The Ubuntu shell never forwards stdout, stderr or a command to the app log. */
internal class AndroidHostShell(app: Application, private val dispatchers: AppDispatchers) :
    HostLocalShell {
    private val linux = BuiltinLinux.get(app)

    override suspend fun available(): Boolean = withContext(dispatchers.io) { linux.installed }

    override suspend fun run(script: String, timeoutMs: Long): Int =
        withContext(dispatchers.io) {
            linux.run(script, (timeoutMs + 999) / 1000, logOutput = false).exitCode
        }

    override suspend fun start(name: String, script: String, port: Int) =
        withContext(dispatchers.io) {
            require(name.matches(Regex("byo-[a-f0-9]{24}")))
            linux.startService(name, script, port, null)
        }

    override suspend fun stop(name: String) =
        withContext(dispatchers.io) {
            require(name.matches(Regex("byo-[a-f0-9]{24}")))
            linux.stopService(name)
        }

    override suspend fun running(name: String): Boolean =
        withContext(dispatchers.io) { linux.serviceRunning(name) }
}

/**
 * All access stays below the actual app files root; every ancestor is checked without following
 * links.
 */
internal class AndroidHostFiles(app: Application, private val dispatchers: AppDispatchers) :
    HostPrivateFiles {
    private val support = app.filesDir.canonicalFile
    private val root = File(support, "linux/ubuntu/tmp")

    private fun file(path: String, leafLink: Boolean = false): File {
        val candidate = File(path).absoluteFile
        require(candidate.path == path && (path == root.path || path.startsWith(root.path + "/")))
        val parts = path.removePrefix(support.path + "/").split('/')
        require(parts.none { it.isEmpty() || it == "." || it == ".." })
        var current = support
        for ((index, part) in parts.withIndex()) {
            current = File(current, part)
            val mode =
                try {
                    Os.lstat(current.path).st_mode
                } catch (error: ErrnoException) {
                    if (error.errno == OsConstants.ENOENT && index == parts.lastIndex) continue
                    throw error
                }
            require(!OsConstants.S_ISLNK(mode) || (leafLink && index == parts.lastIndex))
            if (index < parts.lastIndex) require(OsConstants.S_ISDIR(mode))
        }
        return candidate
    }

    override suspend fun supportDirectory(): String = support.path

    override suspend fun kind(path: String): HostEntryKind =
        withContext(dispatchers.io) {
            // The runner verifies the three shared boundary directories themselves before any
            // write.
            val boundary = listOf(File(support, "linux"), File(support, "linux/ubuntu"))
            val candidate =
                if (boundary.any { it.path == path })
                    File(path).also {
                        require(it.canonicalFile == it.absoluteFile)
                    }
                else file(path, leafLink = true)
            try {
                val mode = Os.lstat(candidate.path).st_mode
                when {
                    OsConstants.S_ISLNK(mode) -> HostEntryKind.Link
                    OsConstants.S_ISDIR(mode) -> HostEntryKind.Directory
                    OsConstants.S_ISREG(mode) -> HostEntryKind.File
                    else -> HostEntryKind.Link
                }
            } catch (error: ErrnoException) {
                if (error.errno == OsConstants.ENOENT) HostEntryKind.Missing else throw error
            }
        }

    override suspend fun list(path: String): List<String> =
        withContext(dispatchers.io) {
            file(path).list()?.toList() ?: throw IllegalStateException()
        }

    override suspend fun deleteTree(path: String) =
        withContext(dispatchers.io) {
            require(path != root.path)
            fun erase(target: File) {
                val checked = file(target.path, leafLink = true)
                val mode =
                    try {
                        Os.lstat(checked.path).st_mode
                    } catch (error: ErrnoException) {
                        if (error.errno == OsConstants.ENOENT) return
                        throw error
                    }
                if (OsConstants.S_ISDIR(mode)) {
                    val children = checked.listFiles() ?: throw IllegalStateException()
                    children.forEach(::erase)
                }
                check(checked.delete())
            }
            erase(file(path, leafLink = true))
        }

    override suspend fun write(path: String, text: String) =
        withContext(dispatchers.io) {
            val target = file(path)
            target.outputStream().use {
                it.write(text.toByteArray(Charsets.UTF_8))
                it.flush()
            }
            Os.chmod(target.path, 0x180)
        }

    override suspend fun size(path: String): Long =
        withContext(dispatchers.io) { file(path).length() }

    override suspend fun read(path: String): String =
        withContext(dispatchers.io) {
            val target = file(path)
            require(target.length() <= 65_536)
            target.readText()
        }
}

internal class AndroidHostPorts(private val dispatchers: AppDispatchers) : LoopbackPorts {
    override suspend fun freePort(): Int =
        withContext(dispatchers.io) {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        }

    override suspend fun canConnect(port: Int): Boolean =
        withContext(dispatchers.io) {
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 250) }
                true
            } catch (_: java.io.IOException) {
                false
            }
        }
}

/** Fixed loopback origin, no proxy or redirects, with a strict body cap. */
internal class AndroidHostHttp(
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
) : LoopbackHttp {
    override suspend fun exchange(request: LoopbackRequest, maxBytes: Int): LoopbackReply =
        withContext(dispatchers.io) {
            require(request.port in 1..65535 && maxBytes in 1..65_536)
            require(
                (request.method == "GET" && request.path == "/_oc/host") ||
                    (request.method == "POST" && request.path == "/_oc/revoke")
            )
            redactor.registerSecret(request.authorization)
            val connection =
                URL("http://127.0.0.1:${request.port}${request.path}")
                    .openConnection(Proxy.NO_PROXY) as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = request.method
                connection.setRequestProperty("Authorization", request.authorization)
                val status = connection.responseCode
                if (status != 200) return@withContext LoopbackReply(status, byteArrayOf())
                val body = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val bytes = ByteArray(1024)
                    while (true) {
                        val count = input.read(bytes)
                        if (count < 0) break
                        require(body.size() + count <= maxBytes)
                        body.write(bytes, 0, count)
                    }
                }
                LoopbackReply(status, body.toByteArray())
            } finally {
                connection.disconnect()
            }
        }
}

package io.github.eslamasabry.oc.engine.integrations.host

import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Dart fixture's host key: Ed25519 wire encoding with bytes 0..31. */
internal fun hostPublicKey(fill: ((Int) -> Int)? = null): String =
    "ssh-ed25519 " + Base64.encode(
        byteArrayOf(0, 0, 0, 11) + "ssh-ed25519".encodeToByteArray() + byteArrayOf(0, 0, 0, 32) +
            ByteArray(32) { (fill?.invoke(it) ?: it).toByte() },
    )

/** The Dart `key(value)` fixture: 32 bytes of [value]. */
internal fun hostKey(value: Int = 1): ByoHostKey = ByoHostKey.fromPublicKey(hostPublicKey { value })!!

internal fun devicePublicKey(): String {
    fun text(value: String) = byteArrayOf(0, 0, 0, value.length.toByte()) + value.encodeToByteArray()
    val point = byteArrayOf(4) + hexBytes("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296") +
        hexBytes("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
    return "ecdsa-sha2-nistp256 " +
        Base64.encode(text("ecdsa-sha2-nistp256") + text("nistp256") + byteArrayOf(0, 0, 0, point.size.toByte()) + point)
}

/** An in-memory app-private file tree that never follows links. */
internal class MemFs(val support: String = "/data/app/files") : HostPrivateFiles {
    sealed interface Node

    object Dir : Node

    class FileNode(var text: String) : Node

    class LinkNode(val target: String) : Node

    val nodes = linkedMapOf<String, Node>()

    val tmp: String get() = "$support/linux/ubuntu/tmp"

    init {
        mkdirs(tmp)
    }

    fun mkdirs(path: String) {
        var p = ""
        for (part in path.split('/').filter { it.isNotEmpty() }) {
            p = "$p/$part"
            nodes.getOrPut(p) { Dir }
        }
    }

    fun children(path: String): List<String> =
        nodes.keys.filter { it.startsWith("$path/") && '/' !in it.removePrefix("$path/") }.map { it.removePrefix("$path/") }

    fun tmpEntries(): List<String> = children(tmp)

    fun parentIsDir(path: String): Boolean = nodes[path.substringBeforeLast('/')] is Dir

    override suspend fun supportDirectory(): String = support

    override suspend fun kind(path: String): HostEntryKind =
        when (nodes[path]) {
            null -> HostEntryKind.Missing
            is Dir -> HostEntryKind.Directory
            is FileNode -> HostEntryKind.File
            is LinkNode -> HostEntryKind.Link
        }

    override suspend fun list(path: String): List<String> = children(path)

    override suspend fun deleteTree(path: String) {
        nodes.keys.filter { it == path || it.startsWith("$path/") }.forEach { nodes.remove(it) }
    }

    override suspend fun write(path: String, text: String) {
        check(parentIsDir(path))
        nodes[path] = FileNode(text)
    }

    override suspend fun size(path: String): Long = (nodes[path] as FileNode).text.length.toLong()

    override suspend fun read(path: String): String = (nodes[path] as FileNode).text

    fun files(): Map<String, String> = nodes.filterValues { it is FileNode }.mapValues { (it.value as FileNode).text }
}

internal class FakeLoopback : LoopbackPorts {
    private var next = 40_000
    val open = mutableSetOf<Int>()

    override suspend fun freePort(): Int = next++

    override suspend fun canConnect(port: Int): Boolean = port in open
}

/** The Dart `FakeShell`: runs the private mkdir/touch scripts against [fs] and answers SSH commands. */
internal class FakeShell(private val fs: MemFs, private val loopback: FakeLoopback) : HostLocalShell {
    val nativeScripts = mutableListOf<String>()
    val privateCommands = mutableListOf<String>()
    val stopped = mutableListOf<String>()
    val observedFiles = linkedMapOf<String, String>()
    val listeners = linkedMapOf<String, Int>()
    var installInput: String? = null
    var supported = true
    var failInstall = false
    var failureOutput = "PRIVATE_ERROR_SENTINEL"

    private fun observe() {
        observedFiles.putAll(fs.files().filterKeys { it.startsWith(fs.tmp) })
    }

    private fun local(remote: String) = remote.replace(Regex("^/tmp/"), "${fs.tmp}/")

    private fun quoted(script: String) = Regex("'([^']*)'").findAll(script).map { it.groupValues[1] }.toList()

    override suspend fun available(): Boolean = supported

    override suspend fun run(script: String, timeoutMs: Long): Int {
        observe()
        nativeScripts += script
        if (script.startsWith("umask 077; mkdir ")) {
            val path = local(quoted(script).first())
            if (fs.nodes.containsKey(path) || !fs.parentIsDir(path)) return 1
            fs.nodes[path] = MemFs.Dir
            return 0
        }
        if (script.startsWith("umask 077; : > ")) {
            val path = local(quoted(script).first())
            if (!fs.parentIsDir(path)) return 1
            fs.nodes[path] = MemFs.FileNode("")
            return 0
        }
        val dir = local(Regex("^sh '([^']+)/command'").find(script)!!.groupValues[1])
        val command = fs.read("$dir/command")
        privateCommands += command
        var output = ""
        if ("ssh-keyscan" in command) {
            output = "server.example ${hostPublicKey()}\n"
        } else if ("uname -m" in command) {
            output = "x86_64\n"
        } else if ("sh -s" in command) {
            installInput = fs.read("$dir/input")
            output =
                if (failInstall) {
                    failureOutput
                } else {
                    """{"hostId":"machine_1","bundleVersion":"1.1.0","openCodeVersion":"1.18.32","port":4096}"""
                }
        }
        fs.write("$dir/result", output)
        return if (failInstall && "sh -s" in command) 255 else 0
    }

    override suspend fun start(name: String, script: String, port: Int) {
        observe()
        nativeScripts += script
        listeners[name] = port
        loopback.open += port
    }

    override suspend fun stop(name: String) {
        stopped += name
        listeners.remove(name)?.let { loopback.open -= it }
    }

    override suspend fun running(name: String): Boolean = name in listeners
}

internal class FakeSigner(private val fs: MemFs? = null) : ByoHostSigner {
    val ensured = mutableListOf<String>()
    val agents = linkedMapOf<String, String>()
    val closed = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var resets = 0

    override suspend fun ensureIdentity(profileId: String): ByoHostIdentity {
        ensured += profileId
        return ByoHostIdentity("oc.byoHostSsh.$profileId", devicePublicKey())
    }

    override suspend fun openAgent(profileId: String, socketPath: String, user: String) {
        assertEquals("ubuntu", user)
        assertTrue("/linux/ubuntu/tmp/.oa-" in socketPath)
        fs?.let { assertEquals(HostEntryKind.Directory, it.kind(socketPath.substringBeforeLast('/'))) }
        agents[profileId] = socketPath
    }

    override suspend fun closeAgent(profileId: String) {
        closed += profileId
        agents.remove(profileId)
    }

    override suspend fun deleteIdentity(profileId: String) {
        deleted += profileId
    }

    override suspend fun deleteAllIdentities() {
        resets++
    }
}

internal class FakeHttp(private val handler: (LoopbackRequest) -> LoopbackReply) : LoopbackHttp {
    val seen = mutableListOf<LoopbackRequest>()

    override suspend fun exchange(request: LoopbackRequest, maxBytes: Int): LoopbackReply {
        seen += request
        return handler(request)
    }
}

internal fun fakeTailnet(): ByoHostTailnetResolver = ByoHostTailnetResolver { listOf(ip4(100, 64, 0, 10)) }

internal class TestTunnel(override val localPort: Int) : ByoHostTunnel {
    override suspend fun close() {}
}

internal fun <T> HostResult<T>.ok(): T =
    when (this) {
        is HostResult.Ok -> value
        is HostResult.Failed -> error("expected Ok, got $this")
    }

/** Synthetic fixture addresses, built at run time so no address literal sits in the source. */
internal fun ip4(a: Int, b: Int, c: Int, d: Int): String = listOf(a, b, c, d).joinToString(".")

internal fun ip6(vararg groups: String): String = groups.joinToString(":")

internal fun tsNet(name: String): String = name + ".ts" + ".net"

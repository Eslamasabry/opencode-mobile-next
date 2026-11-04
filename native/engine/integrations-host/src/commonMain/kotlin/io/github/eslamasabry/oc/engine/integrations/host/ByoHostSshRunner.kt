package io.github.eslamasabry.oc.engine.integrations.host

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Flutter `ByoHostSshRunner`: every method answers a fixed code; no remote stderr or body becomes a
 * failure.
 */
public interface ByoHostSshRunner {
    public suspend fun available(): Boolean

    public suspend fun inspectKey(target: ByoHostTarget): HostResult<ByoHostKey>

    public suspend fun generateIdentity(profileId: String): HostResult<ByoHostIdentity>

    public suspend fun install(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        login: ByoHostLogin,
        identity: ByoHostIdentity,
        deviceToken: String,
        bundle: ByoHostBundle,
    ): HostResult<ByoHostDescriptor>

    public suspend fun forward(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        identity: ByoHostIdentity,
        remotePort: Int,
    ): HostResult<ByoHostTunnel>

    public suspend fun describe(
        tunnel: ByoHostTunnel,
        deviceId: String,
        deviceToken: String,
    ): HostResult<ByoHostDescriptor>

    /** True only when the supervisor confirmed the revocation; every failure is false. */
    public suspend fun revoke(tunnel: ByoHostTunnel, deviceId: String, deviceToken: String): Boolean

    public suspend fun cleanup(profileId: String): HostResult<Unit>

    public suspend fun dispose()
}

/**
 * OpenSSH inside the built-in Ubuntu (Flutter `BuiltinByoHostSshRunner`). No process receives a
 * secret in its argv, no imported private key ever touches disk (the device key lives in the
 * Keystore and signs through the native agent), the host key is pinned, the installer is
 * authenticated by the reviewed archive digest before it runs, and the supervisor is reached over
 * authenticated loopback without redirects. The caller serializes work per profile.
 */
public class BuiltinByoHostSshRunner(
    private val shell: HostLocalShell,
    private val files: HostPrivateFiles,
    private val signer: ByoHostSigner,
    private val tailnet: ByoHostTailnetResolver,
    private val ports: LoopbackPorts,
    private val http: LoopbackHttp,
    private val random: HostRandom = HostRandom.Secure,
) : ByoHostSshRunner {
    private val agentDirectories = mutableMapOf<String, String>()
    private val forwardingProfiles = mutableSetOf<String>()
    private val tunnels = mutableSetOf<SshTunnel>()
    private val activePaths = mutableSetOf<String>()
    private var disposed = false

    private fun nonce(): String =
        random.bytes(12).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    override suspend fun available(): Boolean =
        try {
            !disposed && shell.available()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }

    private fun digest16(profileId: String): String = DartJson.sha256Hex(profileId).substring(0, 16)

    private fun profilePrefix(profileId: String): String = "oc-byo-${digest16(profileId)}-"

    private suspend fun privateRoot(profileId: String): String {
        if (!byoHostSafeId(profileId)) abort(ByoHostFailureCode.InvalidTarget)
        if (!available()) abort(ByoHostFailureCode.Unavailable)
        val support = files.supportDirectory()
        val root = "$support/linux/ubuntu"
        // Reject symlinks in the shared-files boundary, including /tmp itself.
        for (path in listOf("$support/linux", root, "$root/tmp")) {
            if (files.kind(path) != HostEntryKind.Directory) abort(ByoHostFailureCode.Storage)
        }
        return root
    }

    private suspend fun cleanupProfile(profileId: String, root: String) {
        val prefix = profilePrefix(profileId)
        val orphan = Regex("^${Regex.escape(prefix)}[a-f0-9]{24}$")
        // Recover only this profile's abandoned directories. Stop the exact native tunnel service
        // before deleting its
        // key; never match processes by pattern.
        for (leaf in files.list("$root/tmp")) {
            val path = "$root/tmp/$leaf"
            if (!orphan.matches(leaf) || path in activePaths) continue
            if (files.kind(path) != HostEntryKind.Directory) continue
            shell.stop("byo-${leaf.substring(prefix.length)}")
            files.deleteTree(path)
        }
    }

    /**
     * Fresh-process recovery: erase this profile's dead work only; keep its Keystore identity and
     * live tunnel.
     */
    public suspend fun recoverOrphans(profileId: String): HostResult<Unit> =
        hostResult(ByoHostFailureCode.Storage) {
            val root = privateRoot(profileId)
            cleanupProfile(profileId, root)
            if (profileId !in forwardingProfiles && profileId !in agentDirectories) {
                val stale = "$root/tmp/.oa-${digest16(profileId)}"
                if (files.kind(stale) != HostEntryKind.Missing) files.deleteTree(stale)
            }
        }

    override suspend fun cleanup(profileId: String): HostResult<Unit> =
        hostResult(ByoHostFailureCode.Storage) {
            if (profileId in forwardingProfiles) abort(ByoHostFailureCode.Busy)
            val root = privateRoot(profileId)
            closeAgent(profileId)
            val stale = "$root/tmp/.oa-${digest16(profileId)}"
            if (files.kind(stale) != HostEntryKind.Missing) files.deleteTree(stale)
            cleanupProfile(profileId, root)
            signer.deleteIdentity(profileId)
        }

    private suspend fun work(profileId: String): PrivateWork =
        try {
            newWork(profileId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HostAbort) {
            throw e
        } catch (_: Throwable) {
            abort(ByoHostFailureCode.Storage)
        }

    private suspend fun newWork(profileId: String): PrivateWork {
        val root = privateRoot(profileId)
        cleanupProfile(profileId, root)
        val name = "${profilePrefix(profileId)}${nonce()}"
        val local = "$root/tmp/$name"
        // mkdir/chmod run before any sensitive byte is written. Random exclusive path.
        val remote = "/tmp/$name"
        activePaths += local
        val code =
            shell.run(
                "umask 077; mkdir ${q(remote)} >/dev/null 2>&1 && chmod 700 ${q(remote)} >/dev/null 2>&1",
                SHORT,
            )
        if (code != 0 || files.kind(local) != HostEntryKind.Directory) {
            activePaths -= local
            abort(ByoHostFailureCode.Storage)
        }
        return PrivateWork(local, remote, shell, files) { activePaths -= local }
    }

    override suspend fun inspectKey(target: ByoHostTarget): HostResult<ByoHostKey> =
        hostResult(ByoHostFailureCode.Transport) {
            val resolved = tailnet.resolve(target).orAbort()
            val work = work("scan")
            try {
                val result =
                    work.execute(
                        "ssh-keyscan -T 10 -t ed25519 -p ${resolved.port} ${q(resolved.host)}"
                    )
                if (result.code != 0) abort(ByoHostFailureCode.Transport)
                val keys = mutableSetOf<String>()
                for (line in result.output.lines()) {
                    if (line.startsWith("#") || line.isBlank()) continue
                    val fields = line.trim().split(Regex("\\s+"))
                    if (fields.size == 3 && fields[1] == "ssh-ed25519")
                        keys += "${fields[1]} ${fields[2]}"
                }
                if (keys.size != 1) abort(ByoHostFailureCode.NeedsTrust)
                ByoHostKey.fromPublicKey(keys.single()) ?: abort(ByoHostFailureCode.NeedsTrust)
            } finally {
                work.remove()
            }
        }

    override suspend fun generateIdentity(profileId: String): HostResult<ByoHostIdentity> =
        hostResult(ByoHostFailureCode.Unavailable) {
            if (!available()) abort(ByoHostFailureCode.Unavailable)
            val root = privateRoot(profileId)
            cleanupProfile(profileId, root)
            val identity = signer.ensureIdentity(profileId)
            ByoHostIdentity(identity.keyAlias, identity.publicKey)
        }

    private suspend fun openAgent(
        profileId: String,
        target: ByoHostTarget,
        identity: ByoHostIdentity,
    ): String {
        val actual = signer.ensureIdentity(profileId)
        if (actual.keyAlias != identity.keyAlias || actual.publicKey != identity.publicKey)
            abort(ByoHostFailureCode.IdentityChanged)
        closeAgent(profileId)
        val root = privateRoot(profileId)
        val leaf = ".oa-${digest16(profileId)}"
        val local = "$root/tmp/$leaf"
        when (files.kind(local)) {
            HostEntryKind.Missing -> Unit
            HostEntryKind.Directory ->
                files.deleteTree(local) // a previous process's dead socket, no key bytes
            else -> abort(ByoHostFailureCode.Storage)
        }
        val remote = "/tmp/$leaf"
        val code =
            shell.run(
                "umask 077; mkdir ${q(remote)} >/dev/null 2>&1 && chmod 700 ${q(remote)} >/dev/null 2>&1",
                SHORT,
            )
        if (code != 0) abort(ByoHostFailureCode.Storage)
        agentDirectories[profileId] = local
        try {
            signer.openAgent(profileId, "$local/s", target.user)
            return "$remote/s"
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            closeAgent(profileId)
            abort(ByoHostFailureCode.Unavailable)
        }
    }

    private suspend fun closeAgent(profileId: String) {
        signer.closeAgent(profileId)
        val directory = agentDirectories.remove(profileId)
        if (directory != null && files.kind(directory) != HostEntryKind.Missing)
            files.deleteTree(directory)
    }

    private suspend fun ssh(
        work: PrivateWork,
        target: ByoHostTarget,
        pin: ByoHostKey,
        login: ByoHostLogin,
        agentSocket: String? = null,
        publicKey: String? = null,
    ): String {
        if (ByoHostKey.fromPublicKey(pin.publicKey)?.fingerprint != pin.fingerprint)
            abort(ByoHostFailureCode.HostKeyChanged)
        work.write("known_hosts", "oc-byo-host ${pin.publicKey}\n")
        val args =
            mutableListOf(
                "ssh",
                "-F",
                "/dev/null",
                "-T",
                "-p",
                "${target.port}",
                "-o",
                "HostKeyAlias=oc-byo-host",
                "-o",
                "UserKnownHostsFile=${work.remote}/known_hosts",
                "-o",
                "GlobalKnownHostsFile=/dev/null",
                "-o",
                "StrictHostKeyChecking=yes",
                "-o",
                "HostKeyAlgorithms=ssh-ed25519",
                "-o",
                "UpdateHostKeys=no",
                "-o",
                "VerifyHostKeyDNS=no",
                "-o",
                "IdentityAgent=${agentSocket ?: "none"}",
                "-o",
                "IdentityFile=none",
                "-o",
                "IdentitiesOnly=yes",
                "-o",
                "ForwardAgent=no",
                "-o",
                "ForwardX11=no",
                "-o",
                "PermitLocalCommand=no",
                "-o",
                "ProxyCommand=none",
                "-o",
                "ProxyJump=none",
                "-o",
                "ControlMaster=no",
                "-o",
                "ControlPath=none",
                "-o",
                "ConnectionAttempts=1",
                "-o",
                "ConnectTimeout=15",
                "-o",
                "ServerAliveInterval=15",
                "-o",
                "ServerAliveCountMax=2",
                "-o",
                "NumberOfPasswordPrompts=1",
                "-o",
                "LogLevel=ERROR",
                "-o",
                "EscapeChar=none",
                "-o",
                "RequestTTY=no",
            )
        // Imported bootstrap keys are unsupported: no private key may touch disk.
        if (login.privateKey != null || login.passphrase != null)
            abort(ByoHostFailureCode.Authentication)
        if (agentSocket != null && publicKey != null) {
            work.write("device.pub", "$publicKey\n")
            args +=
                listOf(
                    "-i",
                    "${work.remote}/device.pub",
                    "-o",
                    "PubkeyAcceptedAlgorithms=ecdsa-sha2-nistp256",
                )
        } else {
            args += listOf("-o", "PubkeyAuthentication=no")
        }
        val password = login.password
        var environment = ""
        if (password != null) {
            if ('\n' in password || '\r' in password) abort(ByoHostFailureCode.Authentication)
            work.write("password", password)
            work.write(
                "askpass",
                "#!/bin/sh\ncase \"\$1\" in *assword*) exec cat ${q("${work.remote}/password")};; *) exit 1;; esac\n",
                executable = true,
            )
            environment =
                "DISPLAY=oc-byo SSH_ASKPASS_REQUIRE=force SSH_ASKPASS=${q("${work.remote}/askpass")} "
            args += listOf("-o", "BatchMode=no", "-o", "PreferredAuthentications=password")
        } else {
            args += listOf("-o", "BatchMode=yes", "-o", "PreferredAuthentications=publickey")
        }
        args += listOf("-l", target.user)
        // The caller adds the fixed destination; never a remote command derived from user input.
        return environment + args.joinToString(" ") { q(it) }
    }

    override suspend fun install(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        login: ByoHostLogin,
        identity: ByoHostIdentity,
        deviceToken: String,
        bundle: ByoHostBundle,
    ): HostResult<ByoHostDescriptor> {
        var work: PrivateWork? = null
        var usesAgent = false
        try {
            return hostResult(ByoHostFailureCode.Uncertain) {
                val resolved = tailnet.resolve(target).orAbort()
                if (login.privateKey != null || login.passphrase != null)
                    abort(ByoHostFailureCode.Authentication)
                val job = work(profileId).also { work = it }
                if (!TOKEN.matches(deviceToken)) abort(ByoHostFailureCode.Authentication)
                var agentSocket: String? = null
                if (login.password == null) {
                    agentSocket = openAgent(profileId, resolved, identity)
                    usesAgent = true
                }
                val ssh =
                    ssh(
                        job,
                        resolved,
                        hostKey,
                        login,
                        agentSocket,
                        if (agentSocket == null) null else identity.publicKey,
                    )
                val architecture = job.execute("$ssh ${q(resolved.host)} uname -m")
                if (architecture.code != 0) abort(sshFailure(architecture.output))
                val arch =
                    when (architecture.output.trim()) {
                        "x86_64" -> "x64"
                        "aarch64" -> "arm64"
                        else -> ""
                    }
                val artifact = bundle.artifacts[arch] ?: abort(ByoHostFailureCode.BundleUnavailable)
                val payload =
                    JsonObject(
                            mapOf(
                                "deviceId" to JsonPrimitive(profileId),
                                "token" to JsonPrimitive(deviceToken),
                                "publicKey" to JsonPrimitive(identity.publicKey),
                            )
                        )
                        .toString()
                // The entire remote script is a private file carried over SSH stdin. The reviewed
                // archive digest
                // authenticates the installer before it executes.
                val script =
                    """set -eu
umask 077
work=${'$'}(mktemp -d)
trap 'rm -rf -- "${'$'}work"' EXIT HUP INT TERM
curl --proto '=https' --proto-redir '=https' --location --tlsv1.2 --fail --silent --show-error --max-time 600 --output "${'$'}work/bundle.tar.gz" ${q(artifact.url)}
printf '%s  %s\n' ${q(artifact.sha256)} "${'$'}work/bundle.tar.gz" | sha256sum -c - >/dev/null || { printf '{"error":"checksum"}\n'; exit 1; }
tar -xzf "${'$'}work/bundle.tar.gz" -C "${'$'}work" install.sh
sh "${'$'}work/install.sh" --archive "${'$'}work/bundle.tar.gz" --sha256 ${q(artifact.sha256)} <<'OC_BYO_INPUT'
$payload
OC_BYO_INPUT
"""
                job.write("input", script)
                val result =
                    job.execute(
                        "$ssh ${q(resolved.host)} sh -s < ${q("${job.remote}/input")}",
                        timeoutMs = 15 * 60_000L,
                    )
                if (result.code != 0) abort(installFailure(result.output))
                val descriptor = descriptor(result.output)
                if (
                    descriptor.bundleVersion != bundle.version ||
                        descriptor.openCodeVersion != bundle.openCodeVersion
                ) {
                    abort(ByoHostFailureCode.Protocol)
                }
                descriptor
            }
        } finally {
            login.consume()
            if (usesAgent) quietly { closeAgent(profileId) }
            work?.let { quietly { it.remove() } }
        }
    }

    override suspend fun forward(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        identity: ByoHostIdentity,
        remotePort: Int,
    ): HostResult<ByoHostTunnel> =
        hostResult(ByoHostFailureCode.Transport) {
            val resolved = tailnet.resolve(target).orAbort()
            if (profileId in forwardingProfiles) abort(ByoHostFailureCode.Busy)
            if (remotePort !in 1..65535) abort(ByoHostFailureCode.InvalidTarget)
            val work = work(profileId)
            val name = "byo-${work.remote.takeLast(24)}"
            try {
                val ssh =
                    ssh(
                        work,
                        resolved,
                        hostKey,
                        ByoHostLogin(),
                        openAgent(profileId, resolved, identity),
                        identity.publicKey,
                    )
                val port = ports.freePort()
                val output = work.empty("result")
                val script =
                    "ulimit -f 128; exec $ssh -o ExitOnForwardFailure=yes -N -L ${q("127.0.0.1:$port:127.0.0.1:$remotePort")} " +
                        "${q(resolved.host)} > ${q(output)} 2>&1"
                shell.start(name, script, port)
                forwardingProfiles += profileId
                lateinit var tunnel: SshTunnel
                tunnel =
                    SshTunnel(port, { shell.running(name) }) {
                        shell.stop(name)
                        closeAgent(profileId)
                        work.remove()
                        forwardingProfiles -= profileId
                        tunnels -= tunnel
                    }
                tunnels += tunnel
                // The native start can precede SSH readiness. Bound the wait; never fall back to a
                // public endpoint.
                for (attempt in 0 until 40) {
                    if (ports.canConnect(port)) {
                        if (shell.running(name)) {
                            delay(300)
                            if (shell.running(name)) return@hostResult tunnel
                        }
                        break
                    }
                    delay(100)
                }
                val failure =
                    sshFailure(
                        if (files.kind("${work.local}/result") == HostEntryKind.File)
                            work.read("result")
                        else ""
                    )
                tunnel.close()
                abort(failure)
            } catch (e: CancellationException) {
                quietly { stopWork(name, work, profileId) }
                throw e
            } catch (e: Throwable) {
                quietly { stopWork(name, work, profileId) }
                throw e
            }
        }

    private suspend fun stopWork(name: String, work: PrivateWork, profileId: String) {
        shell.stop(name)
        closeAgent(profileId)
        work.remove()
        forwardingProfiles -= profileId
    }

    private suspend fun request(
        tunnel: ByoHostTunnel,
        deviceId: String,
        token: String,
        path: String,
        method: String,
    ): String {
        if (!byoHostSafeId(deviceId) || !TOKEN.matches(token) || tunnel.localPort !in 1..65535)
            abort(ByoHostFailureCode.Authentication)
        if (tunnel is SshTunnel && !tunnel.alive()) abort(ByoHostFailureCode.Transport)
        val authorization = "Basic " + Base64.encode("$deviceId:$token".encodeToByteArray())
        val reply =
            try {
                http.exchange(
                    LoopbackRequest(method, tunnel.localPort, path, authorization),
                    MAX_REPLY,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                abort(ByoHostFailureCode.Transport)
            }
        if (reply.status != 200 || reply.body.size > MAX_REPLY) abort(ByoHostFailureCode.Protocol)
        return reply.body.decodeToString()
    }

    override suspend fun describe(
        tunnel: ByoHostTunnel,
        deviceId: String,
        deviceToken: String,
    ): HostResult<ByoHostDescriptor> =
        hostResult(ByoHostFailureCode.Transport) {
            descriptor(request(tunnel, deviceId, deviceToken, "/_oc/host", "GET"))
        }

    override suspend fun revoke(
        tunnel: ByoHostTunnel,
        deviceId: String,
        deviceToken: String,
    ): Boolean =
        hostResult(ByoHostFailureCode.Transport) {
                val body = request(tunnel, deviceId, deviceToken, "/_oc/revoke", "POST")
                val json =
                    Json.parseToJsonElement(body) as? JsonObject
                        ?: abort(ByoHostFailureCode.Protocol)
                json["revoked"]?.jsonPrimitive?.booleanOrNull == true
            }
            .valueOrNull() == true

    override suspend fun dispose() {
        disposed = true
        for (tunnel in tunnels.toList()) {
            quietly { tunnel.close() }
            tunnels -= tunnel
        }
        for (id in agentDirectories.keys.toList()) quietly { closeAgent(id) }
    }

    private class SshTunnel(
        override val localPort: Int,
        private val isAlive: suspend () -> Boolean,
        private val doClose: suspend () -> Unit,
    ) : ByoHostTunnel {
        private var closed = false

        suspend fun alive(): Boolean = !closed && isAlive()

        override suspend fun close() {
            if (closed) return
            closed = true
            try {
                doClose()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                closed = false
                abort(ByoHostFailureCode.Transport)
            }
        }
    }

    public companion object {
        private const val SHORT = 40_000L
        private const val MAX_REPLY = 16_384
        private val TOKEN = Regex("^[A-Za-z0-9_-]{32,256}$")

        internal fun descriptor(text: String): ByoHostDescriptor {
            val data =
                try {
                    Json.parseToJsonElement(text) as? JsonObject
                } catch (_: IllegalArgumentException) {
                    null
                } ?: abort(ByoHostFailureCode.Protocol)
            fun str(key: String): String? =
                (data[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            val id = str("hostId")
            val bundle = str("bundleVersion")
            val version = str("openCodeVersion")
            val port = (data["port"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            if (
                id == null ||
                    bundle == null ||
                    version == null ||
                    port == null ||
                    !byoHostSafeId(id) ||
                    !SEMVER.matches(bundle) ||
                    !SEMVER.matches(version) ||
                    port !in 1..65535
            ) {
                abort(ByoHostFailureCode.Protocol)
            }
            return ByoHostDescriptor(id, bundle, version, port)
        }

        /**
         * stderr may precede the installer's final fixed JSON error. Never expose server text, and
         * never infer a code from a substring of it.
         */
        internal fun installFailure(output: String): ByoHostFailureCode {
            for (line in output.lines().asReversed()) {
                val code =
                    try {
                        ((Json.parseToJsonElement(line) as? JsonObject)?.get("error")
                                as? JsonPrimitive)
                            ?.takeIf { it.isString }
                            ?.content
                    } catch (_: IllegalArgumentException) {
                        continue
                    }
                val failure =
                    when {
                        code == "checksum" || code == "checksumMismatch" ->
                            ByoHostFailureCode.Checksum
                        code == "unsupportedHost" -> ByoHostFailureCode.UnsupportedHost
                        code == "userServiceUnavailable" -> ByoHostFailureCode.Unavailable
                        code == "hostBusy" -> ByoHostFailureCode.Busy
                        code == "sshPolicyRequired" || code == "forwardingPolicyRequired" ->
                            ByoHostFailureCode.SshPolicyRequired
                        code == "lingerRequired" -> ByoHostFailureCode.LingerRequired
                        code == "unsupportedBundle" -> ByoHostFailureCode.BundleUnavailable
                        code == "hostConflict" -> ByoHostFailureCode.IdentityChanged
                        code == "unsafeArchive" || code == "unsafePath" ->
                            ByoHostFailureCode.Protocol
                        code != null && code.startsWith("invalid") -> ByoHostFailureCode.Protocol
                        else -> null
                    }
                if (failure != null) return failure
            }
            val failure = sshFailure(output)
            return if (failure == ByoHostFailureCode.Transport) ByoHostFailureCode.Uncertain
            else failure
        }

        internal fun sshFailure(output: String): ByoHostFailureCode =
            when {
                "REMOTE HOST IDENTIFICATION HAS CHANGED" in output ||
                    "Host key verification failed" in output -> ByoHostFailureCode.HostKeyChanged
                "Permission denied" in output -> ByoHostFailureCode.Authentication
                else -> ByoHostFailureCode.Transport
            }
    }
}

/** POSIX single-quote. */
internal fun q(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/** Best-effort cleanup that must not replace the primary result. Cancellation still propagates. */
internal suspend inline fun quietly(block: () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        // the primary result already decided the outcome
    }
}

internal class CommandResult(val code: Int, val output: String)

/** One private exchange directory, shared by the phone (local path) and Ubuntu (remote path). */
internal class PrivateWork(
    val local: String,
    val remote: String,
    private val shell: HostLocalShell,
    private val files: HostPrivateFiles,
    private val onRemove: () -> Unit,
) {
    suspend fun empty(name: String, executable: Boolean = false): String {
        val path = "$remote/$name"
        val code =
            shell.run(
                "umask 077; : > ${q(path)} && chmod ${if (executable) "700" else "600"} ${q(path)}",
                10_000,
            )
        if (code != 0) abort(ByoHostFailureCode.Storage)
        return path
    }

    suspend fun write(name: String, value: String, executable: Boolean = false) {
        empty(name, executable)
        files.write("$local/$name", value)
    }

    suspend fun read(name: String): String {
        val path = "$local/$name"
        if (files.kind(path) != HostEntryKind.File || files.size(path) > 65_536)
            abort(ByoHostFailureCode.Protocol)
        return files.read(path)
    }

    suspend fun execute(command: String, timeoutMs: Long = 40_000): CommandResult {
        val output = empty("result")
        // Redirect the shell itself, so parse/exec errors cannot leak through native logs.
        write("command", "#!/bin/sh\nulimit -f 128\n$command\n")
        val code = shell.run("sh ${q("$remote/command")} > ${q(output)} 2>&1", timeoutMs)
        return CommandResult(code, read("result"))
    }

    suspend fun remove() {
        try {
            if (files.kind(local) == HostEntryKind.Directory) files.deleteTree(local)
            onRemove()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            abort(ByoHostFailureCode.Storage)
        }
    }
}

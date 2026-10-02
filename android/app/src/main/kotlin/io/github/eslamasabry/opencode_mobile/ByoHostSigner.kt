package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/** Only public metadata crosses Flutter. AndroidKeyStore never exports private
 * bytes. A same-UID filesystem socket supplies the bounded SSH agent subset. */
class ByoHostSigner(context: Context, messenger: BinaryMessenger) {
    private val app = context.applicationContext
    private val channel = MethodChannel(messenger, "oc/byo_host_signer")
    private val handler = Handler(Looper.getMainLooper())
    private val operations = Executors.newSingleThreadExecutor()
    private val agents = ConcurrentHashMap<String, Agent>()
    @Volatile private var disposed = false

    init {
        channel.setMethodCallHandler { call, result ->
            if (call.method !in setOf("ensureIdentity", "openAgent", "closeAgent", "deleteIdentity", "deleteAllIdentities")) {
                result.notImplemented()
            } else if (disposed) {
                result.error("unavailable", "The phone signer is unavailable.", null)
            } else {
                operations.execute {
                    try {
                        val value = handle(call)
                        handler.post { result.success(value) }
                    } catch (_: Exception) {
                        // Never expose exception messages, request payloads, or paths.
                        handler.post { result.error("unavailable", "The phone signer is unavailable.", null) }
                    }
                }
            }
        }
    }

    private fun profile(call: MethodCall): String = (call.argument<String>("profileId") ?: "").also {
        require(it.matches(Regex("[A-Za-z0-9_-]{1,64}")))
    }
    private fun alias(id: String) = "oc.byoHostSsh.$id"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun ensure(id: String): KeyStore.PrivateKeyEntry {
        val ks = store()
        if (!ks.containsAlias(alias(id))) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(alias(id), KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }
        return ks.getEntry(alias(id), null) as KeyStore.PrivateKeyEntry
    }
    private fun publicBlob(entry: KeyStore.PrivateKeyEntry): ByteArray {
        val key = entry.certificate.publicKey as ECPublicKey
        require(key.params.curve.field.fieldSize == 256)
        return ByoHostAgentProtocol.publicBlob(key.w.affineX, key.w.affineY)
    }
    private fun handle(call: MethodCall): Any? {
        if (call.method == "deleteAllIdentities") {
            agents.values.forEach { it.close() }
            agents.clear()
            val ks = store()
            val owned = ks.aliases().toList().filter {
                it.startsWith("oc.byoHostSsh.") &&
                    it.removePrefix("oc.byoHostSsh.").matches(Regex("[A-Za-z0-9_-]{1,64}"))
            }
            owned.forEach { ks.deleteEntry(it) }
            return null
        }
        val id = profile(call)
        when (call.method) {
            "ensureIdentity" -> {
                val blob = publicBlob(ensure(id))
                return mapOf(
                    "keyAlias" to alias(id),
                    "publicKey" to "${ByoHostAgentProtocol.ALGORITHM} ${Base64.encodeToString(blob, Base64.NO_WRAP)}"
                )
            }
            "openAgent" -> {
                val user = call.argument<String>("user") ?: ""
                require(user.matches(Regex("[A-Za-z_][A-Za-z0-9_.-]{0,63}")))
                val path = call.argument<String>("socketPath") ?: ""
                val root = File(app.filesDir, "linux/ubuntu/tmp")
                require(root.canonicalFile == File(app.filesDir.canonicalFile, "linux/ubuntu/tmp")) // No symlinks below app files.
                val suffix = path.removePrefix(root.path + "/")
                require(path == root.path + "/" + suffix)
                require(suffix.matches(Regex("\\.oa-[a-f0-9]{16}/s")))
                require(path.toByteArray(Charsets.UTF_8).size < 108)
                val file = File(path)
                require(file.parentFile!!.canonicalFile == File(root.canonicalFile, suffix.substringBefore('/')))
                val parent = Os.lstat(file.parent)
                require(OsConstants.S_ISDIR(parent.st_mode) && parent.st_uid == Process.myUid())
                require(parent.st_mode and 0x1ff == 0x1c0) // 0700, not a shared directory.
                require(!file.exists())
                require(agents.size < 16 || agents.containsKey(id))
                val entry = store().getEntry(alias(id), null) as? KeyStore.PrivateKeyEntry
                    ?: throw IllegalStateException()
                agents.remove(id)?.close()
                val agent = Agent(file, publicBlob(entry), user, entry)
                try {
                    agent.start()
                    agents[id] = agent
                } catch (error: Exception) {
                    agent.close()
                    throw error
                }
            }
            "closeAgent" -> agents.remove(id)?.close()
            "deleteIdentity" -> {
                agents.remove(id)?.close()
                store().deleteEntry(alias(id))
            }
        }
        return null
    }

    private class Agent(
        val file: File,
        val publicKey: ByteArray,
        val user: String,
        val entry: KeyStore.PrivateKeyEntry
    ) {
        private val listener = LocalSocket()
        private var server: LocalServerSocket? = null
        private val clients = ConcurrentHashMap.newKeySet<LocalSocket>()
        private val workers = Executors.newFixedThreadPool(4)
        private val permits = Semaphore(4)
        @Volatile private var active = false
        private var bound = false

        fun start() {
            listener.bind(LocalSocketAddress(file.path, LocalSocketAddress.Namespace.FILESYSTEM))
            bound = true
            Os.chmod(file.path, 0x180) // 0600
            server = LocalServerSocket(listener.fileDescriptor)
            active = true
            Thread({
                while (active) {
                    val client = try { server!!.accept() } catch (_: Exception) { break }
                    if (!permits.tryAcquire()) { client.close(); continue }
                    clients.add(client)
                    try {
                        workers.execute {
                            try { serve(client) } catch (_: Exception) { /* fixed failure, no logging */ }
                            finally {
                                clients.remove(client)
                                try { client.close() } catch (_: Exception) { }
                                permits.release()
                            }
                        }
                    } catch (_: Exception) {
                        clients.remove(client)
                        client.close()
                        permits.release()
                    }
                }
            }, "byo-host-agent").apply { isDaemon = true; start() }
        }
        private fun serve(client: LocalSocket) {
            require(client.peerCredentials.uid == Process.myUid())
            client.soTimeout = 10000
            val input = DataInputStream(client.inputStream)
            val output = DataOutputStream(client.outputStream)
            while (active) {
                val size = input.readInt()
                require(size in 1..ByoHostAgentProtocol.MAX_FRAME)
                val frame = ByteArray(size).also { input.readFully(it) }
                val reply = ByoHostAgentProtocol.reply(frame, publicKey, user) { data ->
                    Signature.getInstance("SHA256withECDSA").run {
                        // This reference is a Keystore handle; its private bytes
                        // never enter this process. No getEncoded/export call.
                        initSign(entry.privateKey)
                        update(data)
                        sign()
                    }
                }
                output.writeInt(reply.size)
                output.write(reply)
                output.flush()
            }
        }
        fun close() {
            active = false
            try { server?.close() } catch (_: Exception) { }
            try { listener.close() } catch (_: Exception) { }
            clients.forEach { try { it.close() } catch (_: Exception) { } }
            workers.shutdownNow()
            if (bound) try { file.delete() } catch (_: Exception) { }
        }
    }

    fun dispose() {
        disposed = true
        channel.setMethodCallHandler(null)
        // Serialize after pending creates, so none can escape cleanup.
        operations.execute { agents.values.forEach { it.close() }; agents.clear() }
        operations.shutdown()
    }
}

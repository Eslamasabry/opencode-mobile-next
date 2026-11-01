package io.github.eslamasabry.opencode_mobile

import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class NativeEngineReady(val port: Int, val identity: RuntimeProcessIdentity)

/** Authenticated metadata from the shipped child's private stdout, never a PID-only exemption. */
internal object NativeEngineReadiness {
    private const val MIN_READY_PORT = 1024L
    private const val MAX_READY_PORT = 65535L
    private const val RESERVED_SERVER_PORT = 4097L
    private const val UUID_LENGTH = 36
    private const val UNSIGNED_BYTE_MASK = 0xff
    private val hex = Regex("[0-9a-f]{64}")
    fun verify(value: Map<*, *>, token: String, profile: String,
        childPid: Int?, appPid: Int,
    ): NativeEngineReady {
        require(value.keys == setOf("schemaVersion", "profileId", "port", "nonce", "mac", "identity"))
        fun number(key: String): Long {
            val item = value[key]
            require(item is Int || item is Long)
            return (item as Number).toLong()
        }
        require(number("schemaVersion") == 2L && value["profileId"] == profile && hex.matches(token))
        val port = number("port")
        require(port in MIN_READY_PORT..MAX_READY_PORT && port != RESERVED_SERVER_PORT)
        val nonce = value["nonce"] as? String ?: error("engine_ready_invalid")
        val signature = value["mac"] as? String ?: error("engine_ready_invalid")
        require(nonce.length == UUID_LENGTH && UUID.fromString(nonce).toString() == nonce && hex.matches(signature))
        val identity = RuntimeProcessIdentity.read(value["identity"] as? Map<*, *> ?: error("engine_ready_invalid"))
        require(childPid != null && identity.pid == childPid && identity.parent == appPid)
        val message = "oc-phone-engine-ready-v2\n$profile\n$port\n$nonce\n" +
            "${identity.pid}\n${identity.startTicks}\n${identity.parent}\n${identity.group}\n${identity.session}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val expected = mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and UNSIGNED_BYTE_MASK) }
        require(MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII), signature.toByteArray(Charsets.US_ASCII),
        ))
        return NativeEngineReady(port.toInt(), identity)
    }
}

/** Only this live child handle can supply metadata hidden by PR_SET_DUMPABLE=0. */
internal class NativeEnginePeer(private val child: Process, private val identity: RuntimeProcessIdentity) {
    fun current(active: Process?, handlePid: Int?): RuntimeProcessIdentity? {
        if (active !== child || !child.isAlive) return null
        check(handlePid == identity.pid) { "ownershipUnknown" }
        return identity.takeIf { child.isAlive }
    }

    companion object {
        fun resolve(
            pid: Int, visible: RuntimeProcessIdentity?, peer: RuntimeProcessIdentity?,
        ): RuntimeProcessIdentity? {
            if (peer?.pid != pid) return visible
            check(visible == null || visible == peer) { "ownershipUnknown" }
            return peer
        }
        fun merge(visible: List<RuntimeProcessIdentity>, peer: RuntimeProcessIdentity?): List<RuntimeProcessIdentity> {
            if (peer == null) return visible
            val found = visible.filter { it.pid == peer.pid }
            check(found.size <= 1 && (found.isEmpty() || found.single() == peer)) { "ownershipUnknown" }
            return if (found.isEmpty()) visible + peer else visible
        }
    }
}

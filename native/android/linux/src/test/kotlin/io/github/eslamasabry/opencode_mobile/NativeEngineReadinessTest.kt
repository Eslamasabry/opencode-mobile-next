package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class NativeEngineReadinessTest {
    private val token = "ab".repeat(32)
    private val identity = RuntimeProcessIdentity(12345, 987654321, 12000, 12000, 0)
    // Shared Rust startup.rs vector. Each numeric field is authenticated.
    private fun frame(): Map<String, Any> = mapOf("schemaVersion" to 2, "profileId" to "phone",
        "port" to 43123, "nonce" to "57cfb6f9-6f2d-4d84-bbc4-26d23927a2c3",
        "identity" to identity.map(), "mac" to "a821cb0022b70367032aa1ff3dff2714f9dbbfaf79fcf17b547fb9a52875552f")
    private fun verify(value: Map<*, *> = frame(), pid: Int? = 12345, parent: Int = 12000) =
        NativeEngineReadiness.verify(value, token, "phone", pid, parent)

    @Test fun rustReadyVectorAuthenticatesIdentityAndPort() {
        assertEquals(NativeEngineReady(43123, identity), verify())
    }
    @Test fun everyIdentityFieldAndPortAreSigned() {
        for (key in identity.map().keys) {
            val modified = identity.map().toMutableMap()
            modified[key] = (modified.getValue(key) as Number).toLong() + 1
            assertThrows(IllegalArgumentException::class.java) { verify(frame() + ("identity" to modified)) }
        }
        assertThrows(IllegalArgumentException::class.java) { verify(frame() + ("port" to 43124)) }
    }
    @Test fun identityMustBelongToThisDirectChildAndParent() {
        for (pid in listOf(null, 1, 12346)) {
            assertThrows(IllegalArgumentException::class.java) { verify(pid = pid) }
        }
        assertThrows(IllegalArgumentException::class.java) { verify(parent = 12001) }
        assertThrows(IllegalArgumentException::class.java) {
            NativeEngineReadiness.verify(frame(), "cd".repeat(32), "phone", 12345, 12000)
        }
    }
    @Test fun malformedAndLegacyFramesFailClosed() {
        for (bad in listOf(frame() + ("schemaVersion" to 1), frame() - "identity",
            frame() + ("extra" to "ignored"), frame() + ("port" to 43123.0))) {
            assertThrows(IllegalArgumentException::class.java) { verify(bad) }
        }
        for ((key, value) in listOf("pid" to 1, "pid" to 2147483648L, "startTicks" to 0,
            "startTicks" to "987654321", "parent" to -1, "group" to 0, "session" to -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                verify(frame() + ("identity" to (identity.map() + (key to value))))
            }
        }
    }
    private class Child : Process() {
        var live = true
        override fun isAlive() = live
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor(): Int { live = false; return 0 }
        override fun exitValue(): Int { check(!live); return 0 }
        override fun destroy() { live = false }
    }
    @Test fun liveProofIsRevokedOnExitReplacementOrHandleMismatch() {
        val child = Child()
        val peer = NativeEnginePeer(child, identity)
        assertEquals(identity, peer.current(child, identity.pid))
        assertNull(peer.current(Child(), identity.pid))
        assertNull(peer.current(null, identity.pid))
        assertThrows(IllegalStateException::class.java) { peer.current(child, identity.pid + 1) }
        child.destroy()
        assertNull(peer.current(child, identity.pid))
    }
    @Test fun hiddenAuthenticatedPeerRemainsAnOrdinaryKnownPeer() {
        val current = NativeEnginePeer.merge(emptyList(), identity)
        val captured = NativeRuntimeOwnership.capturePeers(listOf(identity.pid),
            { NativeEnginePeer.resolve(it, null, identity) }, { false })
        assertEquals(listOf(identity), captured)
        val receipt = NativeRuntimeReceipt("11111111-1111-1111-1111-111111111111", "a".repeat(64),
            1, null, null, captured)
        val plan = NativeRuntimeOwnership.plan(receipt, receipt.boot, current, emptySet()) { _, _ -> false }
        assertEquals(setOf(identity.pid), plan.other)
        assertTrue(plan.server.isEmpty())
        assertThrows(IllegalStateException::class.java) {
            NativeRuntimeOwnership.capturePeers(listOf(identity.pid),
                { NativeEnginePeer.resolve(it, null, null) }, { false })
        }
    }
    @Test fun readableConflictsCannotBeOverwrittenByCachedProof() {
        val reused = identity.copy(startTicks = identity.startTicks + 1)
        assertThrows(IllegalStateException::class.java) { NativeEnginePeer.resolve(identity.pid, reused, identity) }
        assertThrows(IllegalStateException::class.java) { NativeEnginePeer.merge(listOf(reused), identity) }
        assertEquals(listOf(identity), NativeEnginePeer.merge(listOf(identity), identity))
        assertNull(NativeEnginePeer.resolve(identity.pid + 1, null, identity))
    }
}

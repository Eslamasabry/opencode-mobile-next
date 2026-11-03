package io.github.eslamasabry.oc.engine.project.gitops

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatBranchHolderTest {
    @Test
    fun `B-NAV-023 the holder reads the branch once and rebinds on a new project`() = runTest {
        val reads = ArrayList<String>()
        val holder = ChatBranchHolder(backgroundScope, BranchNameCache()) { _, dir -> reads += dir; BranchRead.Read("main") }
        assertNull(holder.branchName.value)
        holder.bind(null, "/a")
        holder.bind(null, "/a")
        holder.bind(null, " /a ")
        runCurrent()
        assertEquals(listOf("/a"), reads)
        assertEquals("main", holder.branchName.value)
        val gate = CompletableDeferred<BranchRead>()
        val slow = ChatBranchHolder(backgroundScope, BranchNameCache()) { _, dir -> reads += dir; gate.await() }
        slow.bind(null, "/a")
        runCurrent()
        slow.bind(null, "/b")
        runCurrent()
        assertEquals(listOf("/a", "/a", "/b"), reads)
        assertNull(slow.branchName.value)
        gate.complete(BranchRead.Read("dev"))
        runCurrent()
        assertEquals("dev", slow.branchName.value)
    }

    @Test
    fun `B-NAV-023 a chat opened again names the branch from its first frame`() = runTest {
        val cache = BranchNameCache()
        val first = ChatBranchHolder(backgroundScope, cache) { _, _ -> BranchRead.Read("main") }
        first.bind(null, "/j8")
        runCurrent()
        assertEquals("main", first.branchName.value)
        first.close()

        val gate = CompletableDeferred<BranchRead>()
        val again = ChatBranchHolder(backgroundScope, cache) { _, _ -> gate.await() }
        again.bind(null, "/j8")
        assertFalse(again.hasRead)
        assertEquals("main", again.branchName.value)
        // Its own read then wins: a switched branch replaces the cached name.
        gate.complete(BranchRead.Read("feature/x"))
        runCurrent()
        assertTrue(again.hasRead)
        assertEquals("feature/x", again.branchName.value)
        assertEquals("feature/x", cache.get("null|/j8"))
    }

    @Test
    fun `B-NAV-023 a failed read keeps what the chip had and another profile is another project`() = runTest {
        val cache = BranchNameCache()
        cache.put("p1|/a", "main")
        val holder = ChatBranchHolder(backgroundScope, cache) { _, _ -> BranchRead.Failed }
        holder.bind("p1", "/a")
        runCurrent()
        assertEquals("main", holder.branchName.value)
        holder.bind("p2", "/a")
        runCurrent()
        assertNull(holder.branchName.value)
    }
}

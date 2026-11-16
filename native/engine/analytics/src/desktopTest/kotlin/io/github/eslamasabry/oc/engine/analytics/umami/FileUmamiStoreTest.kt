package io.github.eslamasabry.oc.engine.analytics.umami

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FileUmamiStoreTest {
    @Test
    fun `B-180 a queue replacement is readable after restart and revocation removes persisted bytes`() = runTest {
        val dir = Files.createTempDirectory("usage-outbox").toFile()
        try {
            val path = File(dir, "usage/outbox.json")
            val store = FileUmamiStore(path)
            assertTrue(store.replace("first") is Outcome.Ok)
            assertEquals("first", FileUmamiStore(path).read().getOrNull())
            assertTrue(store.replace("second") is Outcome.Ok)
            assertEquals("second", FileUmamiStore(path).read().getOrNull())
            assertFalse(File(path.parentFile, path.name + ".new").exists())
            assertTrue(store.replace(null) is Outcome.Ok)
            assertFalse(path.exists())
            assertNull(FileUmamiStore(path).read().getOrNull())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `B-180 an oversized queue cannot replace retained bytes or be restored`() = runTest {
        val dir = Files.createTempDirectory("usage-outbox-bound").toFile()
        try {
            val path = File(dir, "outbox.json")
            val store = FileUmamiStore(path)
            store.replace("kept")
            assertTrue(store.replace("x".repeat(UmamiSender.MAX_STORE_BYTES + 1)) is Outcome.Failed)
            assertEquals("kept", store.read().getOrNull())
            path.writeText("x".repeat(UmamiSender.MAX_STORE_BYTES + 1))
            assertNull(store.read().getOrNull())
            assertTrue(store.replace(null) is Outcome.Ok)
            assertFalse(path.exists())
        } finally { dir.deleteRecursively() }
    }
}

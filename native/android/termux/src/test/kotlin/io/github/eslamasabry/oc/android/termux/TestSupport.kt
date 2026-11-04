package io.github.eslamasabry.oc.android.termux

import io.github.eslamasabry.oc.core.common.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

/** Every lane on one dispatcher (file IO runs inline on the test's own dispatcher). */
internal class SameDispatchers(private val dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val main: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher

    override fun serial(name: String): CoroutineDispatcher = dispatcher
}

/** Real threads for the loopback receiver, whose accept and reads block. */
internal object ThreadDispatchers : AppDispatchers {
    private val pool = Executors.newFixedThreadPool(8) { runnable -> Thread(runnable, "termux-test-io").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    override val main: CoroutineDispatcher get() = pool
    override val default: CoroutineDispatcher get() = pool
    override val io: CoroutineDispatcher get() = pool

    override fun serial(name: String): CoroutineDispatcher = pool
}

internal inline fun expectFailure(code: TermuxMigrationFailure, block: () -> Unit) {
    try {
        block()
    } catch (e: TermuxMigrationException) {
        assertEquals(code, e.code)
        return
    }
    fail("expected $code")
}

internal inline fun expectAnyFailure(block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        assertIs<TermuxMigrationException>(e)
        return
    }
    fail("expected a migration failure")
}

internal fun tempDir(prefix: String): Path = Files.createTempDirectory(prefix)

internal fun Path.deleteRecursivelyQuietly() {
    try {
        deleteTree(this)
    } catch (_: Exception) {
        toFile().deleteRecursively()
    }
}

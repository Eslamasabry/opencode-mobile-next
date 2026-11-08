package io.github.eslamasabry.oc.android.platform

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plain JVM: NativeCrashStore uses java.io only. */
class NativeCrashStoreTest {
    private lateinit var dir: File
    private lateinit var store: NativeCrashStore
    private lateinit var records: NativeCrashRecords
    private val record get() = File(dir, "native-last-crash.properties")
    private val now get() = System.currentTimeMillis()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("crash").toFile()
        store = NativeCrashStore(dir)
        records = NativeCrashRecords(store)
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun consent(at: Long = now - 60_000) = File(dir, "crash-diagnostics-consent").writeText(at.toString())

    private class Hostile(secret: String) : IllegalStateException(secret) {
        var touched = false
        override val message: String
            get() { touched = true; return "token=fake-secret-code" }
        override fun toString(): String { touched = true; return "fake-secret-code /home/oc login.example" }
    }

    @Test
    fun `B-ANDR-036 the latest JVM crash is read as one category-only record`() {
        consent()
        val at = now - 1_000
        store.write(IllegalStateException("boom"), at)
        assertEquals(CrashRecord(CrashKind.InvalidState, at, emptyList()), records.latest())
    }

    @Test
    fun `B-ANDR-036 a wrapped service crash keeps the safe permission cause`() {
        consent()
        val at = now - 1_000
        store.write(RuntimeException("start failed", SecurityException("uid 10123 package x")), at)
        val latest = records.latest()!!
        assertEquals(CrashKind.Other, latest.kind)
        assertEquals(listOf(CrashKind.PermissionDenied), latest.causes)
        val text = record.readText()
        assertTrue("Permission denied" in text)
        assertFalse("uid 10123" in text)
    }

    @Test
    fun `B-ANDR-036 causes are bounded to three and a cause cycle ends`() {
        consent()
        val at = now - 1_000
        val deepest = IOException("a", SecurityException("too deep"))
        val chain = IllegalArgumentException(
            "x",
            NullPointerException("y").apply { initCause(InterruptedException("z").apply { initCause(deepest) }) },
        )
        store.write(chain, at)
        assertEquals(
            CrashRecord(CrashKind.InvalidArgument, at,
                listOf(CrashKind.MissingValue, CrashKind.Interrupted, CrashKind.InputOutput)),
            records.latest(),
        )

        val inner = IOException("loop")
        val outer = IllegalStateException("loop", inner)
        inner.initCause(outer)
        store.write(outer, at)
        assertEquals(CrashRecord(CrashKind.InvalidState, at, listOf(CrashKind.InputOutput)), records.latest())
    }

    @Test
    fun `B-ANDR-036 invalid input fails closed`() {
        consent()
        record.writeText("timestamp=${now - 1_000}\nexceptionClass=com.example.Secret\nmessage=fake-secret-code\n")
        assertNull(records.latest(), "an unknown class is not a record")
        record.writeText("timestamp=${now + 3_600_000}\nexceptionClass=java.lang.Throwable\n")
        assertNull(records.latest(), "a future timestamp is not a record")
        record.writeText("\u0000\u0001not properties at all \\u12")
        assertNull(records.latest(), "a corrupt file never throws")
        record.writeBytes(ByteArray(30_000) { 'a'.code.toByte() })
        assertNull(records.latest(), "an oversized file is not read")
    }

    @Test
    fun `B-SEC-043 only categories are saved and a hostile toString or message is never called`() {
        consent()
        val hostile = Hostile("fake-secret-code")
        store.write(RuntimeException("password=hunter2 sk-ant-fake", hostile), now - 1_000)
        val text = record.readText()
        listOf("hunter2", "sk-ant-fake", "fake-secret-code", "/home/oc", "login.example", "Hostile").forEach {
            assertFalse(it in text, "$it must not reach disk")
        }
        assertFalse(hostile.touched, "the store never asks an exception for text")
        assertEquals(listOf(CrashKind.InvalidState), records.latest()!!.causes)
    }

    @Test
    fun `B-SEC-043 without consent nothing is kept and an old record is erased`() {
        store.write(IllegalStateException(), now - 1_000)
        assertFalse(record.exists())
        consent()
        store.write(IllegalStateException(), now - 1_000)
        assertTrue(record.exists())
        File(dir, "crash-diagnostics-consent").delete()
        assertNull(records.latest())
        assertFalse(record.exists(), "reading with consent off clears the record")
    }

    @Test
    fun `B-SEC-043 corrupt or future consent fails closed`() {
        File(dir, "crash-diagnostics-consent").writeText("not-a-number")
        store.write(IllegalStateException(), now - 1_000)
        assertFalse(record.exists())
        consent(now + 3_600_000)
        store.write(IllegalStateException(), now - 1_000)
        assertFalse(record.exists())
        consent(now - 60_000)
        store.write(IllegalStateException(), now - 120_000)
        assertFalse(record.exists(), "a crash before consent is not kept")
    }

    @Test
    fun `B-SEC-043 a symlinked snapshot cannot redirect evidence`() {
        consent()
        val outside = Files.createTempDirectory("elsewhere").toFile()
        try {
            val target = File(outside, "stolen.properties").apply { writeText("timestamp=${now - 1_000}\nexceptionClass=java.lang.Throwable\n") }
            Files.createSymbolicLink(record.toPath(), target.toPath())
            assertNull(records.latest(), "a linked record is never read")
            assertFalse(records.clear(), "clear refuses a linked path and reports it, never throws")

            // A link to an existing file fails the canonical-path check (the copy's `check`, caught by the handler).
            assertFailsWith<IllegalStateException> { store.write(IllegalStateException(), now - 1_000) }
            assertFalse("IllegalStateException" in target.readText(), "nothing written through the link")

            // A dangling link canonicalises to itself, so the rename replaces the link, never its target.
            record.delete()
            val dangling = File(outside, "absent.properties")
            Files.createSymbolicLink(record.toPath(), dangling.toPath())
            store.write(IllegalStateException(), now - 1_000)
            assertFalse(Files.isSymbolicLink(record.toPath()), "the rename replaced the link itself")
            assertFalse(dangling.exists(), "nothing written through the dangling link")

            val tmpTarget = File(outside, "stolen.tmp")
            Files.createSymbolicLink(File(dir, "native-last-crash.properties.tmp").toPath(), tmpTarget.toPath())
            store.write(IllegalStateException(), now - 1_000)
            assertFalse(tmpTarget.exists(), "a dangling temporary link is removed, never written through (S1-PLAT0 fix)")
            assertEquals(CrashKind.InvalidState, records.latest()!!.kind)
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test
    fun `B-SEC-043 the handler hands Android only a scrubbed error and still records the category`() {
        consent()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        var forwarded: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> forwarded = e }
        try {
            val handler = store.install()
            val crash = IOException("Bearer fake-token", IllegalStateException("chat text the user typed"))
            handler.uncaughtException(Thread.currentThread(), crash)
            val seen = forwarded!!
            // B-180, Flutter 2.1.5: the class names and checked frames reach Crashlytics, never a message.
            assertEquals("java.io.IOException", seen.message)
            assertEquals("java.lang.IllegalStateException", seen.cause?.message)
            assertNull(seen.cause?.cause)
            assertTrue(seen.stackTrace.isNotEmpty())
            assertFalse(generateSequence(seen) { it.cause }.any { "fake-token" in it.toString() || "chat text" in it.toString() })
            assertEquals(CrashKind.InputOutput, records.latest()!!.kind)
            assertFalse("fake-token" in record.readText())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test
    fun `B-ANDR-036 clear removes the record and its interrupted temporary`() {
        consent()
        store.write(IllegalStateException(), now - 1_000)
        File(dir, "native-last-crash.properties.tmp").writeText("partial")
        assertTrue(records.clear())
        assertFalse(record.exists())
        assertFalse(File(dir, "native-last-crash.properties.tmp").exists())
        assertNull(records.latest())
    }
}

class CrashSanitizerTest {
    @Test
    fun `B-180-020 a frame whose class or method is not a plain identifier is left out, a path file name too`() {
        val error = IllegalStateException("secret sk-1234567890")
        error.stackTrace =
            arrayOf(
                StackTraceElement("io.github.App", "send", "App.kt", 42),
                StackTraceElement("io.github.App", "name with a token sk-123", "App.kt", 7),
                StackTraceElement("io.github.App", "open", "/data/user/0/secret.kt", 9),
            )
        val safe = CrashSanitizer.sanitize(error)
        assertEquals("java.lang.IllegalStateException", safe.message)
        assertEquals(2, safe.stackTrace.size)
        assertEquals(StackTraceElement("io.github.App", "send", "App.kt", 42), safe.stackTrace[0])
        assertEquals(StackTraceElement("io.github.App", "open", null, -1), safe.stackTrace[1])
        assertFalse("sk-" in safe.toString())
    }

    @Test
    fun `B-180-021 a cause loop ends and at most four exceptions are kept`() {
        val a = RuntimeException("a")
        val b = IllegalArgumentException("b", a)
        a.initCause(b)
        val depth = generateSequence(CrashSanitizer.sanitize(a)) { it.cause }.count()
        assertEquals(2, depth)
        var deep: Throwable = Exception("0")
        repeat(6) { deep = Exception("$it", deep) }
        assertEquals(4, generateSequence(CrashSanitizer.sanitize(deep)) { it.cause }.count())
    }
}

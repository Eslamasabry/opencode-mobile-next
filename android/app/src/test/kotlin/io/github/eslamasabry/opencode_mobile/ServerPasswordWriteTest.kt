package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPasswordWriteTest {
    private val canary = "pw-argv-canary-7f3a9c1e5b"

    @Test
    fun theScriptIsAnArgumentAndHoldsNoPassword() {
        val script = ServerPasswordWrite.script()
        assertFalse(script.contains(canary))
        assertFalse(script.contains("printf"))
        assertTrue(script.contains("cat > '${ServerPasswordWrite.FILE}.tmp'"))
        // The trace label still names it generically.
        assertTrue(script.contains("${ServerPasswordWrite.FILE}.tmp"))
    }

    @Test
    fun writesStdinPrivatelyAndAnEmptyStdinKeepsTheOldFile() {
        val root = Files.createTempDirectory("oc-pw-write").toFile()
        try {
            val file = File(root, "oc-builtin/server.password")
            val script = ServerPasswordWrite.script(file.path)
            assertEquals(0, sh(script, canary))
            assertEquals(canary, file.readText())
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
            assertEquals("rwx------",
                PosixFilePermissions.toString(Files.getPosixFilePermissions(file.parentFile.toPath())))
            assertEquals(78, sh(script, ""))
            assertEquals(canary, file.readText())
            assertFalse(File(file.path + ".tmp").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun writesThePasswordWhereTheProtectedSandboxDeniesChmod() {
        // The protected Linux denies chmod for every process; a script that
        // needs chmod fails the protected restart with passwordNotSaved.
        val script = ServerPasswordWrite.script()
        assertFalse(script.contains("chmod"))
        val root = Files.createTempDirectory("oc-pw-nochmod").toFile()
        try {
            val bin = File(root, "bin").apply { mkdirs() }
            File(bin, "chmod").apply {
                writeText("#!/bin/sh\necho 'chmod: Operation not permitted' >&2\nexit 1\n")
                setExecutable(true)
            }
            val file = File(root, "oc-builtin/server.password")
            val env = mapOf("PATH" to "${bin.path}:${System.getenv("PATH")}")
            assertEquals(0, sh(ServerPasswordWrite.script(file.path), canary, env))
            assertEquals(canary, file.readText())
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun theRestartRecipeReadsThePasswordInsideUbuntu() {
        val script = NativeServerRecipe("builtin", "openCode2", 1L, "0123456789abcdef".repeat(4)).restorationScript()
        assertTrue(script.contains("password=\$(cat /root/.oc-builtin/server.password)"))
        assertTrue(script.contains("[ -s /root/.oc-builtin/server.password ] ||"))
    }

    private fun sh(script: String, input: String, env: Map<String, String> = emptyMap()): Int {
        val builder = ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true)
        builder.environment().putAll(env)
        val process = builder.start()
        process.outputStream.use { it.write(input.toByteArray()) }
        process.inputStream.readBytes()
        return process.waitFor()
    }
}

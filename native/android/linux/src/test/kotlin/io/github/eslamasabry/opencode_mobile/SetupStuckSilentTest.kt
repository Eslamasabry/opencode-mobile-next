package io.github.eslamasabry.opencode_mobile

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-488: phone DNS replaces hardcoded public resolv.conf and silent steps fall back to compatible mode. */
class SetupStuckSilentTest {
    private fun linuxSource(name: String): String {
        val root =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        return File(root, "android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/$name").readText()
    }

    @Test
    fun `B-SETUP-017 BuiltinLinux syncs phone network DNS instead of hardcoding public resolv conf`() {
        val source = linuxSource("BuiltinLinux.kt")
        assertFalse(
            "BuiltinLinux.configure must not hardcode public nameservers directly",
            source.contains("writeText(\"nameserver 1.1.1.1\\nnameserver 8.8.8.8\\n\")"),
        )
        assertTrue(
            "BuiltinLinux must sync resolv.conf from the phone's active network DNS",
            source.contains("BuiltinDnsConfig.sync("),
        )
    }

    @Test
    fun `B-SETUP-018 SetupRunner tracks step silence and triggers compatible mode fallback`() {
        val runner = linuxSource("SetupRunner.kt")
        assertTrue(
            "SetupRunner must watch for silent steps instead of blocking without a silence bound",
            runner.contains("SetupStepSilence"),
        )
        assertTrue(
            "SetupRunner must route silent and crashing steps through compatible fallback",
            runner.contains("SetupCompatFallback"),
        )
    }
}

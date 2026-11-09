package io.github.eslamasabry.oc.quality

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The privacy leak guard over every tracked text file of the (public) repository, plus planted samples proving each
 * rule fires. Planted strings are built at runtime from parts.
 */
class PrivacyLeakGuardTest {
    private val root = File(System.getProperty("oc.root"))
    private val repo = root.parentFile
    private val allowFile = File(root, "quality/config/privacy/token-fixtures.txt")

    /** The explicit ceiling of the token-fixture allowlist. Lower it when entries go; never raise it. */
    private val allowlistCeiling = 40

    private fun allowlist(): Set<String> =
        allowFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()

    private fun trackedFiles(): List<String>? {
        if (!File(repo, ".git").exists()) return null
        val p = ProcessBuilder("git", "-C", repo.path, "ls-files", "-z").redirectErrorStream(false).start()
        val bytes = p.inputStream.readBytes()
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) return null
        return String(bytes, Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
    }

    @Test
    fun `no private infrastructure detail or unlisted token shape is tracked`() {
        val tracked = trackedFiles() ?: return println("privacy leak guard: no git checkout here, skipped (CI runs it)")
        val files = HashMap<String, String>()
        for (rel in tracked) {
            val f = File(repo, rel)
            if (!f.isFile || f.length() > 4_000_000) continue
            val bytes = f.readBytes()
            if (bytes.take(8000).any { it == 0.toByte() }) continue // binary
            files[rel] = String(bytes, Charsets.UTF_8)
        }
        assertTrue(files.size > 1000, "privacy leak guard saw only ${files.size} files")
        val findings = PrivacyLeakGuard.check(files, allowlist())
        assertTrue(
            findings.isEmpty(),
            "privacy leak guard (${findings.size}); paths and rule ids only, values are never printed:\n" +
                findings.sortedBy { it.path }.take(60).joinToString("\n") { "${it.rule}  ${it.path}" },
        )
    }

    @Test
    fun `the token allowlist only shrinks`() {
        val list = allowlist()
        assertTrue(list.size <= allowlistCeiling, "allowlist has ${list.size} entries, ceiling is $allowlistCeiling")
        assertTrue(list.all { it.startsWith("native/") || it.startsWith("test/") || it.startsWith("docs/") })
        assertTrue(list.none { "*" in it }, "allowlist entries are exact paths")
    }

    @Test
    fun `each hard rule catches its planted sample`() {
        val d = "."
        val samples = mapOf(
            "PRIVATE-VPS" to listOf("169", "58", "71", "211").joinToString(d),
            "PRIVATE-TAILNET-PC" to listOf("100", "126", "15", "6").joinToString(d),
            "PRIVATE-TAILNET-DESKTOP" to listOf("100", "120", "55", "43").joinToString(d),
            "PRIVATE-MAIL" to "soma" + d + "eas" + "@" + "example.com",
            "PRIVATE-ADB-SERIAL" to "adb-" + "320437" + "155564",
            "PRIVATE-HOME-PATH" to "/home/" + "es" + "lam/Storage",
        )
        assertEquals(PrivacyLeakGuard.hardRules.keys, samples.keys, "every hard rule has a planted sample")
        for ((rule, sample) in samples) {
            val found = PrivacyLeakGuard.check(mapOf("native/docs/x.md" to "see $sample here"), emptySet())
            assertEquals(listOf(rule), found.map { it.rule }, "hard rule $rule")
            // an allowlisted path does not excuse a hard rule
            val still = PrivacyLeakGuard.check(mapOf("native/docs/x.md" to sample), setOf("native/docs/x.md"))
            assertTrue(still.any { it.rule == rule }, "hard rule $rule ignores the allowlist")
        }
    }

    @Test
    fun `each token rule catches its planted sample, and only the allowlist excuses it`() {
        val a36 = "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"
        val seg = "abcdefghijklmnopqrstuvwxyz0123456789"
        val samples = mapOf(
            "TOKEN-GITHUB" to "gh" + "p_" + a36,
            "TOKEN-API-KEY" to "s" + "k-proj-" + seg,
            "TOKEN-AWS" to "AK" + "IA" + "ABCDEFGHIJKLMNOP",
            "TOKEN-SLACK" to "xo" + "xb-" + "1234567890-abcdefghij",
            "TOKEN-GOOGLE" to "AI" + "za" + seg.take(35),
            "TOKEN-JWT" to "ey" + "J" + seg.take(20) + ".ey" + "J" + seg.take(20) + "." + seg.take(12),
            "TOKEN-PRIVATE-KEY" to "-----" + "BEGIN RSA PRIVATE" + " KEY-----",
        )
        assertEquals(PrivacyLeakGuard.tokenRules.keys, samples.keys, "every token rule has a planted sample")
        for ((rule, sample) in samples) {
            val path = "native/core/src/main/Leak.kt"
            val hit = PrivacyLeakGuard.check(mapOf(path to "val t = \"$sample\""), emptySet())
            assertEquals(listOf(rule), hit.map { it.rule }, "token rule $rule")
            assertTrue(PrivacyLeakGuard.check(mapOf(path to sample), setOf(path)).isEmpty(), "allowlisted fixture $rule")
        }
    }

    @Test
    fun `a stale allowlist entry fails so the list shrinks`() {
        val stale = PrivacyLeakGuard.check(mapOf("test/gone_test.dart" to "nothing sensitive"), setOf("test/gone_test.dart", "test/missing.dart"))
        assertEquals(listOf("STALE-ALLOWLIST", "STALE-ALLOWLIST"), stale.map { it.rule })
    }

    @Test
    fun `documentation addresses and ordinary text pass`() {
        val text = "tailnet range 100.64.0.0/10, example 100.100.100.100 and 100.64.0.10, path /home/owner/x, mail a@example.com"
        assertTrue(PrivacyLeakGuard.check(mapOf("docs/a.md" to text), emptySet()).isEmpty())
    }
}

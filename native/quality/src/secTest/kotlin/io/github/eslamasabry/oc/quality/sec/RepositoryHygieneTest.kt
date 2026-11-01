package io.github.eslamasabry.oc.quality.sec

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `RepositoryHygieneTest` (plan 16 §2.3), ported from `test/repository_hygiene_test.dart` and extended with the
 * leak gate. Live checks read `git ls-files` of the whole repository; the planted checks feed the same functions.
 */
class RepositoryHygieneTest {
    private val repo get() = Live.repo

    @Test
    fun `B-SEC-150 no keystores, signing secrets or third-party skill packs are committed`() {
        val tracked = RepositoryHygiene.trackedFiles(repo)
        assertTrue(tracked.size > 100, "git ls-files saw ${tracked.size} files")
        assertClean(RepositoryHygiene.checkTracked(tracked), "tracked tree")
        assertClean(RepositoryHygiene.checkIgnores(File(repo, ".gitignore").readText(), File(Live.root, ".gitignore").readText()), "ignore rules")
    }

    @Test
    fun `B-SEC-150 planted keystore, key properties, p12 and skill pack are caught`() {
        val planted = listOf("native/androidApp/release.jks", "native/key.properties", "native/upload.keystore", "native/core/net/src/test/resources/c.p12", ".claude/skills/pack/SKILL.md", "native/README.md")
        assertEquals(5, RepositoryHygiene.checkTracked(planted).size)
        assertEquals(2, RepositoryHygiene.checkIgnores("build/\n", "build/\nkey.properties\n*.jks\n*.keystore\n").size)
    }

    @Test
    fun `B-BUILD-030 governance files are present and every public entry point states non-affiliation`() =
        assertClean(RepositoryHygiene.checkGovernance(repo), "governance")

    @Test
    fun `B-BUILD-030 planted entry point without the statement fails`() {
        val t = Planted()
        RepositoryHygiene.GOVERNANCE.forEach { t.file(it, "x") }
        t.file("README.md", "> This project is " + RepositoryHygiene.NON_AFFILIATION.replace(" the official", "\n> the **official") + "** sometimes.")
        assertEquals(listOf("HYG-AFFILIATION", "HYG-AFFILIATION"), RepositoryHygiene.checkGovernance(t.root).map { it.id })
    }

    @Test
    fun `leak gate catches a planted personal address, a tailnet address and a deny-listed name in added lines`() {
        val at = "@"
        val tail = "100." // built from parts so this file passes its own gate
        val diff = """
            +++ b/native/docs/note.md
            +reach me at someone${at}gmail.com
            +the box is at ${tail}101.7.3
            +documentation address 100.64.0.10 is fine
            +built on build-box-7
             context line with someone${at}gmail.com is not added
            +++ b/native/e2e/fixtures/servers.json
            +"host": "${tail}100.1.1"
        """.trimIndent()
        val findings = LeakGate.scan(LeakGate.addedLines(diff), listOf("build-box-7"))
        assertEquals(listOf("LEAK-MAIL", "LEAK-TAILNET", "LEAK-DENYLIST"), findings.map { it.id })
        assertEquals(1, LeakGate.checkIdentities(listOf(LeakGate.NOREPLY + "\t" + "x" + at + "example.com")).size)
        assertClean(LeakGate.checkIdentities(listOf(LeakGate.NOREPLY + "\t" + LeakGate.NOREPLY)), "noreply identities")
    }

    /**
     * The live leak gate runs on `-Poc.leakBase=<rev>` (the commits being pushed). `-Poc.leakMode=release` makes a
     * missing deny-list and a non-noreply identity fail; on a work branch they are reported, not failed.
     */
    @Test
    fun `leak gate on the commits being pushed`() {
        val base = System.getProperty("oc.leakBase") ?: return println("leak gate: no -Poc.leakBase given, skipped (work-branch run)")
        val release = System.getProperty("oc.leakMode") == "release"
        val diff = git("diff", "--no-color", "-U0", "$base..HEAD")
        val denylist = LeakGate.denylist(Live.root)
        if (denylist == null) {
            check(!release) { "leak gate: native/config/private-denylist.txt is missing on the release path" }
            println("leak gate: native/config/private-denylist.txt is missing; deny-list terms not checked")
        }
        assertClean(LeakGate.scan(LeakGate.addedLines(diff), denylist.orEmpty()), "leak gate $base..HEAD")
        val identities = LeakGate.checkIdentities(git("log", "--format=%ae%x09%ce", "$base..HEAD").lines())
        if (release) assertClean(identities, "leak gate identities") else if (identities.isNotEmpty()) println("leak gate: ${identities.size} non-noreply commit identities (fail on the release path)")
    }

    private fun git(vararg args: String): String {
        val p = ProcessBuilder(listOf("git") + args).directory(repo).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "git ${args.joinToString(" ")} failed" }
        return out
    }
}

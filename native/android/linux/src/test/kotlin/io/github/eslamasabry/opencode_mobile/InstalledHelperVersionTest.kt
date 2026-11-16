package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstalledHelperVersionTest {
    @get:Rule val folder = TemporaryFolder()

    private fun installed(): Pair<File, File> {
        val root = folder.newFolder()
        val guest = "/home/oc/.local/share/oc-paseo/0.15.4-fixture/node_modules"
        val bin = File(root, "home/oc/.local/bin").apply { mkdirs() }
        Files.createSymbolicLink(File(bin, "paseo").toPath(), Paths.get("$guest/.bin/paseo"))
        val pkg = File(root, guest.removePrefix("/") + "/@getpaseo/cli/package.json")
        pkg.parentFile.mkdirs()
        pkg.writeText("""{"name":"@getpaseo/cli","version":"0.15.4"}""")
        return root to pkg
    }

    @Test fun `B-AGENT-162 installed package version avoids a CLI start`() {
        val (root, _) = installed()
        var probes = 0
        assertEquals("0.15.4", InstalledHelperVersion.read(root) { probes++; "0.9.2" })
        assertEquals(0, probes)
    }

    @Test fun `B-AGENT-162 missing or unreadable package falls back to the bounded CLI probe`() {
        for (kind in listOf("missing", "unreadable", "malformed", "wrong-package")) {
            val (root, pkg) = installed()
            when (kind) {
                "missing" -> pkg.delete()
                // A directory cannot be read as package text, even on a privileged CI runner.
                "unreadable" -> { pkg.delete(); pkg.mkdir() }
                "malformed" -> pkg.writeText("not json")
                else -> pkg.writeText("""{"name":"other","version":"0.15.4"}""")
            }
            var probes = 0
            assertEquals(kind, "0.9.2", InstalledHelperVersion.read(root) { probes++; "0.9.2" })
            assertEquals(kind, 1, probes)
        }
    }
}

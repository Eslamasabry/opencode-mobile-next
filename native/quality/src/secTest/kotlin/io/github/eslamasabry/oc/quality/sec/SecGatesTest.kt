package io.github.eslamasabry.oc.quality.sec

import java.io.File
import kotlin.test.Test

/**
 * The plan 16 §2.3 gates that read source files and resources, on the live tree. Each tolerates only the items
 * listed in `sec-pending.txt`, and fails when a listed item starts to hold (the list only shrinks).
 * Named after the gates: plan 16's row ids for them (B-SEC-193/194/195, B-PROTO-139) name other behaviours in
 * `native/catalog/behaviors.jsonl` (see the S1-SECGATES report, open problem 1).
 */
class SecGatesTest {
    private val root get() = Live.root
    private val pending get() = Live.config.pending

    @Test
    fun `NetworkSecurityConfigTest - exactly the base-config element, system trust only, the manifest points at it`() =
        assertClean(Pending.apply(NetworkSecurityConfig.checkTree(root), pending, NetworkSecurityConfig.IDS), "NetworkSecurityConfigTest")

    @Test
    fun `FileProviderPathsTest - exactly the three roots`() =
        assertClean(Pending.apply(FileProviderPaths.checkTree(root), pending, FileProviderPaths.IDS), "FileProviderPathsTest")

    @Test
    fun `ExportedSurfaceTest - the reviewed component snapshot and the section 1_8_1 hardening`() =
        assertClean(Pending.apply(ExportedSurface.checkTree(root, Live.config.exportedSurface), pending, ExportedSurface.IDS), "ExportedSurfaceTest")

    @Test
    fun `TlsFloorTest - OkHttp at least 5_5_1, no downgrade constraint, no bundled CA`() {
        val catalog = File(root, "gradle/libs.versions.toml").readText()
        val paths = root.walkTopDown().onEnter { it.name !in setOf("build", ".gradle", ".git") }.filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }.toList()
        assertClean(
            TlsFloor.checkCatalog(catalog) + TlsFloor.checkBuildScripts(SecTree.buildScripts(root)) + TlsFloor.checkBundledTrust(paths),
            "TlsFloorTest",
        )
    }

    @Test
    fun `QaHooksAbsentTest - no qa source wired into release, no qa class or marker in production`() {
        val findings = QaHooks.checkBuildScripts(SecTree.buildScripts(root)) +
            QaHooks.checkSources(Live.files, File(root, "version.properties").readText())
        assertClean(findings, "QaHooksAbsentTest")
    }

    @Test
    fun `LintTableTest - androidApp lint xml raises the section 2_2 table to error, one reasoned suppression`() =
        assertClean(LintTable.checkLintXml(File(root, "androidApp/lint.xml").readText()), "androidApp/lint.xml")

    @Test
    fun `LintTableTest - the convention plugins apply the same table with abortOnError and no baseline`() =
        assertClean(LintTable.checkConvention(File(root, "build-logic/convention/src/main/kotlin/oc/buildlogic/LintPolicy.kt").readText()), "LintPolicy.kt")

    @Test
    fun `sec-pending lists only ids that some gate can raise`() {
        val known = NetworkSecurityConfig.IDS + FileProviderPaths.IDS + ExportedSurface.IDS
        assertClean(pending.filter { it !in known }.map { "$it is not a gate finding id" }, "sec-pending.txt")
    }
}

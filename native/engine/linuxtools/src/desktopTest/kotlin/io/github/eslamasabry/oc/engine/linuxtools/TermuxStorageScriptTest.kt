package io.github.eslamasabry.oc.engine.linuxtools

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Port of the tools.sh halves of test/termux_storage_test.dart: the embedded tools script runs under bash against a
 * fixture tree that mirrors the phone's paths (Termux $PREFIX, $HOME and the proot rootfs), with a stub `ps` on PATH.
 */
class TermuxStorageScriptTest {
    private inner class Phone {
        val root: File = tempDir("oc-tools-storage-")
        val home = File(root, "home").apply { mkdirs() }
        val prefix = File(root, "prefix").apply { mkdirs() }
        val bin = File(root, "bin").apply { mkdirs() }
        val rootfs = File(prefix, "var/lib/proot-distro/containers/opencode-ubuntu/rootfs").apply { mkdirs() }
        val tools = File(root, "tools.sh").apply { writeText(TERMUX_TOOLS_SCRIPT) }
        val ps = File(bin, "ps")
        val psFixture = File(root, "ps.txt").apply { writeText("") }
        val ocDir get() = File(home, ".oc")

        init {
            writeExecutable(ps, "#!/bin/bash\ncat \"\$OC_PS_FIXTURE\"\n")
        }

        /** Writes [bytes] of data at [relative] (under [base]). */
        fun file(base: File, relative: String, bytes: Int = 2048) {
            val target = File(base, relative)
            target.parentFile.mkdirs()
            target.writeBytes(ByteArray(bytes) { 0x41 })
        }

        fun processes(lines: String) = psFixture.writeText(lines)

        fun run(vararg args: String): Ran =
            requireHermetic(root, home.path, prefix.path, tools.path, psFixture.path).let { runProcess(
                listOf("bash", tools.path) + args,
                mapOf(
                    "HOME" to home.path,
                    "PREFIX" to prefix.path,
                    "PATH" to "${bin.path}:${System.getenv("PATH")}",
                    "OC_PS_FIXTURE" to psFixture.path,
                ),
            ) }

        fun clean(category: String): TermuxStorageCleanResult = TermuxStorageCleanResult.parse(run("storage-clean", category).stdout)!!

        fun rel(paths: Iterable<TermuxStoragePath>): Set<String> = paths.map { it.path.replaceFirst(root.path, "") }.toSet()
    }

    private val phones = mutableListOf<Phone>()

    @AfterTest
    fun cleanUp() = phones.forEach { it.root.deleteTree() }

    private fun bare(): Phone = Phone().also { phones += it }

    private fun phoneWithCaches(): Phone {
        val phone = bare()
        val r = phone.rootfs
        phone.file(r, "root/.gradle/caches/a.jar")
        phone.file(r, "root/.gradle/wrapper/b.zip")
        phone.file(r, "root/.pub-cache/c")
        phone.file(r, "root/.cache/d")
        phone.file(r, "root/.dartServer/e")
        phone.file(phone.home, ".npm/f")
        phone.file(phone.home, ".npm/_cacache/content-v2/cache")
        phone.file(phone.home, ".cache/g")
        phone.file(r, "tmp/opencode/scratch-1/h")
        phone.file(r, "tmp/opencode/scratch-2/i")
        phone.file(r, "tmp/opencode/flutter/bin/flutter")
        phone.file(r, "tmp/opencode/flutter_linux_3.35.tar.xz")
        phone.file(phone.prefix, "tmp/j")
        phone.file(r, "usr/lib/android-sdk/k")
        phone.file(r, "usr/lib/jvm/l")
        phone.file(r, "root/projects/IPTV_King/lib/main.dart")
        phone.file(r, "root/projects/IPTV_King/build/app.apk")
        phone.file(r, "root/projects/IPTV_King/android/build/x")
        phone.file(r, "root/projects/IPTV_King/.dart_tool/y")
        phone.file(r, "root/projects/IPTV_King/node_modules/z")
        phone.file(r, "root/projects/notes/README.md")
        phone.file(r, "usr/local/lib/node_modules/opencode-ai/index.js")
        phone.file(r, "root/.local/share/opencode/auth.json")
        phone.file(phone.home, ".oc/aiteam/city/rig")
        phone.file(phone.prefix, "bin/gc")
        phone.file(r, "root/aiteam-work/w")
        // Only an idle shell: nothing is using any category.
        phone.processes("  700     1  0.0 00:00:00 3000 100 bash bash\n")
        return phone
    }

    private fun scan(phone: Phone): TermuxStorageReport {
        val scan = phone.run("storage-scan")
        assertEquals(0, scan.exitCode, "${scan.stdout}\n${scan.stderr}")
        val status = phone.run("storage-status")
        assertEquals(0, status.exitCode, status.stderr)
        val parsed = TermuxStorageScanStatus.parse(status.stdout)
        assertEquals(TermuxStorageScanState.Done, parsed.state, parsed.log)
        return parsed.report!!
    }

    private fun TermuxStorageReport.byKey() = categories.associateBy { it.key }

    private val rfs = "/prefix/var/lib/proot-distro/containers/opencode-ubuntu/rootfs"

    @Test
    fun `B-LINUX-247 scan measures every category with the right paths and flags`() {
        val phone = phoneWithCaches()
        val report = scan(phone)
        val cats = report.byKey()
        assertEquals(
            listOf(
                "build_caches", "agent_scratch", "project_build_outputs", "toolchains", "ai_team", "opencode",
                "projects", "shared_caches",
            ),
            cats.keys.toList(),
        )
        assertEquals(setOf("$rfs/root/.gradle/caches", "/home/.npm/_cacache"), phone.rel(cats["build_caches"]!!.paths))
        assertTrue(cats["build_caches"]!!.canClean)
        assertTrue(cats["build_caches"]!!.bytes >= 2 * 2048)
        for (key in cats.keys.filter { it != "build_caches" }) {
            assertFalse(cats[key]!!.deletable, key)
            assertFalse(cats[key]!!.canClean, key)
        }
        assertEquals(
            setOf(
                "$rfs/root/.gradle/wrapper", "$rfs/root/.pub-cache", "$rfs/root/.cache", "$rfs/root/.dartServer",
                "/home/.npm/f", "/home/.cache",
            ),
            phone.rel(cats["shared_caches"]!!.paths),
        )
        assertEquals(
            setOf("$rfs/tmp/opencode/scratch-1", "$rfs/tmp/opencode/scratch-2", "/prefix/tmp"),
            phone.rel(cats["agent_scratch"]!!.paths),
        )
        assertEquals(
            setOf(
                "$rfs/root/projects/IPTV_King/build", "$rfs/root/projects/IPTV_King/android/build",
                "$rfs/root/projects/IPTV_King/.dart_tool", "$rfs/root/projects/IPTV_King/node_modules",
            ),
            phone.rel(cats["project_build_outputs"]!!.paths),
        )
        assertEquals(
            setOf(
                "$rfs/usr/lib/android-sdk", "$rfs/usr/lib/jvm", "$rfs/tmp/opencode/flutter",
                "$rfs/tmp/opencode/flutter_linux_3.35.tar.xz",
            ),
            phone.rel(cats["toolchains"]!!.paths),
        )
        assertEquals("termuxStorageNoteToolchains", cats["toolchains"]!!.noteKey)
        assertEquals(
            setOf("/home/.oc/aiteam", "/prefix/bin/gc", "$rfs/root/aiteam-work"),
            phone.rel(cats["ai_team"]!!.paths),
        )
        assertFalse(cats["opencode"]!!.deletable)
        assertEquals(
            setOf("$rfs/usr/local/lib/node_modules", "$rfs/root/.local/share/opencode"),
            phone.rel(cats["opencode"]!!.paths),
        )
        assertFalse(cats["projects"]!!.deletable)
        assertEquals(
            setOf("$rfs/root/projects/IPTV_King", "$rfs/root/projects/notes"),
            phone.rel(cats["projects"]!!.paths),
        )
        assertEquals(listOf("IPTV_King", "notes"), report.projects.map { it.name })
        val iptv = report.projects.first()
        assertTrue(iptv.buildBytes >= 4 * 2048)
        assertTrue(iptv.bytes > iptv.buildBytes)
        assertTrue(report.totalBytes > report.deletableBytes)
        assertNotNull(report.scannedAtEpochSeconds)
        // The log the UI tails names each measured path in short form.
        val log = File(phone.ocDir, "storage-scan.log").readText()
        assertTrue(log.contains("[oc] Build caches"))
        assertTrue(log.contains("ubuntu:/root/.gradle/caches"))
        assertTrue(log.contains("~/.npm"))
        assertTrue(log.contains("[oc] Scan complete"))
        assertEquals(TermuxStorageScanStage.Total, TermuxStorageScanStage.reached(log))
    }

    @Test
    fun `B-LINUX-247 scan accepts the older installed-rootfs layout`() {
        val phone = bare()
        phone.rootfs.deleteTree()
        val old = File(phone.prefix, "var/lib/proot-distro/installed-rootfs/opencode-ubuntu").apply { mkdirs() }
        phone.file(old, "root/.gradle/caches/a")
        phone.processes("")
        val report = scan(phone)
        assertEquals(
            setOf("/prefix/var/lib/proot-distro/installed-rootfs/opencode-ubuntu/root/.gradle/caches"),
            phone.rel(report.byKey()["build_caches"]!!.paths),
        )
        val result = phone.clean("build_caches")
        assertEquals(1, result.removed.size)
        assertTrue(result.refused.isEmpty())
    }

    @Test
    fun `B-LINUX-247 a cancel file stops the scan before it measures anything`() {
        val phone = phoneWithCaches()
        phone.ocDir.mkdirs()
        File(phone.ocDir, "storage-scan.cancel").createNewFile()
        assertEquals(0, phone.run("storage-scan").exitCode)
        val status = TermuxStorageScanStatus.parse(phone.run("storage-status").stdout)
        assertEquals(TermuxStorageScanState.Cancelled, status.state)
        assertNull(status.report)
        assertTrue(status.log.contains("[oc] Scan cancelled"))
        assertFalse(File(phone.ocDir, "storage-scan.pid").exists())
    }

    @Test
    fun `B-LINUX-247 storage-summary answers with the last total`() {
        val phone = phoneWithCaches()
        val before = TermuxStorageSummary.parse(phone.run("storage-summary").stdout)
        assertEquals(TermuxStorageScanState.Idle, before.state)
        assertNull(before.totalBytes)
        val report = scan(phone)
        val after = TermuxStorageSummary.parse(phone.run("storage-summary").stdout)
        assertEquals(TermuxStorageScanState.Done, after.state)
        assertEquals(report.totalBytes, after.totalBytes)
        assertEquals(report.scannedAtEpochSeconds, after.scannedAtEpochSeconds)
    }

    @Test
    fun `B-LINUX-247 clean removes only the listed paths of one category`() {
        val phone = phoneWithCaches()
        val report = scan(phone)
        val before = report.byKey()["build_caches"]!!
        val result = phone.clean("build_caches")
        assertTrue(result.refused.isEmpty())
        assertEquals(before.bytes, result.freedBytes)
        assertEquals(phone.rel(before.paths), phone.rel(result.removed))
        for (path in before.paths) {
            if (path.path == "${phone.home.path}/.cache") {
                assertTrue(File(path.path).isDirectory)
                assertTrue(File(path.path).list()!!.isEmpty())
            } else {
                assertFalse(exists(path.path), path.path)
            }
        }
        // Everything else is untouched.
        val r = phone.rootfs.path
        for (kept in listOf(
            "$r/root/projects/IPTV_King/lib/main.dart", "$r/root/projects/IPTV_King/build/app.apk",
            "$r/tmp/opencode/scratch-1/h", "$r/root/.local/share/opencode/auth.json", "$r/usr/lib/jvm/l",
        )) {
            assertTrue(File(kept).exists(), kept)
        }
        // A second clean must rescan instead of trusting pre-clean measurements.
        val again = phone.clean("build_caches")
        assertEquals(0L, again.freedBytes)
        assertTrue(again.removed.isEmpty())
        assertTrue(again.rescanRequired)
    }

    @Test
    fun `B-LINUX-247 protected inventory categories refuse even direct calls`() {
        val phone = phoneWithCaches()
        scan(phone)
        for (category in listOf("project_build_outputs", "agent_scratch", "toolchains", "ai_team", "shared_caches")) {
            val result = phone.clean(category)
            assertTrue(result.removed.isEmpty(), category)
            assertEquals("never_deletable", result.refused.single().reason, category)
        }
        for (path in listOf(
            "${phone.rootfs.path}/root/projects/IPTV_King/build/app.apk",
            "${phone.rootfs.path}/root/projects/IPTV_King/node_modules/z",
            "${phone.rootfs.path}/tmp/opencode/scratch-1/h",
            "${phone.prefix.path}/tmp/j",
            "${phone.home.path}/.cache/g",
            "${phone.home.path}/.oc/aiteam/city/rig",
            "${phone.rootfs.path}/usr/lib/jvm/l",
        )) {
            assertTrue(File(path).exists(), path)
        }
    }

    @Test
    fun `B-LINUX-247 clean refuses caches while a build is running`() {
        val phone = phoneWithCaches()
        scan(phone)
        phone.processes("300 1 1.0 02:00:00 300000 7200 java java org.gradle.launcher.daemon.bootstrap.GradleDaemon\n")
        val result = phone.clean("build_caches")
        assertEquals(listOf("java"), result.inUse!!.processes)
        assertTrue(result.removed.isEmpty())
        assertTrue(File(phone.rootfs, "root/.gradle/caches/a.jar").exists())
    }

    @Test
    fun `B-LINUX-247 failed process discovery preserves caches and the current report`() {
        val phone = phoneWithCaches()
        scan(phone)
        val reportFile = File(phone.ocDir, "storage-scan.json")
        val manifestFile = File(phone.ocDir, "storage-scan.paths")
        val beforeReport = reportFile.readText()
        val beforeManifest = manifestFile.readText()
        writeExecutable(phone.ps, "#!/bin/bash\necho \"unsupported process columns\" >&2\nexit 1\n")
        val result = phone.clean("build_caches")
        assertEquals("process_check_failed", result.refused.single().reason)
        assertEquals(0L, result.freedBytes)
        assertTrue(result.removed.isEmpty())
        assertFalse(result.rescanRequired)
        assertEquals(beforeReport, reportFile.readText())
        assertEquals(beforeManifest, manifestFile.readText())
        assertEquals("done", File(phone.ocDir, "storage-scan.state").readText())
        assertTrue(File(phone.rootfs, "root/.gradle/caches/a.jar").exists())
        assertTrue(File(phone.home, ".npm/_cacache/content-v2/cache").exists())
    }

    private fun incompleteLock(emptyPid: Boolean) {
        val phone = phoneWithCaches()
        scan(phone)
        val lock = File(phone.ocDir, "storage-operation.lock").apply { mkdir() }
        if (emptyPid) File(lock, "pid").writeText("")
        assertEquals(75, phone.run("storage-clean", "build_caches").exitCode)
        assertEquals(75, phone.run("storage-scan").exitCode)
        assertTrue(File(phone.rootfs, "root/.gradle/caches/a.jar").exists())
        assertEquals(0, runProcess(listOf("touch", "-m", "-t", "202001010000", lock.path)).exitCode)
        val result = phone.clean("build_caches")
        assertTrue(result.refused.isEmpty())
        assertEquals(2, result.removed.size)
        assertFalse(lock.exists())
    }

    @Test
    fun `B-LINUX-247 incomplete lock stays busy while fresh and recovers after aging`() = incompleteLock(emptyPid = false)

    @Test
    fun `B-LINUX-247 incomplete lock with an empty pid stays busy while fresh and recovers after aging`() =
        incompleteLock(emptyPid = true)

    @Test
    fun `B-LINUX-247 plain Termux without Ubuntu only cleans its npm content cache`() {
        val phone = phoneWithCaches()
        phone.rootfs.deleteTree()
        val report = scan(phone)
        assertEquals(setOf("/home/.npm/_cacache"), phone.rel(report.byKey()["build_caches"]!!.paths))
        val result = phone.clean("build_caches")
        assertEquals(1, result.removed.size)
        assertTrue(File(phone.home, ".npm/f").exists())
        assertTrue(File(phone.home, ".cache/g").exists())
    }

    @Test
    fun `B-LINUX-247 an active operation blocks cleanup and scanning`() {
        val phone = phoneWithCaches()
        scan(phone)
        val lock = File(phone.ocDir, "storage-operation.lock").apply { mkdir() }
        File(lock, "pid").writeText("${ProcessHandle.current().pid()}\n")
        assertEquals(75, phone.run("storage-clean", "build_caches").exitCode)
        assertEquals(75, phone.run("storage-scan").exitCode)
        assertTrue(File(phone.rootfs, "root/.gradle/caches/a.jar").exists())
    }

    @Test
    fun `B-LINUX-247 a wrong-owner cache is refused instead of removed`() {
        val phone = phoneWithCaches()
        scan(phone)
        writeExecutable(File(phone.bin, "stat"), "#!/bin/bash\nprintf 99999999\n")
        val result = phone.clean("build_caches")
        assertTrue(result.removed.isEmpty())
        assertEquals(0L, result.freedBytes)
        assertEquals(2, result.refused.size)
        assertTrue(File(phone.rootfs, "root/.gradle/caches/a.jar").exists())
    }

    @Test
    fun `B-LINUX-247 legacy reports require a fresh scan`() {
        val phone = phoneWithCaches()
        scan(phone)
        val file = File(phone.ocDir, "storage-scan.json")
        file.writeText(file.readText().replace(",\"cleanup_policy\":2,\"stale\":false", ""))
        val result = phone.clean("build_caches")
        assertTrue(result.rescanRequired)
        assertTrue(result.removed.isEmpty())
        assertTrue(TermuxStorageReport.parse(file.readText())!!.isStale)
    }

    @Test
    fun `B-LINUX-247 forged cache entries cannot remove broad or project paths`() {
        val phone = phoneWithCaches()
        scan(phone)
        val protected =
            listOf(
                "${phone.home.path}/.cache",
                "${phone.prefix.path}/tmp",
                "${phone.home.path}/.oc/aiteam",
                "${phone.rootfs.path}/root/projects/IPTV_King/build",
                "${phone.rootfs.path}/root/.pub-cache",
                "${phone.rootfs.path}/root/.gradle/wrapper",
                "${phone.rootfs.path}/usr/lib/jvm",
            )
        val manifest = File(phone.ocDir, "storage-scan.paths")
        manifest.writeText(protected.joinToString("") { "build_caches\ttrue\t999999\t$it\n" })
        val result = phone.clean("build_caches")
        assertEquals(0L, result.freedBytes)
        assertTrue(result.removed.isEmpty())
        assertEquals(protected.size, result.refused.size)
        for (path in protected) {
            assertTrue(File(path).isDirectory, path)
            assertTrue(manifest.readText().contains(path))
        }
    }

    @Test
    fun `B-LINUX-247 a replaced cache ancestor symlink is refused and retained`() {
        val phone = phoneWithCaches()
        scan(phone)
        val gradle = File(phone.rootfs, "root/.gradle")
        val moved = File(phone.root, "personal-gradle")
        check(gradle.renameTo(moved))
        symlink(gradle, moved.path)
        val result = phone.clean("build_caches")
        assertEquals("protected", result.refused.single().reason)
        assertTrue(File(moved, "caches/a.jar").exists())
        assertTrue(File(phone.ocDir, "storage-scan.paths").readText().contains("${gradle.path}/caches"))
    }

    @Test
    fun `B-LINUX-247 freed bytes use current measurements and invalidate the persisted report`() {
        val phone = phoneWithCaches()
        val report = scan(phone)
        phone.file(phone.rootfs, "root/.gradle/caches/new.jar", bytes = 9000)
        val result = phone.clean("build_caches")
        assertTrue(result.freedBytes > report.deletableBytes)
        assertTrue(result.rescanRequired)
        val status = TermuxStorageScanStatus.parse(phone.run("storage-status").stdout)
        assertEquals(TermuxStorageScanState.Stale, status.state)
        assertTrue(status.report!!.isStale)
        assertNull(TermuxStorageSummary.parse(phone.run("storage-summary").stdout).totalBytes)
        assertFalse(scan(phone).isStale)
    }

    @Test
    fun `B-LINUX-247 partial removal counts observed shrinkage and preserves the refused entry`() {
        val phone = phoneWithCaches()
        phone.file(phone.rootfs, "root/.gradle/caches/keep.jar", bytes = 4000)
        scan(phone)
        writeExecutable(
            File(phone.bin, "rm"),
            "#!/bin/bash\n" +
                "for arg in \"\$@\"; do\n" +
                "  case \"\$arg\" in */.gradle/caches) /bin/rm -- \"\$arg/a.jar\"; exit 1 ;; esac\n" +
                "done\n" +
                "exec /bin/rm \"\$@\"\n",
        )
        val result = phone.clean("build_caches")
        assertEquals("remove_failed", result.refused.single().reason)
        assertTrue(result.freedBytes > 0)
        assertFalse(result.removed.any { it.path.endsWith("/.gradle/caches") })
        assertTrue(File(phone.rootfs, "root/.gradle/caches/keep.jar").exists())
        assertTrue(File(phone.ocDir, "storage-scan.paths").readText().contains("/.gradle/caches"))
        assertTrue(result.rescanRequired)
    }

    @Test
    fun `B-LINUX-247 never offers OpenCode itself or project sources`() {
        val phone = phoneWithCaches()
        scan(phone)
        for (category in listOf("opencode", "projects")) {
            val result = phone.clean(category)
            assertTrue(result.removed.isEmpty())
            assertEquals("never_deletable", result.refused.single().reason)
        }
        assertEquals(64, phone.run("storage-clean", "sessions").exitCode)
        assertTrue(File(phone.rootfs, "root/.local/share/opencode/auth.json").exists())
    }

    @Test
    fun `B-LINUX-247 nothing is removed when no scan has listed anything`() {
        val phone = phoneWithCaches()
        assertEquals(65, phone.run("storage-clean", "build_caches").exitCode)
        assertTrue(File(phone.rootfs, "root/.gradle/caches/a.jar").exists())
    }
}

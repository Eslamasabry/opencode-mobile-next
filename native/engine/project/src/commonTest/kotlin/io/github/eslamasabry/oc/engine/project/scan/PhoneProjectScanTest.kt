package io.github.eslamasabry.oc.engine.project.scan

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A file tree standing in for the phone's storage; each listing may take [listDelay] of virtual time. */
private class FakeTree(private val listDelay: Duration = Duration.ZERO) : ScanFileSystem {
    val kinds = LinkedHashMap<String, ScanEntryKind>()
    val listed = ArrayList<String>()

    fun file(path: String) {
        parents(path)
        kinds[path] = ScanEntryKind.File
    }

    fun dir(path: String) {
        parents(path)
        kinds[path] = ScanEntryKind.Folder
    }

    fun link(path: String) {
        parents(path)
        kinds[path] = ScanEntryKind.Link
    }

    private fun parents(path: String) {
        var p = path.substringBeforeLast('/', "")
        while (p.isNotEmpty() && p != ROOT) {
            kinds.getOrPut(p) { ScanEntryKind.Folder }
            p = p.substringBeforeLast('/', "")
        }
    }

    override suspend fun list(path: String): List<ScanEntry>? {
        if (listDelay > Duration.ZERO) delay(listDelay)
        listed += path
        if (path != ROOT && kinds[path] != ScanEntryKind.Folder) return null
        return kinds.filterKeys { it.substringBeforeLast('/') == path }.map { (k, v) -> ScanEntry(k.substringAfterLast('/'), v) }
    }
}

private const val ROOT = "/sd"

class PhoneProjectScanTest {
    private suspend fun scan(scan: PhoneProjectScan): Pair<List<PhoneProject>, PhoneScanEnd> {
        val found = ArrayList<PhoneProject>()
        val end = scan.run { found += it }
        return found to end
    }

    private fun TestScope.scanner(
        tree: FakeTree,
        root: String = ROOT,
        maxVisited: Int = 20_000,
        maxResults: Int = 200,
        reportAs: String? = null,
        childrenAreProjects: Boolean = false,
    ) = PhoneProjectScanner(
        tree,
        root,
        testScheduler.timeSource,
        maxVisited = maxVisited,
        maxResults = maxResults,
        reportAs = reportAs,
        childrenAreProjects = childrenAreProjects,
    )

    @Test
    fun `B-LINUX-148 finds a folder by its marker and says what kind it is`() = runTest {
        val t = FakeTree()
        t.file("$ROOT/a/pubspec.yaml"); t.file("$ROOT/b/package.json"); t.file("$ROOT/c/Cargo.toml")
        t.file("$ROOT/d/go.mod"); t.file("$ROOT/e/pyproject.toml"); t.file("$ROOT/f/App.sln")
        t.dir("$ROOT/g/.git"); t.file("$ROOT/h/notes.txt")
        val (found, end) = scan(scanner(t).start())
        assertEquals(PhoneScanEnd.Completed, end)
        assertEquals(
            mapOf(
                "a" to PhoneProjectKind.Dart, "b" to PhoneProjectKind.Node, "c" to PhoneProjectKind.Rust,
                "d" to PhoneProjectKind.Go, "e" to PhoneProjectKind.Python, "f" to PhoneProjectKind.Dotnet,
                "g" to PhoneProjectKind.Git,
            ),
            found.associate { it.name to it.kind },
        )
        assertTrue(found.first { it.name == "g" }.hasGit)
        assertFalse(found.first { it.name == "a" }.hasGit)
    }

    @Test
    fun `B-LINUX-148 every listed marker makes a project`() = runTest {
        val names =
            listOf(
                ".git", "package.json", "pubspec.yaml", "pyproject.toml", "requirements.txt", "Cargo.toml", "go.mod",
                "pom.xml", "build.gradle", "build.gradle.kts", "Gemfile", "composer.json", "CMakeLists.txt", "Makefile",
                "x.sln", "x.csproj",
            )
        val t = FakeTree()
        names.forEachIndexed { i, n -> if (n == ".git") t.dir("$ROOT/p$i/.git") else t.file("$ROOT/p$i/$n") }
        assertEquals(names.size, scan(scanner(t).start()).first.size)
    }

    @Test
    fun `B-LINUX-148 a repository with a code marker keeps its kind and has git`() = runTest {
        val t = FakeTree()
        t.dir("$ROOT/app/.git"); t.file("$ROOT/app/package.json")
        val p = scan(scanner(t).start()).first.single()
        assertEquals(PhoneProjectKind.Node, p.kind)
        assertTrue(p.hasGit)
    }

    @Test
    fun `B-LINUX-148 never goes into a project`() = runTest {
        val t = FakeTree()
        t.file("$ROOT/app/package.json"); t.file("$ROOT/app/inner/pubspec.yaml")
        assertEquals(listOf("app"), scan(scanner(t).start()).first.map { it.name })
        assertFalse("$ROOT/app/inner" in t.listed)
    }

    @Test
    fun `B-LINUX-148 skips Android, node_modules, hidden and build folders`() = runTest {
        val t = FakeTree()
        for (s in listOf("Android", "node_modules", ".hidden", "build", ".dart_tool", "venv", ".venv", "__pycache__", "target", "dist")) {
            t.file("$ROOT/$s/p/package.json")
        }
        t.file("$ROOT/work/p/package.json")
        assertEquals(listOf("$ROOT/work/p"), scan(scanner(t).start()).first.map { it.path })
    }

    @Test
    fun `B-LINUX-148 does not follow links`() = runTest {
        val t = FakeTree()
        t.file("$ROOT/real/p/package.json"); t.link("$ROOT/links/again")
        assertEquals(listOf("$ROOT/real/p"), scan(scanner(t).start()).first.map { it.path })
        assertFalse("$ROOT/links/again" in t.listed)
    }

    @Test
    fun `B-LINUX-148 looks five levels down and no further`() = runTest {
        val t = FakeTree()
        t.file("$ROOT/1/2/3/4/five/package.json"); t.file("$ROOT/1/2/3/4/5/six/package.json")
        val (found, end) = scan(scanner(t).start())
        assertEquals(listOf("five"), found.map { it.name })
        assertEquals(PhoneScanEnd.Completed, end)
    }

    @Test
    fun `B-LINUX-148 the root itself is not a project`() = runTest {
        val t = FakeTree()
        t.file("$ROOT/package.json")
        assertTrue(scan(scanner(t).start()).first.isEmpty())
    }

    @Test
    fun `B-LINUX-149 the time cap stops it and says so`() = runTest {
        val t = FakeTree()
        t.file("$ROOT/a/package.json")
        val (found, end) = scan(scanner(t).start(Duration.ZERO))
        assertEquals(PhoneScanEnd.TimedOut, end)
        assertTrue(found.isEmpty())
    }

    @Test
    fun `B-LINUX-149 the time cap stops a slow scan part way on virtual time`() = runTest {
        val t = FakeTree(listDelay = 100.milliseconds)
        for (i in 0 until 20) t.file("$ROOT/p$i/package.json")
        val (found, end) = scan(scanner(t).start(550.milliseconds))
        assertEquals(PhoneScanEnd.TimedOut, end)
        assertEquals(5, found.size) // root at 100 ms, then p0..p4 by 600 ms
    }

    @Test
    fun `B-LINUX-149 the visit cap stops it as limited`() = runTest {
        val t = FakeTree()
        for (i in 0 until 10) t.dir("$ROOT/d$i/x")
        assertEquals(PhoneScanEnd.Limited, scan(scanner(t, maxVisited = 3).start()).second)
    }

    @Test
    fun `B-LINUX-149 the result cap keeps the first results`() = runTest {
        val t = FakeTree()
        for (i in 0 until 6) t.file("$ROOT/p$i/package.json")
        val (found, end) = scan(scanner(t, maxResults = 2).start())
        assertEquals(listOf("p0", "p1"), found.map { it.name })
        assertEquals(PhoneScanEnd.Limited, end)
    }

    @Test
    fun `B-LINUX-149 cancel stops it`() = runTest {
        val t = FakeTree()
        for (i in 0 until 30) t.dir("$ROOT/d$i/e/f")
        val run = scanner(t).start()
        run.cancel()
        assertEquals(PhoneScanEnd.Cancelled, scan(run).second)
        assertTrue(run.cancelled)

        // And mid-run, on virtual time.
        val slow = FakeTree(listDelay = 10.milliseconds)
        for (i in 0 until 30) slow.dir("$ROOT/d$i/e/f")
        val running = scanner(slow).start()
        var end: PhoneScanEnd? = null
        val job = launch { end = running.run {} }
        testScheduler.advanceTimeBy(55)
        running.cancel()
        job.join()
        assertEquals(PhoneScanEnd.Cancelled, end)
        assertTrue(running.visited in 1..10)
    }

    @Test
    fun `B-LINUX-149 an unreadable root ends quietly`() = runTest {
        val t = FakeTree()
        val (found, end) = scan(scanner(t, root = "$ROOT/missing").start())
        assertTrue(found.isEmpty())
        assertEquals(PhoneScanEnd.Completed, end)
    }

    @Test
    fun `B-LINUX-150 every folder straight in the project space is a project, with or without markers`() = runTest {
        val t = FakeTree()
        t.file("$ROOT/proj/a/package.json"); t.dir("$ROOT/proj/b/.git"); t.dir("$ROOT/proj/c/notes")
        val found = scan(scanner(t, root = "$ROOT/proj", reportAs = "/root/projects", childrenAreProjects = true).start()).first
        val byName = found.associateBy { it.name }
        assertEquals(setOf("a", "b", "c"), byName.keys)
        assertEquals("/root/projects/a", byName.getValue("a").path)
        assertEquals(PhoneProjectKind.Node, byName.getValue("a").kind)
        assertTrue(byName.getValue("b").hasGit)
        assertFalse(byName.getValue("c").hasGit)
    }

    @Test
    fun `B-LINUX-150 markers still find projects deeper down`() = runTest {
        val t = FakeTree()
        t.file("$ROOT/proj/group/deep/pubspec.yaml")
        val found = scan(scanner(t, root = "$ROOT/proj", reportAs = "/root/projects", childrenAreProjects = true).start()).first
        assertEquals(listOf("/root/projects/group"), found.map { it.path })
    }

    @Test
    fun `B-LINUX-150 in sequence the first scan's results come first`() = runTest {
        val t = FakeTree()
        t.file("$ROOT/proj/a/package.json"); t.file("$ROOT/phone/b/package.json")
        val s =
            PhoneProjectScan.sequence(
                listOf(
                    { limit -> scanner(t, root = "$ROOT/proj", reportAs = "/root/projects", childrenAreProjects = true).start(limit) },
                    { limit -> scanner(t, root = "$ROOT/phone").start(limit) },
                ),
                10.seconds,
                testScheduler.timeSource,
            )
        val (found, end) = scan(s)
        assertEquals(listOf("a", "b"), found.map { it.name })
        assertEquals(PhoneScanEnd.Completed, end)
        assertTrue(s.visited > 2)
    }

    @Test
    fun `B-LINUX-150 a list becomes a scan and a failed list ends quietly`() = runTest {
        val listed = PhoneProjectScan.fromLoad { listOf(PhoneProject("x", "/root/projects/x", PhoneProjectKind.Git, true)).ok() }
        val (found, end) = scan(listed)
        assertEquals("x", found.single().name)
        assertEquals(PhoneScanEnd.Completed, end)
        val broken = PhoneProjectScan.fromLoad { Failure.Unknown().failed() }
        assertEquals(emptyList<PhoneProject>() to PhoneScanEnd.Completed, scan(broken))
    }

    @Test
    fun `B-LINUX-150 cancel stops the whole sequence`() = runTest {
        val t = FakeTree(listDelay = 10.milliseconds)
        for (i in 0 until 20) t.dir("$ROOT/phone/d$i/e/f")
        var second = false
        val s =
            PhoneProjectScan.sequence(
                listOf({ limit -> scanner(t).start(limit) }, { limit -> second = true; scanner(t).start(limit) }),
                10.seconds,
                testScheduler.timeSource,
            )
        s.cancel()
        assertEquals(PhoneScanEnd.Cancelled, scan(s).second)

        val running =
            PhoneProjectScan.sequence(
                listOf({ limit -> scanner(t).start(limit) }, { limit -> second = true; scanner(t).start(limit) }),
                10.seconds,
                testScheduler.timeSource,
            )
        var end: PhoneScanEnd? = null
        val job = launch { end = running.run {} }
        testScheduler.advanceTimeBy(35)
        running.cancel()
        job.join()
        assertEquals(PhoneScanEnd.Cancelled, end)
        assertFalse(second)
    }
}

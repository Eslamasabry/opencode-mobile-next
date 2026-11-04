package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** BUILD-REST repo gates: B-BUILD-001, B-BUILD-023, B-BUILD-031, B-BUILD-033 (see [BuildRestGates]). */
class BuildRestGatesTest {
    private val root = File(System.getProperty("oc.root"))
    private val ui by lazy { BuildRestGates.production(root, "feature", "ui") }
    private val features by lazy { ui.filterKeys { it.startsWith("feature/") } }
    private val kits by lazy { ui.filterKeys { it.startsWith("ui/kit") } }
    private val everything by lazy { BuildRestGates.production(root, "feature", "ui", "android", "engine", "androidApp", "desktopApp") }

    private fun assertNone(hits: List<BuildRestGates.Hit>, what: String) =
        assertTrue(hits.isEmpty(), "$what:\n" + hits.joinToString("\n") { "${it.path}:${it.line} ${it.rule}" })

    // ---- B-BUILD-001 ----

    @Test
    fun `B-BUILD-001 the UI layer never imports a protocol client and never branches on the server flavour`() {
        assertTrue(ui.size > 100, "scanned ${ui.size} UI files")
        assertNone(ui.flatMap { (p, t) -> BuildRestGates.uiBoundary(p, t) }, "UI files that reach past the domain gateway")
        val buildFiles =
            listOf("feature", "ui").flatMap { layer ->
                File(root, layer).listFiles().orEmpty().map { File(it, "build.gradle.kts") }.filter { it.isFile }
            }
        assertTrue(buildFiles.size > 20)
        assertNone(buildFiles.flatMap { BuildRestGates.uiBuildFile(it.relativeTo(root).path, it.readText()) }, "UI modules on a protocol")
    }

    @Test
    fun `B-BUILD-001 planted protocol imports, flavour branches and dependencies are caught`() {
        val planted =
            """
            import io.github.eslamasabry.oc.protocol.oc2.Oc2Client
            // import io.github.eslamasabry.oc.protocol.paseo.Commented — a comment is not code
            val x = if (profile.backend == BackendKind.OpenCode2) 1 else 2
            when (profile.backend) { else -> Unit }
            val ok = BackendKind.OpenCode2 // naming a value to build a profile is not a branch
            """.trimIndent()
        val hits = BuildRestGates.uiBoundary("feature/x/X.kt", planted)
        assertEquals(listOf(1, 3, 4), hits.map { it.line })
        assertEquals(1, BuildRestGates.uiBuildFile("feature/x/build.gradle.kts", """implementation(project(":protocol:oc2"))""").size)
    }

    @Test
    fun `B-BUILD-001 notifications are posted only from the reviewed posting paths, and the list only shrinks`() {
        val posters = everything.filter { (p, t) -> BuildRestGates.notificationPost(p, t).isNotEmpty() }.keys
        assertEquals(emptySet(), posters - BuildRestGates.NOTIFICATION_POSTERS.keys, "new posting path: route it through the alert shade")
        assertEquals(emptySet(), BuildRestGates.NOTIFICATION_POSTERS.keys - posters, "an entry no longer posts: remove it")
        assertTrue(BuildRestGates.NOTIFICATION_POSTERS.values.all { it.length > 20 }, "every entry gives a real reason")
    }

    // ---- B-BUILD-023 ----

    @Test
    fun `B-BUILD-023 features keep colours, motion values, dialogs and left-right layout in the kit`() {
        assertTrue(features.size > 100)
        assertNone(features.flatMap { (p, t) -> BuildRestGates.featureUi(p, t) }, "feature files breaking the kit-only rules")
    }

    @Test
    fun `B-BUILD-023 the kit never opens a URL itself`() {
        assertTrue(kits.size > 50)
        assertNone(kits.flatMap { (p, t) -> BuildRestGates.kitUrl(p, t) }, "kit files opening URLs")
    }

    @Test
    fun `B-BUILD-023 hard width caps in features are reviewed and the list only shrinks`() {
        val capped = features.filter { (p, t) -> BuildRestGates.widthCap(p, t).isNotEmpty() }.keys
        assertEquals(emptySet(), capped - BuildRestGates.WIDTH_CAPS.keys, "new hard width cap: use the kit frame")
        assertEquals(emptySet(), BuildRestGates.WIDTH_CAPS.keys - capped, "an entry is no longer needed: remove it")
        assertTrue(BuildRestGates.WIDTH_CAPS.values.all { it.length > 20 })
    }

    @Test
    fun `B-BUILD-023 planted colours, motion, dialogs, left-right layout and kit URLs are caught but comments are not`() {
        val planted =
            """
            val c = Color(0xFF112233)
            val m = tween(300)
            AlertDialog(onDismissRequest = {})
            Box(Modifier.align(Alignment.TopLeft))
            // Color(0xFF000000) in a comment
            val between = text.between("a", "b")
            Row(horizontalArrangement = Arrangement.Start)
            """.trimIndent()
        assertEquals(listOf(1, 2, 3, 4), BuildRestGates.featureUi("feature/x/X.kt", planted).map { it.line })
        assertEquals(1, BuildRestGates.kitUrl("ui/kit/X.kt", "LocalUriHandler.current.openUri(u)").size)
        assertEquals(1, BuildRestGates.widthCap("feature/x/X.kt", "Modifier.widthIn(max = 320.dp)").size)
    }

    // ---- B-BUILD-031 ----

    @Test
    fun `B-BUILD-031 the build targets the pinned SDK and Java levels and the version is a name plus a positive build number`() {
        val sdk = File(root, "build-logic/convention/src/main/kotlin/oc/buildlogic/Sdk.kt").readText()
        fun pin(name: String) = Regex("""const val $name = (\d+)""").find(sdk)?.groupValues?.get(1)?.toInt()
        assertEquals(37, pin("COMPILE"))
        assertEquals(1, pin("COMPILE_MINOR"))
        assertEquals(36, pin("TARGET"))
        assertEquals(26, pin("MIN"))
        assertEquals(17, pin("BUILD"), "module bytecode targets JDK 17")
        assertEquals(21, pin("TEST_LAUNCHER"))
        val version = File(root, "version.properties").readLines().filter { '=' in it && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        assertTrue(Regex("""\d+\.\d+\.\d+(-[a-z0-9.]+)?""").matches(version.getValue("versionName")), "versionName ${version["versionName"]}")
        val code = version.getValue("versionCode").toInt()
        assertTrue(code >= 10000, "versionCode never goes below the switch base 10000")
    }

    @Test
    fun `B-BUILD-031 every CI workflow that runs Gradle pins the same JDK`() {
        val workflows = File(root, "../.github/workflows").listFiles().orEmpty().filter { it.extension == "yml" }
        assertTrue(workflows.isNotEmpty())
        val javas = workflows.flatMap { w -> Regex("""java-version:\s*"?(\d+)"?""").findAll(w.readText()).map { w.name to it.groupValues[1] } }
        assertTrue(javas.isNotEmpty())
        assertEquals(setOf("17"), javas.map { it.second }.toSet(), "workflows pin different JDKs: $javas")
    }

    // ---- B-BUILD-033 ----

    private val routeFiles by lazy { ui.filterKeys { it.startsWith("ui/routes/") } }

    @Test
    fun `B-BUILD-033 every page has a home - each route is drawn by an entry installer`() {
        val routes = BuildRestGates.routes(routeFiles)
        assertTrue(routes.size > 25, "found ${routes.size} routes")
        val entries = BuildRestGates.entries(BuildRestGates.production(root, "feature", "ui", "androidApp"))
        val homeless = routes.filterNot { BuildRestGates.hasEntry(it, entries) }
        assertEquals(emptyList(), homeless, "routes no EntryInstaller draws")
    }

    @Test
    fun `B-BUILD-033 route ids are unique and every edge lands on a page`() {
        val routes = BuildRestGates.routes(routeFiles)
        val ids = routes.map { it.toString() }
        assertEquals(ids.distinct().sorted(), ids.sorted(), "duplicate route ids")
        val edges = BuildRestGates.edges(everything)
        assertTrue(edges.isNotEmpty())
        val known = routes.map { it.toString() }.toSet()
        val dangling = edges.filter { (_, r) -> r.toString() !in known }
        assertEquals(emptyList(), dangling, "open() targets that are not pages")
    }

    @Test
    fun `B-BUILD-033 planted homeless routes and dangling edges are caught`() {
        val files = mapOf("ui/routes/R.kt" to "sealed interface XRoute : AppRoute {\n data class A(val id: String = f(1)) : XRoute\n data object B : XRoute\n}")
        val routes = BuildRestGates.routes(files)
        assertEquals(listOf("XRoute.A", "XRoute.B"), routes.map { it.toString() })
        assertEquals(listOf("XRoute.B"), routes.filterNot { BuildRestGates.hasEntry(it, setOf("XRoute.A")) }.map { it.toString() })
        val edges = BuildRestGates.edges(mapOf("f.kt" to "nav.open(XRoute.C)"))
        assertEquals("XRoute.C", edges.single().second.toString())
    }
}

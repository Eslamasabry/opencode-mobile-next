package io.github.eslamasabry.oc.tools.rules.data

import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * The repository-file and data checks of the guard tests, as one plain task (`ocDataChecks`) on the compile check path.
 * Each finding says what is wrong and the way forward. Replaces, check by check:
 *  - ModuleOwnersTest: MODULE_OWNERS rows equal the Gradle projects;
 *  - TestSourceRulesTest (first test): no feature or ui build file hides tests or borrows another module's sources;
 *  - Oc2FixtureManifestTest: every OpenCode 2 fixture equals its SHA-256 manifest line and nothing is unlisted;
 *  - AgentPinsParityTest (first test): the generated Kotlin pins equal the pins JSON byte for byte.
 * The tests stay; this is the same fact checked earlier, with a plain message.
 */
fun main(args: Array<String>) {
    val root = File(args.firstOrNull() ?: error("usage: DataChecks <native root>"))
    val findings = checks(root).flatMap { (name, run) -> run().map { "[$name] $it" } }
    if (findings.isEmpty()) {
        println("ocDataChecks: ${checks(root).size} checks passed")
        return
    }
    System.err.println("ocDataChecks: ${findings.size} problem(s) in repository files:")
    findings.forEach { System.err.println("  $it") }
    exitProcess(1)
}

internal fun checks(root: File): List<Pair<String, () -> List<String>>> =
    listOf(
        "module-owners" to { moduleOwners(root) },
        "test-sources" to { testSources(root) },
        "oc2-fixtures" to { oc2Fixtures(root) },
        "agent-pins" to { agentPins(root) },
    )

internal fun moduleOwners(root: File): List<String> {
    val discovered =
        listOf("core", "protocol", "engine", "ui", "feature", "android", "tools").flatMap { layer ->
            File(root, layer).listFiles().orEmpty()
                .filter { File(it, "build.gradle.kts").isFile }
                .map { ":$layer:${it.name}" }
        } + listOf("androidApp", "desktopApp", "quality").filter { File(root, "$it/build.gradle.kts").isFile }.map { ":$it" }
    val rows =
        File(root, "MODULE_OWNERS").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t').first().trim() }
    val missing = discovered.toSet() - rows.toSet()
    val stale = rows.toSet() - discovered.toSet()
    return missing.sorted().map { "$it is a Gradle module with no row in MODULE_OWNERS: ask the coordinator to add its row (path, kind, owner, since)." } +
        stale.sorted().map { "MODULE_OWNERS names $it, but no such module exists: remove the row, or restore the module's build.gradle.kts." }
}

private val exclude = Regex("""\bexclude\s*[({]""")
private val borrowed = Regex("""\bsrcDirs?\s*\(\s*"(\.\./|[^"]*ui/gate)""")

internal fun testSources(root: File): List<String> =
    listOf("feature", "ui").flatMap { layer -> File(root, layer).listFiles().orEmpty().map { File(it, "build.gradle.kts") } }
        .filter { it.isFile }
        .flatMap { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            file.readLines().mapIndexedNotNull { i, raw ->
                val line = raw.substringBefore("//")
                val what =
                    when {
                        exclude.containsMatchIn(line) && "kotlin." in line -> "a source-set exclude hides files from the build"
                        exclude.containsMatchIn(line) && "test" in line.lowercase() -> "an exclude names tests"
                        borrowed.containsMatchIn(line) -> "a srcDir borrows another module's sources"
                        else -> null
                    }
                what?.let { "$path:${i + 1}: $it. Tests must compile and run: remove it (for the golden gate, depend on :ui:gate)." }
            }
        }

private fun sha256(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

internal fun oc2Fixtures(root: File): List<String> {
    val dir = File(root, "fixtures/oc2")
    val manifestFile = File(dir, "MANIFEST.sha256")
    if (!manifestFile.isFile) return listOf("fixtures/oc2/MANIFEST.sha256 is missing: restore it from the line.")
    val manifest =
        manifestFile.readLines().filter { it.isNotBlank() }.associate { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            parts[1].removePrefix("*") to parts[0]
        }
    val problems = mutableListOf<String>()
    for ((path, hash) in manifest) {
        val f = File(dir, path)
        when {
            !f.isFile -> problems += "fixtures/oc2/$path is listed in MANIFEST.sha256 but missing: restore the file, or delete its line."
            sha256(f) != hash -> problems += "fixtures/oc2/$path no longer matches its SHA-256 line: the fixture is a copied server answer; restore it, or update the line on purpose."
        }
    }
    val onDisk =
        File(dir, "flutter").walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.toSet()
    (onDisk - manifest.keys).sorted().forEach { problems += "fixtures/oc2/$it is not in MANIFEST.sha256: add its hash line, or delete the file." }
    return problems
}

internal fun agentPins(root: File): List<String> {
    val json = File(root, "engine/agents/pins/agent-pins.json")
    val kotlin = File(root, "engine/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/agents/catalog/AgentPinsJson.kt")
    if (!json.isFile || !kotlin.isFile) return listOf("the agent pins file or its Kotlin copy is missing.")
    val text = kotlin.readText()
    val start = text.indexOf("\"\"\"")
    val end = text.lastIndexOf("\"\"\"")
    if (start < 0 || end <= start) return listOf("AgentPinsJson.kt holds no raw string; it must be a byte-for-byte copy of agent-pins.json.")
    val copy = text.substring(start + 3, end)
    return if (copy == json.readText()) emptyList()
    else listOf("AgentPinsJson.kt differs from pins/agent-pins.json. The JSON is the source of truth: paste it into the Kotlin file whole (a pin moves only with an app release).")
}

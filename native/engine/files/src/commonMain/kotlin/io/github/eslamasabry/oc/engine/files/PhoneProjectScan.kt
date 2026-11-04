package io.github.eslamasabry.oc.engine.files

import kotlin.time.Clock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

public enum class PhoneProjectKind {
    Dart,
    Node,
    Python,
    Rust,
    Go,
    Java,
    Ruby,
    Php,
    Dotnet,
    Cpp,
    Git,
    Folder,
}

public data class PhoneProject(val path: String, val kind: PhoneProjectKind, val hasGit: Boolean)

public data class ScanEntry(val name: String, val directory: Boolean, val symlink: Boolean = false)

public fun interface ProjectScanFiles {
    public suspend fun list(path: String): List<ScanEntry>?
}

public data class ProjectScanRoot(
    val path: String,
    val reportAs: String = path,
    val childrenAreProjects: Boolean = false,
)

public enum class ProjectScanEnd {
    Completed,
    TimedOut,
    Limited,
}

public data class ProjectScanResult(
    val projects: List<PhoneProject>,
    val visited: Int,
    val end: ProjectScanEnd,
)

/**
 * Breadth-first discovery. A single budget covers roots in order; cancellation stops the whole
 * sequence.
 */
public class PhoneProjectScanner(private val files: ProjectScanFiles, private val clock: Clock) {
    public suspend fun scan(
        roots: List<ProjectScanRoot>,
        timeLimitMs: Long = 10_000,
        maxVisited: Int = 20_000,
        maxResults: Int = 200,
        maxDepth: Int = 5,
    ): ProjectScanResult {
        val started = clock.now()
        val found = ArrayList<PhoneProject>()
        var visited = 0
        fun result(end: ProjectScanEnd): ProjectScanResult =
            ProjectScanResult(found.toList(), visited, end)
        for (root in roots) {
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.add(root.path to 0)
            while (queue.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                if ((clock.now() - started).inWholeMilliseconds >= timeLimitMs)
                    return result(ProjectScanEnd.TimedOut)
                if (visited >= maxVisited || found.size >= maxResults)
                    return result(ProjectScanEnd.Limited)
                val (path, depth) = queue.removeFirst()
                visited++
                val entries = files.list(path) ?: continue
                currentCoroutineContext().ensureActive()
                val names = entries.map { it.name }.toSet()
                val kind = classify(names)
                if (depth > 0 && (kind != null || (root.childrenAreProjects && depth == 1))) {
                    found +=
                        PhoneProject(
                            root.reportAs + path.removePrefix(root.path),
                            kind ?: PhoneProjectKind.Folder,
                            ".git" in names,
                        )
                    if (found.size >= maxResults) return result(ProjectScanEnd.Limited)
                    continue
                }
                if (depth >= maxDepth) continue
                entries
                    .asSequence()
                    .filter { it.directory && !it.symlink && !it.name.startsWith('.') }
                    .filter { (root.childrenAreProjects && depth == 0) || it.name !in skipped }
                    .sortedBy { it.name.lowercase() }
                    .forEach { queue.add(path.trimEnd('/') + "/" + it.name to depth + 1) }
            }
        }
        return result(ProjectScanEnd.Completed)
    }

    public companion object {
        private val markers =
            linkedMapOf(
                "pubspec.yaml" to PhoneProjectKind.Dart,
                "package.json" to PhoneProjectKind.Node,
                "pyproject.toml" to PhoneProjectKind.Python,
                "requirements.txt" to PhoneProjectKind.Python,
                "Cargo.toml" to PhoneProjectKind.Rust,
                "go.mod" to PhoneProjectKind.Go,
                "pom.xml" to PhoneProjectKind.Java,
                "build.gradle" to PhoneProjectKind.Java,
                "build.gradle.kts" to PhoneProjectKind.Java,
                "Gemfile" to PhoneProjectKind.Ruby,
                "composer.json" to PhoneProjectKind.Php,
                "CMakeLists.txt" to PhoneProjectKind.Cpp,
                "Makefile" to PhoneProjectKind.Cpp,
            )
        private val skipped =
            setOf("Android", "node_modules", "build", "venv", "__pycache__", "target", "dist")

        public fun classify(names: Set<String>): PhoneProjectKind? =
            markers.entries.firstOrNull { it.key in names }?.value
                ?: if (names.any { it.endsWith(".sln") || it.endsWith(".csproj") })
                    PhoneProjectKind.Dotnet
                else if (".git" in names) PhoneProjectKind.Git else null
    }
}

package io.github.eslamasabry.oc.engine.project.scan

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/*
 * The "Find projects" scanner (port of lib/platform/phone_project_scan.dart): breadth first over the phone's storage,
 * a few levels down, never into a project it found. The filesystem is a port ([ScanFileSystem]); the clock is an
 * injected [TimeSource] so caps are testable on virtual time.
 */

/** What a project on the phone is mostly made of, from the file that gave it away. [Git] is a repository with none of the others. */
public enum class PhoneProjectKind { Dart, Node, Python, Rust, Go, Java, Ruby, Php, Dotnet, Cpp, Git }

/** A project found on the phone's storage. */
public data class PhoneProject(val name: String, val path: String, val kind: PhoneProjectKind, val hasGit: Boolean)

/** Why a scan stopped. */
public enum class PhoneScanEnd {
    /** Every folder within the depth limit was looked at. */
    Completed,

    /** The time cap came first; looking longer may find more. */
    TimedOut,

    /** Too many folders or results; the list is the first part. */
    Limited,

    /** Stopped by [PhoneProjectScan.cancel]. */
    Cancelled,
}

/** One entry of a folder listing. A link is never a folder (links are not followed). */
public data class ScanEntry(val name: String, val kind: ScanEntryKind)

public enum class ScanEntryKind { Folder, File, Link }

/** The filesystem the scanner reads. Reads only. */
public fun interface ScanFileSystem {
    /** The entries of [path] without following links, or null when the folder cannot be read. */
    public suspend fun list(path: String): List<ScanEntry>?
}

/**
 * One scan: [run] looks and hands each project to its callback as it is found, then says why it stopped. A scan runs
 * once; [cancel] is safe before, during and after the run.
 */
public interface PhoneProjectScan {
    /** Folders looked at so far. */
    public val visited: Int

    /** True once [cancel] was called. */
    public val cancelled: Boolean

    public fun cancel()

    public suspend fun run(found: (PhoneProject) -> Unit): PhoneScanEnd

    public companion object {
        /**
         * A scan that runs [starts] one after the other (the first one's results come first), sharing one [limit].
         * Cancel stops the one running; the end is the first that was not [PhoneScanEnd.Completed].
         */
        public fun sequence(
            starts: List<(Duration) -> PhoneProjectScan>,
            limit: Duration,
            timeSource: TimeSource,
        ): PhoneProjectScan = SequenceScan(starts, limit, timeSource)

        /** A scan that is a list found elsewhere (a server's folder listing). A failure ends it quietly, with nothing found. */
        public fun fromLoad(load: suspend () -> Outcome<List<PhoneProject>>): PhoneProjectScan = LoadedScan(load)
    }
}

/** The kind of project [names] (the entries of one folder) make it, or null when it is not a project. */
public data class PhoneProjectClass(val kind: PhoneProjectKind, val git: Boolean)

public class PhoneProjectScanner(
    private val fileSystem: ScanFileSystem,
    public val root: String,
    private val timeSource: TimeSource,
    public val maxDepth: Int = 5,
    public val maxVisited: Int = 20_000,
    public val maxResults: Int = 200,
    /** The path results carry in place of [root] (the project space is read from files but shown as `/root/projects`). */
    public val reportAs: String? = null,
    /** Every folder straight in [root] is a project, whatever it holds. Markers still name its kind. */
    public val childrenAreProjects: Boolean = false,
) {
    /** Starts nothing until [PhoneProjectScan.run]; [timeLimit] is the whole scan's cap, measured from the run's start. */
    public fun start(timeLimit: Duration = 10.seconds): PhoneProjectScan = TreeScan(timeLimit)

    private inner class TreeScan(private val timeLimit: Duration) : PhoneProjectScan {
        @Volatile override var visited: Int = 0
            private set

        @Volatile override var cancelled: Boolean = false
            private set

        override fun cancel() {
            cancelled = true
        }

        override suspend fun run(found: (PhoneProject) -> Unit): PhoneScanEnd {
            val started = timeSource.markNow()
            var count = 0
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.addLast(root to 0)
            while (queue.isNotEmpty()) {
                if (cancelled) return PhoneScanEnd.Cancelled
                if (started.elapsedNow() >= timeLimit) return PhoneScanEnd.TimedOut
                if (visited >= maxVisited) return PhoneScanEnd.Limited
                val (path, depth) = queue.removeFirst()
                visited++
                val entries = fileSystem.list(path) ?: continue // Unreadable: skipped silently.
                if (path != root) {
                    val project =
                        classify(entries.map { it.name })
                            ?: if (childrenAreProjects && depth == 1) PhoneProjectClass(PhoneProjectKind.Git, false) else null
                    if (project != null) {
                        found(
                            PhoneProject(
                                name = path.substringAfterLast('/'),
                                path = if (reportAs == null) path else reportAs + path.substring(root.length),
                                kind = project.kind,
                                hasGit = project.git,
                            ),
                        )
                        count++
                        if (count >= maxResults) return PhoneScanEnd.Limited
                        continue // Never into a project.
                    }
                }
                if (depth >= maxDepth) continue
                val folders = entries.filter { it.kind == ScanEntryKind.Folder }.map { it.name }.sortedBy { it.lowercase() }
                for (name in folders) {
                    if (name.startsWith(".")) continue
                    if (name in SKIPPED && !(childrenAreProjects && depth == 0)) continue
                    queue.addLast("$path/$name" to depth + 1)
                }
            }
            return if (cancelled) PhoneScanEnd.Cancelled else PhoneScanEnd.Completed
        }
    }

    public companion object {
        /** Files (or folders) that make the folder holding them a project, in the order that decides its kind. */
        public val MARKERS: List<Pair<String, PhoneProjectKind>> =
            listOf(
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

        /** Folders that are never worth going into. */
        public val SKIPPED: Set<String> =
            setOf("Android", "node_modules", "build", ".dart_tool", "venv", ".venv", "__pycache__", "target", "dist")

        public fun classify(names: Iterable<String>): PhoneProjectClass? {
            val set = names.toSet()
            val git = ".git" in set
            val kind =
                MARKERS.firstOrNull { it.first in set }?.second
                    ?: if (set.any { it.endsWith(".sln") || it.endsWith(".csproj") }) PhoneProjectKind.Dotnet
                    else if (git) PhoneProjectKind.Git
                    else null
            return kind?.let { PhoneProjectClass(it, git) }
        }
    }
}

private class SequenceScan(
    private val starts: List<(Duration) -> PhoneProjectScan>,
    private val limit: Duration,
    private val timeSource: TimeSource,
) : PhoneProjectScan {
    @Volatile private var current: PhoneProjectScan? = null
    @Volatile private var before = 0

    @Volatile override var cancelled: Boolean = false
        private set

    override val visited: Int get() = before + (current?.visited ?: 0)

    override fun cancel() {
        cancelled = true
        current?.cancel()
    }

    override suspend fun run(found: (PhoneProject) -> Unit): PhoneScanEnd {
        val started = timeSource.markNow()
        for (start in starts) {
            if (cancelled) return PhoneScanEnd.Cancelled
            val left = limit - started.elapsedNow()
            if (left <= Duration.ZERO) return PhoneScanEnd.TimedOut
            val scan = start(left)
            current = scan
            if (cancelled) scan.cancel()
            val reason = scan.run(found)
            before += scan.visited
            current = null
            if (reason != PhoneScanEnd.Completed) return reason
        }
        return PhoneScanEnd.Completed
    }
}

private class LoadedScan(private val load: suspend () -> Outcome<List<PhoneProject>>) : PhoneProjectScan {
    @Volatile override var visited: Int = 0
        private set

    @Volatile override var cancelled: Boolean = false
        private set

    override fun cancel() {
        cancelled = true
    }

    override suspend fun run(found: (PhoneProject) -> Unit): PhoneScanEnd {
        val projects = (load() as? Outcome.Ok)?.value.orEmpty() // Nothing found there; other places are still searched.
        for (project in projects) {
            if (cancelled) break
            found(project)
            visited++
        }
        return if (cancelled) PhoneScanEnd.Cancelled else PhoneScanEnd.Completed
    }
}

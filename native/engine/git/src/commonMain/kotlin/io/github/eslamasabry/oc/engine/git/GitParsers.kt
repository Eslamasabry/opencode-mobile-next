package io.github.eslamasabry.oc.engine.git

import kotlinx.collections.immutable.toImmutableList

// Parsers for Git's machine-readable output (port of lib/domain/git/git_parsers.dart). Pure functions over strings.

/** The `log` format [parseGitLog] reads: fields split by US (0x1f), commits split by NUL (`git log -z`). */
public const val GIT_LOG_FORMAT: String = "%H%x1f%h%x1f%an%x1f%ae%x1f%at%x1f%P%x1f%D%x1f%s"

/** The `for-each-ref` format [parseGitBranches] reads (`%1f` is US). */
public const val GIT_BRANCH_FORMAT: String =
    "%(refname)%1f%(objectname:short)%1f%(upstream:short)%1f%(upstream:track,nobracket)%1f%(HEAD)%1f" +
        "%(committerdate:unix)%1f%(contents:subject)"

private const val NUL = '\u0000'
private const val US = '\u001f'

/** `git status --porcelain=v2 --branch -z`. */
public fun parseGitStatus(output: String): GitStatus {
    val records = output.split(NUL)
    var head: String? = null
    var commit: String? = null
    var upstream: String? = null
    var ahead = 0
    var behind = 0
    val files = ArrayList<GitFileChange>()
    var i = 0
    while (i < records.size) {
        val record = records[i]
        i++
        if (record.isEmpty()) continue
        if (record.startsWith("# ")) {
            val header = record.substring(2)
            val space = header.indexOf(' ')
            if (space < 0) continue
            val value = header.substring(space + 1)
            when (header.substring(0, space)) {
                "branch.oid" -> commit = if (value == "(initial)") null else value
                "branch.head" -> head = if (value == "(detached)") null else value
                "branch.upstream" -> upstream = value
                "branch.ab" ->
                    for (part in value.split(' ')) {
                        if (part.startsWith("+")) ahead = part.substring(1).toIntOrNull() ?: 0
                        if (part.startsWith("-")) behind = part.substring(1).toIntOrNull() ?: 0
                    }
            }
            continue
        }
        when (record[0]) {
            '1' -> {
                // 1 XY sub mH mI mW hH hI path
                val f = splitFields(record, 9) ?: continue
                files += GitFileChange(
                    path = f[8],
                    staged = kindOf(f[1][0]),
                    unstaged = kindOf(f[1][1]),
                    submodule = f[2].startsWith("S"),
                )
            }
            '2' -> {
                // 2 XY sub mH mI mW hH hI Xscore path, then origPath as the next record.
                val f = splitFields(record, 10) ?: continue
                val original = if (i < records.size) records[i++] else null
                files += GitFileChange(
                    path = f[9],
                    originalPath = original?.takeIf { it.isNotEmpty() },
                    staged = kindOf(f[1][0]),
                    unstaged = kindOf(f[1][1]),
                    submodule = f[2].startsWith("S"),
                )
            }
            'u' -> {
                // u XY sub m1 m2 m3 mW h1 h2 h3 path
                val f = splitFields(record, 11) ?: continue
                files += GitFileChange(path = f[10], conflict = conflictOf(f[1]), submodule = f[2].startsWith("S"))
            }
            '?' -> if (record.length > 2) files += GitFileChange(record.substring(2), unstaged = GitChangeKind.Untracked)
            '!' -> if (record.length > 2) files += GitFileChange(record.substring(2), unstaged = GitChangeKind.Ignored)
        }
    }
    return GitStatus(GitBranchState(head, commit, upstream, ahead, behind), files.toImmutableList())
}

/** [record] split on its first `count - 1` spaces, so the last field (a path) keeps its own spaces. */
private fun splitFields(record: String, count: Int): List<String>? {
    val fields = ArrayList<String>(count)
    var start = 0
    repeat(count - 1) {
        val space = record.indexOf(' ', start)
        if (space < 0) return null
        fields += record.substring(start, space)
        start = space + 1
    }
    fields += record.substring(start)
    if (fields[1].length != 2 || fields.last().isEmpty()) return null
    return fields
}

private fun kindOf(code: Char): GitChangeKind? =
    when (code) {
        'M' -> GitChangeKind.Modified
        'A' -> GitChangeKind.Added
        'D' -> GitChangeKind.Deleted
        'R' -> GitChangeKind.Renamed
        'C' -> GitChangeKind.Copied
        'T' -> GitChangeKind.TypeChanged
        else -> null
    }

private fun conflictOf(xy: String): GitConflictKind =
    when (xy) {
        "DD" -> GitConflictKind.BothDeleted
        "AU" -> GitConflictKind.AddedByUs
        "UD" -> GitConflictKind.DeletedByThem
        "UA" -> GitConflictKind.AddedByThem
        "DU" -> GitConflictKind.DeletedByUs
        "AA" -> GitConflictKind.BothAdded
        else -> GitConflictKind.BothModified
    }

/** One `git diff --numstat -z` entry. Counts are null for binary files. */
public data class GitNumstat(val path: String, val originalPath: String? = null, val added: Int? = null, val deleted: Int? = null) {
    val binary: Boolean get() = added == null && deleted == null
}

/** `git diff [--cached] --numstat -z`: `A\tD\tpath\0`, or for a rename `A\tD\t\0old\0new\0`. */
public fun parseGitNumstat(output: String): List<GitNumstat> {
    val records = output.split(NUL)
    val entries = ArrayList<GitNumstat>()
    var i = 0
    while (i < records.size) {
        val record = records[i]
        i++
        if (record.isEmpty()) continue
        val first = record.indexOf('\t')
        val second = if (first < 0) -1 else record.indexOf('\t', first + 1)
        if (second < 0) continue
        val added = record.substring(0, first).toIntOrNull()
        val deleted = record.substring(first + 1, second).toIntOrNull()
        val path = record.substring(second + 1)
        if (path.isNotEmpty()) {
            entries += GitNumstat(path, added = added, deleted = deleted)
        } else if (i + 1 < records.size) {
            val original = records[i]
            val renamed = records[i + 1]
            i += 2
            entries += GitNumstat(renamed, original, added, deleted)
        }
    }
    return entries
}

/** [status] files with line counts from the two numstat runs. */
public fun withGitNumstat(status: GitStatus, unstaged: List<GitNumstat> = emptyList(), staged: List<GitNumstat> = emptyList()): GitStatus {
    val work = unstaged.associateBy { it.path }
    val index = staged.associateBy { it.path }
    return GitStatus(
        status.branch,
        status.files
            .map { file ->
                val w = work[file.path]
                val s = index[file.path]
                file.copy(
                    addedLines = w?.added ?: file.addedLines,
                    deletedLines = w?.deleted ?: file.deletedLines,
                    stagedAddedLines = s?.added ?: file.stagedAddedLines,
                    stagedDeletedLines = s?.deleted ?: file.stagedDeletedLines,
                    binary = (w?.binary ?: false) || (s?.binary ?: false) || file.binary,
                )
            }
            .toImmutableList(),
    )
}

/** `git log -z --format=<GIT_LOG_FORMAT>`. */
public fun parseGitLog(output: String): List<GitCommit> {
    val commits = ArrayList<GitCommit>()
    for (record in output.split(NUL)) {
        val trimmed = if (record.startsWith("\n")) record.substring(1) else record
        if (trimmed.isEmpty()) continue
        val f = trimmed.split(US)
        if (f.size < 8) continue
        commits += GitCommit(
            id = f[0],
            shortId = f[1],
            authorName = f[2],
            authorEmail = f[3],
            timeEpochSeconds = f[4].toLongOrNull() ?: 0L,
            parents = (if (f[5].isEmpty()) emptyList() else f[5].split(' ')).toImmutableList(),
            refs = (if (f[6].isEmpty()) emptyList() else f[6].split(", ")).toImmutableList(),
            subject = f.subList(7, f.size).joinToString(US.toString()).trimEnd(),
        )
    }
    return commits
}

/** `git for-each-ref --format=<GIT_BRANCH_FORMAT> refs/heads refs/remotes`. A remote's `HEAD` is skipped. */
public fun parseGitBranches(output: String): GitBranches {
    val local = ArrayList<GitBranch>()
    val remote = ArrayList<GitBranch>()
    for (line in output.split('\n')) {
        if (line.isEmpty()) continue
        val f = line.split(US)
        if (f.size < 7) continue
        val ref = f[0]
        val track = parseTrack(f[3])
        val time = f[5].toLongOrNull()
        val subject = f.subList(6, f.size).joinToString(US.toString()).takeIf { it.isNotEmpty() }
        val commit = f[1].takeIf { it.isNotEmpty() }
        if (ref.startsWith("refs/heads/")) {
            local += GitBranch(
                name = ref.removePrefix("refs/heads/"),
                remote = false,
                commit = commit,
                upstream = f[2].takeIf { it.isNotEmpty() },
                ahead = track.ahead,
                behind = track.behind,
                upstreamGone = track.gone,
                current = f[4] == "*",
                timeEpochSeconds = time,
                subject = subject,
            )
        } else if (ref.startsWith("refs/remotes/")) {
            val name = ref.removePrefix("refs/remotes/")
            val slash = name.indexOf('/')
            if (slash <= 0 || name.endsWith("/HEAD")) continue
            remote += GitBranch(
                name = name,
                remote = true,
                remoteName = name.substring(0, slash),
                commit = commit,
                timeEpochSeconds = time,
                subject = subject,
            )
        }
    }
    return GitBranches(local.toImmutableList(), remote.toImmutableList())
}

private data class Track(val ahead: Int, val behind: Int, val gone: Boolean)

private fun parseTrack(value: String): Track {
    if (value == "gone") return Track(0, 0, true)
    var ahead = 0
    var behind = 0
    for (part in value.split(", ")) {
        if (part.startsWith("ahead ")) ahead = part.substring(6).toIntOrNull() ?: 0
        if (part.startsWith("behind ")) behind = part.substring(7).toIntOrNull() ?: 0
    }
    return Track(ahead, behind, false)
}

/** The marker files the snapshot script prints, one per line, for the operation left half-done. */
public fun parseGitOperation(output: String): GitOperation {
    val markers = output.split('\n').map { it.trim() }.toSet()
    return when {
        "rebase-merge" in markers || "rebase-apply" in markers || "REBASE_HEAD" in markers -> GitOperation.Rebase
        "MERGE_HEAD" in markers -> GitOperation.Merge
        "CHERRY_PICK_HEAD" in markers -> GitOperation.CherryPick
        "REVERT_HEAD" in markers -> GitOperation.Revert
        else -> GitOperation.None
    }
}

/** `git config --get-regexp '^remote\..*\.url$'` lines, addresses already stripped of user info by the script. */
public fun parseGitRemotes(output: String): List<GitRemote> {
    val remotes = LinkedHashMap<String, GitRemote>()
    for (line in output.split('\n')) {
        val space = line.indexOf(' ')
        if (space < 0) continue
        val key = line.substring(0, space)
        val url = line.substring(space + 1).trim()
        if (!key.startsWith("remote.") || !key.endsWith(".url")) continue
        val name = key.substring("remote.".length, key.length - ".url".length)
        if (name.isEmpty() || name in remotes) continue
        remotes[name] = GitRemote(name, gitRemoteKind(url), if (url.isEmpty()) null else stripGitUserInfo(url))
    }
    return remotes.values.toList()
}

private val USER_INFO = Regex("://[^/@\\s]*@")

/** [url] without the user info of a `scheme://user:secret@host` address. An scp-style `git@host:path` keeps it. */
public fun stripGitUserInfo(url: String): String = USER_INFO.replaceFirst(url, "://")

public fun gitRemoteKind(url: String): GitRemoteKind {
    val value = url.trim().lowercase()
    if (value.startsWith("git@github.com:") || value.startsWith("ssh://git@github.com/") ||
        value.startsWith("ssh://github.com/")
    ) {
        return GitRemoteKind.GitHubSsh
    }
    if (value.startsWith("https://")) {
        val rest = value.removePrefix("https://")
        val hostPort = rest.substringBefore('/').substringAfterLast('@')
        val host = hostPort.substringBefore(':')
        if (host == "github.com" || host == "www.github.com") return GitRemoteKind.GitHubHttps
    }
    return GitRemoteKind.Other
}

/** The two lines `printf '%s\n%s\n' name email` printed. */
public fun parseGitIdentity(output: String): GitIdentity {
    val lines = output.split('\n')
    return GitIdentity(lines.getOrElse(0) { "" }.trim(), lines.getOrElse(1) { "" }.trim())
}

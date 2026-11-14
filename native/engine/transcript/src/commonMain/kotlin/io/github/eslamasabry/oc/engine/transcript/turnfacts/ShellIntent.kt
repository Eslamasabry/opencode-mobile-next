package io.github.eslamasabry.oc.engine.transcript.turnfacts

/**
 * What a shell step did, judged from its command line before any words (owner 2026-11-14: eleven command rows that all
 * read `cd /home/…; . /home/…/.toolchain/env.sh; …` and so read the same). The words come from [kind] in the chat's
 * strings ("Ran tests", "Searched files", "Read notes.md"); the command itself stays in the step's opened detail.
 *
 * [arg] is the one name a kind carries: the file a [ShellKind.ReadOne] read, the program a [ShellKind.Program] ran.
 */
public data class ShellIntent(val kind: ShellKind, val arg: String? = null)

/** The kinds of shell work a step row names in plain words. */
public enum class ShellKind {
    /** A test runner ran: "Ran tests". */
    Tests,
    /** grep, rg, find and the like: "Searched files". */
    Search,
    /** One named file was read (cat, head, tail, sed -n): "Read notes.md"; [ShellIntent.arg] is its name. */
    ReadOne,
    /** One file whose name is an id, or a stream: "Read a file". */
    ReadFile,
    /** Several files were read: "Read files". */
    ReadMany,
    /** ls, tree, du: "Listed files". */
    List,
    /** ps, pgrep: "Checked running processes". */
    Processes,
    /** kill, pkill: "Stopped a process". */
    StopProcess,
    /** git status, diff, log, show: "Checked the changes". */
    CheckChanges,
    /** A linter or analyzer: "Checked the code". */
    Analyze,
    /** A build: "Built the project". */
    Build,
    /** A package install: "Installed packages". */
    Install,
    /** curl, wget: "Fetched a web page". */
    Fetch,
    /** rm: "Deleted files". */
    Delete,
    /** mkdir, cp, mv, touch, chmod: "Changed files". */
    ChangeFiles,
    /** Only a sleep: "Waited". */
    Wait,
    /** Any other program, named in [ShellIntent.arg]: "Ran adb". */
    Program,
    /** Nothing a person would recognise: "Ran a command". */
    Unknown,
}

/**
 * [command] in plain terms. The set-up a command line opens with is skipped before judging what ran: `cd <dir>`, a
 * toolchain or environment sourced (`. env.sh`, `source …`), `export`/`set`, `VAR=value` before a program, `sudo`,
 * `time`, `nohup`, `timeout <n>`; a quiet `echo`, `printf` or `sleep` gives way to the real work after it. `bash -c
 * '…'` is judged by what it runs. Within a pipeline the first program counts (`grep … | head` searched).
 */
public fun shellIntent(command: String?): ShellIntent {
    val text = command?.trim().orEmpty()
    if (text.isEmpty()) return ShellIntent(ShellKind.Unknown)
    var quiet: ShellIntent? = null
    for (segment in shellSegments(text)) {
        val words = shellWords(shellPipeline(segment).firstOrNull() ?: continue)
        val core = withoutPrefixes(words)
        if (core.isEmpty()) continue
        val program = programName(core[0])
        if (program in SETUP) continue
        if (program in QUIET) {
            if (quiet == null && program == "sleep") quiet = ShellIntent(ShellKind.Wait)
            continue
        }
        if (program in SHELLS) {
            val inner = core.indexOfFirst { it == "-c" || it == "-lc" || it == "-ic" }
            if (inner >= 0 && inner + 1 < core.size) return shellIntent(core[inner + 1])
        }
        return classify(program, core.drop(1))
    }
    return quiet ?: ShellIntent(ShellKind.Unknown)
}

private fun classify(program: String, args: List<String>): ShellIntent {
    val sub = args.firstOrNull { !it.startsWith("-") }?.lowercase()
    fun intent(kind: ShellKind) = ShellIntent(kind)
    return when (program) {
        in TEST_RUNNERS -> intent(ShellKind.Tests)
        "flutter", "dart" ->
            when (sub) {
                "test" -> intent(ShellKind.Tests)
                "analyze", "format" -> intent(ShellKind.Analyze)
                "build" -> intent(ShellKind.Build)
                "pub" -> if (args.any { it == "get" || it == "add" || it == "upgrade" }) intent(ShellKind.Install) else named(program)
                else -> named(program)
            }
        "go", "cargo", "deno", "bun", "swift", "dotnet" ->
            when (sub) {
                "test" -> intent(ShellKind.Tests)
                "build" -> intent(ShellKind.Build)
                "vet", "clippy", "lint", "fmt" -> intent(ShellKind.Analyze)
                "add", "install", "get" -> intent(ShellKind.Install)
                else -> named(program)
            }
        "npm", "yarn", "pnpm", "npx" -> {
            val script = if (sub == "run") args.dropWhile { it.lowercase() != "run" }.drop(1).firstOrNull()?.lowercase() else sub
            when {
                script == null -> named(program)
                script.startsWith("test") || script in TEST_RUNNERS -> intent(ShellKind.Tests)
                script in INSTALL_WORDS -> intent(ShellKind.Install)
                script.startsWith("build") -> intent(ShellKind.Build)
                script.startsWith("lint") || script == "tsc" || script == "eslint" -> intent(ShellKind.Analyze)
                else -> named(program)
            }
        }
        "gradle", "gradlew", "mvn", "mvnw" ->
            when {
                args.any { GRADLE_TEST.containsMatchIn(it) } -> intent(ShellKind.Tests)
                args.any { GRADLE_CHECK.containsMatchIn(it) } -> intent(ShellKind.Analyze)
                args.any { GRADLE_BUILD.containsMatchIn(it) } -> intent(ShellKind.Build)
                else -> named("gradle")
            }
        "make", "ninja", "cmake" -> if (args.any { it == "test" || it == "check" }) intent(ShellKind.Tests) else intent(ShellKind.Build)
        "tsc", "eslint", "ruff", "flake8", "mypy", "ktlint", "detekt", "shellcheck", "pylint" -> intent(ShellKind.Analyze)
        "python", "python3", "py" ->
            when {
                args.size >= 2 && args[0] == "-m" && args[1] in TEST_RUNNERS -> intent(ShellKind.Tests)
                args.size >= 2 && args[0] == "-m" && args[1] == "pip" && "install" in args -> intent(ShellKind.Install)
                else -> args.firstOrNull { !it.startsWith("-") }?.let { named(fileName(it)) } ?: named(program)
            }
        "node" -> args.firstOrNull { !it.startsWith("-") }?.let { named(fileName(it)) } ?: named(program)
        "pip", "pip3", "uv", "poetry", "gem", "composer" -> if (sub in INSTALL_WORDS || sub == "sync") intent(ShellKind.Install) else named(program)
        "apt", "apt-get", "pkg", "brew", "dnf", "yum", "pacman", "apk" ->
            if (sub in INSTALL_WORDS || sub == "upgrade" || sub == "update") intent(ShellKind.Install) else named(program)
        in SEARCH -> intent(ShellKind.Search)
        // `sed -n '1,80p' notes.md` reads; `sed -i …` changes. The script is the first word that is not a flag.
        "sed", "awk" ->
            if (program == "sed" && args.any { it.startsWith("-i") }) intent(ShellKind.ChangeFiles)
            else read(args.filterNot { it.startsWith("-") }.drop(1))
        in READ -> read(args)
        in LIST -> intent(ShellKind.List)
        in PROCESSES -> intent(ShellKind.Processes)
        in STOP -> intent(ShellKind.StopProcess)
        "git" ->
            when (sub) {
                "status", "diff", "log", "show", "branch", "blame", "shortlog", "reflog" -> intent(ShellKind.CheckChanges)
                "grep" -> intent(ShellKind.Search)
                null -> named("git")
                else -> named("git $sub")
            }
        in FETCH -> intent(ShellKind.Fetch)
        in DELETE -> intent(ShellKind.Delete)
        in CHANGE -> intent(ShellKind.ChangeFiles)
        else -> named(program)
    }
}

/** A read of [args]' files: one named file, one unnamed (an id, a stream), or several. */
private fun read(args: List<String>): ShellIntent {
    val files = ArrayList<String>()
    var skipNext = false
    for (a in args) {
        if (skipNext) {
            skipNext = false
            continue
        }
        if (a.startsWith("-")) {
            // "-n 20", "-c 80": a flag whose number follows it.
            if (a.length == 2 && a[1] in "nc") skipNext = true
            continue
        }
        if (a.all { it.isDigit() } || a.startsWith("'") || a.startsWith("\"") || a.startsWith("s/")) continue
        files += a
    }
    return when {
        files.size > 1 -> ShellIntent(ShellKind.ReadMany)
        files.isEmpty() -> ShellIntent(ShellKind.ReadFile)
        else -> {
            val name = fileName(files.single())
            if (name.isEmpty() || name.length > MAX_NAME || looksLikeAnId(name) || name.any { it == '*' || it == '$' }) ShellIntent(ShellKind.ReadFile)
            else ShellIntent(ShellKind.ReadOne, name)
        }
    }
}

/** A program a person can recognise by its name, or "a command" when its name is an id or a path's noise. */
private fun named(program: String): ShellIntent {
    val name = program.trim()
    return if (name.isEmpty() || name.length > MAX_NAME || looksLikeAnId(name) || name.any { it == '$' || it == '`' || it == '(' })
        ShellIntent(ShellKind.Unknown)
    else ShellIntent(ShellKind.Program, name)
}

/** The leading words that only set up the program: env assignments, sudo, time, nohup, `timeout <n>`, `env`. */
private fun withoutPrefixes(words: List<String>): List<String> {
    var i = 0
    while (i < words.size) {
        val w = words[i]
        when {
            ASSIGNMENT.matches(w) -> i++
            w in PREFIXES -> i++
            w == "timeout" -> {
                i++
                while (i < words.size && (words[i].startsWith("-") || words[i].first().isDigit())) i++
            }
            else -> return words.drop(i)
        }
    }
    return emptyList()
}

/** A program word as its name: the last path segment, "./gradlew" → "gradlew", "run.sh" stays. */
private fun programName(word: String): String = fileName(word).lowercase()

private fun fileName(word: String): String = word.trim('"', '\'').replace('\\', '/').trimEnd('/').substringAfterLast('/')

/** Eight or more hex characters with a digit and no spaces (a task id, a session file). */
private fun looksLikeAnId(name: String): Boolean = ID_LIKE.containsMatchIn(name)

/** The line split at `;`, `&&`, `||`, `&` and new lines outside quotes. */
internal fun shellSegments(text: String): List<String> = splitOutsideQuotes(text) { s, i ->
    when (s[i]) {
        ';', '\n' -> 1
        '&' -> if (i + 1 < s.length && s[i + 1] == '&') 2 else if (i > 0 && s[i - 1] == '>') 0 else if (i + 1 < s.length && s[i + 1] == '>') 0 else 1
        '|' -> if (i + 1 < s.length && s[i + 1] == '|') 2 else 0
        else -> 0
    }
}

/** A segment split into its pipeline's programs (`|`, not `||`). */
internal fun shellPipeline(segment: String): List<String> = splitOutsideQuotes(segment) { s, i ->
    if (s[i] == '|' && (i + 1 >= s.length || s[i + 1] != '|') && (i == 0 || s[i - 1] != '|')) 1 else 0
}

private inline fun splitOutsideQuotes(text: String, cut: (String, Int) -> Int): List<String> {
    val out = ArrayList<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (quote != null) {
            current.append(c)
            if (c == quote) quote = null
            else if (c == '\\' && quote == '"' && i + 1 < text.length) current.append(text[++i])
            i++
            continue
        }
        if (c == '\'' || c == '"') {
            quote = c
            current.append(c)
            i++
            continue
        }
        if (c == '\\' && i + 1 < text.length) {
            current.append(c).append(text[i + 1])
            i += 2
            continue
        }
        val n = cut(text, i)
        if (n > 0) {
            if (current.isNotBlank()) out += current.toString().trim()
            current.clear()
            i += n
            continue
        }
        current.append(c)
        i++
    }
    if (current.isNotBlank()) out += current.toString().trim()
    return out
}

/** A segment's words, quotes kept off a quoted word (`'a b'` is one word `a b`). */
internal fun shellWords(segment: String): List<String> {
    val out = ArrayList<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var started = false
    for (c in segment) {
        when {
            quote != null -> if (c == quote) quote = null else current.append(c)
            c == '\'' || c == '"' -> {
                quote = c
                started = true
            }
            c.isWhitespace() -> {
                if (started || current.isNotEmpty()) out += current.toString()
                current.clear()
                started = false
            }
            else -> current.append(c)
        }
    }
    if (started || current.isNotEmpty()) out += current.toString()
    return out.filter { it.isNotEmpty() && !REDIRECT.matches(it) }
}

private const val MAX_NAME = 32
private val ID_LIKE = Regex("(?=[0-9a-f-]*[0-9])[0-9a-f]{8,}", RegexOption.IGNORE_CASE)
private val ASSIGNMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$")
private val REDIRECT = Regex("^[0-9]*[<>]{1,2}&?[0-9]*$|^[0-9]*>&[0-9]+$|^&>$")
private val GRADLE_TEST = Regex("(?i)(^|:)(test|check)[A-Za-z]*$|Test$|connected")
private val GRADLE_CHECK = Regex("(?i)(^|:)(lint|detekt|ktlint)")
private val GRADLE_BUILD = Regex("(?i)(^|:)(assemble|build|bundle|install|compile|package)")

private val SETUP = setOf("cd", "pushd", "popd", ".", "source", "export", "set", "unset", "ulimit", "umask", "true", ":", "clear", "alias", "shopt", "trap", "hash", "local", "declare", "eval")
private val QUIET = setOf("echo", "printf", "sleep", "wait", "date")
private val PREFIXES = setOf("sudo", "time", "nohup", "env", "command", "exec", "nice", "stdbuf", "caffeinate")
private val SHELLS = setOf("bash", "sh", "zsh", "dash")
private val TEST_RUNNERS = setOf("pytest", "jest", "vitest", "mocha", "ctest", "phpunit", "rspec", "karma", "ava", "tox", "nox")
private val INSTALL_WORDS = setOf("install", "i", "ci", "add")
private val SEARCH = setOf("grep", "egrep", "fgrep", "rg", "ag", "ack", "find", "fd", "fdfind", "locate", "which", "whereis")
private val READ = setOf("cat", "head", "tail", "less", "more", "bat", "nl", "wc", "xxd", "hexdump", "strings", "jq", "od")
private val LIST = setOf("ls", "tree", "du", "df", "stat", "file", "dir", "exa", "eza")
private val PROCESSES = setOf("ps", "pgrep", "top", "htop", "jobs", "lsof", "ss", "netstat", "pidof", "free", "uptime", "vmstat")
private val STOP = setOf("kill", "pkill", "killall")
private val FETCH = setOf("curl", "wget", "http", "https", "xh")
private val DELETE = setOf("rm", "rmdir", "trash", "unlink", "shred")
private val CHANGE = setOf("mkdir", "cp", "mv", "touch", "chmod", "chown", "ln", "tar", "unzip", "zip", "gzip", "gunzip", "tee", "patch", "rsync", "truncate")

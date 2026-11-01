package io.github.eslamasabry.oc.quality.sec

import java.io.File

/**
 * `RepositoryHygieneTest` (plan 16 §2.3; ported from `test/repository_hygiene_test.dart`, B-SEC-150, B-BUILD-030):
 * what the tracked tree may carry, plus the leak gate for the commits being pushed or tagged.
 */
object RepositoryHygiene {
    private val bannedTracked =
        mapOf(
            "keystore (*.jks)" to Regex("""\.jks$""", RegexOption.IGNORE_CASE),
            "keystore (*.keystore)" to Regex("""\.keystore$""", RegexOption.IGNORE_CASE),
            "key store (*.p12)" to Regex("""\.p12$""", RegexOption.IGNORE_CASE),
            "signing properties (key.properties)" to Regex("""(^|/)key\.properties$"""),
            "third-party skill pack (.claude/skills/)" to Regex("""(^|/)\.claude/skills/"""),
        )

    val GOVERNANCE = listOf("SECURITY.md", "SUPPORT.md", "CODE_OF_CONDUCT.md", "CONTRIBUTING.md", "LICENSE")
    val ENTRY_POINTS = listOf("README.md", "SECURITY.md", "SUPPORT.md")
    const val NON_AFFILIATION = "not built, maintained, endorsed by, or affiliated with the official OpenCode team"

    /** B-SEC-150: no keystores, signing secrets or skill packs among the tracked paths (repository-relative). */
    fun checkTracked(paths: List<String>): List<Finding> =
        bannedTracked.flatMap { (what, regex) -> paths.filter { regex.containsMatchIn(it) }.map { Finding("HYG-TRACKED", "$what: $it is tracked") } }

    /** The ignore rules that keep a local signing setup and the skill packs out of `git add`. */
    fun checkIgnores(rootGitignore: String, nativeGitignore: String): List<Finding> {
        val root = rootGitignore.lines().map { it.trim() }.toSet()
        val native = nativeGitignore.lines().map { it.trim() }.toSet()
        return buildList {
            if (".claude/skills/" !in root) add(Finding("HYG-IGNORE", ".gitignore no longer ignores .claude/skills/"))
            listOf("key.properties", "*.jks", "*.keystore", "local.properties").filter { it !in native }
                .forEach { add(Finding("HYG-IGNORE", "native/.gitignore no longer ignores $it")) }
        }
    }

    /** Governance files present; the non-affiliation statement at every public entry point (B-BUILD-030 part). */
    fun checkGovernance(repo: File): List<Finding> = buildList {
        GOVERNANCE.filterNot { File(repo, it).isFile }.forEach { add(Finding("HYG-GOVERNANCE", "$it is missing")) }
        ENTRY_POINTS.filter { File(repo, it).isFile }.forEach { path ->
            val text = File(repo, path).readText().replace(Regex("""[>*\s]+"""), " ")
            if (NON_AFFILIATION !in text) add(Finding("HYG-AFFILIATION", "$path does not state the project is unaffiliated"))
        }
    }

    fun trackedFiles(repo: File): List<String> {
        val p = ProcessBuilder("git", "ls-files", "-z").directory(repo).redirectErrorStream(false).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "git ls-files failed" }
        return out.split('\u0000').filter { it.isNotEmpty() }
    }
}

/**
 * The leak gate (plan 16 §2.3; owner memories public-commit-email, push-freely-snapshot-recipe): added lines of the
 * commits being pushed carry no personal mail address, no tailnet (100.64/10) address outside the documentation
 * address, and no term of the git-ignored deny-list; every author and committer is the public noreply address.
 */
object LeakGate {
    const val NOREPLY = "104366884+Eslamasabry" + "@users.noreply.github.com"
    private const val DOC_TAILNET = "100.64.0.10"
    private val exemptPrefixes = listOf("native/e2e/fixtures/")

    // Built from parts so this file never matches itself.
    private val personalMail = Regex("""[A-Za-z0-9._%+-]+@(""" + "gmail" + """|googlemail|hotmail|outlook|yahoo|icloud|proton(mail)?)\.[a-z.]+""", RegexOption.IGNORE_CASE)
    private val tailnet = Regex("""\b100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3}\b""")

    data class Added(val file: String, val line: String)

    /** Added lines (`+` lines, not `+++`) of a unified diff, with their file. */
    fun addedLines(diff: String): List<Added> {
        var file = ""
        return diff.lines().mapNotNull { l ->
            when {
                l.startsWith("+++ ") -> { file = l.removePrefix("+++ ").removePrefix("b/"); null }
                l.startsWith("+") -> Added(file, l.substring(1))
                else -> null
            }
        }
    }

    fun scan(added: List<Added>, denylist: List<String>): List<Finding> =
        added.filterNot { a -> exemptPrefixes.any { a.file.startsWith(it) } }.flatMap { a ->
            buildList {
                personalMail.findAll(a.line).forEach { _ -> add(Finding("LEAK-MAIL", "${a.file}: a personal mail address in an added line")) }
                tailnet.findAll(a.line).filter { it.value != DOC_TAILNET }.forEach { _ -> add(Finding("LEAK-TAILNET", "${a.file}: a 100.64/10 address in an added line")) }
                denylist.filter { it.isNotBlank() && a.line.contains(it, ignoreCase = true) }.forEach { _ -> add(Finding("LEAK-DENYLIST", "${a.file}: a deny-listed name in an added line")) }
            }
        }

    /** `author<TAB>committer` lines of the pushed commits. */
    fun checkIdentities(lines: List<String>): List<Finding> =
        lines.filter { it.isNotBlank() }.flatMap { l -> l.split('\t').filter { it != NOREPLY }.map { Finding("LEAK-IDENTITY", "a commit is authored or committed as a non-noreply address") } }

    /** The deny-list (`native/config/private-denylist.txt`, git-ignored); null when absent. */
    fun denylist(nativeRoot: File): List<String>? =
        File(nativeRoot, "config/private-denylist.txt").takeIf { it.isFile }?.readLines()?.map { it.substringBefore('#').trim() }?.filter { it.isNotEmpty() }
}

package io.github.eslamasabry.oc.feature.chat.presentation

/** What a reply's link target with no scheme can name: a file of the project, or nothing the app opens. */
internal object LinkTargets {
    private const val MAX_LENGTH = 1024
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    /**
     * The file a link target with no scheme names, or null when it is not one (a web address, a page anchor, a path out
     * of the project through `..` or `~`, one with a query). `./a.txt` is `a.txt`; `/root/p/a.txt` stays absolute for
     * [projectRelativePath] to place.
     */
    fun file(target: String): String? {
        val t = target.trim()
        return if (shaped(t)) place(t) else null
    }

    private fun shaped(t: String): Boolean =
        t.isNotEmpty() &&
            t.length <= MAX_LENGTH &&
            t.none { it.isISOControl() || it == ' ' || it == '\\' || it == '?' || it == '#' } &&
            !t.startsWith("//") &&
            !t.startsWith("~") &&
            !SCHEME.containsMatchIn(t) &&
            t.split('/').none { it == ".." }

    private fun place(t: String): String? =
        when {
            t.startsWith("./") -> t.removePrefix("./").takeIf { it.isNotEmpty() }
            t.startsWith("/") -> t
            '/' in t && '.' in t.substringBefore('/') -> null // example.com/page: a web address without its scheme
            else -> t
        }
}

package io.github.eslamasabry.opencode_mobile.release

/**
 * The native release identity check (port of `tool/release/release_metadata.dart`, B-BUILD-028): `version.properties`
 * (`versionName` + `versionCode`, the native twin of the pubspec `version: X.Y.Z+BUILD`), the first release heading of
 * the changelog and the versioned release notes must name the same `X.Y.Z+BUILD`. CRLF files and trailing `#`
 * comments on a property line are accepted.
 */
internal object ReleaseMetadata {
    private val PROPERTY = Regex("""^\s*([A-Za-z]+)\s*[=:]\s*(.*?)\s*$""")
    private val NAME = Regex("""^\d+\.\d+\.\d+$""")
    private val CODE = Regex("""^\d+$""")
    private val HEADING = Regex("""^## (.+)$""")
    private val HEADING_VERSION = Regex("""^(\d+\.\d+\.\d+\+\d+)(?:\s|$)""")

    const val BAD_IDENTITY: String = "version.properties must contain versionName=X.Y.Z and versionCode=BUILD."

    /** `X.Y.Z+BUILD`, or null when `versionName` is not a plain release version or `versionCode` is not a number. */
    fun releaseVersion(properties: String): String? {
        val values = HashMap<String, String>()
        for (line in lines(properties)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) continue
            val m = PROPERTY.matchEntire(line) ?: continue
            values[m.groupValues[1]] = m.groupValues[2].substringBefore(" #").substringBefore("\t#").trim()
        }
        val name = values["versionName"]?.takeIf(NAME::matches) ?: return null
        val code = values["versionCode"]?.takeIf(CODE::matches) ?: return null
        return "$name+$code"
    }

    /** Actionable discrepancies; empty when the three agree. [releaseNotes] null means the notes file is missing. */
    fun validate(properties: String, changelog: String, releaseNotes: String?): List<String> {
        val version = releaseVersion(properties) ?: return listOf(BAD_IDENTITY)
        val issues = ArrayList<String>()
        val headings = lines(changelog).mapNotNull { HEADING.matchEntire(it)?.groupValues?.get(1)?.trim() }.toMutableList()
        if (headings.firstOrNull() == "Unreleased") headings.removeAt(0)
        val top = headings.firstOrNull()
        val topVersion = top?.let { HEADING_VERSION.find(it)?.groupValues?.get(1) }
        if (topVersion != version) {
            issues += "CHANGELOG.md first release heading must name $version (found ${top ?: "no release heading"})."
        }
        val notesPath = notesPath(version)
        if (releaseNotes == null) {
            issues += "Add $notesPath with header # OpenCode Mobile $version."
        } else if (lines(releaseNotes).first().trim() != "# OpenCode Mobile $version") {
            issues += "$notesPath must start with # OpenCode Mobile $version."
        }
        return issues
    }

    fun notesPath(version: String): String = "docs/releases/v$version.md"

    private fun lines(text: String): List<String> = text.split('\n').map { it.removeSuffix("\r") }
}

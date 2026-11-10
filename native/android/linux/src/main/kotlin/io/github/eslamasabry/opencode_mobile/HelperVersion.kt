package io.github.eslamasabry.opencode_mobile

/**
 * Which Paseo helper versions may run. The app's own pin is the one it installs; any other version the app has
 * tested (the older pin the Flutter app installed) is still run as it is, as the Flutter app's ordinary start did:
 * an older helper is an update on offer, never a reason to refuse to start.
 */
internal object HelperVersion {
    private val bare = Regex("^v?([0-9]+\\.[0-9]+\\.[0-9]+)$")
    private val anywhere = Regex("(?<![0-9.])v?([0-9]+\\.[0-9]+\\.[0-9]+)(?![0-9.])")

    /**
     * The version in `paseo --version` output. A line that is only a version wins, so a warning line that happens to
     * hold a number (a Node notice, say) cannot be taken for it; otherwise the first version in the text.
     */
    fun parse(output: String?): String? {
        if (output == null) return null
        for (line in output.lineSequence().map { it.trim() }.toList().asReversed()) {
            bare.matchEntire(line)?.let { return it.groupValues[1] }
        }
        return anywhere.find(output)?.groupValues?.get(1)
    }

    /** True when [found] is the wanted [expected] pin or one of the [tested] versions. */
    fun runs(found: String?, expected: String, tested: Set<String>): Boolean =
        found != null && (found == expected || found in tested)
}

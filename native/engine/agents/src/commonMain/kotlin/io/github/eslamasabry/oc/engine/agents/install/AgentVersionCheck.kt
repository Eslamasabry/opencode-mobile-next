package io.github.eslamasabry.oc.engine.agents.install

/**
 * The version decision of a phone agent install, as plain values (the shell side is `AgentPhoneScripts.PROBE`).
 *
 * The install downloads the catalog's pinned file and checks its SHA-256 first, so the bytes that arrived are always
 * the pinned version. The program is then run once with `--version`, and its output must name the pinned version as a
 * whole word ([matches]). That run can fail in two ways, and they need different words:
 * - it did not answer (too slow, crashed, printed nothing usable): [AgentSetupFailure.NotStarted]. Trying again can
 *   help (a less busy phone, a cached download);
 * - it answered with another version: [AgentSetupFailure.Version], with both versions ([reported]). Nothing on the
 *   phone changes that; only an app with another pin does.
 *
 * Before that, the pinned file itself may be gone: its address answers 404/410 because upstream withdrew that
 * release. That is [AgentSetupFailure.Withdrawn] (also only an app update helps), not a dropped connection.
 */
public object AgentVersionCheck {
    /** How long the program may take to print its version (Flutter: 30 s). */
    public const val PROBE_SECONDS: Int = 120

    /** The version-shaped text the probe keeps from the program's output (grep -E). */
    public const val FOUND_PATTERN: String = "[0-9]+\\.[0-9]+\\.[0-9]+[0-9A-Za-z.+-]*"

    /** At most this many characters of [FOUND_PATTERN] reach the job's record. */
    public const val FOUND_MAX: Int = 40

    /** The probe's line when the program did not answer in [PROBE_SECONDS]. */
    public const val SLOW_LINE: String = "[oc] The agent did not answer its version check within $PROBE_SECONDS seconds"

    /** The Flutter line: the program ran but its output named no version (or it failed or could not start). */
    public const val SILENT_LINE: String = "[oc] The agent did not pass its version check"

    /** The probe's line when the program named another version: prefix, the found version, middle, the pin. */
    public const val OTHER_PREFIX: String = "[oc] The agent reported version "
    public const val OTHER_MIDDLE: String = "; this app installs "

    /** The install's line when the pinned file's address answered 404 or 410; the pinned version follows it. */
    public const val WITHDRAWN_PREFIX: String = "[oc] This app's pinned download is no longer offered: version "

    private const val PIN = "([0-9][a-zA-Z0-9.+_-]*)"

    private val other = Regex("^" + Regex.escape(OTHER_PREFIX) + "($FOUND_PATTERN)" + Regex.escape(OTHER_MIDDLE) +
        PIN + "$")

    private val withdrawn = Regex("^" + Regex.escape(WITHDRAWN_PREFIX) + PIN + "$")

    /** Whether [output] names [expected] as a whole word, the way the probe's `oc_version_pattern` decides. */
    public fun matches(output: String, expected: String): Boolean =
        Regex("(^|[^0-9A-Za-z.])" + Regex.escape(expected) + "([^0-9A-Za-z.]|$)", RegexOption.MULTILINE)
            .containsMatchIn(output)

    /** What the probe would keep from [output] when it does not [match][matches]: its first version-shaped text. */
    public fun found(output: String): String? =
        Regex(FOUND_PATTERN).find(output)?.value?.take(FOUND_MAX)

    /**
     * The failure kind of an agent step whose fixed last line is [error], or null when that line is not one of the
     * agent version check's own (other rules decide then). Paseo's "Paseo did not pass its version check" is not
     * an agent line and stays with them.
     */
    public fun failure(error: String?): AgentSetupFailure? {
        val line = error?.trim() ?: return null
        return when {
            line == SLOW_LINE || line == SILENT_LINE -> AgentSetupFailure.NotStarted
            other.matches(line) -> AgentSetupFailure.Version
            withdrawn.matches(line) -> AgentSetupFailure.Withdrawn
            else -> null
        }
    }

    /**
     * The versions in the line of a [AgentSetupFailure.Version] (both) or [AgentSetupFailure.Withdrawn] (the pinned
     * one only); null for any other line.
     */
    public fun reported(error: String?): ReportedVersion? {
        val line = error?.trim() ?: return null
        other.matchEntire(line)?.let { return ReportedVersion(found = it.groupValues[1], expected = it.groupValues[2]) }
        withdrawn.matchEntire(line)?.let { return ReportedVersion(found = null, expected = it.groupValues[1]) }
        return null
    }
}

/** The version a downloaded agent named ([found]; null when it never ran) and the one this app installs. */
public data class ReportedVersion(val found: String?, val expected: String)

package io.github.eslamasabry.oc.core.net

/**
 * A single-use PTY upgrade. The ticket and authorization values never appear in its description.
 */
public class TerminalSocketSpec(
    public val url: String,
    public val headers: Map<String, String>,
    public val initialCursor: Long = 0,
) {
    override fun toString(): String = "TerminalSocketSpec(<private>)"
}

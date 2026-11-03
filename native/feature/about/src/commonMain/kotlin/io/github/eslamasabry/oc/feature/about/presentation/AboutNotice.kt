package io.github.eslamasabry.oc.feature.about.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * One sentence of the build notice ("About this build", Flutter `buildProvenanceBody`): where the app comes from and
 * what it does not promise. There is deliberately no line about a release channel: an Android publication must not
 * claim "public alpha" or any readiness on other platforms (B-NAV-001).
 */
public enum class ProvenanceLine { BuiltWithAi, AndroidPrimary, DesktopExperimental, ReportWhatBreaks }

public object AboutNotice {
    /** The build notice, in reading order. */
    public val provenance: ImmutableList<ProvenanceLine> =
        persistentListOf(
            ProvenanceLine.BuiltWithAi,
            ProvenanceLine.AndroidPrimary,
            ProvenanceLine.DesktopExperimental,
            ProvenanceLine.ReportWhatBreaks,
        )

    /**
     * The third-party notices as readers see them (Flutter `aboutNoticesForReaders`): from the first `## ` section to
     * just before `## Regenerating…`, so the maintainer's intro (and how the file is verified) and the regeneration
     * steps stay out. A file with no `## ` section is shown whole.
     */
    public fun noticesForReaders(markdown: String): String {
        val lines = markdown.split('\n')
        val start = lines.indexOfFirst { it.startsWith("## ") }
        if (start < 0) return markdown
        val end = (start until lines.size).firstOrNull { lines[it].startsWith("## Regenerating") } ?: lines.size
        return lines.subList(start, end).joinToString("\n").trim()
    }
}

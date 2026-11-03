package io.github.eslamasabry.oc.feature.about.presentation

import kotlinx.collections.immutable.ImmutableList

/** The Open source notices page as data. */
public data class NoticesUi(
    val provenance: ImmutableList<ProvenanceLine> = AboutNotice.provenance,
    val phase: NoticesPhase = NoticesPhase.Loading,
    /** The notices for readers (Markdown source shown as reading text), when [phase] is Ready. */
    val text: String = "",
)

public enum class NoticesPhase { Loading, Ready, Failed }

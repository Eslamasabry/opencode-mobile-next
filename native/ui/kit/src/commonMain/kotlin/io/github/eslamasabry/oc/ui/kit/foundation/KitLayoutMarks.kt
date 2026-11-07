package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

/**
 * How a kit part may meet the window edge (docs/kit/LAYOUT-RULES.md §2). The layout guard (`:ui:gate` LayoutGuard)
 * reads it from the semantics tree; a part declares it here, next to its code, never in a global ignore list.
 */
public enum class KitEdge {
    /**
     * The part's own box may reach the window edges (a list row's highlight, a full-width tap area): its box is not
     * held to the gutter, but the words and controls inside it still are.
     */
    Bleed,

    /**
     * Window chrome anchored to an edge by design (a navigation rail or bar): the part and everything inside it own
     * their insets, so the guard skips the gutter check for the whole subtree.
     */
    Chrome,
}

/** The semantics keys of the layout rules. Read by the layout guard only; they say nothing to a screen reader. */
public object KitLayoutMarks {
    /** Set on a part that may meet the window edge, see [KitEdge]. */
    public val Edge: SemanticsPropertyKey<KitEdge> = SemanticsPropertyKey("KitEdge")

    /** Set on a text slot the kit lets end in "…" (titles, row supporting lines, cut values), with the slot's name. */
    public val MayTruncate: SemanticsPropertyKey<String> = SemanticsPropertyKey("KitMayTruncate")

    /** Set on a list container: rows inside it share one start edge for their titles. */
    public val List: SemanticsPropertyKey<String> = SemanticsPropertyKey("KitList")
}

/** Declares how this part meets the window edge (see [KitEdge]). */
public fun Modifier.kitEdge(edge: KitEdge): Modifier = semantics { this[KitLayoutMarks.Edge] = edge }

/** Declares that this text slot may end in "…"; [slot] names it for the guard's report. */
public fun Modifier.kitMayTruncate(slot: String): Modifier = semantics { this[KitLayoutMarks.MayTruncate] = slot }

/** Declares a list whose rows share one title start edge; [name] names it for the guard's report. */
public fun Modifier.kitList(name: String): Modifier = semantics { this[KitLayoutMarks.List] = name }

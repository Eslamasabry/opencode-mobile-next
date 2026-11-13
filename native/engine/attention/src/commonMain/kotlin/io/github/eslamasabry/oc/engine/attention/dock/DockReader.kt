package io.github.eslamasabry.oc.engine.attention.dock

/**
 * Who is reading the dock's facts, and how much of them it wants. The ring cap and the pin limit belong to the reader,
 * not to the one shared controller: the rings read [Rings] (what they have always shown), the edge light's list reads
 * [list] with more rows and more pins. Two readers never change each other's output.
 */
public data class DockReader(
    /** How many conversations the reader's model carries (see [DockUi.overflow] for the rest). */
    val cap: Int = DockModel.DEFAULT_CAP,
    /** How many pins the reader shows as pinned; the shared store may hold more. */
    val pinLimit: Int = DockModel.MAX_PINNED,
) {
    public companion object {
        /** The rings: [DockModel.DEFAULT_CAP] conversations and [DockModel.MAX_PINNED] pins, as before. */
        public val Rings: DockReader = DockReader()

        /** The edge light's list: [rows] conversations and every pin the shared store holds. */
        public fun list(rows: Int = LIST_ROWS): DockReader = DockReader(rows, DockModel.MAX_STORED_PINS)

        /** Enough rows for a long list; the list says how many more there are beyond this. */
        public const val LIST_ROWS: Int = 20
    }
}

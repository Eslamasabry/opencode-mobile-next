package io.github.eslamasabry.oc.feature.about.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Help › Tips as data (Flutter `TipsScreen`). */
public data class TipsUi(
    val tips: ImmutableList<TipEntry> = persistentListOf(),
    val tours: ImmutableList<TipEntry> = persistentListOf(),
    /** The entry whose sentence is replayed in place on its row (Show me), or null. Counts nothing. */
    val replaying: String? = null,
    /** "Show tips again" was done: the row then says "Tips will show again." */
    val resetDone: Boolean = false,
    val resetting: Boolean = false,
)

/** The page from the registry: tips first, then the mini tutorials, each in registry order. */
public fun tipsUi(entries: List<TipEntry>, replaying: String?, resetDone: Boolean, resetting: Boolean): TipsUi =
    TipsUi(
        tips = entries.filter { it.kind == TipKind.Tip }.toImmutableList(),
        tours = entries.filter { it.kind == TipKind.Tour }.toImmutableList(),
        replaying = replaying,
        resetDone = resetDone,
        resetting = resetting,
    )

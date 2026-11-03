package io.github.eslamasabry.oc.feature.about.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** A one-sentence tip, or a short tutorial. */
public enum class TipKind { Tip, Tour }

/**
 * One row of Help › Tips. [id] is the teaching id (the `oc.tips` storage name, `:engine:settings` TeachingIds); the
 * words are `:ui:words` strings chosen by id in the UI.
 */
public data class TipEntry(val id: String, val kind: TipKind)

/**
 * Every tip and mini tutorial, in the order Help › Tips lists them (Flutter `teachingRegistry`, mirrored by
 * `:engine:settings` TeachingRegistry.defaults; this feature may not depend on that engine, so the ids are repeated
 * here and checked by B-NAV-074's test).
 */
public object TipsRegistry {
    public const val STOP_AFTER_STEP: String = "stop-after-step"
    public const val SEND_LATER: String = "send-later"
    public const val HOLD_MIC: String = "hold-mic"
    public const val MAKE_IT_YOURS: String = "make-it-yours"
    public const val WHATS_NEW: String = "whats-new"

    public val entries: ImmutableList<TipEntry> =
        persistentListOf(
            TipEntry(STOP_AFTER_STEP, TipKind.Tip),
            TipEntry(SEND_LATER, TipKind.Tip),
            TipEntry(HOLD_MIC, TipKind.Tip),
            TipEntry(MAKE_IT_YOURS, TipKind.Tour),
            TipEntry(WHATS_NEW, TipKind.Tour),
        )
}

package io.github.eslamasabry.oc.ui.kit.motion

import io.github.eslamasabry.oc.ui.design.motion.MotionLevel

/** Plan 08 §3.3: every kit animation belongs to exactly one class; a part's KDoc `Motion:` line names it. */
public enum class KitMotionClass { Fade, Fold, Travel, Transform, Follow, Settle, ProgressLoop, AmbientLoop }

/** What a class does at a level (plan 08 §3.3, §7). */
public enum class KitMotionAnswer {
    /** Animates with its own spec. */
    Moves,

    /** A travel turned into a QUICK cross-fade (Calm). */
    Fades,

    /** Follows the finger 1:1 at every level. */
    Follows,

    /** A loop that draws one still frame. */
    Still,

    /** The next frame is final and nothing keeps running (MOT-7). */
    Instant,
}

/** Plan 08 §3.3 as data. `MotionClassTableTest` (B-KIT-054) walks it. */
public object KitMotionRules {
    public fun answer(cls: KitMotionClass, level: MotionLevel): KitMotionAnswer =
        when (cls) {
            KitMotionClass.Follow -> KitMotionAnswer.Follows
            KitMotionClass.Fade, KitMotionClass.Fold, KitMotionClass.Transform, KitMotionClass.Settle ->
                if (level == MotionLevel.Off) KitMotionAnswer.Instant else KitMotionAnswer.Moves
            KitMotionClass.Travel ->
                when (level) {
                    MotionLevel.Full -> KitMotionAnswer.Moves
                    MotionLevel.Calm -> KitMotionAnswer.Fades
                    MotionLevel.Off -> KitMotionAnswer.Instant
                }
            KitMotionClass.ProgressLoop -> if (level == MotionLevel.Off) KitMotionAnswer.Still else KitMotionAnswer.Moves
            KitMotionClass.AmbientLoop -> if (level == MotionLevel.Full) KitMotionAnswer.Moves else KitMotionAnswer.Still
        }
}

/**
 * The kit motion manifest for the S2-KITB parts (plan 08 §5.15, §9.3): each moving part (or moving piece of a part) and
 * its one class. `ReducedMotionSettlesTest` and `MotionClassTableTest` walk it; a new motion part adds its line here in
 * the same change. The K0/part-A/part-B owners add their parts when they fold this into the kit-wide manifest.
 */
public object KitMotionManifest {
    public val parts: Map<String, KitMotionClass> =
        linkedMapOf(
            "KitSwap" to KitMotionClass.Fade,
            "KitSwap.count" to KitMotionClass.Travel,
            "KitSpin" to KitMotionClass.Travel,
            "KitReveal" to KitMotionClass.Fold,
            "KitEntrance" to KitMotionClass.Travel,
            "KitPresence" to KitMotionClass.Travel,
            "KitAnimatedRows" to KitMotionClass.Fold,
            "KitAnimatedValue" to KitMotionClass.Fade,
            "KitDim" to KitMotionClass.Fade,
            "KitRefresh.pull" to KitMotionClass.Follow,
            "KitRefresh.breath" to KitMotionClass.AmbientLoop,
            "KitTip" to KitMotionClass.Travel,
            "KitJumpPill" to KitMotionClass.Travel,
            "KitTabSwitcher" to KitMotionClass.Fade,
            "KitTabStrip.indicator" to KitMotionClass.Travel,
            "KitSearchField.clear" to KitMotionClass.Fade,
            "KitScrollFade" to KitMotionClass.Follow,
            "KitSheetStep" to KitMotionClass.Fold,
            "KitSideSheet.drag" to KitMotionClass.Follow,
            "KitSideSheet.settle" to KitMotionClass.Settle,
            "KitModalMotion.dialog" to KitMotionClass.Travel,
            "KitModalMotion.menu" to KitMotionClass.Travel,
            "KitModalMotion.sheet" to KitMotionClass.Settle,
        )
}

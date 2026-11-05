package io.github.eslamasabry.oc.ui.design.motion

import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion

/**
 * Motion numbers (plan 08 §2.1–§2.5, §2.7; names follow Dart `KitMotion`, `lib/ui/kit/kit_motion.dart`). No Compose:
 * the kit builds every spec from these in `KitMotion.kt`. Milliseconds unless the name says otherwise.
 */
public object MotionTokens {
    // Durations (§2.1)
    public const val QUICK: Int = FlutterKitMotion.QUICK_MS
    public const val STANDARD: Int = FlutterKitMotion.STANDARD_MS
    public const val ROW_OPEN: Int = 450
    public const val ROW_RETURN: Int = FlutterKitMotion.ROW_RETURN_MS
    public const val GLOW_FADE: Int = FlutterKitMotion.EDGE_LIGHT_HUE_FADE_MS
    public const val FROST_FADE: Int = 180
    public const val STYLE_FADE: Int = 200
    public const val STATE_FADE: Int = 120
    public const val PRESS_HOLD: Int = FlutterKitMotion.PRESS_HOLD_MS
    public const val STOP_IGNORE_AFTER_SEND: Int = 800
    public const val PREDICTIVE_SPAN: Int = 300
    // Waits and holds, not motion
    public const val ESCALATE_AFTER: Int = FlutterKitMotion.ESCALATE_AFTER_MS
    public const val UNDO_WINDOW: Int = FlutterKitMotion.UNDO_WINDOW_MS
    public const val COPIED_HOLD: Int = FlutterKitMotion.COPIED_HOLD_MS
    public const val TYPING_SETTLE: Int = FlutterKitMotion.TYPING_SETTLE_MS
    public const val HELD_ANSWER: Int = 3000
    public const val ARRIVAL_HOLD: Int = 2400
    public const val TIP_HOLD: Int = 4000
    public const val TIP_FADE: Int = 200
    // Loops (§2.4), ms per lap or breath
    public const val GLOW_CLASSIC_LAP: Int = 3600
    public const val GLOW_MIN_LAP: Int = 1800
    public const val GLOW_SOFT_LAP: Int = 8000
    public const val GLOW_SOFT_MIN_LAP: Int = 6000
    public const val STOP_RING_LAP: Int = 2000
    public const val LIVE_SWEEP_LAP: Int = FlutterKitMotion.STEP_SWEEP_MS
    public const val STEP_LAP: Int = FlutterKitMotion.STEP_LAP_MS
    public const val STEP_LAP_CALM: Int = FlutterKitMotion.STEP_LAP_CALM_MS
    public const val STEP_BREATHE: Int = FlutterKitMotion.STEP_BREATHE_MS
    public const val STEP_FLASH: Int = FlutterKitMotion.STEP_FLASH_MS
    public const val STEP_TICK: Int = FlutterKitMotion.STEP_TICK_MS
    public const val STEP_SPRING: Int = FlutterKitMotion.STEP_SPRING_MS
    public const val STEP_FADE: Int = FlutterKitMotion.STEP_FADE_MS
    public const val CARET_BREATH: Int = 1100
    public const val SPINNER_LAP: Int = 1333
    public const val REFRESH_BREATH: Int = FlutterKitMotion.BREATH_MS
    // Distances (dp) and ratios (§2.5)
    public const val PAGE_TRAVEL_DP: Int = 30
    public const val ENTRANCE_RISE_DP: Int = 8
    public const val MENU_DROP_DP: Int = 8
    public const val TIP_RISE_DP: Int = 4
    public const val PREVIEW_CORNER_DP: Int = 28
    public const val ROW_CORNER_DP: Int = 20
    public const val DESCENDER_ALLOWANCE_DP: Int = 4
    public const val AWAY_FROM_LATEST_DP: Int = 480
    public const val FOLLOW_SLACK_DP: Int = 48
    public const val PAGE_FADE_OUT_SHARE: Float = 0.3f
    public const val COUNT_ROLL_SHARE: Float = 0.6f
    public const val MARK_FROM: Float = FlutterKitMotion.MARK_FROM
    public const val LIFT_START_SCALE: Float = 0.92f
    public const val PREDICTIVE_MIN_SCALE: Float = 0.9f
    public const val PREDICTIVE_FADE_AT: Float = 0.35f
    public const val DIM_MOST: Float = 0.32f
    public const val REVEAL_BASE_CPS: Float = 45f
    public const val REVEAL_MAX_LAG_MS: Int = 250
    // Springs (§2.3): Compose spring(dampingRatio, stiffness), unit mass
    public const val SPATIAL_ZETA: Float = 0.9f
    public const val SPATIAL_FAST_K: Float = 1400f
    public const val SPATIAL_K: Float = 700f
    public const val SPATIAL_SLOW_K: Float = 300f
    public const val LARGE_TRAVEL_K: Float = 550f
    public const val REPLY_GROW_K: Float = FlutterKitMotion.REPLY_GROW_STIFFNESS
    public const val EFFECTS_FAST_K: Float = 3800f
    public const val EFFECTS_K: Float = 1600f
    public const val EFFECTS_SLOW_K: Float = 800f
}

/**
 * Flutter's named curves as cubic control points (pinned SDK `curves.dart`). They are **not** the androidx curves of
 * the same name (plan 06 §4.8): Compose's `EaseOutCubic` is (0.33, 1, 0.68, 1), Flutter's is (0.215, 0.61, 0.355, 1).
 */
public enum class CurveId(public val x1: Float, public val y1: Float, public val x2: Float, public val y2: Float) {
    /** Flutter `easeOutCubic` (Dart `KitMotion.enter`): anything going 0 → 1. */
    Enter(0.215f, 0.61f, 0.355f, 1f),
    /** Flutter `easeInCubic` (Dart `KitMotion.exit`): reverse slots only. */
    Exit(0.55f, 0.055f, 0.675f, 0.19f),
    StepEase(0.455f, 0.03f, 0.515f, 0.955f),
    StepOut(0.25f, 0.46f, 0.45f, 0.94f),
    Pulse(0.42f, 0f, 0.58f, 1f),
    PredictiveBack(0.1f, 0.1f, 0f, 1f),
    Land(0.175f, 0.885f, 0.32f, 1.275f),
}

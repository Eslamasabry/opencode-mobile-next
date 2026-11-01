package io.github.eslamasabry.oc.ui.design.motion

/** The person's motion choice and the resolved level (plan 06 §4.8, plan 08 §3.2). */
public enum class MotionLevel { Full, Calm, Off }

public data class MotionSignals(val systemScale: Float, val powerSave: Boolean, val severeHeat: Boolean)

/** MOT-7 (Off: the next frame is final), MOT-13 (battery saver or severe heat means Calm). */
public fun resolveMotion(s: MotionSignals, user: MotionLevel, saver: Boolean): MotionLevel =
    when {
        s.systemScale == 0f || user == MotionLevel.Off || saver -> MotionLevel.Off
        s.severeHeat || s.powerSave || user == MotionLevel.Calm -> MotionLevel.Calm
        else -> MotionLevel.Full
    }

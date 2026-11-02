package io.github.eslamasabry.oc.android.migration

import android.os.Process

/** `qa` build type only (plan 05 §10.5 J2): the real interrupter, reading its controls from `files/native/qa-*`. */
public object StepInterrupters {
    public fun forBuild(paths: MigrationPaths): StepInterrupter = QaStepInterrupter(paths) { Process.killProcess(Process.myPid()) }
}

package io.github.eslamasabry.opencode_mobile

import android.content.Context

/** Standalone pure JVM compile fixture only; never part of the Android test source set. */
internal class BuiltinLinux {
    fun checkIdleStop() {}
    companion object { fun get(context: Context) = BuiltinLinux() }
}

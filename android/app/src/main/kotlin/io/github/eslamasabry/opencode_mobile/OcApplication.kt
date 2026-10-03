package io.github.eslamasabry.opencode_mobile

import android.app.Application

/** Installed before activities/services so foreground service failures survive. */
class OcApplication : Application() {
    override fun onCreate() {
        try { NativeCrashStore(filesDir).install() } catch (_: Throwable) { }
        super.onCreate()
    }
}

package io.github.eslamasabry.oc.android.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.eslamasabry.oc.core.common.Outcome

// TODO(move-to-core): plan 11 §14.6 / CR11-9 puts TailscaleAppState and TailscaleApp in :core:domain, which does not
//  have them yet (core is being reconciled; this unit may not edit it). They are platform-free and move unchanged.

public enum class TailscaleAppState { Installed, Missing, Unavailable }

public interface TailscaleApp {
    /** Package `com.tailscale.ipn` only (needs the `<queries>` entry this module's manifest declares). */
    public fun state(): TailscaleAppState

    /** Launches only that package's launch intent; no VPN control, no tailnet state. False when nothing opened. */
    public fun open(): Boolean
}

/**
 * The Tailscale handoff (ANDROID-PLATFORM-34, plan 11 §14.6). Port of `TailscaleHandoff` (Flutter app at bd0a13d6)
 * without the channel. It maps only explicit package evidence and fails closed (B-SEC-174): `Installed` needs the
 * package found **and** enabled; not found is `Missing`; disabled or any error is `Unavailable`. It never takes an
 * intent, address or tailnet value from a caller.
 */
public class AndroidTailscaleApp(
    private val context: Context,
    private val activities: CurrentActivity,
) : TailscaleApp {
    override fun state(): TailscaleAppState =
        try {
            @Suppress("DEPRECATION") // getApplicationInfo(String, Int): the flags overload is API 33+.
            val info = context.packageManager.getApplicationInfo(PACKAGE, 0)
            if (info.packageName == PACKAGE && info.enabled) TailscaleAppState.Installed else TailscaleAppState.Unavailable
        } catch (_: PackageManager.NameNotFoundException) {
            TailscaleAppState.Missing
        } catch (_: RuntimeException) {
            TailscaleAppState.Unavailable
        }

    override fun open(): Boolean {
        val launch =
            try {
                context.packageManager.getLaunchIntentForPackage(PACKAGE)
            } catch (_: RuntimeException) {
                null
            } ?: return false
        // Fail closed: only an intent that names the Tailscale package itself, with nothing of ours added to it.
        val target = launch.component?.packageName ?: launch.`package`
        if (target != PACKAGE) return false
        val explicit = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(launch.component)
        if (launch.component == null) explicit.setPackage(PACKAGE)
        return startFromVisibleActivity(context, activities, explicit) is Outcome.Ok
    }

    public companion object {
        public const val PACKAGE: String = "com.tailscale.ipn"
    }
}

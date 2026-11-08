// P12-FIX-LINUX-VOICE shim (native-authored, not a twin). Replaces the S1 MainActivity / StatusActionReceiver
// stand-ins: those declared the same FQCNs as the :androidApp classes, so this library could not join the APK. The
// copies now reach the app's real components by their frozen class names, which is exactly what the Flutter-era
// notifications and launch intents already name.
package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.content.Intent
import io.github.eslamasabry.oc.core.common.launch.LaunchToken

internal object AppComponents {
    /** Frozen extra name, verbatim from the Flutter app's MainActivity (the native MainActivity reads the same). */
    const val EXTRA_LAUNCH_ACTION = "oc.shortcut"

    const val MAIN_ACTIVITY = "io.github.eslamasabry.opencode_mobile.MainActivity"
    const val STATUS_ACTION_RECEIVER = "io.github.eslamasabry.opencode_mobile.StatusActionReceiver"

    /**
     * `Intent(context, MainActivity::class.java)`: the app's launcher activity in this package, carrying this install's
     * launch token (B-175) so MainActivity accepts the setup and Linux actions these notifications send.
     */
    fun mainActivity(context: Context): Intent =
        LaunchToken.stamp(context, Intent().setClassName(context.packageName, MAIN_ACTIVITY))

    /** `Intent(context, StatusActionReceiver::class.java)`: the app's status-row receiver. */
    fun statusAction(context: Context): Intent = Intent().setClassName(context.packageName, STATUS_ACTION_RECEIVER)
}

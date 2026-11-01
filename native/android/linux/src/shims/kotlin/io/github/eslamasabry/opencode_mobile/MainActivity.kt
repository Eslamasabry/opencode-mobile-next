// S1-LINUX shim (native-authored, not a twin). TODO(S3-RTSPLIT): the copies must stop naming MainActivity directly; the
// real class is the :androidApp shell at this same FQCN, so this stand-in cannot ship on the same classpath.
package io.github.eslamasabry.opencode_mobile

import android.app.Activity

/** Compile stand-in: the copies build launch intents for `MainActivity` and read its frozen launch-action extra. */
open class MainActivity : Activity() {
    companion object {
        /** Frozen extra name, verbatim from the Flutter app's MainActivity. */
        const val EXTRA_LAUNCH_ACTION = "oc.shortcut"
    }
}

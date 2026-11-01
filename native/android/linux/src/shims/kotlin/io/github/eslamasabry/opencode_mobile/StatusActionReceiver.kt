// S1-LINUX shim (native-authored, not a twin). TODO(S4 W11-09): the real receiver is a shell in :androidApp with its body
// in :android:background; the Flutter one forwards over a MethodChannel and cannot be copied here.
package io.github.eslamasabry.opencode_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Compile stand-in: the status row names this class as the target of its Stop actions. It does nothing. */
class StatusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

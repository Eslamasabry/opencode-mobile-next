package io.github.eslamasabry.opencode_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.eslamasabry.oc.android.notifications.FrozenExtras
import io.github.eslamasabry.oc.android.notifications.StatusRenderer
import io.github.eslamasabry.opencode_mobile.graph.attentionRuntime
import io.github.eslamasabry.opencode_mobile.graph.platformHub
import io.github.eslamasabry.opencode_mobile.graph.replyWatch
import kotlinx.coroutines.launch

/*
 * Thin shells at the Flutter app's frozen FQCNs (plan 11 §2.2, unit P12-PLATFORM): a Flutter-era alert still in the
 * shade after the update fires these exact components. The body is :android:notifications' NotificationActionBody;
 * it never starts an activity (no trampoline).
 */

/** Allow once / Reject from a permission or question alert, bound to the exact pending request. */
class CodingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hub = context.platformHub
        val pending = goAsync()
        hub.scope.launch {
            try {
                hub.actionBody.onCodingAction(intent)
            } finally {
                pending.finish()
            }
        }
    }
}

/** The status row's Stop and dismissal. */
class StatusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // A swipe away of the conversations row: it stays away until a new request or failure raises its pulse count.
        if (intent.getStringExtra(FrozenExtras.EXTRA_STATUS_ACTION) == StatusRenderer.DISMISSED_ACTION) {
            context.attentionRuntime.onRowDismissed(intent.getStringExtra(FrozenExtras.EXTRA_STATUS_TURN))
            // Plan 11 §9.3: swiping the row away while only reply watch holds it ends the watch for that run.
            context.replyWatch.body.onRowDismissed(intent)
        }
        val hub = context.platformHub
        val pending = goAsync()
        hub.scope.launch {
            try {
                hub.actionBody.onStatusAction(intent)
            } finally {
                pending.finish()
            }
        }
    }
}

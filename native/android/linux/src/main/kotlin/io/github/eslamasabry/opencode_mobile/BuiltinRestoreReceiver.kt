package io.github.eslamasabry.opencode_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Protected system broadcasts only; restoration is native and policy-gated. */
class BuiltinRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = NativeServerRestoreEvent.fromAction(intent.action) ?: return
        // No caller extras, Activity, retry scheduler or alternative service type.
        try {
            val linux = BuiltinLinux.get(context.applicationContext)
            val ticket = linux.prepareServerEventRestore(event) ?: return
            if (!BuiltinServerService.startForRestore(context.applicationContext, ticket)) {
                linux.cancelServerEventRestore(ticket)
            }
        } catch (_: Throwable) {
            // OS dispatch/policy rejection is a closed event, never a retry loop.
        }
    }
}

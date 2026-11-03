// S1-LINUX shim (native-authored, not a twin). P12-FIX-LINUX-VOICE: renamed from R so it never clashes with the app R; the copies import it `as R`.
@file:Suppress("ClassName", "ObjectPropertyName")

package io.github.eslamasabry.opencode_mobile

/**
 * The copied runtime files say `R.string.x` in the Flutter app's package. This library's resources are generated into
 * `io.github.eslamasabry.oc.android.linux.R` (its namespace), so this object forwards each name the copies read to that class. Resource names and
 * text are the Flutter app's, copied verbatim into src/main/res.
 */
internal object LinuxR {
    object string {
        val native_live_channel: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_live_channel
        val native_live_description: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_live_description
        val native_phone_server_body: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_phone_server_body
        val native_phone_server_channel: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_phone_server_channel
        val native_phone_server_description: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_phone_server_description
        val native_setup_channel: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_setup_channel
        val native_status_action_new_chat: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_action_new_chat
        val native_status_action_open: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_action_open
        val native_status_action_start: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_action_start
        val native_status_action_stop_opencode: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_action_stop_opencode
        val native_status_action_stop_reply: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_action_stop_reply
        val native_status_on_this_phone: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_on_this_phone
        val native_status_paused_android_body: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_paused_android_body
        val native_status_paused_android_title: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_paused_android_title
        val native_status_paused_idle_body: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_paused_idle_body
        val native_status_paused_idle_title: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_paused_idle_title
        val native_status_public_paused: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_public_paused
        val native_status_public_ready: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_public_ready
        val native_status_ready_body: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_ready_body
        val native_status_ready_title: Int get() = io.github.eslamasabry.oc.android.linux.R.string.native_status_ready_title
    }

    object plurals {
        val native_chip_pending: Int get() = io.github.eslamasabry.oc.android.linux.R.plurals.native_chip_pending
        val native_chip_running: Int get() = io.github.eslamasabry.oc.android.linux.R.plurals.native_chip_running
    }

    object mipmap {
        /** TODO(S3-RTSPLIT): the app's launcher icon lives in :androidApp; the platform default stands in until then. */
        val ic_launcher: Int get() = android.R.mipmap.sym_def_app_icon
    }
}

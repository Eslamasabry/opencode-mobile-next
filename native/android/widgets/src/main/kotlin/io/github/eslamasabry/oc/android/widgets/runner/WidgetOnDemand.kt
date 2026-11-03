package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.common.dart.DartJson
import java.util.Base64

/**
 * The on-demand half of the widget snapshot that the runners trust (Flutter `_currentWidgetScope`,
 * `_widgetOnDemandActions` and the private `oc.widgetTarget.<profile>` record). The snapshot publisher feeds these into
 * `WidgetSnapshotInput.scopeToken` / `onDemandFingerprint` / `WidgetRowDetails.onDemandActions` and publishes [target]
 * as the private target, so a cold tap can be checked against the same identities.
 */
public class WidgetActionScope(private val randomBytes: (Int) -> ByteArray) {
    private var identity: List<Any?>? = null
    private var token: String = ""

    /**
     * An opaque token that changes whenever the connection identity changes (profile, gateway, directory,
     * workspace): a location switch revokes every launcher action built for the old one.
     */
    @Synchronized
    public fun token(identity: List<Any?>): String {
        if (identity != this.identity || token.isEmpty()) {
            this.identity = identity
            token = Base64.getUrlEncoder().encodeToString(randomBytes(24))
        }
        return token
    }

    public companion object {
        /**
         * The buttons a row offers without opening the app: allow once / reject for its pending permission, and stop
         * for a running turn whose server user-message identity is known. None for a private (transient) transport.
         */
        public fun actions(supported: Boolean, permission: Boolean, busy: Boolean, runMarker: String?): List<String> =
            if (!supported) emptyList()
            else buildList {
                if (permission) addAll(listOf("allowOnce", "reject"))
                if (busy && !runMarker.isNullOrEmpty()) add("stop")
            }

        /** The app-private routing record; the public snapshot never carries the directory or workspace. */
        public fun target(
            profileId: String,
            scopeToken: String,
            fingerprint: String,
            directory: String?,
            workspace: String?,
        ): String =
            DartJson.encode(
                linkedMapOf(
                    "profileID" to profileId,
                    "scopeToken" to scopeToken,
                    "fingerprint" to fingerprint,
                    "directory" to directory,
                    "workspace" to workspace,
                )
            )
    }
}

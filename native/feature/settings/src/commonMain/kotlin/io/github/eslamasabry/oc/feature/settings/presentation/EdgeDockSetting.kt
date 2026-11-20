package io.github.eslamasabry.oc.feature.settings.presentation

import kotlinx.coroutines.flow.StateFlow

/**
 * Settings › Conversations › Other conversations: what the app draws on the edge of a conversation for switching to
 * another one. [EdgeLight] is the small light that opens one list (the default), [Hidden] draws nothing.
 */
public enum class OtherConversationsMode(public val stored: String) {
    EdgeLight("edge-light"),
    Hidden("hidden"),
    ;

    public companion object {
        /** The saved word back to a choice; null for anything unknown. */
        public fun fromStored(value: String?): OtherConversationsMode? = entries.firstOrNull { it.stored == value }

        /**
         * What a device with a saved choice, a retired `"rings"` value, or only the dock's old on/off switch
         * (`oc.dockShown`) shows. `"hidden"` stays [Hidden]; `"edge-light"` and `"rings"` become [EdgeLight];
         * otherwise `"false"` on the old switch stays [Hidden] and anything else (including nothing saved) is
         * [EdgeLight].
         */
        public fun migrate(saved: String?, legacyShown: String?): OtherConversationsMode =
            when (saved) {
                Hidden.stored -> Hidden
                EdgeLight.stored, "rings" -> EdgeLight
                else -> if (legacyShown == "false") Hidden else EdgeLight
            }
    }
}

/**
 * The choice of what the app draws for switching conversations, bound by the app that draws it. Device-wide: the
 * rings' and the light's own edge and height are chosen on the thing itself and saved there.
 */
public interface EdgeDockSetting {
    public val mode: StateFlow<OtherConversationsMode>

    public fun setMode(mode: OtherConversationsMode)
}

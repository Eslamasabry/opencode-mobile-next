package io.github.eslamasabry.oc.feature.settings.presentation

import kotlinx.coroutines.flow.StateFlow

/**
 * Settings › Conversations › Other conversations: what the app draws on the edge of a conversation for switching to
 * another one. [Rings] is the edge dock (the default), [EdgeLight] the small light that opens one list, [Hidden] none.
 */
public enum class OtherConversationsMode(public val stored: String) {
    Rings("rings"),
    EdgeLight("edge-light"),
    Hidden("hidden"),
    ;

    public companion object {
        /** The saved word back to a choice; null for anything unknown. */
        public fun fromStored(value: String?): OtherConversationsMode? = entries.firstOrNull { it.stored == value }

        /**
         * What a device that has the new choice saved, or only the dock's old on/off switch (`oc.dockShown`), shows.
         * The saved choice wins. Otherwise "false" (the person turned the dock off) is [Hidden] and anything else,
         * including nothing saved, is [Rings].
         */
        public fun migrate(saved: String?, legacyShown: String?): OtherConversationsMode =
            fromStored(saved) ?: if (legacyShown == "false") Hidden else Rings
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

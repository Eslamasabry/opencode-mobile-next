package io.github.eslamasabry.oc.engine.agents.models

/**
 * Where a setting or a new conversation lives, as a typed value (Flutter `phone_server_names.dart`). The app's own
 * server is [ThisPhone], whose words already start with "On", so titles built on it never read "on On this phone"
 * (B-NAV-035). The UI renders these through its words table ([ServerPlaceWords] here for the port).
 */
public sealed interface ServerPlace {
    /** The in-app server ("On this phone"). */
    public data object ThisPhone : ServerPlace

    /** Any other server, by the name it is shown under. */
    public data class Named(public val name: String) : ServerPlace

    public companion object {
        /**
         * The place for a shown server name: the phone card's own title (in the shown locale) is [ThisPhone]; the
         * Flutter code compared against `phoneServerCardTitle` the same way.
         */
        public fun of(shownName: String, phoneCardTitle: String): ServerPlace =
            if (shownName == phoneCardTitle) ThisPhone else Named(shownName)
    }
}

/** A title or note that names a [ServerPlace]. */
public sealed interface ServerPlaceText {
    public val place: ServerPlace

    /** The model picker's title: "New conversations on …". */
    public data class NewConversations(override val place: ServerPlace) : ServerPlaceText

    /** A server page section heading: "On …". */
    public data class SectionOn(override val place: ServerPlace) : ServerPlaceText

    /** "Saved on …. Other apps using … get this too." */
    public data class SavedOn(override val place: ServerPlace) : ServerPlaceText

    /** Confirmation after "Use my default": "Reset on … to your own choices: [what]." */
    public data class UseMyDefaultDone(override val place: ServerPlace, public val what: String) : ServerPlaceText
}

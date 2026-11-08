package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/**
 * FactsBook, the memory pages (F: lib/ui/screens/factsbook/factsbook_routes.dart). Owned by :feature:factsbook. Each
 * page reads the connected server profile's pairing, as Flutter does.
 */
@Serializable
public sealed interface FactsBookRoute : AppRoute {
    /** About you, the full place: search, the places, every thing by kind, Add something. */
    @Serializable public data object AboutYou : FactsBookRoute

    /** Add something: a note or pasted text. */
    @Serializable
    public data object Capture : FactsBookRoute {
        override val motion: RouteMotion
            get() = RouteMotion.SlideUp
    }

    /** Needs you: changes waiting for the person's OK and notes not saved yet. */
    @Serializable public data object NeedsYou : FactsBookRoute

    /** Notes not saved yet: the node's queue and this phone's outbox. */
    @Serializable public data object NotSaved : FactsBookRoute

    @Serializable public data class Thing(val id: String) : FactsBookRoute

    @Serializable public data class Search(val query: String) : FactsBookRoute

    @Serializable public data object Areas : FactsBookRoute

    @Serializable public data object Reminders : FactsBookRoute

    /** Check this reminder: [id] null makes a new one; [locale] is the app's language (`en` or `ar`). */
    @Serializable
    public data class ReminderEdit(val id: String?, val locale: String = "en") : FactsBookRoute {
        override val motion: RouteMotion
            get() = RouteMotion.SlideUp
    }

    /** Set up FactsBook: pair with a node by address and one-time code. */
    @Serializable public data object Connect : FactsBookRoute

    /** Status: each part of the node in words, the signed history check, Disconnect. */
    @Serializable public data object Status : FactsBookRoute

    /** Memory settings: the brief, the locked folder, the home time zone. */
    @Serializable public data object Settings : FactsBookRoute

    /** Triggers: rules that look at what arrives, what they did, and what waits for the person. */
    @Serializable public data object Triggers : FactsBookRoute

    /** The rule editor: [id] null makes a new rule. */
    @Serializable public data class Rule(val id: String? = null) : FactsBookRoute

    /** "What happened to this message" for one event. */
    @Serializable public data class Event(val id: String) : FactsBookRoute
}

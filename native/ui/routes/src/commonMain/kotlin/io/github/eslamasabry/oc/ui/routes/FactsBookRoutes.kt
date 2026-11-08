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

    /** Notes not saved yet: the node's queue and this phone's outbox. */
    @Serializable public data object NotSaved : FactsBookRoute

    @Serializable public data class Thing(val id: String) : FactsBookRoute

    @Serializable public data class Search(val query: String) : FactsBookRoute

    @Serializable public data object Areas : FactsBookRoute

    /** Set up FactsBook: pair with a node by address and one-time code. */
    @Serializable public data object Connect : FactsBookRoute
}

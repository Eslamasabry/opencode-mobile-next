package io.github.eslamasabry.oc.feature.factsbook.presentation

import io.github.eslamasabry.oc.engine.factsbook.FactsBookEngine
import io.github.eslamasabry.oc.engine.factsbook.FactsBookPhoneNode
import io.github.eslamasabry.oc.engine.factsbook.HttpFactsBookGateway
import kotlinx.coroutines.flow.StateFlow

/**
 * What the app wiring gives the FactsBook pages (bound in :androidApp's FactsBookWiring; a fake in tests): the engine,
 * the connected server profile it reads the pairing of (Flutter: `connProvider.profile`), the hidden developer switch
 * (`oc.dev.factsbook`, About › Developer) and the unauthorized client a pairing starts with.
 */
public interface FactsBookAccess {
    public val engine: FactsBookEngine

    /** The connected server profile, or null with none. */
    public val activeProfileId: StateFlow<String?>

    /** Whether FactsBook shows at all (Flutter `factsBookEnabledProvider`). Off by default. */
    public val enabled: StateFlow<Boolean>

    public suspend fun setEnabled(on: Boolean)

    /** The client for a private [baseUrl] before there is a token. */
    public fun pairingGateway(baseUrl: String): HttpFactsBookGateway

    /** Memory on this phone: FactsBook inside the built-in Linux ([FactsBookPhoneNode.supported] false elsewhere). */
    public val phoneNode: FactsBookPhoneNode

    /** Runs [work] past the page that asked for it (an install keeps going when the person leaves the page). */
    public fun background(work: suspend () -> Unit)
}

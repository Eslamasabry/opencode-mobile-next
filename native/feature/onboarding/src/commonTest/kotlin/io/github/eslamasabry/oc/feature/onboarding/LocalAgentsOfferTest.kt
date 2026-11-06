package io.github.eslamasabry.oc.feature.onboarding

import io.github.eslamasabry.oc.feature.onboarding.presentation.LocalAgentsOffer
import io.github.eslamasabry.oc.feature.onboarding.presentation.LocalAgentsOfferPreferences
import io.github.eslamasabry.oc.feature.onboarding.presentation.LocalAgentsOfferView
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** E00-S06: the local agents offer respects a Not now given in the Flutter app before the update. */
class LocalAgentsOfferTest {
    /** The adopted Flutter preferences, by logical name (the file holds `flutter.` + name). */
    private class Prefs(vararg seed: Pair<String, Any>) : LocalAgentsOfferPreferences {
        val values = mutableMapOf(*seed)

        override suspend fun readBool(key: String): Boolean? = values[key] as? Boolean

        override suspend fun writeBool(key: String, value: Boolean) {
            values[key] = value
        }
    }

    @Test
    fun `B-MIGR-005 local_agents_offer_skipped true from the Flutter app hides the offer`() = runTest {
        // What Flutter 2.1.4 wrote on Not now: <boolean name="flutter.local_agents_offer_skipped" value="true" />.
        val offer = LocalAgentsOffer(Prefs("local_agents_offer_skipped" to true))
        assertEquals(LocalAgentsOfferView.Hidden, offer.showOffer())
    }

    @Test
    fun `B-MIGR-005 with no answer, or a false one, the offer shows`() = runTest {
        assertEquals(LocalAgentsOfferView.Offer, LocalAgentsOffer(Prefs()).showOffer())
        assertEquals(LocalAgentsOfferView.Offer, LocalAgentsOffer(Prefs("local_agents_offer_skipped" to false)).showOffer())
        // A value of the wrong type is not an answer: the offer shows, and the stored value is left as it was.
        val odd = Prefs("local_agents_offer_skipped" to "true")
        assertEquals(LocalAgentsOfferView.Offer, LocalAgentsOffer(odd).showOffer())
        assertEquals("true", odd.values["local_agents_offer_skipped"])
    }

    @Test
    fun `B-MIGR-005 Not now is kept under the Flutter key and type, and asking for agents still shows the offer`() = runTest {
        val prefs = Prefs()
        val offer = LocalAgentsOffer(prefs)
        offer.skip()
        assertEquals(mapOf<String, Any>("local_agents_offer_skipped" to true), prefs.values)
        assertEquals(LocalAgentsOfferView.Hidden, offer.showOffer())
        assertEquals(LocalAgentsOfferView.Offer, offer.showOffer(autoStart = true))
    }
}

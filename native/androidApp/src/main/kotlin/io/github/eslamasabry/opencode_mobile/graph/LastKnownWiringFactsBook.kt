package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.cache.LastKnown
import io.github.eslamasabry.oc.engine.factsbook.FactsBookKept
import io.github.eslamasabry.oc.engine.factsbook.KeptAnswer
import io.github.eslamasabry.oc.engine.factsbook.KeptFactsBook
import kotlinx.serialization.json.JsonElement

/**
 * FactsBook's last answers (overview, things, areas, reminders, triggers), kept per profile and server on one shelf of
 * [KeptFactsBook.SHELF]'s caps (256 KB an answer, 60 answers). The node's address is in each entry's key, so another
 * node's answer never shows; the profile's part goes with the profile (LastKnownDeletion).
 */
class AppFactsBookKept(store: LastKnown, stores: PreviewStores) : FactsBookKept {
    private val answers = ProfileShelf(store, stores, KeptFactsBook.SHELF)

    override suspend fun read(profile: String, key: String): JsonElement? = answers.read(ProfileId(profile), key)?.body

    override suspend fun keep(profile: String, key: String, body: JsonElement) = answers.keep(ProfileId(profile), key, KeptAnswer(body))
}

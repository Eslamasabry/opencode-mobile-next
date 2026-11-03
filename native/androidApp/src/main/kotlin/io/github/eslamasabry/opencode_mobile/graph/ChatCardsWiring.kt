package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.cards.CardJournalStore
import io.github.eslamasabry.oc.engine.cards.CardsPrefs

/**
 * P12-CHAT-CARDS Android bindings: the "Cards from agents" choice under the Flutter key `oc.genui.enabled.<profileId>`
 * (off when absent, so a migrated install keeps the person's choice) and the card dispatch journal under
 * `oc.genui.journal.<profileId>` (B-CHAT-051; the `oc.<what>.<profileId>` shape is swept with the profile). Card links
 * have no binding here: they take the chat's own ask-first link flow (B-SEC-065, AGENTS.md rule 8).
 */
internal class PreviewCardsPrefs(private val stores: PreviewStores) : CardsPrefs {
    override fun enabled(profileId: String): Boolean? =
        try {
            (stores.prefs.get(CardsPrefs.enabledKey(profileId)) as? PrefValue.Bool)?.value
        } catch (e: RuntimeException) {
            null // the store is not open yet: off until it is
        }

    override suspend fun setEnabled(profileId: String, on: Boolean): Outcome<Unit> {
        if (stores.warm() is Outcome.Failed) return Outcome.Failed(Failure.StorageRefused())
        return stores.prefs.commit(stores.lanes.app) { putBool(CardsPrefs.enabledKey(profileId), on) }
    }
}

internal class PreviewCardJournalStore(private val stores: PreviewStores) : CardJournalStore {
    override fun read(profileId: String): String? =
        try {
            (stores.prefs.get(CardsPrefs.journalKey(profileId)) as? PrefValue.Str)?.value
        } catch (e: RuntimeException) {
            CardJournalStore.UNREADABLE // the store is not open yet: fail closed until it is
        }

    override suspend fun write(profileId: String, json: String?): Outcome<Unit> {
        if (stores.warm() is Outcome.Failed) return Outcome.Failed(Failure.StorageRefused())
        val key = CardsPrefs.journalKey(profileId)
        return stores.prefs.commit(stores.lanes.app) { if (json == null) remove(key) else putString(key, json) }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object ChatCardsAndroidBindings {
    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun cardsPrefs(stores: PreviewStores): CardsPrefs = PreviewCardsPrefs(stores)

    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun cardJournal(stores: PreviewStores): CardJournalStore = PreviewCardJournalStore(stores)
}

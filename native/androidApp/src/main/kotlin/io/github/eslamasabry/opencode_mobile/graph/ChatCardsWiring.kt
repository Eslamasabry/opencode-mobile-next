package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.cards.CardJournalStore
import io.github.eslamasabry.oc.engine.cards.CardsPrefs
import io.github.eslamasabry.oc.engine.connection.TransientOwners
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser

/**
 * P12-CHAT-CARDS Android bindings: the "Cards from agents" choice under the Flutter key
 * `oc.genui.enabled.<profileId>` (off when absent, so a migrated install keeps the person's choice)
 * and the card dispatch journal under `oc.genui.journal.<profileId>` (B-CHAT-051; the
 * `oc.<what>.<profileId>` shape is swept with the profile). Card links have no binding here: they
 * take the chat's own ask-first link flow (B-SEC-065, AGENTS.md rule 8).
 */
/** Saved owners fence both aliases and late writes before the generic profile sweep. */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class CardStorageAdmission(
    private val stores: PreviewStores,
    private val transientOwners: TransientOwners,
) : ProfileCloser {
    private val retired = java.util.concurrent.ConcurrentHashMap.newKeySet<ProfileId>()
    override val owner: String = ":androidApp cards storage"

    fun resolve(raw: String): ProfileId? {
        val id = ProfileId(raw)
        val saved = transientOwners.ownerOf(id) ?: id
        return saved.takeIf { it !in retired && stores.profiles.profile(it) != null }
    }

    override fun stopAdmission(profile: ProfileId) {
        retired += profile
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

    override fun reopen(profile: ProfileId) {
        retired -= profile
    }
}

internal class PreviewCardsPrefs(
    private val stores: PreviewStores,
    private val admission: CardStorageAdmission,
) : CardsPrefs {
    override fun enabled(profileId: String): Boolean? {
        val owner = admission.resolve(profileId) ?: return null
        return try {
            (stores.prefs.get(CardsPrefs.enabledKey(owner.raw)) as? PrefValue.Bool)?.value
        } catch (e: RuntimeException) {
            null // the store is not open yet: off until it is
        }
    }

    override suspend fun setEnabled(profileId: String, on: Boolean): Outcome<Unit> {
        if (stores.warm() is Outcome.Failed) return Outcome.Failed(Failure.StorageRefused())
        val owner =
            admission.resolve(profileId)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing))
        return stores.prefs.commit(stores.lanes.forProfile(owner)) {
            putBool(CardsPrefs.enabledKey(owner.raw), on)
        }
    }
}

internal class PreviewCardJournalStore(
    private val stores: PreviewStores,
    private val admission: CardStorageAdmission,
) : CardJournalStore {
    override fun read(profileId: String): String? {
        if (admission.resolve(profileId) == null) return CardJournalStore.UNREADABLE
        // Journal identities retain the actual child profile. The saved owner's writer lane
        // fences deletion, while the generic sweep explicitly includes its .agents keys.
        return try {
            (stores.prefs.get(CardsPrefs.journalKey(profileId)) as? PrefValue.Str)?.value
        } catch (e: RuntimeException) {
            CardJournalStore.UNREADABLE // the store is not open yet: fail closed until it is
        }
    }

    override suspend fun write(profileId: String, json: String?): Outcome<Unit> {
        if (stores.warm() is Outcome.Failed) return Outcome.Failed(Failure.StorageRefused())
        val owner =
            admission.resolve(profileId)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing))
        val key = CardsPrefs.journalKey(profileId)
        return stores.prefs.commit(stores.lanes.forProfile(owner)) {
            if (json == null) remove(key) else putString(key, json)
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object ChatCardsAndroidBindings {
    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun cardsPrefs(stores: PreviewStores, admission: CardStorageAdmission): CardsPrefs =
        PreviewCardsPrefs(stores, admission)

    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun cardJournal(stores: PreviewStores, admission: CardStorageAdmission): CardJournalStore =
        PreviewCardJournalStore(stores, admission)
}

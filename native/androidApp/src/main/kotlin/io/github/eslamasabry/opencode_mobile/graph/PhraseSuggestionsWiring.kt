package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseChip
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseChipStore
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseChipStores
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseMoment
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.feature.chat.composer.presentation.PhraseSuggestions
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@BindingContainer
@ContributesTo(AppScope::class)
object PhraseSuggestionsWiring {
    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun phraseChipStores(stores: PreviewStores, clock: Clock, redactor: Redactor): PhraseChipStores =
        PhraseChipStores(PhraseReadPrefs(PreviewComposerPrefs(stores)), clock, redactor)
}

data class PhraseSettingsSnapshot(val enabled: Boolean, val hiddenCount: Int, val available: Boolean)

/** One native owner for chat and Settings, with all store calls on the same serial lane and deletion admission. */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PhraseSuggestions>())
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class NativePhraseSuggestions(
    private val stores: PreviewStores,
    owners: Set<PhraseChipStores>,
    dispatchers: AppDispatchers,
    private val appScope: AppCoroutineScope,
) : PhraseSuggestions, ProfileCloser {
    private val ownerStore = owners.single()
    private val lane = dispatchers.serial("phrase-suggestions")
    private val closed = ConcurrentHashMap.newKeySet<String>()
    override val changes: StateFlow<Int> = ownerStore.changes
    override val owner: String = ":engine:composer (phrase suggestions)"

    override suspend fun ready(profileId: String): Boolean = withContext(lane) {
        profileId.isNotBlank() && stores.warm() is Outcome.Ok && profileId !in closed && stores.profiles.profile(ProfileId(profileId)) != null
    }

    override suspend fun enabled(): Boolean = withContext(lane) {
        stores.warm() is Outcome.Ok && ownerStore.enabled()
    }

    suspend fun snapshot(profileId: String?): PhraseSettingsSnapshot = withContext(lane) {
        if (stores.warm() !is Outcome.Ok) return@withContext PhraseSettingsSnapshot(false, 0, false)
        val profile = profileId?.takeIf { it.isNotBlank() && it !in closed && stores.profiles.profile(ProfileId(it)) != null }
        PhraseSettingsSnapshot(ownerStore.enabled(), profile?.let { ownerStore.forProfile(it).hiddenCount } ?: 0, true)
    }

    suspend fun setEnabled(value: Boolean): Boolean = withContext(lane) {
        stores.warm() is Outcome.Ok && ownerStore.setEnabled(value)
    }

    suspend fun showHidden(profileId: String): Boolean = withContext(lane) {
        ready(profileId) && ownerStore.forProfile(profileId).showHidden()
    }

    override suspend fun choices(profileId: String, moment: PhraseMoment, starters: List<String>, exclude: Set<String>): List<PhraseChip> = withContext(lane) {
        if (!ready(profileId) || !ownerStore.enabled()) emptyList()
        else ownerStore.forProfile(profileId).chipsFor(moment, starters, exclude)
    }

    override suspend fun record(profileId: String, text: String, moment: PhraseMoment, hasAttachments: Boolean): Boolean = withContext(lane) {
        ready(profileId) && ownerStore.enabled() && ownerStore.forProfile(profileId).record(text, moment, hasAttachments)
    }

    override suspend fun hide(profileId: String, text: String): Boolean = withContext(lane) {
        ready(profileId) && ownerStore.forProfile(profileId).hide(text)
    }

    override fun stopAdmission(profile: ProfileId) { closed += profile.raw }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = withContext(lane) {
        ownerStore.closeProfile(profile.raw)
        Outcome.Ok(Unit)
    }

    override fun reopen(profile: ProfileId) {
        appScope.launch(lane) {
            ownerStore.reopenProfile(profile.raw)
            closed -= profile.raw
        }
    }

    companion object {
        fun register(scope: ProfileScope) {
            scope.register(KeyShape("oc.phraseChips.", KeyScope.Profile, PrefType.Str, ":engine:composer", "local learned and hidden phrases"))
            scope.register(KeyShape(PhraseChipStore.ENABLED_KEY, KeyScope.App, PrefType.Bool, ":engine:composer", "device-wide phrase suggestions switch"))
        }
    }
}

/** A malformed Flutter phrase record follows the engine's corrupt-blob rule; a malformed switch fails closed. */
private class PhraseReadPrefs(private val inner: ComposerPrefs) : ComposerPrefs by inner {
    override fun string(key: String): String? = try { inner.string(key) } catch (failure: IllegalStateException) {
        if (key.startsWith("oc.phraseChips.")) "\u0000" else throw failure
    }
    override fun bool(key: String): Boolean? = try { inner.bool(key) } catch (failure: IllegalStateException) {
        if (key == PhraseChipStore.ENABLED_KEY) false else throw failure
    }
}

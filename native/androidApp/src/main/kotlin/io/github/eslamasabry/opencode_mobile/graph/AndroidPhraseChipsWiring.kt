package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseChipStores
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseMoment
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerPhrases
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ComposerPhrases>())
class AndroidPhraseChips(
    private val stores: PreviewStores,
    clock: Clock,
    redactor: Redactor,
    profileScope: ProfileScope,
) : ComposerPhrases {
    private val phrases = PhraseChipStores(PreviewComposerPrefs(stores), clock, redactor)
    private val closed = AtomicReference<Set<ProfileId>>(emptySet())

    init {
        profileScope.register(
            KeyShape(
                "oc.phraseChips.",
                KeyScope.Profile,
                PrefType.Str,
                ":engine:composer",
                "learned phrase suggestions",
            )
        )
        profileScope.register(
            KeyShape(
                "oc.phraseChips.enabled",
                KeyScope.App,
                PrefType.Bool,
                ":engine:composer",
                "phrase suggestion switch",
            )
        )
    }

    override val changes: Flow<Unit> =
        merge(phrases.changes.map { Unit }, stores.prefs.changes.map { Unit })

    private fun available(profile: ProfileId): Boolean =
        profile !in closed.get() &&
            stores.profiles.profile(profile) != null &&
            stores.lanes.forProfile(profile).isAdmitting

    override fun suggestions(profile: ProfileId, moment: PhraseMoment): List<String> =
        if (available(profile) && phrases.enabled())
            phrases.forProfile(profile.raw).chipsFor(moment).map { it.text }
        else emptyList()

    override suspend fun record(
        profile: ProfileId,
        text: String,
        moment: PhraseMoment,
        attachments: Boolean,
    ) {
        if (available(profile) && phrases.enabled())
            phrases.forProfile(profile.raw).record(text, moment, attachments)
    }

    override suspend fun hide(profile: ProfileId, text: String): Boolean =
        available(profile) && phrases.forProfile(profile.raw).hide(text)

    private fun updateClosed(change: (Set<ProfileId>) -> Set<ProfileId>) {
        while (true) {
            val before = closed.get()
            if (closed.compareAndSet(before, change(before))) break
        }
    }

    fun stop(profile: ProfileId) {
        updateClosed { it + profile }
    }

    suspend fun drain(profile: ProfileId) {
        phrases.closeProfile(profile.raw)
    }

    fun reopen(profile: ProfileId) {
        phrases.reopenProfile(profile.raw)
        updateClosed { it - profile }
    }
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class PhraseChipsDeletion(private val phrases: AndroidPhraseChips) : ProfileCloser {
    override val owner: String = ":engine:composer/phrases"

    override fun stopAdmission(profile: ProfileId) {
        phrases.stop(profile)
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        phrases.drain(profile)
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId) {
        phrases.reopen(profile)
    }
}

package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.flatMap
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.feature.chat.composer.history.ComposerHistory

/** Uses the adopted shelf and its deletion fence; history never gets a second preference cache. */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ComposerHistory>())
class ComposerHistoryWiring(
    private val stores: PreviewStores,
    private val shelf: PromptShelf,
    files: StashPayloads,
) : ComposerHistory {
    private val ready = PreviewShelfReady(stores, shelf, files.store)

    private suspend fun <T> prepared(
        profile: ProfileId,
        command: suspend () -> Outcome<T>,
    ): Outcome<T> =
        attempt({ Failure.StorageRefused() }) {
                when (val preparation = ready.prepare()) {
                    is Outcome.Failed -> preparation
                    is Outcome.Ok ->
                        if (stores.profiles.profile(profile) == null)
                            Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
                        else command()
                }
            }
            .flatMap { it }

    override suspend fun recent(profile: ProfileId): Outcome<List<String>> =
        prepared(profile) { Outcome.Ok(shelf.history(profile.raw)) }

    override suspend fun record(profile: ProfileId, text: String): Outcome<Unit> =
        prepared(profile) { shelf.recordSent(profile.raw, text) }
}

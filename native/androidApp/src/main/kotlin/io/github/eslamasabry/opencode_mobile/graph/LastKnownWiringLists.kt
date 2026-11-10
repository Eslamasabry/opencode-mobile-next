package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.core.data.cache.LastKnown
import io.github.eslamasabry.oc.core.data.cache.ServerMark
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.feature.plans.presentation.KeptPlans
import io.github.eslamasabry.oc.feature.plans.presentation.PlansLastKnown
import io.github.eslamasabry.oc.feature.prompts.presentation.KeptServerList
import io.github.eslamasabry.oc.feature.prompts.presentation.ServerListsLastKnown
import io.github.eslamasabry.oc.feature.review.presentation.KeptReview
import io.github.eslamasabry.oc.feature.review.presentation.ReviewLastKnown
import io.github.eslamasabry.oc.feature.schedules.presentation.KeptScheduleEditor
import io.github.eslamasabry.oc.feature.schedules.presentation.KeptSchedules
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditorLastKnown
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesLastKnown

/*
 * Cache-first (owner rule, 2026-11-09), lists and editors: Schedules, Review, Prompts' server lists and Plans keep
 * what they last showed on a ProfileShelf (see LastKnownWiring.kt) and draw it first on the next open. Profile
 * deletion sweeps them through LastKnownDeletion.
 */

/** The scheduled prompts' list as last shown, per profile and server; 64 KB at most. */
@ContributesIntoSet(AppScope::class, binding = binding<SchedulesLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppSchedulesLastKnown(store: LastKnown, stores: PreviewStores) : SchedulesLastKnown {
    private val lists = ProfileShelf(store, stores, SCHEDULES)

    override fun peek(profile: ProfileId): KeptSchedules? = lists.peek(profile)

    override suspend fun read(profile: ProfileId): KeptSchedules? = lists.read(profile)

    override suspend fun keep(profile: ProfileId, kept: KeptSchedules) = lists.keep(profile, value = kept)

    companion object {
        val SCHEDULES = CacheShelf("schedules", KeptSchedules.serializer(), maxBytes = 64 * 1024, maxEntries = 1)
    }
}

/** The schedule editor's fields and model choices as last shown (one entry per schedule, and one for a new one). */
@ContributesIntoSet(AppScope::class, binding = binding<ScheduleEditorLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppScheduleEditorLastKnown(store: LastKnown, stores: PreviewStores) : ScheduleEditorLastKnown {
    private val editors = ProfileShelf(store, stores, EDITORS)

    override fun peek(profile: ProfileId, item: String): KeptScheduleEditor? = editors.peek(profile, item)

    override suspend fun read(profile: ProfileId, item: String): KeptScheduleEditor? = editors.read(profile, item)

    override suspend fun keep(profile: ProfileId, item: String, kept: KeptScheduleEditor) =
        editors.keep(profile, item, kept)

    companion object {
        val EDITORS = CacheShelf("scheduleEdit", KeptScheduleEditor.serializer(), maxBytes = 32 * 1024, maxEntries = 30)
    }
}

/** Review's changed files, one file's diff and the staged undo, per profile and server and conversation. */
@ContributesIntoSet(AppScope::class, binding = binding<ReviewLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppReviewLastKnown(store: LastKnown, stores: PreviewStores) : ReviewLastKnown {
    private val pages = ProfileShelf(store, stores, REVIEW)

    private fun key(ref: ConversationRef, item: String) = "${ref.sessionId.raw}|$item"

    override fun peek(ref: ConversationRef, item: String): KeptReview? = pages.peek(ref.profileId, key(ref, item))

    override suspend fun read(ref: ConversationRef, item: String): KeptReview? = pages.read(ref.profileId, key(ref, item))

    override suspend fun keep(ref: ConversationRef, item: String, kept: KeptReview) =
        pages.keep(ref.profileId, key(ref, item), kept)

    companion object {
        /** 60 pages per profile (changes lists, diffs of up to 150k characters, staged undos). */
        val REVIEW = CacheShelf("review", KeptReview.serializer(), maxBytes = 512 * 1024, maxEntries = 60)
    }
}

/** The slash commands, skills and task list last seen, per profile and server and conversation. */
@ContributesIntoSet(AppScope::class, binding = binding<ServerListsLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppServerListsLastKnown(store: LastKnown, stores: PreviewStores) : ServerListsLastKnown {
    private val lists = ProfileShelf(store, stores, LISTS)

    private fun key(ref: ConversationRef, item: String) = "${ref.sessionId.raw}|$item"

    override fun peek(ref: ConversationRef, item: String): KeptServerList? = lists.peek(ref.profileId, key(ref, item))

    override suspend fun read(ref: ConversationRef, item: String): KeptServerList? =
        lists.read(ref.profileId, key(ref, item))

    override suspend fun keep(ref: ConversationRef, item: String, kept: KeptServerList) =
        lists.keep(ref.profileId, key(ref, item), kept)

    companion object {
        val LISTS = CacheShelf("serverLists", kotlinx.serialization.serializer<KeptServerList>(), maxBytes = 96 * 1024, maxEntries = 40)
    }
}

/**
 * Plans merges every saved server, so its one entry is marked with the whole set of saved servers: a server added,
 * changed or deleted makes the old cards invisible (and any profile deletion clears the entry, below).
 */
@ContributesIntoSet(AppScope::class, binding = binding<PlansLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppPlansLastKnown(private val store: LastKnown, private val stores: PreviewStores) : PlansLastKnown {
    private fun mark(): ServerMark? {
        val all = stores.profiles.profiles.value
        if (all.isEmpty()) return null
        return ServerMark.of("plans", *all.map { it.profile.id.raw + "|" + it.profile.lastKnownMark().value }.sorted().toTypedArray())
    }

    override fun peek(): KeptPlans? = mark()?.let { store.peek(PLANS_OWNER, it, PLANS) }

    override suspend fun read(): KeptPlans? = mark()?.let { store.read(PLANS_OWNER, it, PLANS) }

    override suspend fun keep(kept: KeptPlans) {
        val mark = mark() ?: return
        store.write(PLANS_OWNER, mark, PLANS, value = kept)
    }

    companion object {
        /** Not a profile: the owner of the one cross-server entry. */
        val PLANS_OWNER = ProfileId("plans-all-servers")
        val PLANS = CacheShelf("plans", kotlinx.serialization.serializer<KeptPlans>(), maxBytes = 96 * 1024, maxEntries = 1)
    }
}

/** Deleting any profile also drops the cross-server Plans entry (it named that server). */
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class PlansLastKnownDeletion(private val store: LastKnown) : ProfileResourceCleaner {
    override val owner: String = ":feature:plans (last-known cards across servers)"
    override val order: Int = DeletionStep.DEVICE_SURFACES

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        store.clear(AppPlansLastKnown.PLANS_OWNER)
}

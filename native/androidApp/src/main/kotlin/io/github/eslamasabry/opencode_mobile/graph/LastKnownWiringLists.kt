package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.core.data.cache.LastKnown
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

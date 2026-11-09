package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.engine.integrations.catalog.KeptLibrary
import io.github.eslamasabry.oc.engine.integrations.catalog.LibraryLastKnown
import io.github.eslamasabry.oc.feature.projects.presentation.KeptWorkspace
import io.github.eslamasabry.oc.feature.projects.presentation.NoWorkspaceLastKnown
import io.github.eslamasabry.oc.feature.projects.presentation.WorkspaceLastKnown

/*
 * Cache-first ports for the Tools library and a project's checkout sheet (see LastKnownWiring.kt for the pattern).
 * Both live on the one last-known store, so LastKnownDeletion removes them with the profile.
 */

/** The Tools library's lists, kept per profile and server and folder; 20 places, 384 KB each at most. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AppLibraryLastKnown(store: io.github.eslamasabry.oc.core.data.cache.LastKnown, stores: PreviewStores) : LibraryLastKnown {
    private val lists = ProfileShelf(store, stores, LIBRARY)

    override fun peek(profile: ProfileId, place: String): KeptLibrary? = lists.peek(profile, place)

    override suspend fun keep(profile: ProfileId, place: String, kept: KeptLibrary) = lists.keep(profile, place, kept)

    companion object {
        val LIBRARY = CacheShelf("library", KeptLibrary.serializer(), maxBytes = 384 * 1024, maxEntries = 20)
    }
}

/** A project's checkout sheet, kept per profile and server and folder; 40 folders, 96 KB each at most. */
@ContributesBinding(AppScope::class, replaces = [NoWorkspaceLastKnown::class])
@SingleIn(AppScope::class)
@Inject
class AppWorkspaceLastKnown(store: io.github.eslamasabry.oc.core.data.cache.LastKnown, stores: PreviewStores) : WorkspaceLastKnown {
    private val sheets = ProfileShelf(store, stores, WORKSPACE)

    override fun peek(profile: ProfileId, directory: String): KeptWorkspace? = sheets.peek(profile, directory)

    override suspend fun keep(profile: ProfileId, directory: String, kept: KeptWorkspace) =
        sheets.keep(profile, directory, kept)

    companion object {
        val WORKSPACE = CacheShelf("workspace", KeptWorkspace.serializer(), maxBytes = 96 * 1024, maxEntries = 40)
    }
}

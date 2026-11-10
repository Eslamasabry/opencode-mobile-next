package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.core.data.cache.LastKnown
import io.github.eslamasabry.oc.feature.chats.attention.AttentionLastKnown
import io.github.eslamasabry.oc.feature.chats.attention.KeptAttention
import io.github.eslamasabry.oc.feature.chats.presentation.ImportsLastKnown
import io.github.eslamasabry.oc.feature.chats.presentation.KeptImports

/** Needs you's last rows, kept per profile and server; 64 KB at most. Swept with the profile (LastKnownDeletion). */
@ContributesIntoSet(AppScope::class, binding = binding<AttentionLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppAttentionLastKnown(store: LastKnown, stores: PreviewStores) : AttentionLastKnown {
    private val pages = ProfileShelf(store, stores, ATTENTION)

    override fun peek(profile: String): KeptAttention? = pages.peek(ProfileId(profile))

    override suspend fun read(profile: String): KeptAttention? = pages.read(ProfileId(profile))

    override suspend fun keep(profile: String, kept: KeptAttention) = pages.keep(ProfileId(profile), value = kept)

    companion object {
        val ATTENTION = CacheShelf("attention", KeptAttention.serializer(), maxBytes = 64 * 1024, maxEntries = 1)
    }
}

/** The Claude import list last read for a folder, kept per profile and server and folder; 20 folders, 64 KB each. */
@ContributesIntoSet(AppScope::class, binding = binding<ImportsLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppImportsLastKnown(store: LastKnown, stores: PreviewStores) : ImportsLastKnown {
    private val lists = ProfileShelf(store, stores, IMPORTS)

    override fun peek(profile: ProfileId, directory: String): KeptImports? = lists.peek(profile, directory)

    override suspend fun read(profile: ProfileId, directory: String): KeptImports? = lists.read(profile, directory)

    override suspend fun keep(profile: ProfileId, directory: String, kept: KeptImports) =
        lists.keep(profile, directory, kept)

    companion object {
        val IMPORTS = CacheShelf("claudeImports", KeptImports.serializer(), maxBytes = 64 * 1024, maxEntries = 20)
    }
}

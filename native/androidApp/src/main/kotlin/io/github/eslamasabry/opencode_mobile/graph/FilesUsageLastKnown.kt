package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.core.data.cache.LastKnown
import io.github.eslamasabry.oc.feature.files.presentation.FilesLastKnown
import io.github.eslamasabry.oc.feature.files.presentation.KeptFile
import io.github.eslamasabry.oc.feature.files.presentation.KeptFolder
import io.github.eslamasabry.oc.feature.usage.presentation.KeptUsageReport
import io.github.eslamasabry.oc.feature.usage.presentation.UsageLastKnown

/*
 * Cache-first (owner rule, 2026-11-09): Files and Usage keep what they showed on shelves of the last-known store.
 * The kept classes live in the features (this module has no serialization plugin). Names and numbers only: no file
 * is kept unless it is plain text, and no usage value carries a key or an account.
 */

/** Folder listings (60 per profile) and plain-text files (20 per profile, 320 KB each at most). */
@ContributesIntoSet(AppScope::class, binding = binding<FilesLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppFilesLastKnown(store: LastKnown, stores: PreviewStores) : FilesLastKnown {
    private val folders = ProfileShelf(store, stores, FOLDERS)
    private val files = ProfileShelf(store, stores, FILES)

    override fun peekFolder(profile: ProfileId, key: String): KeptFolder? = folders.peek(profile, key)

    override suspend fun readFolder(profile: ProfileId, key: String): KeptFolder? = folders.read(profile, key)

    override suspend fun keepFolder(profile: ProfileId, key: String, folder: KeptFolder) =
        folders.keep(profile, key, folder)

    override fun peekFile(profile: ProfileId, key: String): KeptFile? = files.peek(profile, key)

    override suspend fun readFile(profile: ProfileId, key: String): KeptFile? = files.read(profile, key)

    override suspend fun keepFile(profile: ProfileId, key: String, file: KeptFile) {
        if (file.text.length <= FilesLastKnown.MAX_TEXT_CHARS) files.keep(profile, key, file)
    }

    companion object {
        val FOLDERS = CacheShelf("folder", KeptFolder.serializer(), maxBytes = 256 * 1024, maxEntries = 60)
        val FILES = CacheShelf("file", KeptFile.serializer(), maxBytes = 320 * 1024, maxEntries = 20)
    }
}

/** The last usage report per server, range, project and time zone (12 per profile). */
@ContributesIntoSet(AppScope::class, binding = binding<UsageLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppUsageLastKnown(store: LastKnown, stores: PreviewStores) : UsageLastKnown {
    private val reports = ProfileShelf(store, stores, REPORTS)

    override fun peek(profile: ProfileId, key: String): KeptUsageReport? = reports.peek(profile, key)

    override suspend fun read(profile: ProfileId, key: String): KeptUsageReport? = reports.read(profile, key)

    override suspend fun keep(profile: ProfileId, key: String, report: KeptUsageReport) =
        reports.keep(profile, key, report)

    companion object {
        val REPORTS = CacheShelf("usage", KeptUsageReport.serializer(), maxBytes = 128 * 1024, maxEntries = 12)
    }
}

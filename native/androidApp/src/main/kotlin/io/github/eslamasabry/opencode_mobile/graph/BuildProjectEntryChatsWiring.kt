package io.github.eslamasabry.opencode_mobile.graph

import android.app.ActivityManager
import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.about.managespace.ManageSpaceFacts
import io.github.eslamasabry.oc.feature.about.managespace.ManageSpacePort
import java.io.File
import kotlinx.coroutines.withContext

/**
 * Unit P12-BUILD-PROJECT-ENTRY-CHATS: the Android-only bindings of :feature:about and :engine:project. Today: the
 * manage-space guard page (B-ANDR-035) that Android opens from Settings › Apps › Clear storage.
 */
@ContributesTo(AppScope::class)
interface BuildProjectEntryChatsGraph {
    val manageSpacePort: AndroidManageSpacePort
}

/** Measures and clears the app's cache folders, and hands the full clear to Android. Never reads file contents. */
@SingleIn(AppScope::class)
@Inject
class AndroidManageSpacePort(
    private val application: Application,
    private val dispatchers: AppDispatchers,
    private val connections: ConnectionRegistry,
) : ManageSpacePort {
    private val cacheRoots: List<File> get() = listOfNotNull(application.cacheDir, application.codeCacheDir, application.externalCacheDir)

    override suspend fun measure(): Outcome<ManageSpaceFacts> =
        withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                ManageSpaceFacts(
                    cacheBytes = cacheRoots.sumOf(::sizeOf),
                    savedServers = connections.servers.value.size,
                    phoneServer = false, // the built-in Linux is not in this build
                )
            }
        }

    override suspend fun clearCache(): Outcome<Long> =
        withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                var freed = 0L
                cacheRoots.forEach { root ->
                    root.listFiles()?.forEach { child ->
                        val size = sizeOf(child)
                        if (child.deleteRecursively()) freed += size
                    }
                }
                freed
            }
        }

    override suspend fun clearAllData(): Boolean =
        withContext(dispatchers.main) {
            val cleared =
                attempt({ Failure.Device() }) {
                    application.getSystemService(ActivityManager::class.java).clearApplicationUserData()
                }
            (cleared as? Outcome.Ok)?.value == true
        }

    private fun sizeOf(file: File): Long =
        when {
            !file.exists() -> 0L
            file.isFile -> file.length()
            else -> file.walkTopDown().onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }.filter { it.isFile }.sumOf { it.length() }
        }
}

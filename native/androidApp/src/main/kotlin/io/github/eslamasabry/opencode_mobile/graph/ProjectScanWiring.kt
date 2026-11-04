package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.Environment
import android.system.Os
import android.system.OsConstants
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.engine.files.PhoneProjectScanner
import io.github.eslamasabry.oc.engine.files.ProjectScanFiles
import io.github.eslamasabry.oc.engine.files.ProjectScanEnd
import io.github.eslamasabry.oc.engine.files.ProjectScanResult
import io.github.eslamasabry.oc.engine.files.ProjectScanRoot
import io.github.eslamasabry.oc.engine.files.ScanEntry
import io.github.eslamasabry.oc.feature.projects.presentation.NoPhoneProjectDiscovery
import io.github.eslamasabry.oc.feature.projects.presentation.PhoneProjectDiscovery
import java.io.File
import kotlin.time.Clock
import kotlinx.coroutines.withContext

/** Reads the retained project space first, then phone storage; it never follows directory links. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoPhoneProjectDiscovery::class])
@Inject
class AndroidPhoneProjectDiscovery(
    private val app: Application,
    private val stores: PreviewStores,
    private val platform: PlatformCapabilities,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
) : PhoneProjectDiscovery {
    override fun available(profile: ProfileId): Boolean {
        if (!platform.builtinLinux) return false
        val url = stores.profiles.profile(profile)?.profile?.baseUrl ?: return false
        return url.trimEnd('/') == "http://127.0.0.1:4097"
    }

    override suspend fun scan(profile: ProfileId): ProjectScanResult =
        withContext(dispatchers.io) {
            if (!available(profile))
                return@withContext ProjectScanResult(emptyList(), 0, ProjectScanEnd.Completed)
            val files = ProjectScanFiles { path ->
                try {
                    val root = File(path)
                    val mode = Os.lstat(path).st_mode
                    if (!OsConstants.S_ISDIR(mode) || OsConstants.S_ISLNK(mode)) null
                    else
                        root.listFiles()?.mapNotNull { file ->
                            try {
                                val stat = Os.lstat(file.absolutePath)
                                ScanEntry(
                                    file.name,
                                    OsConstants.S_ISDIR(stat.st_mode),
                                    OsConstants.S_ISLNK(stat.st_mode),
                                )
                            } catch (_: android.system.ErrnoException) {
                                null
                            }
                        }
                } catch (_: android.system.ErrnoException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            val retained = File(app.filesDir, "projects")
            val legacy = BuiltinDiscovery.projects(app.filesDir)
            val projectRoot = if (retained.isDirectory) retained else legacy
            PhoneProjectScanner(files, clock)
                .scan(
                    listOf(
                        ProjectScanRoot(
                            projectRoot.absolutePath,
                            "/root/projects",
                            childrenAreProjects = true,
                        ),
                        ProjectScanRoot(Environment.getExternalStorageDirectory().absolutePath),
                    )
                )
        }
}

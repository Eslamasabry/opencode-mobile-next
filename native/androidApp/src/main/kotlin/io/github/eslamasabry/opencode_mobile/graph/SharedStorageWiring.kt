package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.termux.AndroidGitRepairTermuxFiles
import io.github.eslamasabry.oc.android.termux.GitRepairTermuxState
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageAccess
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageBlock
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.ProfileOrigins
import io.github.eslamasabry.oc.engine.runtime.paths.SharedStoragePath
import java.net.URI
import kotlinx.coroutines.withContext

/**
 * The app and Termux have different Android UIDs. A remote server's /sdcard never requests a phone
 * permission.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<SharedStorageAccess>())
@Inject
class AndroidSharedStorageAccess(
    private val application: Application,
    private val activities: ResumedActivities,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val dispatchers: AppDispatchers,
) : SharedStorageAccess {
    private val termux by lazy { AndroidGitRepairTermuxFiles(application, dispatchers) }

    override suspend fun check(
        profile: ProfileId,
        directory: String?,
        expectedSource: Any?,
    ): Outcome<SharedStorageBlock> {
        if (stores.warm() is Outcome.Failed) return Failure.StorageRefused().failed()
        val source =
            stores.profiles.profile(profile)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val graph = registry.live.value[profile]
        if (
            graph == null ||
                (expectedSource !== graph && expectedSource !== graph.session.server) ||
                ProfileOrigins.serverOrigin(source.profile.baseUrl) != graph.profile.origin
        )
            return Failure.Refused(RefusalReason.StaleScope).failed()
        fun current(): Boolean =
            stores.profiles.profile(profile)?.profile == source.profile &&
                registry.live.value[profile] === graph
        if (!SharedStoragePath.isShared(directory) || source.profile.usesAgentSocket)
            return SharedStorageBlock.None.ok()
        val origin =
            try {
                URI(source.profile.baseUrl)
            } catch (_: Exception) {
                return SharedStorageBlock.None.ok()
            }
        // These are the two managed local hosts, not backend-driven feature visibility.
        if (origin.scheme != "http" || origin.host != "127.0.0.1")
            return SharedStorageBlock.None.ok()
        val block =
            when (origin.port) {
                PhoneServerScripts.PORT ->
                    when (val granted = attempt({ Failure.StorageRefused() }) { appGranted() }) {
                        is Outcome.Failed ->
                            SharedStorageBlock.None // unknown: let the actual file operation answer
                        is Outcome.Ok ->
                            if (granted.value) SharedStorageBlock.None
                            else SharedStorageBlock.AppAccess
                    }
                4096 ->
                    withContext(dispatchers.io) {
                        if (termux.available() != GitRepairTermuxState.Ready)
                            SharedStorageBlock.None
                        else
                            when (termux.sharedStorageReadable()) {
                                true -> SharedStorageBlock.None
                                false -> SharedStorageBlock.TermuxAccess
                                null ->
                                    SharedStorageBlock
                                        .None // unknown is not a grant; attempt the real file
                            // operation
                            }
                    }
                else -> SharedStorageBlock.None
            }
        return if (current()) block.ok() else Failure.Refused(RefusalReason.StaleScope).failed()
    }

    @Suppress("DEPRECATION")
    private fun appGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else
            application.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED &&
                application.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED

    override suspend fun request(
        profile: ProfileId,
        directory: String?,
        expected: SharedStorageBlock,
        expectedSource: Any?,
    ): Outcome<Unit> {
        if (stores.warm() is Outcome.Failed) return Failure.StorageRefused().failed()
        val source =
            stores.profiles.profile(profile)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val graph = registry.live.value[profile]
        val now =
            when (val checked = check(profile, directory, expectedSource)) {
                is Outcome.Failed -> return checked
                is Outcome.Ok -> checked.value
            }
        if (now == SharedStorageBlock.None) return Unit.ok()
        if (now != expected) return Failure.Refused(RefusalReason.StaleScope).failed()
        return withContext(dispatchers.main) {
            if (
                stores.profiles.profile(profile)?.profile != source.profile ||
                    registry.live.value[profile] !== graph
            ) {
                return@withContext Failure.Refused(RefusalReason.StaleScope).failed()
            }
            attempt({ Failure.StorageRefused() }) {
                when (now) {
                    SharedStorageBlock.None -> Unit
                    SharedStorageBlock.AppAccess -> {
                        val activity = activities.current ?: error("no activity")
                        if (Build.VERSION.SDK_INT >= 30) {
                            try {
                                activity.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${application.packageName}"),
                                    )
                                )
                            } catch (_: ActivityNotFoundException) {
                                activity.startActivity(
                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                )
                            }
                        } else {
                            activity.requestPermissions(
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                ),
                                4711,
                            )
                        }
                    }
                    SharedStorageBlock.TermuxAccess -> {
                        val launch =
                            application.packageManager.getLaunchIntentForPackage("com.termux")
                                ?: error("Termux unavailable")
                        application.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
}

package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.voice.device.AndroidDeviceProbe
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.engine.runtime.paths.PhoneFolderChild
import io.github.eslamasabry.oc.engine.runtime.paths.PhoneFolderFiles
import io.github.eslamasabry.oc.engine.runtime.paths.PhoneStorageFolders
import io.github.eslamasabry.oc.engine.runtime.projects.ProjectStorage
import io.github.eslamasabry.oc.engine.runtime.storage.PhoneStorage
import io.github.eslamasabry.oc.engine.runtime.storage.PhoneStorageSnapshot
import io.github.eslamasabry.oc.engine.runtime.storage.PhoneStorageSources
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbeResult
import io.github.eslamasabry.oc.feature.phone.presentation.NoPhoneStorageReadings
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneStorageReadings
import io.github.eslamasabry.oc.feature.projects.presentation.NoPhoneFolders
import io.github.eslamasabry.oc.feature.projects.presentation.PhoneFolderUi
import io.github.eslamasabry.oc.feature.projects.presentation.PhoneFolders
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext

/**
 * Read-only device sources already used by voice and Linux; one source failing never hides the
 * other.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoPhoneStorageReadings::class])
@Inject
class AndroidPhoneStorageReadings(
    app: Application,
    dispatchers: AppDispatchers,
    capabilities: PlatformCapabilities,
) : PhoneStorageReadings {
    override val supported: Boolean = capabilities.builtinLinux
    private val probe = AndroidDeviceProbe(app, dispatchers.io)
    private val storage =
        PhoneStorage(
            object : PhoneStorageSources {
                override suspend fun availableBytes(): Outcome<Long?> =
                    attempt({ Failure.Runtime(RuntimeProblem.StorageUnavailable) }) {
                        (probe.probe() as? DeviceProbeResult.Known)?.availableStorageBytes
                    }

                override suspend fun builtinStorage(): Outcome<ProjectStorage> =
                    withContext(dispatchers.io) {
                        when (
                            val read =
                                attempt({ Failure.Runtime(RuntimeProblem.StorageUnavailable) }) {
                                    BuiltinLinux.get(app).projectStorage()
                                }
                        ) {
                            is Outcome.Failed -> read
                            is Outcome.Ok ->
                                ProjectStorage.fromReply(read.value)?.let { Outcome.Ok(it) }
                                    ?: Outcome.Failed(
                                        Failure.Runtime(RuntimeProblem.StorageUnavailable)
                                    )
                        }
                    }
            },
            isSupported = supported,
        )

    override suspend fun read(includeBuiltinHost: Boolean): PhoneStorageSnapshot =
        storage.read(includeBuiltinHost)
}

/**
 * No-follow filesystem adapter. A symlink in a shared path cannot turn the internal-only browser
 * into another root.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoPhoneFolders::class])
@Inject
class AndroidPhoneFolders(
    private val application: Application,
    private val dispatchers: AppDispatchers,
) : PhoneFolders {
    private val folders =
        PhoneStorageFolders(
            object : PhoneFolderFiles {
                override suspend fun isDirectory(path: String): Boolean =
                    withContext(dispatchers.io) {
                        // Existing/missing/denied are distinguished by children(), including an
                        // unreadable directory.
                        confined(path)
                    }

                override suspend fun children(path: String): Outcome<List<PhoneFolderChild>> =
                    withContext(dispatchers.io) {
                        attempt(::folderFailure) {
                            if (!canReadStorage() || !confined(path))
                                throw AccessDeniedException(path)
                            val directory = Paths.get(path)
                            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS))
                                throw NoSuchFileException(path)
                            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                                throw NoSuchFileException(path)
                            // A link swapped in after confined() must not lead outside storage.
                            val root = Paths.get(PhoneStorageFolders.ROOT).toRealPath()
                            if (!directory.toRealPath().startsWith(root))
                                throw AccessDeniedException(path)
                            Files.newDirectoryStream(directory).use { children ->
                                children
                                    .map { child ->
                                        PhoneFolderChild(
                                            child.fileName.toString(),
                                            Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS),
                                        )
                                    }
                                    .toList()
                            }
                        }
                    }
            }
        )

    override suspend fun list(path: String): Outcome<ImmutableList<PhoneFolderUi>> =
        folders.list(path).map { rows ->
            rows
                // Flutter's browser hides dot folders (.thumbnails and the like).
                .filterNot { it.name.startsWith(".") }
                .map {
                    PhoneFolderUi(it.name, it.path, it.inside?.joinToString(", ")?.ifBlank { null })
                }
                .toImmutableList()
        }

    override suspend fun requestAccess(): Outcome<Unit> =
        withContext(dispatchers.main) {
            attempt({ Failure.Device() }) {
                val intent =
                    if (Build.VERSION.SDK_INT >= 30)
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${application.packageName}"),
                        )
                    else
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${application.packageName}"),
                        )
                application.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

    private fun canReadStorage(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else
            application.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private fun confined(path: String): Boolean {
        val normalized = PhoneStorageFolders.normalize(path) ?: return false
        val root = Paths.get(PhoneStorageFolders.ROOT)
        val target = Paths.get(normalized).normalize()
        if (!target.startsWith(root)) return false
        var current = root
        for (part in root.relativize(target)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) return false
        }
        return true
    }

    private fun folderFailure(error: Throwable): Failure =
        when (error) {
            is NoSuchFileException -> Failure.Folder(FolderProblem.Missing)
            is AccessDeniedException,
            is SecurityException -> Failure.Folder(FolderProblem.Denied)
            else -> Failure.Device()
        }
}

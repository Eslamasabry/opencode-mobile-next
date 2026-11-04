package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.Settings
import android.system.ErrnoException
import android.system.OsConstants
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.feature.phone.storage.ProjectExportProblem
import io.github.eslamasabry.oc.feature.phone.storage.ProjectExportProgress
import io.github.eslamasabry.oc.feature.phone.storage.ProjectExportReceipt
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorage
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageFacts
import io.github.eslamasabry.oc.feature.phone.storage.UnavailableProjectStorage
import java.io.IOException
import java.util.WeakHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Shared by project export and Linux backups. Only foreground callers keep a write alive. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [UnavailableProjectStorage::class])
@Inject
class AndroidProjectExport(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val prefs: KeyValueStore,
    private val clock: Clock,
    private val scope: AppCoroutineScope,
) : ProjectStorage {
    private val files = NativeProjectFiles(app)
    private val operations = Mutex()
    private val hosts = WeakHashMap<ComponentActivity, Host>()
    private var active: Host? = null

    private class Host {
        lateinit var destination: ActivityResultLauncher<String>
        lateinit var source: ActivityResultLauncher<Array<String>>
        lateinit var storageSettings: ActivityResultLauncher<Intent>
        lateinit var storagePermissions: ActivityResultLauncher<Array<String>>
        var storageResult: CompletableDeferred<Boolean>? = null
        var destinationResult: CompletableDeferred<Uri?>? = null
        var sourceResult: CompletableDeferred<Uri?>? = null

        fun close() {
            destinationResult?.complete(null)
            sourceResult?.complete(null)
            storageResult?.complete(false)
            destinationResult = null
            sourceResult = null
            storageResult = null
        }
    }

    /**
     * Fixed launchers register before STARTED. Cancelled requests retain their slot until their own
     * result returns.
     */
    fun attach(activity: ComponentActivity) {
        val host =
            hosts[activity]
                ?: Host().also { host ->
                    host.destination =
                        activity.registerForActivityResult(
                            ActivityResultContracts.CreateDocument("application/zip")
                        ) { uri ->
                            val result = host.destinationResult
                            host.destinationResult = null
                            if (result != null && !result.complete(uri) && uri != null)
                                scope.launch(dispatchers.io) { discard(uri) }
                        }
                    host.source =
                        activity.registerForActivityResult(
                            ActivityResultContracts.OpenDocument()
                        ) { uri ->
                            val result = host.sourceResult
                            host.sourceResult = null
                            result?.complete(uri)
                        }
                    host.storageSettings =
                        activity.registerForActivityResult(
                            ActivityResultContracts.StartActivityForResult()
                        ) {
                            val result = host.storageResult
                            host.storageResult = null
                            result?.complete(storagePermissionGranted() == true)
                        }
                    host.storagePermissions =
                        activity.registerForActivityResult(
                            ActivityResultContracts.RequestMultiplePermissions()
                        ) {
                            val result = host.storageResult
                            host.storageResult = null
                            result?.complete(storagePermissionGranted() == true)
                        }
                    hosts[activity] = host
                }
        active = host
    }

    fun activate(activity: ComponentActivity) {
        active = hosts[activity]
    }

    fun detach(activity: ComponentActivity) {
        hosts.remove(activity)?.let { host ->
            if (active === host) active = null
            host.close()
        }
    }

    suspend fun pickDestination(suggestedName: String): Outcome<String?> =
        withContext(dispatchers.main) {
            val host = active ?: return@withContext Outcome.Failed(Failure.Device())
            if (host.destinationResult != null) return@withContext Outcome.Failed(Failure.Busy())
            val result = CompletableDeferred<Uri?>()
            host.destinationResult = result
            try {
                host.destination.launch(suggestedName)
            } catch (_: RuntimeException) {
                host.destinationResult = null
                return@withContext Outcome.Failed(Failure.Device())
            }
            try {
                Outcome.Ok(result.await()?.toString())
            } catch (cancelled: CancellationException) {
                result.cancel()
                throw cancelled
            }
        }

    suspend fun pickArchive(): Outcome<String?> =
        withContext(dispatchers.main) {
            val host = active ?: return@withContext Outcome.Failed(Failure.Device())
            if (host.sourceResult != null) return@withContext Outcome.Failed(Failure.Busy())
            val result = CompletableDeferred<Uri?>()
            host.sourceResult = result
            try {
                host.source.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream",
                    )
                )
            } catch (_: RuntimeException) {
                host.sourceResult = null
                return@withContext Outcome.Failed(Failure.Device())
            }
            try {
                Outcome.Ok(result.await()?.toString())
            } catch (cancelled: CancellationException) {
                result.cancel()
                throw cancelled
            }
        }

    /**
     * Android grants raw-path access to the app UID; proot still receives only its selected mount
     * roots.
     */
    fun storagePermissionGranted(): Boolean? =
        try {
            if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
            else
                app.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED &&
                    app.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            null
        }

    suspend fun requestStoragePermission(): Outcome<Boolean> =
        withContext(dispatchers.main) {
            if (storagePermissionGranted() == true) return@withContext Outcome.Ok(true)
            val host = active ?: return@withContext Outcome.Failed(Failure.Device())
            if (host.storageResult != null) return@withContext Outcome.Failed(Failure.Busy())
            val result = CompletableDeferred<Boolean>()
            host.storageResult = result
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        host.storageSettings.launch(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${app.packageName}"),
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        host.storageSettings.launch(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    }
                } else
                    host.storagePermissions.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        )
                    )
            } catch (_: RuntimeException) {
                host.storageResult = null
                return@withContext Outcome.Failed(Failure.Device())
            }
            try {
                Outcome.Ok(result.await())
            } catch (cancelled: CancellationException) {
                result.cancel()
                throw cancelled
            }
        }

    override suspend fun measure(): Outcome<ProjectStorageFacts> =
        withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                val servers = prefs.string("oc.profiles")?.let { JSONArray(it).length() } ?: 0
                files.measure(servers).copy(cacheBytes = files.cache(false))
            }
        }

    override suspend fun export(
        includePrivate: Boolean,
        progress: (ProjectExportProgress) -> Unit,
        problem: (ProjectExportProblem) -> Unit,
    ): Outcome<ProjectExportReceipt?> {
        if (!operations.tryLock()) return Outcome.Failed(Failure.Busy())
        var destination: String? = null
        var complete = false
        try {
            val day = clock.now().toString().take(10)
            problem(ProjectExportProblem.Destination)
            val picked =
                pickDestination(
                    "opencode-projects${if (includePrivate) "-private" else ""}-$day.zip"
                )
            val target =
                when (picked) {
                    is Outcome.Failed -> return picked
                    is Outcome.Ok -> picked.value ?: return Outcome.Ok(null)
                }
            destination = target
            problem(ProjectExportProblem.Source)
            val entries =
                withContext(dispatchers.io) {
                    attempt({ Failure.StorageRefused() }) { files.plan(includePrivate) }
                }
            if (entries is Outcome.Failed) return entries
            val outcome =
                writeZipDetailed(
                    target,
                    (entries as Outcome.Ok).value,
                    { done, total -> progress(ProjectExportProgress(done, total)) },
                    problem,
                )
            complete = outcome is Outcome.Ok
            return outcome
        } finally {
            if (destination != null && !complete)
                withContext(NonCancellable + dispatchers.io) { discard(Uri.parse(destination)) }
            operations.unlock()
        }
    }

    /**
     * Each file is re-opened through pinned no-follow descriptors after planning. Partial
     * destinations are removed.
     */
    internal suspend fun writeZip(
        uri: String,
        entries: List<NativeExportEntry>,
        onProgress: (Long, Long) -> Unit,
    ): Outcome<ProjectExportReceipt> = writeZipDetailed(uri, entries, onProgress, null)

    private suspend fun writeZipDetailed(
        uri: String,
        entries: List<NativeExportEntry>,
        onProgress: (Long, Long) -> Unit,
        onProblem: ((ProjectExportProblem) -> Unit)?,
    ): Outcome<ProjectExportReceipt> =
        withContext(dispatchers.io) {
            val destination = Uri.parse(uri)
            var complete = false
            var stage = ProjectExportProblem.Destination
            try {
                val result =
                    attempt({ error ->
                        val noSpace =
                            generateSequence(error) { it.cause }
                                .take(8)
                                .any { it is ErrnoException && it.errno == OsConstants.ENOSPC }
                        onProblem?.invoke(if (noSpace) ProjectExportProblem.Space else stage)
                        Failure.StorageRefused()
                    }) {
                        val context = currentCoroutineContext()
                        var done = 0L
                        var count = 0
                        val total = entries.sumOf { it.bytes }
                        var reportedAt = clock.now()
                        val out =
                            app.contentResolver.openOutputStream(destination, "w")
                                ?: throw IOException("destination unavailable")
                        ZipOutputStream(out.buffered(BUFFER)).use { zip ->
                            val buffer = ByteArray(BUFFER)
                            entries.forEach { entry ->
                                context.ensureActive()
                                if (!NativeProjectFiles.safeName(entry.name))
                                    throw IOException("invalid entry")
                                stage = ProjectExportProblem.Source
                                files.openRegularFileInside(entry.source).use { input ->
                                    stage = ProjectExportProblem.Destination
                                    zip.putNextEntry(
                                        ZipEntry(entry.name).apply {
                                            time = entry.source.lastModified()
                                        }
                                    )
                                    var fileBytes = 0L
                                    while (true) {
                                        context.ensureActive()
                                        stage = ProjectExportProblem.Source
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        stage = ProjectExportProblem.Destination
                                        zip.write(buffer, 0, read)
                                        done += read
                                        fileBytes += read
                                        val now = clock.now()
                                        if ((now - reportedAt).inWholeMilliseconds >= 250) {
                                            reportedAt = now
                                            onProgress(done, total)
                                        }
                                    }
                                    stage = ProjectExportProblem.Source
                                    if (fileBytes != entry.bytes)
                                        throw IOException("source changed")
                                    stage = ProjectExportProblem.Destination
                                    zip.closeEntry()
                                }
                                count++
                            }
                        }
                        onProgress(done, total)
                        ProjectExportReceipt(done, count)
                    }
                complete = result is Outcome.Ok
                result
            } finally {
                if (!complete) withContext(NonCancellable + dispatchers.io) { discard(destination) }
            }
        }

    override suspend fun clearCache(): Outcome<Long> {
        if (!operations.tryLock()) return Outcome.Failed(Failure.Busy())
        try {
            return withContext(dispatchers.io) {
                attempt({ Failure.StorageRefused() }) { files.cache(true) }
            }
        } finally {
            operations.unlock()
        }
    }

    override suspend fun deleteEverything(): Outcome<Boolean> {
        if (!operations.tryLock()) return Outcome.Failed(Failure.Busy())
        try {
            return withContext(dispatchers.main) {
                attempt({ Failure.StorageRefused() }) {
                    (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                        .clearApplicationUserData()
                }
            }
        } finally {
            operations.unlock()
        }
    }

    private fun discard(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(app.contentResolver, uri)
        } catch (_: Exception) {
            // Some document providers refuse deletion. Truncate so no partial private archive
            // remains readable.
            try {
                app.contentResolver.openFileDescriptor(uri, "rwt")?.use { descriptor ->
                    ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use {}
                }
            } catch (_: Exception) {}
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}

@ContributesTo(AppScope::class)
interface ProjectExportAccessors {
    val projectExport: AndroidProjectExport
}

internal val AndroidAppGraph.projectExport: AndroidProjectExport
    get() = (this as ProjectExportAccessors).projectExport

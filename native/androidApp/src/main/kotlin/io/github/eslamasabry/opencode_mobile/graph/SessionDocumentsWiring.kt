package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.domain.SessionDocuments
import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.FileSaver
import io.github.eslamasabry.oc.feature.chat.sessiontransfer.presentation.NoSessionDocuments
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** System picker only. Never logs a handle or reads more than the caller's admitted export size. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoSessionDocuments::class])
@Inject
class AndroidSessionDocuments(
    private val application: Application,
    private val activities: ResumedActivities,
    private val dispatchers: AppDispatchers,
    private val savers: Set<FileSaver>,
) : SessionDocuments {
    private val lane = Mutex()
    private var requests = 0L

    override suspend fun chooseJson(maxBytes: Long): Outcome<ByteArray?> = lane.withLock {
        val picked =
            withContext(dispatchers.main) {
                val activity =
                    activities.current?.takeUnless { it.isFinishing || it.isDestroyed }
                        ?: return@withContext Failure.Device().failed()
                val answer = CompletableDeferred<Uri?>()
                val launcher =
                    activity.activityResultRegistry.register(
                        "oc.session.import.${requests++}",
                        ActivityResultContracts.OpenDocument(),
                    ) {
                        answer.complete(it)
                    }
                val gone =
                    object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            answer.complete(null)
                        }
                    }
                activity.lifecycle.addObserver(gone)
                try {
                    launcher.launch(
                        arrayOf("application/json", "text/json", "application/octet-stream")
                    )
                    answer.await().ok()
                } catch (_: ActivityNotFoundException) {
                    Failure.Media(MediaProblem.NoAppToOpen).failed()
                } catch (_: IllegalStateException) {
                    Failure.Device().failed()
                } finally {
                    activity.lifecycle.removeObserver(gone)
                    launcher.unregister()
                }
            }
        if (picked is Outcome.Failed) return@withLock picked
        val uri = (picked as Outcome.Ok).value ?: return@withLock null.ok()
        withContext(dispatchers.io) {
            try {
                application.contentResolver
                    .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (
                            cursor.moveToFirst() &&
                                !cursor.isNull(0) &&
                                cursor.getLong(0) > maxBytes
                        )
                            return@withContext Failure.Media(MediaProblem.AttachmentTooLarge)
                                .failed()
                    }
                val source =
                    application.contentResolver.openInputStream(uri)
                        ?: return@withContext Failure.Device().failed()
                source.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(32 * 1024)
                    var size = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > maxBytes)
                            return@withContext Failure.Media(MediaProblem.AttachmentTooLarge)
                                .failed()
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray().ok()
                }
            } catch (_: IOException) {
                Failure.Device().failed()
            } catch (_: SecurityException) {
                Failure.Device().failed()
            } catch (_: IllegalArgumentException) {
                Failure.Device().failed()
            }
        }
    }

    override suspend fun saveJson(name: String, bytes: ByteArray): Outcome<Boolean> =
        lane.withLock {
            val saver = savers.singleOrNull() ?: return@withLock Failure.Device().failed()
            saver.save(FileData(name, "application/json", bytes.copyOf()))
        }
}

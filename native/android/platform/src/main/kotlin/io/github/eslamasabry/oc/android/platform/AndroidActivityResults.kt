package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.ActivityResults
import io.github.eslamasabry.oc.core.domain.platform.DocumentTarget
import io.github.eslamasabry.oc.core.domain.platform.PhotoTarget
import io.github.eslamasabry.oc.core.domain.platform.PickedFile
import io.github.eslamasabry.oc.core.domain.platform.TreeTarget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * What a photo launcher reports when nobody is waiting for it any more (the process died, or the caller was
 * cancelled, while the camera or picker was in front; plan 10 §10.4). Only `PendingPhotoRecovery` acts on it.
 */
public sealed interface LauncherOutcome {
    public data object Captured : LauncherOutcome

    public data object Cancelled : LauncherOutcome

    public data class Picked(val files: List<PickedFile>) : LauncherOutcome
}

/**
 * `ActivityResults` on Android (03 §10.4, plan 10 §10.3–§10.4, plan 12 R-08): one launcher per fixed key, registered
 * on `MainActivity` in `onCreate` through [attach], so a result that arrives after process death is delivered to the
 * re-registered launcher of the new activity.
 *
 * - **One owner per result.** Each key has at most one waiting caller; a second request on the same key while one is
 *   in front fails (`Failure.Device`) without launching.
 * - **Caller waiting** (same process): the suspended call resumes with the result.
 * - **Nobody waiting**: photo keys report only the outcome to [onOrphanPhoto] (`PendingPhotoRecovery`), which is the
 *   single owner of a photo whose caller is gone; document keys drop the result (nobody asked for it any more).
 * - No activity attached, or the system refuses the launch: `Failure.Device`, never an exception.
 *
 * Launch and callbacks run on [AppDispatchers.main]; metadata reads (`OpenableColumns`) run on `io`. URIs are never
 * logged. The class also tells share and Tailscale which activity is visible ([CurrentActivity]).
 */
public class AndroidActivityResults(
    private val dispatchers: AppDispatchers,
    private val onOrphanPhoto: (LauncherOutcome) -> Unit,
) : ActivityResults, CurrentActivity {
    /** `ActivityResultContracts.CreateDocument` fixes the MIME type at construction; this one takes it per call. */
    public data class CreateRequest(val suggestedName: String, val mime: String)

    public class CreateDocument : ActivityResultContract<CreateRequest, Uri?>() {
        override fun createIntent(context: Context, input: CreateRequest): Intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(input.mime)
                .putExtra(Intent.EXTRA_TITLE, input.suggestedName)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent.takeIf { resultCode == Activity.RESULT_OK }?.data
    }

    /** `PickMultipleVisualMedia` fixes its maximum at construction; this one takes the room left per call (≥ 2). */
    public class PickImages : ActivityResultContract<Int, List<Uri>>() {
        private val parser = ActivityResultContracts.PickMultipleVisualMedia(2)

        override fun createIntent(context: Context, input: Int): Intent =
            ActivityResultContracts.PickMultipleVisualMedia(input.coerceAtLeast(2)).createIntent(context, imagesOnly())

        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> = parser.parseResult(resultCode, intent)
    }

    private class Attached(
        val activity: WeakReference<Activity>,
        val pickPhoto: ActivityResultLauncher<PickVisualMediaRequest>,
        val pickPhotos: ActivityResultLauncher<Int>,
        val takePhoto: ActivityResultLauncher<Uri>,
        val openDocument: ActivityResultLauncher<Array<String>>,
        val createDocument: ActivityResultLauncher<CreateRequest>,
        val openDocumentTree: ActivityResultLauncher<Uri?>,
    )

    // Main-thread confined: attach, launch (withContext(main)) and every registry callback run there.
    private var attached: Attached? = null
    private val waiters = HashMap<String, CompletableDeferred<Any?>>()

    /** Call from `MainActivity.onCreate`, before `super.onStart` (the registry refuses later registrations). */
    public fun attach(activity: ComponentActivity) {
        attach(activity, activity.activityResultRegistry, activity)
    }

    internal fun attach(activity: Activity, registry: ActivityResultRegistry, owner: LifecycleOwner) {
        val appContext = activity.applicationContext
        val handle =
            Attached(
                activity = WeakReference(activity),
                pickPhoto =
                    registry.register(PICK_PHOTO, owner, ActivityResultContracts.PickVisualMedia()) { uri ->
                        deliver(PICK_PHOTO, uri) { onOrphanPhoto(orphanPicked(appContext, listOfNotNull(uri))) }
                    },
                pickPhotos =
                    registry.register(PICK_PHOTOS, owner, PickImages()) { uris ->
                        deliver(PICK_PHOTOS, uris) { onOrphanPhoto(orphanPicked(appContext, uris.orEmpty())) }
                    },
                takePhoto =
                    registry.register(TAKE_PHOTO, owner, ActivityResultContracts.TakePicture()) { saved ->
                        deliver(TAKE_PHOTO, saved) {
                            onOrphanPhoto(if (saved == true) LauncherOutcome.Captured else LauncherOutcome.Cancelled)
                        }
                    },
                openDocument =
                    registry.register(OPEN_DOCUMENT, owner, ActivityResultContracts.OpenDocument()) { uri ->
                        deliver(OPEN_DOCUMENT, uri) {}
                    },
                createDocument =
                    registry.register(CREATE_DOCUMENT, owner, CreateDocument()) { uri -> deliver(CREATE_DOCUMENT, uri) {} },
                openDocumentTree =
                    registry.register(OPEN_DOCUMENT_TREE, owner, ActivityResultContracts.OpenDocumentTree()) { uri ->
                        deliver(OPEN_DOCUMENT_TREE, uri) {}
                    },
            )
        attached = handle
        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (attached === handle) attached = null
                }
            },
        )
    }

    override fun get(): Activity? = attached?.activity?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override suspend fun pickImages(limit: Int): Outcome<ImmutableList<PickedFile>> {
        if (limit < 1) return Outcome.Ok(persistentListOf())
        val result: Outcome<List<Uri>> =
            if (limit == 1) {
                launch<PickVisualMediaRequest, Uri?>(PICK_PHOTO, imagesOnly()) { it.pickPhoto }.mapOk { listOfNotNull(it) }
            } else {
                launch<Int, List<Uri>>(PICK_PHOTOS, limit) { it.pickPhotos }.mapOk { it.orEmpty() }
            }
        return result.mapOk { uris -> describeAll(uris.take(limit)).toImmutableList() }
    }

    override suspend fun takePhoto(target: PhotoTarget): Outcome<PickedFile?> {
        val uri = target.uri.toUri()
        // TakePicture writes where it is told: only an app-owned FileProvider target (never file://, plan 10 §10.4).
        if (uri.scheme != "content" || uri.authority.isNullOrEmpty()) return Outcome.Failed(Failure.Unexpected())
        return launch<Uri, Boolean>(TAKE_PHOTO, uri) { it.takePhoto }
            .mapOk { saved -> if (saved == true) PickedFile(target.uri, null, "image/jpeg", null) else null }
    }

    override suspend fun openDocument(mimeTypes: List<String>): Outcome<PickedFile?> =
        launch<Array<String>, Uri?>(OPEN_DOCUMENT, mimeTypes.ifEmpty { listOf("*/*") }.toTypedArray()) { it.openDocument }
            .mapOk { uri -> uri?.let { describe(it) } }

    override suspend fun createDocument(suggestedName: String, mime: String): Outcome<DocumentTarget?> =
        launch<CreateRequest, Uri?>(CREATE_DOCUMENT, CreateRequest(suggestedName, mime)) { it.createDocument }
            .mapOk { uri -> uri?.let { DocumentTarget(it.toString(), suggestedName) } }

    override suspend fun openDocumentTree(): Outcome<TreeTarget?> =
        launch<Uri?, Uri?>(OPEN_DOCUMENT_TREE, null) { it.openDocumentTree }
            .mapOk { uri -> uri?.let { TreeTarget(it.toString(), null) } }

    /** Launches [key]'s launcher on main and waits for its result; null result values come back as `Ok(null)`. */
    private suspend fun <I, O> launch(
        key: String,
        input: I,
        launcher: (Attached) -> ActivityResultLauncher<I>,
    ): Outcome<O?> =
        withContext(dispatchers.main) {
            val handle = attached?.takeIf { get() != null } ?: return@withContext Outcome.Failed(Failure.Device())
            if (waiters.containsKey(key)) return@withContext Outcome.Failed(Failure.Device()) // one owner per result
            val waiter = CompletableDeferred<Any?>()
            waiters[key] = waiter
            try {
                launcher(handle).launch(input)
            } catch (_: RuntimeException) {
                // ActivityNotFoundException (no picker or camera app), IllegalStateException (unregistered launcher).
                waiters.remove(key)
                return@withContext Outcome.Failed(Failure.Device())
            }
            try {
                @Suppress("UNCHECKED_CAST")
                Outcome.Ok(waiter.await() as O?)
            } finally {
                // A cancelled caller frees the slot; a later result then counts as "nobody waiting".
                if (waiters[key] === waiter) waiters.remove(key)
            }
        }

    private fun deliver(key: String, value: Any?, orphan: () -> Unit) {
        val waiter = waiters.remove(key)
        if (waiter != null && waiter.complete(value)) return
        orphan()
    }

    private suspend fun describeAll(uris: List<Uri>): List<PickedFile> = uris.map { describe(it) }

    /** Display name, size and type for a picked URI; any provider error leaves the hint null (never fatal). */
    private suspend fun describe(uri: Uri): PickedFile =
        withContext(dispatchers.io) {
            val activity = get()
            if (activity == null) PickedFile(uri.toString(), null, null, null) else describe(activity.applicationContext, uri)
        }

    public companion object {
        public const val PICK_PHOTO: String = "oc.pickPhoto"
        public const val PICK_PHOTOS: String = "oc.pickPhotos"
        public const val TAKE_PHOTO: String = "oc.takePhoto"
        public const val OPEN_DOCUMENT: String = "oc.openDocument"
        public const val CREATE_DOCUMENT: String = "oc.createDocument"
        public const val OPEN_DOCUMENT_TREE: String = "oc.openDocumentTree"

        /** Every fixed key, registered once per activity by [attach]. */
        public val KEYS: List<String> =
            listOf(PICK_PHOTO, PICK_PHOTOS, TAKE_PHOTO, OPEN_DOCUMENT, CREATE_DOCUMENT, OPEN_DOCUMENT_TREE)

        private fun imagesOnly(): PickVisualMediaRequest =
            PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build()

        private fun orphanPicked(context: Context, uris: List<Uri>): LauncherOutcome =
            if (uris.isEmpty()) LauncherOutcome.Cancelled else LauncherOutcome.Picked(uris.map { describe(context, it) })

        internal fun describe(context: Context, uri: Uri): PickedFile {
            var name: String? = null
            var size: Long? = null
            try {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val s = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (n >= 0 && !cursor.isNull(n)) name = cursor.getString(n)?.substringAfterLast('/')
                            if (s >= 0 && !cursor.isNull(s)) size = cursor.getLong(s).takeIf { it >= 0 }
                        }
                    }
            } catch (_: RuntimeException) {
                // SecurityException, IllegalArgumentException from a provider: hints stay null.
            }
            val mime =
                try {
                    context.contentResolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase()
                } catch (_: RuntimeException) {
                    null
                }
            return PickedFile(uri.toString(), name?.takeIf { it.isNotBlank() }, mime, size)
        }
    }
}

// Not core's inline Outcome.map: :core:common is built for JVM 17 and oc.android.library for JVM 11, so core's inline
// functions cannot be inlined here (see the unit report). Same semantics.
private inline fun <T, R> Outcome<T>.mapOk(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(transform(value))
        is Outcome.Failed -> this
    }

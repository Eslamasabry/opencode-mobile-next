package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.FileHandoff
import io.github.eslamasabry.oc.engine.files.FileSaver
import io.github.eslamasabry.oc.engine.files.PdfDocument
import io.github.eslamasabry.oc.engine.files.PdfLimits
import io.github.eslamasabry.oc.engine.files.PdfPages
import io.github.eslamasabry.oc.engine.files.ShareHandler
import io.github.eslamasabry.oc.engine.files.ShareHost
import io.github.eslamasabry.oc.engine.files.ShareReply
import io.github.eslamasabry.oc.engine.files.ShareServer
import io.github.eslamasabry.oc.engine.files.WifiShareRules
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import io.github.eslamasabry.opencode_mobile.LocalPdfService
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// P12-FILES Android bindings (one file, plan rule "Android-only bindings in <Unit>Wiring.kt"): the Wi-Fi download
// server, Open with through the app's FileProvider, PDF pages through the isolated LocalPdfService, and Save. Nothing here is logged: paths,
// links (they hold the share token) and file contents never leave these classes.

/** The phone's Wi-Fi address, a socket bound to exactly that address, and SecureRandom tokens. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ShareHost>())
class AndroidShareHost(private val dispatchers: AppDispatchers, private val appScope: AppCoroutineScope) : ShareHost {
    private val random = SecureRandom()

    override suspend fun wifiAddress(): String? =
        withContext(dispatchers.io) {
            try {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                    .filter { nic -> nic.isUp && !nic.isLoopback && WifiShareRules.isWifiInterface(nic.name) }
                    .flatMap { nic -> nic.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .firstOrNull(WifiShareRules::isPrivateLan)
            } catch (_: SocketException) {
                null
            }
        }

    override suspend fun open(address: String, handler: ShareHandler): Outcome<ShareServer> =
        withContext(dispatchers.io) {
            try {
                val socket = ServerSocket(0, BACKLOG, InetAddress.getByName(address))
                val server = SocketShareServer(socket, handler, CoroutineScope(appScope.coroutineContext + dispatchers.io))
                server.start()
                Outcome.Ok(server)
            } catch (_: IOException) {
                Outcome.Failed(Failure.Media(MediaProblem.WifiShareUnavailable))
            } catch (_: SecurityException) {
                Outcome.Failed(Failure.Media(MediaProblem.WifiShareUnavailable))
            }
        }

    override fun secureBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    private companion object {
        const val BACKLOG = 8
    }
}

/**
 * A minimal HTTP/1.1 server for exactly one share: one request per connection (`Connection: close`), the request
 * line and headers bounded, every answer decided by the engine's [ShareHandler] (404 elsewhere, 405 for non-GET).
 */
internal class SocketShareServer(
    private val socket: ServerSocket,
    private val handler: ShareHandler,
    private val scope: CoroutineScope,
) : ShareServer {
    override val port: Int = socket.localPort

    /** Connections being answered: Stop closes them too, so a download already running ends with the share. */
    private val clients = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Socket, Boolean>())

    @Volatile private var closed = false

    fun start() {
        scope.launch {
            while (!socket.isClosed) {
                val client =
                    try {
                        socket.accept()
                    } catch (_: IOException) {
                        break
                    }
                clients += client
                if (closed) {
                    clients -= client
                    closeQuietly(client)
                    break
                }
                scope.launch {
                    try {
                        serve(client)
                    } finally {
                        clients -= client
                    }
                }
            }
        }
    }

    private fun serve(client: Socket) {
        try {
            client.use { c ->
                c.soTimeout = READ_TIMEOUT_MS
                val request = readHead(c.getInputStream()) ?: return
                val parts = request.split(' ')
                if (parts.size < 2) return
                val out = c.getOutputStream()
                when (val reply = handler.reply(parts[0], parts[1])) {
                    ShareReply.NotFound -> write(out, "404 Not Found", emptyList(), null)
                    ShareReply.MethodNotAllowed -> write(out, "405 Method Not Allowed", listOf("Allow" to "GET, HEAD"), null)
                    is ShareReply.Serve -> {
                        write(out, "200 OK", reply.headers, if (reply.headOnly) null else reply.body)
                        if (!reply.headOnly) handler.served()
                    }
                }
            }
        } catch (_: IOException) {
            // The downloader went away: end this connection only.
        }
    }

    /** The request line; the headers are read and dropped. Null when the head is malformed or too long. */
    private fun readHead(input: InputStream): String? {
        val head = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            head.write(b)
            if (head.size() > MAX_HEAD_BYTES) return null
            last4 = (last4 shl 8) or b
            if (last4 == CRLF_CRLF) break
        }
        return head.toString(Charsets.ISO_8859_1.name()).substringBefore("\r\n")
    }

    private fun write(out: OutputStream, status: String, headers: List<Pair<String, String>>, body: ByteArray?) {
        val lines = StringBuilder("HTTP/1.1 ").append(status).append("\r\n")
        val all = if (headers.any { it.first == "Content-Length" }) headers else headers + ("Content-Length" to "0")
        for ((k, v) in all + ("Connection" to "close")) lines.append(k).append(": ").append(v).append("\r\n")
        lines.append("\r\n")
        out.write(lines.toString().toByteArray(Charsets.UTF_8))
        if (body != null) out.write(body)
        out.flush()
    }

    override suspend fun close() {
        closed = true
        try {
            socket.close()
        } catch (_: IOException) {
            // Closing twice is harmless.
        }
        // Flutter `server.close(force: true)`: every open connection ends now, mid-download included.
        for (client in clients.toList()) closeQuietly(client)
        clients.clear()
    }

    private fun closeQuietly(client: Socket) {
        try {
            client.close()
        } catch (_: IOException) {
            // Already closed.
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 10_000
        const val MAX_HEAD_BYTES = 16 * 1024
        const val CRLF_CRLF = 0x0D0A0D0A
    }
}

/**
 * Open with: a private cache copy (only the latest one is kept) behind `<applicationId>.files`, `ACTION_VIEW` with a
 * read grant through the system chooser. The copy's name is the file's base name, so the receiving app sees it.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<FileHandoff>())
class AndroidFileHandoff(private val application: Application, private val dispatchers: AppDispatchers) : FileHandoff {
    override suspend fun openWith(data: FileData): Outcome<Unit> {
        val file =
            withContext(dispatchers.io) {
                try {
                    val folder = File(application.cacheDir, OPEN_WITH_DIR)
                    folder.deleteRecursively()
                    val slot = File(folder, randomSlot())
                    if (!slot.mkdirs()) return@withContext null
                    File(slot, WifiShareRules.safeName(data.name)).also { it.writeBytes(data.bytes) }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            } ?: return Outcome.Failed(Failure.Media(MediaProblem.SaveFailed))
        return withContext(dispatchers.main) {
            try {
                val uri = FileProvider.getUriForFile(application, application.packageName + AUTHORITY_SUFFIX, file)
                val view =
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, data.mime ?: "application/octet-stream")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                view.clipData = ClipData.newRawUri(file.name, uri)
                val chooser =
                    Intent.createChooser(view, null)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                application.startActivity(chooser)
                Outcome.Ok(Unit)
            } catch (_: ActivityNotFoundException) {
                Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
            } catch (_: IllegalArgumentException) {
                Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
            }
        }
    }

    private fun randomSlot(): String = WifiShareRules.token(ByteArray(SLOT_BYTES).also(SecureRandom()::nextBytes))

    private companion object {
        const val OPEN_WITH_DIR = "open-with"
        const val AUTHORITY_SUFFIX = ".files"
        const val SLOT_BYTES = 9
    }
}

/**
 * PDF pages (B-MEDIA-004, plan 10 §11.6 S4): every page is rendered by [LocalPdfService] in its own isolated,
 * permission-less process, so a PDF that crashes or hangs the native renderer can't touch this process (which holds
 * the server credentials). One request at a time app-wide; 12 s deadline; the PNG's signature and size are checked
 * before anything decodes it. Opening renders page 1 (to learn the page count) and keeps it.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<PdfPages>())
class AndroidPdfPages(private val application: Application, private val dispatchers: AppDispatchers) : PdfPages {
    private val lane = Mutex()

    override suspend fun open(bytes: ByteArray): Outcome<PdfDocument> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Outcome.Failed(Failure.Media(MediaProblem.PdfNeedsAndroid10))
        if (bytes.isEmpty() || bytes.size > PdfLimits.MAX_BYTES) return Outcome.Failed(Failure.Media(MediaProblem.PdfTooLarge))
        return when (val first = render(bytes, 0)) {
            is Outcome.Failed -> first
            is Outcome.Ok -> Outcome.Ok(IsolatedPdfDocument(bytes, first.value))
        }
    }

    private inner class IsolatedPdfDocument(private var bytes: ByteArray?, first: IsolatedPage) : PdfDocument {
        private val firstPng = first.png
        override val pageCount: Int = first.pageCount

        override suspend fun renderPage(index: Int, width: Int): Outcome<ByteArray> {
            if (index == 0) return Outcome.Ok(firstPng)
            val doc = bytes ?: return Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
            if (index !in 0 until minOf(pageCount, PdfLimits.MAX_PAGES)) return Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
            return when (val page = render(doc, index)) {
                is Outcome.Failed -> page
                is Outcome.Ok -> Outcome.Ok(page.value.png)
            }
        }

        override suspend fun close() {
            bytes = null
        }
    }

    internal class IsolatedPage(val png: ByteArray, val pageCount: Int)

    private suspend fun render(bytes: ByteArray, page: Int): Outcome<IsolatedPage> =
        lane.withLock {
            withTimeoutOrNull(DEADLINE_MS) { request(bytes, page) } ?: Outcome.Failed(Failure.Media(MediaProblem.PdfTimedOut))
        }

    /** Two unlinked temp files: the service reads the PDF from one and writes the PNG into the other. */
    private class Descriptors(
        val input: ParcelFileDescriptor,
        val output: ParcelFileDescriptor,
        val outputReader: ParcelFileDescriptor,
    ) {
        fun close() {
            for (d in listOf(input, output, outputReader)) {
                try {
                    d.close()
                } catch (_: IOException) {
                    // Already closed.
                }
            }
        }
    }

    private fun prepare(bytes: ByteArray): Descriptors? {
        var inputFile: File? = null
        var outputFile: File? = null
        val opened = mutableListOf<ParcelFileDescriptor>()
        try {
            inputFile = File.createTempFile("oc-pdf-", ".input", application.cacheDir)
            outputFile = File.createTempFile("oc-pdf-", ".output", application.cacheDir)
            val input = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).also { opened += it }
            val writer = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_WRITE_ONLY).also { opened += it }
            val output = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_WRITE_ONLY).also { opened += it }
            val reader = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY).also { opened += it }
            // Unlink before writing any document byte: only the open descriptors survive, even on a crash.
            check(inputFile.delete() && outputFile.delete())
            opened.remove(writer)
            ParcelFileDescriptor.AutoCloseOutputStream(writer).use { it.write(bytes) }
            return Descriptors(input, output, reader)
        } catch (_: IOException) {
        } catch (_: IllegalStateException) {
        } catch (_: SecurityException) {
        } finally {
            inputFile?.delete()
            outputFile?.delete()
        }
        for (d in opened) {
            try {
                d.close()
            } catch (_: IOException) {}
        }
        return null
    }

    private suspend fun request(bytes: ByteArray, page: Int): Outcome<IsolatedPage> {
        val files = withContext(dispatchers.io) { prepare(bytes) } ?: return Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
        try {
            val reply = withContext(dispatchers.main) { ask(files, page) } ?: return Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
            when (reply.getString(LocalPdfService.KEY_ERROR)) {
                null -> Unit
                LocalPdfService.ERROR_ENCRYPTED -> return Outcome.Failed(Failure.Media(MediaProblem.PdfEncrypted))
                else -> return Outcome.Failed(Failure.Media(MediaProblem.PdfInvalid))
            }
            val count = reply.getInt(LocalPdfService.KEY_COUNT)
            val width = reply.getInt(LocalPdfService.KEY_WIDTH)
            val height = reply.getInt(LocalPdfService.KEY_HEIGHT)
            if (!PdfReply.validSize(count, width, height)) return Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
            val png = withContext(dispatchers.io) { readAll(files.outputReader) } ?: return Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
            if (!PdfReply.validPng(png, width, height)) return Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
            return Outcome.Ok(IsolatedPage(png, count))
        } finally {
            withContext(NonCancellable + dispatchers.io) { files.close() }
        }
    }

    private fun readAll(descriptor: ParcelFileDescriptor): ByteArray? =
        try {
            val size = descriptor.statSize
            if (size !in 1..LocalPdfService.MAX_BYTES) {
                null
            } else {
                val out = ByteArray(size.toInt())
                java.io.FileInputStream(descriptor.fileDescriptor).use { input ->
                    var offset = 0
                    while (offset < out.size) {
                        val read = input.read(out, offset, out.size - offset)
                        if (read <= 0) return null
                        offset += read
                    }
                }
                out
            }
        } catch (_: IOException) {
            null
        }

    /** Binds a fresh isolated instance, sends the descriptors and the page, and waits for the one reply (main). */
    private suspend fun ask(files: Descriptors, page: Int): Bundle? =
        suspendCancellableCoroutine { waiting ->
            val released = java.util.concurrent.atomic.AtomicBoolean(false)
            var connection: ServiceConnection? = null
            fun finish(answer: Bundle?) {
                if (!released.compareAndSet(false, true)) return
                connection?.let {
                    try {
                        application.unbindService(it)
                    } catch (_: IllegalArgumentException) {
                        // Never bound.
                    }
                }
                if (waiting.isActive) waiting.resume(answer)
            }
            val reply =
                Messenger(
                    Handler(Looper.getMainLooper()) { message ->
                        finish(Bundle(message.data))
                        true
                    }
                )
            val bound =
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        try {
                            Messenger(binder).send(
                                Message.obtain().apply {
                                    replyTo = reply
                                    data =
                                        Bundle().apply {
                                            putParcelable(LocalPdfService.KEY_INPUT, files.input)
                                            putParcelable(LocalPdfService.KEY_OUTPUT, files.output)
                                            putInt(LocalPdfService.KEY_PAGE, page)
                                        }
                                }
                            )
                        } catch (_: RemoteException) {
                            finish(null)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) = finish(null)

                    override fun onBindingDied(name: ComponentName) = finish(null)

                    override fun onNullBinding(name: ComponentName) = finish(null)
                }
            connection = bound
            waiting.invokeOnCancellation { Handler(Looper.getMainLooper()).post { finish(null) } }
            val ok =
                try {
                    application.bindIsolatedService(
                        Intent(application, LocalPdfService::class.java),
                        Context.BIND_AUTO_CREATE,
                        "pdf" + WifiShareRules.token(ByteArray(SLOT_BYTES).also(SecureRandom()::nextBytes)).filter { it.isLetterOrDigit() },
                        application.mainExecutor,
                        bound,
                    )
                } catch (_: SecurityException) {
                    false
                } catch (_: IllegalArgumentException) {
                    false
                }
            if (!ok) finish(null)
        }

    private companion object {
        const val DEADLINE_MS = 12_000L
        const val SLOT_BYTES = 12
    }
}

/** What the app accepts back from [LocalPdfService] before decoding anything (plan 10 §11.6 "Read back"). */
internal object PdfReply {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun validSize(count: Int, width: Int, height: Int): Boolean =
        count >= 1 && width in 1..LocalPdfService.MAX_SIDE && height in 1..LocalPdfService.MAX_SIDE &&
            width.toLong() * height <= LocalPdfService.MAX_PIXELS

    /** A PNG whose IHDR says exactly [width] × [height]. */
    fun validPng(png: ByteArray, width: Int, height: Int): Boolean {
        if (png.size < 24) return false
        for (i in SIGNATURE.indices) if (png[i] != SIGNATURE[i]) return false
        if (png.decodeToString(12, 16) != "IHDR") return false
        return int(png, 16) == width && int(png, 20) == height
    }

    private fun int(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)
}

/**
 * Save (B-MEDIA-001, Flutter `FilePicker.saveFile`): the system "create document" picker on the resumed activity, then
 * the exact original bytes into the place the person picked. A cancel is `Ok(false)`; the picker's activity going away
 * counts as a cancel, so the viewer never waits forever.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<FileSaver>())
class AndroidFileSaver(
    private val application: Application,
    private val activities: ResumedActivities,
    private val dispatchers: AppDispatchers,
) : FileSaver {
    private class CreateDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
        override fun createIntent(context: Context, input: Pair<String, String>): Intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(input.second)
                .putExtra(Intent.EXTRA_TITLE, input.first)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
    }

    private var requests = 0

    override suspend fun save(data: FileData): Outcome<Boolean> {
        val target =
            when (val picked = pick(WifiShareRules.safeName(data.name), data.mime ?: "application/octet-stream")) {
                is Outcome.Failed -> return picked
                is Outcome.Ok -> picked.value ?: return Outcome.Ok(false)
            }
        return withContext(dispatchers.io) {
            try {
                val out =
                    application.contentResolver.openOutputStream(target, "wt")
                        ?: return@withContext Outcome.Failed(Failure.Media(MediaProblem.SaveFailed))
                out.use { it.write(data.bytes) }
                Outcome.Ok(true)
            } catch (_: IOException) {
                Outcome.Failed(Failure.Media(MediaProblem.SaveFailed))
            } catch (_: SecurityException) {
                Outcome.Failed(Failure.Media(MediaProblem.SaveFailed))
            } catch (_: IllegalArgumentException) {
                Outcome.Failed(Failure.Media(MediaProblem.SaveFailed))
            }
        }
    }

    private suspend fun pick(name: String, mime: String): Outcome<Uri?> =
        withContext(dispatchers.main) {
            val activity =
                activities.current?.takeUnless { it.isFinishing || it.isDestroyed }
                    ?: return@withContext Outcome.Failed(Failure.Media(MediaProblem.SaveFailed))
            val answer = CompletableDeferred<Uri?>()
            val launcher =
                try {
                    activity.activityResultRegistry.register(SAVE_KEY + (requests++), CreateDocument()) { answer.complete(it) }
                } catch (_: IllegalStateException) {
                    return@withContext Outcome.Failed(Failure.Media(MediaProblem.SaveFailed))
                }
            val gone =
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        answer.complete(null)
                    }
                }
            activity.lifecycle.addObserver(gone)
            try {
                launcher.launch(name to mime)
                Outcome.Ok(answer.await())
            } catch (_: ActivityNotFoundException) {
                Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
            } finally {
                activity.lifecycle.removeObserver(gone)
                launcher.unregister()
            }
        }

    private companion object {
        const val SAVE_KEY = "oc.files.save."
    }
}

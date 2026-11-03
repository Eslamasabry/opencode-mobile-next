package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
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
import io.github.eslamasabry.oc.engine.files.PdfDocument
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// P12-FILES Android bindings (one file, plan rule "Android-only bindings in <Unit>Wiring.kt"): the Wi-Fi download
// server, Open with through the app's FileProvider, and PDF pages through PdfRenderer. Nothing here is logged: paths,
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

    fun start() {
        scope.launch {
            while (!socket.isClosed) {
                val client =
                    try {
                        socket.accept()
                    } catch (_: IOException) {
                        break
                    }
                scope.launch { serve(client) }
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
        try {
            socket.close()
        } catch (_: IOException) {
            // Closing twice is harmless.
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

/** PDF pages through the platform PdfRenderer, one page open at a time, white behind every page. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<PdfPages>())
class AndroidPdfPages(private val application: Application, private val dispatchers: AppDispatchers) : PdfPages {
    override suspend fun open(bytes: ByteArray): Outcome<PdfDocument> =
        withContext(dispatchers.io) {
            val folder = File(application.cacheDir, PDF_DIR).apply { mkdirs() }
            val file = File(folder, WifiShareRules.token(ByteArray(SLOT_BYTES).also(SecureRandom()::nextBytes)) + ".pdf")
            var descriptor: ParcelFileDescriptor? = null
            try {
                file.writeBytes(bytes)
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                descriptor = pfd
                Outcome.Ok(AndroidPdfDocument(PdfRenderer(pfd), pfd, file, dispatchers))
            } catch (_: SecurityException) {
                descriptor?.close()
                file.delete()
                Outcome.Failed(Failure.Media(MediaProblem.PdfEncrypted))
            } catch (_: IOException) {
                descriptor?.close()
                file.delete()
                Outcome.Failed(Failure.Media(MediaProblem.PdfInvalid))
            }
        }

    private companion object {
        const val PDF_DIR = "pdf-view"
        const val SLOT_BYTES = 9
    }
}

private class AndroidPdfDocument(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
    private val file: File,
    private val dispatchers: AppDispatchers,
) : PdfDocument {
    private val lane = Mutex()
    private var closed = false

    override val pageCount: Int = renderer.pageCount

    override suspend fun renderPage(index: Int, width: Int): Outcome<ByteArray> =
        lane.withLock {
            withContext(dispatchers.io) {
                if (closed || index !in 0 until pageCount) return@withContext Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
                try {
                    renderer.openPage(index).use { page ->
                        val w = width.coerceIn(1, MAX_SIDE)
                        val h = (w.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, MAX_SIDE)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val png = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
                            Outcome.Ok(png.toByteArray())
                        } finally {
                            bitmap.recycle()
                        }
                    }
                } catch (_: IllegalStateException) {
                    Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
                } catch (_: IllegalArgumentException) {
                    Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
                } catch (_: OutOfMemoryError) {
                    Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
                }
            }
        }

    override suspend fun close() {
        lane.withLock {
            withContext(dispatchers.io) {
                if (closed) return@withContext
                closed = true
                try {
                    renderer.close()
                    descriptor.close()
                } catch (_: IOException) {
                    // Already closed.
                } finally {
                    file.delete()
                }
            }
        }
    }

    private companion object {
        const val MAX_SIDE = 4096
    }
}

package io.github.eslamasabry.opencode_mobile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.concurrent.Executors
import java.util.UUID

/** App-side transport only. Untrusted PDF parsing happens in LocalPdfService. */
class LocalPdfBridge(private val context: Context, messenger: BinaryMessenger) {
    private val channel = MethodChannel(messenger, "oc/local_pdf")
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var active: Request? = null
    private var disposed = false

    private class Request(val id: String, val page: Int, val result: MethodChannel.Result) {
        @Volatile var finished = false
        var bound = false
        var connection: ServiceConnection? = null
        var input: ParcelFileDescriptor? = null
        var output: ParcelFileDescriptor? = null
        var outputReader: ParcelFileDescriptor? = null
        var timeout: Runnable? = null
        fun closeDescriptors() {
            for (descriptor in listOf(input, output, outputReader)) {
                try { descriptor?.close() } catch (_: Exception) { }
            }
            input = null; output = null; outputReader = null
        }
    }

    init {
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "cancel" -> {
                    val id = call.argument<String>("requestID")
                    active?.takeIf { it.id == id }?.let { finish(it, error = "cancelled") }
                    result.success(null)
                }
                "render" -> {
                    val bytes = call.argument<ByteArray>("bytes")
                    val page = call.argument<Int>("page")
                    val id = call.argument<String>("requestID")
                    when {
                        disposed || Build.VERSION.SDK_INT < 29 -> result.error("unavailable", "PDF preview unavailable", null)
                        active != null -> result.error("busy", "A PDF request is already running", null)
                        bytes == null || bytes.isEmpty() || bytes.size > 10 * 1024 * 1024 ||
                            page == null || page !in 0..199 || id.isNullOrEmpty() || id.length > 100 ->
                            result.error("limit", "PDF preview limit", null)
                        else -> start(Request(id, page, result), bytes)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun start(request: Request, bytes: ByteArray) {
        active = request
        request.timeout = Runnable { finish(request, error = "timeout") }.also {
            main.postDelayed(it, 12000)
        }
        io.execute {
            var inputFile: File? = null
            var outputFile: File? = null
            var inputWriter: ParcelFileDescriptor? = null
            try {
                inputFile = File.createTempFile("oc-pdf-", ".input", context.cacheDir)
                outputFile = File.createTempFile("oc-pdf-", ".output", context.cacheDir)
                request.input = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                inputWriter = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_WRITE_ONLY)
                request.output = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_WRITE_ONLY)
                request.outputReader = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                // Only open descriptors survive: no retained document path, even on a process crash.
                check(inputFile.delete() && outputFile.delete())
                // Unlink before writing any document bytes; a crash can leave
                // at most an empty temporary file from descriptor preparation.
                ParcelFileDescriptor.AutoCloseOutputStream(inputWriter).use { it.write(bytes) }
                main.post { if (!request.finished && !disposed) bind(request) }
            } catch (_: Exception) {
                main.post { finish(request, error = "io_error") }
            } finally {
                try { inputWriter?.close() } catch (_: Exception) { }
                inputFile?.delete(); outputFile?.delete()
            }
        }
    }

    private fun bind(request: Request) {
        // Keep the API guard at the asynchronous binding boundary as well as
        // the channel entry point so every caller preserves the OS requirement.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            finish(request, error = "unavailable")
            return
        }
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (!request.finished) {
                val data = message.data
                val error = data.getString("error")
                if (error != null) finish(request, error = error)
                else readResult(request, data)
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (request.finished) return
                try {
                    Messenger(binder).send(Message.obtain().apply {
                        replyTo = reply
                        data = Bundle().apply {
                            putParcelable("input", request.input)
                            putParcelable("output", request.output)
                            putInt("page", request.page)
                        }
                    })
                } catch (_: Exception) { finish(request, error = "service_died") }
            }
            override fun onServiceDisconnected(name: ComponentName) { finish(request, error = "service_died") }
            override fun onBindingDied(name: ComponentName) { finish(request, error = "service_died") }
            override fun onNullBinding(name: ComponentName) { finish(request, error = "unavailable") }
        }
        request.connection = connection
        try {
            // A fresh isolated instance prevents a closing request's process
            // from being reused by (or cancelling) the next preview.
            request.bound = context.bindIsolatedService(
                Intent(context, LocalPdfService::class.java), Context.BIND_AUTO_CREATE,
                "pdf" + UUID.randomUUID().toString().replace("-", ""), context.mainExecutor, connection)
            if (!request.bound) finish(request, error = "unavailable")
        } catch (_: Exception) { finish(request, error = "unavailable") }
    }

    private fun readResult(request: Request, data: Bundle) {
        val count = data.getInt("pageCount")
        val width = data.getInt("width")
        val height = data.getInt("height")
        if (count < 1 || width !in 1..1536 || height !in 1..1536 || width.toLong() * height > 2000000) {
            finish(request, error = "invalid_result"); return
        }
        io.execute {
            if (request.finished) return@execute
            try {
                val descriptor = request.outputReader ?: error("closed")
                val size = descriptor.statSize
                check(size in 1..(10L * 1024 * 1024))
                request.outputReader = null // AutoCloseInputStream takes descriptor ownership.
                val png = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    val result = ByteArray(size.toInt())
                    var offset = 0
                    while (offset < result.size) {
                        val read = input.read(result, offset, result.size - offset)
                        check(read > 0)
                        offset += read
                    }
                    result
                }
                main.post { finish(request, payload = mapOf("png" to png, "pageCount" to count,
                    "page" to request.page, "width" to width, "height" to height)) }
            } catch (_: Exception) { main.post { finish(request, error = "invalid_result") } }
        }
    }

    private fun finish(request: Request, payload: Any? = null, error: String? = null) {
        if (request.finished) return
        request.finished = true
        request.timeout?.let(main::removeCallbacks)
        if (request.bound) {
            request.bound = false
            try { request.connection?.let(context::unbindService) } catch (_: Exception) { }
        }
        io.execute { request.closeDescriptors() }
        if (active === request) active = null
        if (error != null) request.result.error(error, "PDF preview could not complete", null)
        else request.result.success(payload)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        channel.setMethodCallHandler(null)
        active?.let { finish(it, error = "cancelled") }
        io.shutdown()
    }
}

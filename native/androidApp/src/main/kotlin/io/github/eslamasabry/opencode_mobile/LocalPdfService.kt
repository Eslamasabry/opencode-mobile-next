package io.github.eslamasabry.opencode_mobile

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.OutputStream
import java.util.concurrent.Executors
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Frozen FQCN (plan 10 §11.6, M12; port of the Flutter app's `LocalPdfService`): untrusted PDF bytes are parsed only
 * here, in a non-exported, permission-less isolated process (`:local_pdf`), never in the app process that holds the
 * server credentials (S4). It receives two descriptors and a page number, never a path or URL; one process serves one
 * request; a 10 s watchdog and every unbind kill only this process.
 */
class LocalPdfService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var admitted = false
    private val watchdog = Runnable { Process.killProcess(Process.myPid()) }
    private val incoming =
        Messenger(
            Handler(Looper.getMainLooper()) { message ->
                if (!admitted) {
                    admitted = true
                    main.postDelayed(watchdog, WATCHDOG_MS)
                    // Handler recycles the Message after this callback: own its values now.
                    val data = Bundle(message.data)
                    val reply = message.replyTo
                    worker.execute { render(data, reply) }
                }
                true
            }
        )

    override fun onBind(intent: Intent): IBinder = incoming.binder

    @Suppress("DEPRECATION")
    private fun render(data: Bundle, reply: Messenger?) {
        val input = data.getParcelable<ParcelFileDescriptor>(KEY_INPUT)
        val output = data.getParcelable<ParcelFileDescriptor>(KEY_OUTPUT)
        val answer = Bundle()
        try {
            require(input != null && output != null)
            require(input.statSize in 1..MAX_BYTES)
            val index = data.getInt(KEY_PAGE, -1)
            require(index in 0 until MAX_PAGES)
            PdfRenderer(input).use { renderer ->
                require(index < renderer.pageCount)
                renderer.openPage(index).use { page ->
                    require(page.width in 1..100_000 && page.height in 1..100_000)
                    val scale =
                        min(
                            MAX_SIDE.toDouble() / maxOf(page.width, page.height),
                            sqrt(MAX_PIXELS.toDouble() / (page.width.toDouble() * page.height)),
                        )
                    val width = maxOf(1, floor(page.width * scale).toInt())
                    val height = maxOf(1, floor(page.height * scale).toInt())
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, LimitedOutput(stream)))
                        }
                        answer.putInt(KEY_COUNT, renderer.pageCount)
                        answer.putInt(KEY_WIDTH, width)
                        answer.putInt(KEY_HEIGHT, height)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } catch (_: SecurityException) {
            answer.putString(KEY_ERROR, ERROR_ENCRYPTED)
        } catch (_: Exception) {
            answer.putString(KEY_ERROR, ERROR_INVALID)
        } finally {
            try {
                input?.close()
            } catch (_: Exception) {}
            try {
                output?.close()
            } catch (_: Exception) {}
        }
        try {
            reply?.send(Message.obtain().apply { this.data = answer })
        } catch (_: Exception) {
            Process.killProcess(Process.myPid())
        }
        // The watchdog stays armed until the client unbinds, including an abandoned reply.
    }

    private class LimitedOutput(private val target: OutputStream) : OutputStream() {
        private var count = 0L

        override fun write(value: Int) {
            check(++count <= MAX_BYTES)
            target.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            count += length
            check(count <= MAX_BYTES)
            target.write(bytes, offset, length)
        }
    }

    override fun onUnbind(intent: Intent): Boolean {
        // This process belongs to one request; main stays responsive while a malformed document blocks the worker.
        Process.killProcess(Process.myPid())
        return false
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    internal companion object {
        const val KEY_INPUT = "input"
        const val KEY_OUTPUT = "output"
        const val KEY_PAGE = "page"
        const val KEY_COUNT = "pageCount"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_ERROR = "error"
        const val ERROR_ENCRYPTED = "encrypted"
        const val ERROR_INVALID = "invalid_pdf"
        const val MAX_BYTES = 10L * 1024 * 1024
        const val MAX_PAGES = 200
        const val MAX_SIDE = 1536
        const val MAX_PIXELS = 2_000_000
        const val WATCHDOG_MS = 10_000L
    }
}

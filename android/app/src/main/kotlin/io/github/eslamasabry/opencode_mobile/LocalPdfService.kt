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

/** Non-exported isolated UID. Receives two FDs, never a document path or URL. */
class LocalPdfService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var admitted = false
    private val watchdog = Runnable { Process.killProcess(Process.myPid()) }
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (!admitted) {
            admitted = true
            main.postDelayed(watchdog, 10000)
            // Handler recycles Message after this callback. Own its values now.
            val data = Bundle(message.data)
            val reply = message.replyTo
            worker.execute { render(data, reply) }
        }
        true
    })
    override fun onBind(intent: Intent): IBinder = incoming.binder

    @Suppress("DEPRECATION")
    private fun render(data: Bundle, reply: Messenger?) {
        val input = data.getParcelable<ParcelFileDescriptor>("input")
        val output = data.getParcelable<ParcelFileDescriptor>("output")
        val answer = Bundle()
        try {
            require(input != null && output != null)
            require(input.statSize in 1..(10L * 1024 * 1024))
            val index = data.getInt("page", -1)
            require(index in 0..199)
            PdfRenderer(input).use { renderer ->
                require(index < renderer.pageCount)
                renderer.openPage(index).use { page ->
                    require(page.width in 1..100000 && page.height in 1..100000)
                    val scale = min(1536.0 / maxOf(page.width, page.height),
                        sqrt(2000000.0 / (page.width.toDouble() * page.height)))
                    val width = maxOf(1, floor(page.width * scale).toInt())
                    val height = maxOf(1, floor(page.height * scale).toInt())
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, LimitedOutput(stream)))
                        }
                        answer.putInt("pageCount", renderer.pageCount)
                        answer.putInt("width", width)
                        answer.putInt("height", height)
                    } finally { bitmap.recycle() }
                }
            }
        } catch (_: SecurityException) { answer.putString("error", "encrypted") }
        catch (_: Exception) { answer.putString("error", "invalid_pdf") }
        finally {
            try { input?.close() } catch (_: Exception) { }
            try { output?.close() } catch (_: Exception) { }
        }
        try { reply?.send(Message.obtain().apply { this.data = answer }) }
        catch (_: Exception) { Process.killProcess(Process.myPid()) }
        // Keep the watchdog armed until the client unbinds, including abandoned replies.
    }

    private class LimitedOutput(private val target: OutputStream) : OutputStream() {
        private var count = 0L
        override fun write(value: Int) { check(++count <= 10L * 1024 * 1024); target.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            count += length; check(count <= 10L * 1024 * 1024); target.write(bytes, offset, length)
        }
    }

    override fun onUnbind(intent: Intent): Boolean {
        // This process belongs only to this one renderer request. Main stays
        // responsive while a malformed document blocks the native worker.
        Process.killProcess(Process.myPid())
        return false
    }
    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }
}

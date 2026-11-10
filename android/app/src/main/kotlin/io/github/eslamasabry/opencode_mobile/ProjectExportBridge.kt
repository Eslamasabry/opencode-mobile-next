package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Native half of `oc/project_export` (lib/builtin/project_export.dart).
 *
 * Dart decides which files an export holds and writes them to a plan file
 * (`name\tsource` per line) in the app's cache; this class only streams
 * those files into a zip at the place the person picked with the system
 * "Save to" picker. It runs while the page is open (no service, no
 * unbounded background work); closing the page stops it and the partial
 * zip is deleted. Sources must be regular files inside filesDir, checked
 * with lstat so a link swapped in after planning is never followed.
 *
 * Also the manage-space actions: measure/clear the cache (never the running
 * in-app server's proot-tmp) and ActivityManager.clearApplicationUserData.
 */
class ProjectExportBridge(
    private val activity: Activity,
    messenger: BinaryMessenger,
) {
    private val channel = MethodChannel(messenger, CHANNEL_NAME)
    private val handler = Handler(Looper.getMainLooper())
    private val context: Context = activity.applicationContext
    private var pickResult: MethodChannel.Result? = null
    private var worker: Thread? = null
    private val cancelled = AtomicBoolean(false)
    private var disposed = false

    init {
        channel.setMethodCallHandler(::onCall)
    }

    fun dispose() {
        disposed = true
        cancelled.set(true)
        channel.setMethodCallHandler(null)
        pickResult?.success(null)
        pickResult = null
    }

    /** Forwarded from the host activity; true when it was the picker. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_PICK) return false
        val result = pickResult ?: return true
        pickResult = null
        val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
        result.success(uri?.toString())
        return true
    }

    private fun onCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "pickDestination" -> pick(call.argument<String>("name") ?: "projects.zip", result)
            "export" -> export(call, result)
            "cancel" -> {
                cancelled.set(true)
                result.success(null)
            }
            "cacheBytes" -> background(result) { cacheRoots().sumOf { measure(it) } }
            "clearCache" -> background(result) { cacheRoots().sumOf { clear(it) } }
            "clearAllData" -> {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                // The system stops the app right after; a false means it refused.
                result.success(manager.clearApplicationUserData())
            }
            else -> result.notImplemented()
        }
    }

    private fun pick(name: String, result: MethodChannel.Result) {
        if (pickResult != null) {
            result.error("busy", null, null)
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_TITLE, name)
        pickResult = result
        try {
            activity.startActivityForResult(intent, REQUEST_PICK)
        } catch (_: Exception) {
            pickResult = null
            result.error("unavailable", null, null)
        }
    }

    private fun export(call: MethodCall, result: MethodChannel.Result) {
        val uriText = call.argument<String>("uri")
        val planPath = call.argument<String>("plan")
        if (uriText == null || planPath == null || worker?.isAlive == true) {
            result.success(failure("failed", "busy or missing arguments"))
            return
        }
        cancelled.set(false)
        val uri = Uri.parse(uriText)
        val thread = Thread({
            val outcome = runExport(uri, File(planPath))
            File(planPath).delete()
            handler.post { if (!disposed) result.success(outcome) }
        }, "project-export")
        worker = thread
        thread.start()
    }

    private fun runExport(uri: Uri, plan: File): Map<String, Any> {
        val filesRoot = context.filesDir.canonicalPath + File.separator
        var bytes = 0L
        var files = 0
        var lastReport = 0L
        var stage = "destination"
        try {
            val lines = plan.readLines()
            val out = context.contentResolver.openOutputStream(uri, "w")
                ?: return discard(uri, failure("destination", "no stream"))
            ZipOutputStream(out.buffered(BUFFER)).use { zip ->
                val buffer = ByteArray(BUFFER)
                for (line in lines) {
                    if (cancelled.get()) throw Cancelled()
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val name = line.substring(0, tab)
                    val source = File(line.substring(tab + 1))
                    if (!safeName(name) || !regularFileInside(source, filesRoot)) continue
                    stage = "source"
                    val input = try {
                        FileInputStream(source)
                    } catch (_: IOException) {
                        // Gone since the plan was made: nothing to keep.
                        continue
                    }
                    input.use {
                        stage = "destination"
                        zip.putNextEntry(ZipEntry(name).apply { time = source.lastModified() })
                        while (true) {
                            stage = "source"
                            val read = it.read(buffer)
                            if (read < 0) break
                            stage = "destination"
                            zip.write(buffer, 0, read)
                            bytes += read
                            if (cancelled.get()) throw Cancelled()
                            val now = System.currentTimeMillis()
                            if (now - lastReport > PROGRESS_MS) {
                                lastReport = now
                                val done = bytes
                                handler.post {
                                    if (!disposed) channel.invokeMethod("progress", done)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                    files++
                }
                stage = "destination"
            }
            return mapOf("ok" to true, "bytes" to bytes, "files" to files)
        } catch (_: Cancelled) {
            return discard(uri, failure("cancelled", ""))
        } catch (e: Exception) {
            val text = e.message ?: e.javaClass.simpleName
            val reason = if (text.contains("ENOSPC") || text.contains("No space", true)) {
                "space"
            } else {
                stage
            }
            return discard(uri, failure(reason, e.javaClass.simpleName))
        }
    }

    private fun discard(uri: Uri, outcome: Map<String, Any>): Map<String, Any> {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            // The provider may not allow deletion; the half file stays there.
        }
        return outcome
    }

    private fun failure(reason: String, detail: String): Map<String, Any> =
        mapOf("ok" to false, "reason" to reason, "detail" to detail)

    private fun safeName(name: String): Boolean =
        !name.startsWith("/") && name.split('/').none { it == ".." || it.isEmpty() }

    private fun regularFileInside(file: File, root: String): Boolean = try {
        val parent = file.parentFile?.canonicalPath ?: ""
        (parent + File.separator).startsWith(root) &&
            OsConstants.S_ISREG(Os.lstat(file.path).st_mode)
    } catch (_: ErrnoException) {
        false
    } catch (_: IOException) {
        false
    }

    private fun cacheRoots(): List<File> =
        listOfNotNull(context.cacheDir, context.externalCacheDir)

    private fun kept(parent: File, name: String): Boolean =
        parent == context.cacheDir && name == PROOT_TMP

    private fun measure(dir: File): Long = (dir.list() ?: emptyArray()).sumOf { name ->
        if (kept(dir, name)) 0L else size(File(dir, name))
    }

    private fun clear(dir: File): Long = (dir.list() ?: emptyArray()).sumOf { name ->
        if (kept(dir, name)) {
            0L
        } else {
            val child = File(dir, name)
            val bytes = size(child)
            delete(child)
            bytes
        }
    }

    /** lstat-based: a link counts as nothing and is never followed. */
    private fun size(file: File): Long = try {
        val mode = Os.lstat(file.path).st_mode
        when {
            OsConstants.S_ISREG(mode) -> file.length()
            OsConstants.S_ISDIR(mode) -> (file.list() ?: emptyArray()).sumOf { size(File(file, it)) }
            else -> 0L
        }
    } catch (_: ErrnoException) {
        0L
    }

    private fun delete(file: File) {
        try {
            if (OsConstants.S_ISDIR(Os.lstat(file.path).st_mode)) {
                file.setWritable(true, true)
                (file.list() ?: emptyArray()).forEach { delete(File(file, it)) }
            }
        } catch (_: ErrnoException) {
            return
        }
        file.delete()
    }

    private fun <T> background(result: MethodChannel.Result, work: () -> T) {
        Thread({
            val value = try {
                work()
            } catch (_: Exception) {
                null
            }
            handler.post { if (!disposed) result.success(value) }
        }, "project-export-cache").start()
    }

    private class Cancelled : Exception()

    companion object {
        const val CHANNEL_NAME = "oc/project_export"
        private const val REQUEST_PICK = 0x0c5e
        private const val BUFFER = 64 * 1024
        private const val PROGRESS_MS = 250L
        private const val PROOT_TMP = "proot-tmp"
    }
}

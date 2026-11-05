package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Answers a script's `::oc fetch <url> <dest> <sha256>` request (issue #117):
 * the download tool inside Ubuntu (curl under proot) can crash on some ROMs,
 * so the app downloads the file with Android's own networking into the path
 * the script expects. The script keeps its own checksum check; this checks the
 * same pin too and deletes a file that does not match.
 *
 * Handshake, by two marker files next to the destination:
 *  - `<dest>.oc-fetch-ack` appears at once: the app took the request.
 *  - `<dest>.oc-fetch-done` appears last and holds `ok` or `fail <plain reason>`.
 *
 * Resumable: a partial destination is continued with a Range request.
 */
class SetupAppFetch(
    private val rootfs: File,
    private val allowInsecure: Boolean = false,
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    /** Never throws; the answer is in the `.oc-fetch-done` file. */
    fun run(
        request: SetupProtocol.Event.Fetch,
        cancelled: () -> Boolean,
        progress: (done: Long, total: Long) -> Unit,
    ) {
        val target = resolve(request.dest)
        if (target == null) {
            // No ack is possible without a safe place to put it.
            return
        }
        target.parentFile?.mkdirs()
        val ack = File(target.path + ".oc-fetch-ack")
        val done = File(target.path + ".oc-fetch-done")
        done.delete()
        ack.writeText("1")
        val answer = try {
            download(request, target, cancelled, progress)
            "ok"
        } catch (error: FetchFailure) {
            "fail ${error.plain}"
        } catch (error: Exception) {
            "fail the connection failed"
        }
        val tmp = File(target.path + ".oc-fetch-tmp")
        tmp.writeText(answer)
        if (!tmp.renameTo(done)) done.writeText(answer)
    }

    class FetchFailure(val plain: String) : Exception(plain)

    /** [dest] is a guest path; it must stay inside the root filesystem. */
    fun resolve(dest: String): File? {
        if (!dest.startsWith("/") || dest.contains('\u0000') || dest.split('/').any { it == ".." }) return null
        val base = rootfs.canonicalFile
        val target = File(base, dest.removePrefix("/"))
        // Resolve symlinks of the folder so a guest link cannot lead outside.
        val parent = target.parentFile ?: return null
        var existing: File? = parent
        while (existing != null && !existing.exists()) existing = existing.parentFile
        val real = existing?.canonicalFile ?: return null
        if (real != base && !real.path.startsWith(base.path + File.separator)) return null
        return target
    }

    private fun download(
        request: SetupProtocol.Event.Fetch,
        target: File,
        cancelled: () -> Boolean,
        progress: (Long, Long) -> Unit,
    ) {
        val url = try { URL(request.url) } catch (_: Exception) { throw FetchFailure("the address is not valid") }
        if (url.protocol != "https" && !(allowInsecure && url.protocol == "http")) {
            throw FetchFailure("the address is not secure")
        }
        if (!Regex("[0-9a-fA-F]{64}").matches(request.sha256)) throw FetchFailure("the expected checksum is missing")
        var attempt = 0
        while (true) {
            if (cancelled()) throw FetchFailure("cancelled")
            try {
                transfer(url, target, cancelled, progress)
                break
            } catch (error: FetchFailure) {
                throw error
            } catch (error: IOException) {
                attempt++
                if (attempt >= ATTEMPTS) throw FetchFailure("the connection failed")
                Thread.sleep(RETRY_MS * attempt)
            }
        }
        if (!sha256(target).equals(request.sha256, ignoreCase = true)) {
            target.delete()
            throw FetchFailure("the download did not match its checksum")
        }
    }

    private fun transfer(url: URL, target: File, cancelled: () -> Boolean, progress: (Long, Long) -> Unit) {
        var have = if (target.isFile) target.length() else 0L
        val connection = connect(url)
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (have > 0) connection.setRequestProperty("Range", "bytes=$have-")
            val code = connection.responseCode
            if (code == 416) {
                // The partial file is not usable; start over next attempt.
                target.delete()
                throw IOException("range not satisfiable")
            }
            if (code != 200 && code != 206) throw FetchFailure("the server answered $code")
            val append = code == 206 && have > 0
            if (!append) have = 0
            val total = when {
                code == 206 -> connection.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
                else -> null
            } ?: (connection.contentLengthLong.takeIf { it > 0 }?.plus(have) ?: 0L)
            var written = have
            var lastReport = 0L
            connection.inputStream.use { input ->
                java.io.FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled()) throw FetchFailure("cancelled")
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 500) {
                            lastReport = now
                            progress(written, total)
                        }
                    }
                }
            }
            if (total > 0 && written < total) throw IOException("short read")
            progress(written, if (total > 0) total else written)
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ATTEMPTS = 4
        const val RETRY_MS = 3_000L
    }
}

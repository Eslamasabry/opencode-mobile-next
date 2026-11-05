package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.MalformedURLException
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
    /** Never throws for a failed download; the answer is in the `.oc-fetch-done` file. */
    fun run(
        request: SetupProtocol.Event.Fetch,
        cancelled: () -> Boolean,
        progress: (done: Long, total: Long) -> Unit,
    ) {
        // No ack is possible without a safe place to put the file.
        val target = RootfsPaths(rootfs).resolve(request.dest) ?: return
        target.parentFile?.mkdirs()
        val done = File(target.path + ".oc-fetch-done")
        done.delete()
        File(target.path + ".oc-fetch-ack").writeText("1")
        val answer = try {
            download(request, target, cancelled, progress)
            "ok"
        } catch (error: FetchFailure) {
            "fail ${error.plain}"
        } catch (error: IOException) {
            "fail ${FetchFailure("the connection failed", error).plain}"
        } catch (error: IllegalArgumentException) {
            "fail ${FetchFailure("the request was not valid", error).plain}"
        }
        val tmp = File(target.path + ".oc-fetch-tmp")
        tmp.writeText(answer)
        if (!tmp.renameTo(done)) done.writeText(answer)
    }

    class FetchFailure(val plain: String, cause: Throwable? = null) : Exception(plain, cause)

    private class Reply(val connection: HttpURLConnection, val append: Boolean, val have: Long, val total: Long)

    private fun download(
        request: SetupProtocol.Event.Fetch,
        target: File,
        cancelled: () -> Boolean,
        progress: (Long, Long) -> Unit,
    ) {
        val url = parseUrl(request.url)
        if (!Regex("[0-9a-fA-F]{$SHA256_HEX_LENGTH}").matches(request.sha256)) {
            throw FetchFailure("the expected checksum is missing")
        }
        transferWithRetry(url, target, cancelled, progress)
        verify(target, request.sha256)
    }

    private fun parseUrl(text: String): URL {
        val url = try {
            URL(text)
        } catch (error: MalformedURLException) {
            throw FetchFailure("the address is not valid", error)
        }
        if (url.protocol != "https" && !(allowInsecure && url.protocol == "http")) {
            throw FetchFailure("the address is not secure")
        }
        return url
    }

    private fun transferWithRetry(url: URL, target: File, cancelled: () -> Boolean, progress: (Long, Long) -> Unit) {
        var attempt = 0
        var finished = false
        while (!finished) {
            if (cancelled()) throw FetchFailure("cancelled")
            try {
                transfer(url, target, cancelled, progress)
                finished = true
            } catch (error: IOException) {
                attempt++
                if (attempt >= ATTEMPTS) throw FetchFailure("the connection failed", error)
                Thread.sleep(RETRY_MS * attempt)
            }
        }
    }

    private fun verify(target: File, expected: String) {
        if (!fileSha256(target).equals(expected, ignoreCase = true)) {
            target.delete()
            throw FetchFailure("the download did not match its checksum")
        }
    }

    private fun transfer(url: URL, target: File, cancelled: () -> Boolean, progress: (Long, Long) -> Unit) {
        val have = if (target.isFile) target.length() else 0L
        val connection = connect(url)
        try {
            val reply = open(connection, have, target)
            val written = stream(reply, target, cancelled, progress)
            if (reply.total > 0 && written < reply.total) throw IOException("short read")
            progress(written, if (reply.total > 0) reply.total else written)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(connection: HttpURLConnection, have: Long, target: File): Reply {
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        if (have > 0) connection.setRequestProperty("Range", "bytes=$have-")
        val code = connection.responseCode
        if (code == HTTP_RANGE_NOT_SATISFIABLE) {
            // The partial file is not usable; the next attempt starts over.
            target.delete()
            throw IOException("range not satisfiable")
        }
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            throw FetchFailure("the server answered $code")
        }
        val append = code == HttpURLConnection.HTTP_PARTIAL && have > 0
        val base = if (append) have else 0L
        return Reply(connection, append, base, totalOf(connection, code, base))
    }

    private fun totalOf(connection: HttpURLConnection, code: Int, base: Long): Long {
        val ranged = if (code == HttpURLConnection.HTTP_PARTIAL) {
            connection.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
        } else null
        return ranged ?: connection.contentLengthLong.takeIf { it > 0 }?.plus(base) ?: 0L
    }

    /** Copies the body to [target]; returns the file's length afterwards. */
    private fun stream(reply: Reply, target: File, cancelled: () -> Boolean, progress: (Long, Long) -> Unit): Long =
        reply.connection.inputStream.use { input ->
            FileOutputStream(target, reply.append).use { output ->
                copy(input, output, reply, cancelled, progress)
            }
        }

    private fun copy(
        input: InputStream,
        output: OutputStream,
        reply: Reply,
        cancelled: () -> Boolean,
        progress: (Long, Long) -> Unit,
    ): Long {
        var written = reply.have
        var lastReport = 0L
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var n = input.read(buffer)
        while (n >= 0) {
            if (cancelled()) throw FetchFailure("cancelled")
            output.write(buffer, 0, n)
            written += n
            val now = System.currentTimeMillis()
            if (now - lastReport >= PROGRESS_MS) {
                lastReport = now
                progress(written, reply.total)
            }
            n = input.read(buffer)
        }
        return written
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 65_536
        const val ATTEMPTS = 4
        const val RETRY_MS = 3_000L
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 30_000
        const val PROGRESS_MS = 500L
        const val SHA256_HEX_LENGTH = 64
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}

private const val SHA_BUFFER_BYTES = 65_536

private fun fileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(SHA_BUFFER_BYTES)
        var n = input.read(buffer)
        while (n >= 0) {
            digest.update(buffer, 0, n)
            n = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Maps guest paths into the root filesystem, refusing anything that would leave it. */
class RootfsPaths(private val rootfs: File) {
    /** [dest] is a guest path; it must stay inside the root filesystem. */
    fun resolve(dest: String): File? {
        if (!isSafeGuestPath(dest)) return null
        val base = rootfs.canonicalFile
        val target = File(base, dest.removePrefix("/"))
        return target.takeIf { insideRootfs(base, it) }
    }

    private fun isSafeGuestPath(dest: String): Boolean =
        dest.startsWith("/") && !dest.contains('\u0000') && dest.split('/').none { it == ".." }

    /** Resolves symlinks of the nearest existing folder so a guest link cannot lead outside. */
    private fun insideRootfs(base: File, target: File): Boolean {
        var existing: File? = target.parentFile
        while (existing != null && !existing.exists()) existing = existing.parentFile
        val real = existing?.canonicalFile ?: return false
        return real == base || real.path.startsWith(base.path + File.separator)
    }
}

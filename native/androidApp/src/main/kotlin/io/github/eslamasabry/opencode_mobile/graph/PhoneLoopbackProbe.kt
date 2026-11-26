package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.opencode_mobile.DirectLoopbackHttp
import java.util.Base64
import org.json.JSONObject

/** The authenticated, fixed-loopback readiness check shared by phone starts and status reads. */
internal object PhoneLoopbackProbe {
    fun probe(port: Int, password: String): ProbeAnswer {
        val auth = basic(password)
        for (path in PhoneServerScripts.PROBE_PATHS) {
            val status = fetch(port, path, auth, false).first
            if (status == HTTP_NOT_FOUND) continue
            return PhoneServerScripts.answer(status)
        }
        return ProbeAnswer.NoAnswer
    }

    /**
     * The version the RUNNING phone server reports on its own health answer (never a saved record, never the link):
     * the first probe path that answers with a JSON object carrying `version`, else null. A bounded read.
     */
    fun version(port: Int, password: String): String? {
        val auth = basic(password)
        return PhoneServerScripts.PROBE_PATHS.firstNotNullOfOrNull { path ->
            fetch(port, path, auth, true).second?.let(::versionIn)
        }
    }

    private fun basic(password: String): String =
        "Basic " +
            Base64.getEncoder()
                .encodeToString("${PhoneServerScripts.USERNAME}:$password".toByteArray())

    private fun fetch(port: Int, path: String, auth: String, body: Boolean): Pair<Int?, String?> =
        try {
            val conn = DirectLoopbackHttp.open(port, path)
            conn.connectTimeout = CONNECT_MS
            conn.readTimeout = READ_MS
            conn.setRequestProperty("Authorization", auth)
            try {
                answerOf(conn, body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null to null
        }

    private fun answerOf(conn: java.net.HttpURLConnection, body: Boolean): Pair<Int?, String?> {
        val status = conn.responseCode
        val stream = if (status >= HTTP_BAD_REQUEST) conn.errorStream else conn.inputStream
        val text =
            stream?.use { input ->
                if (body && status == HTTP_OK) {
                    readAtMost(input, MAX_BODY)
                } else {
                    drainAtMost(input, MAX_BODY)
                    null
                }
            }
        return status to text
    }

    /** Drains at most [max] bytes into a fixed buffer so probes close cleanly without unbounded allocation. */
    private fun drainAtMost(input: java.io.InputStream, max: Int) {
        val scratch = ByteArray(DRAIN_CHUNK)
        var remaining = max
        while (remaining > 0) {
            val read = input.read(scratch, 0, minOf(scratch.size, remaining))
            if (read < 0) break
            remaining -= read
        }
    }

    /**
     * At most [max] bytes as text. minSdk 26: InputStream.readNBytes is API 33, and on Android 8-12 its missing
     * method is an Error that `catch (e: Exception)` does not catch.
     */
    private fun readAtMost(input: java.io.InputStream, max: Int): String {
        val buffer = ByteArray(max)
        var n = 0
        while (n < max) {
            val read = input.read(buffer, n, max - n)
            if (read < 0) break
            n += read
        }
        return buffer.decodeToString(0, n)
    }

    private fun versionIn(text: String): String? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val raw = root.optString("version").ifEmpty { root.optJSONObject("data")?.optString("version").orEmpty() }
        return raw.removePrefix("v").trim().takeIf { VERSION_SHAPE.matches(it) }
    }

    private const val CONNECT_MS = 2_000
    private const val READ_MS = 4_000
    private const val MAX_BODY = 8192
    private const val DRAIN_CHUNK = 1024
    private const val HTTP_OK = 200
    private const val HTTP_BAD_REQUEST = 400
    private const val HTTP_NOT_FOUND = 404
    private val VERSION_SHAPE = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+([-+.][0-9A-Za-z.-]+)?$")
}

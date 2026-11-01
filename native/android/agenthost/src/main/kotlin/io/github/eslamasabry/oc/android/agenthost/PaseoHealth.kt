package io.github.eslamasabry.oc.android.agenthost

import java.net.HttpURLConnection
import java.net.URL

/**
 * The helper's own health check, as `PhoneAgentsHost.daemonAnswers` did it: `GET http://127.0.0.1:<port>/api/health`
 * (needs no password), 3 s to connect and to read, redirects never followed, true only for 200. Blocking: callers run
 * it on the I/O dispatcher. The address is app-authored loopback, never a server value.
 */
internal object PaseoHealth {
    private const val TIMEOUT_MS = 3000

    fun answers(port: Int): Boolean {
        if (port !in 1..65535) return false
        val connection = try {
            URL("http", "127.0.0.1", port, "/api/health").openConnection() as HttpURLConnection
        } catch (_: Exception) {
            return false
        }
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            val ok = connection.responseCode == HttpURLConnection.HTTP_OK
            connection.inputStream.use { input -> while (input.read() >= 0) { /* drain */ } }
            ok
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}

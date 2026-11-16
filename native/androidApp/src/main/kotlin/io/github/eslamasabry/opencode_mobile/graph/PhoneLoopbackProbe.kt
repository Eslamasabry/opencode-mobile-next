package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** The authenticated, fixed-loopback readiness check shared by phone starts and status reads. */
internal object PhoneLoopbackProbe {
    fun probe(port: Int, password: String): ProbeAnswer {
        val auth =
            "Basic " +
                Base64.getEncoder()
                    .encodeToString("${PhoneServerScripts.USERNAME}:$password".toByteArray())
        for (path in PhoneServerScripts.PROBE_PATHS) {
            val status =
                try {
                    val conn =
                        URL("http", "127.0.0.1", port, path).openConnection()
                            as HttpURLConnection
                    conn.connectTimeout = 2_000
                    conn.readTimeout = 4_000
                    conn.setRequestProperty("Authorization", auth)
                    try {
                        conn.responseCode
                    } finally {
                        conn.disconnect()
                    }
                } catch (e: Exception) {
                    null
                }
            if (status == 404) continue
            return PhoneServerScripts.answer(status)
        }
        return ProbeAnswer.NoAnswer
    }
}

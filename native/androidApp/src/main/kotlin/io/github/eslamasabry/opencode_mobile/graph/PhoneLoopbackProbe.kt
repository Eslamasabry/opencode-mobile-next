package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import java.util.Base64
import io.github.eslamasabry.opencode_mobile.DirectLoopbackHttp

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
                        DirectLoopbackHttp.open(port, path)
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

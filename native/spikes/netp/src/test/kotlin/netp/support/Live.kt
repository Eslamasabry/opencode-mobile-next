package netp.support

import java.io.File
import java.util.Base64
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live-proof settings. The password is read from a file path (NETP_PASSWORD_FILE), never argv, and is never
 * printed: every result line goes through [result], which refuses a line that contains it.
 */
object Live {
    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    val oc2Url: String? get() = env("NETP_OC2_URL")          // scratch OpenCode 2.1.1
    val oc2NextUrl: String? get() = env("NETP_OC2_URL_NEXT")  // scratch OpenCode 2.2.5
    val paseoUrl: String? get() = env("NETP_PASEO_URL")       // scratch Paseo 0.11.2, ws://127.0.0.1:4099/ws

    private val password: String? by lazy { env("NETP_PASSWORD_FILE")?.let { File(it).readText().trim() } }

    fun password(): String = password ?: error("NETP_PASSWORD_FILE not set")

    fun basic(): String = "Basic " + Base64.getEncoder().encodeToString("opencode:${password()}".toByteArray())

    fun require(url: String?, what: String): String {
        assumeTrue(url != null && password != null, "$what not configured (live proof skipped)")
        return url!!
    }

    fun result(line: String) {
        val pw = password
        check(pw == null || !line.contains(pw)) { "refusing to print a line that contains the password" }
        println("NETP-RESULT $line")
    }
}

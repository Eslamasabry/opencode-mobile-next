package netp

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import netp.support.Live
import netp.support.Oc2
import org.junit.jupiter.api.Tag

/**
 * P-N4 live probe of OpenCode 2.1.1 (pin) and 2.2.5 (latest, also the Flutter builtin pin): the route diff itself is
 * `probe/route_diff.py` over `recorded/<v>/openapi.json`; this test records the runtime facts the spec cannot show.
 */
@Tag("live")
class PN4ProbeTest {
    @Test fun `P-N4 live probe 2_1_1`() = runBlocking { probe(Live.require(Live.oc2Url, "NETP_OC2_URL")) }

    @Test fun `P-N4 live probe 2_2_5`() = runBlocking { probe(Live.require(Live.oc2NextUrl, "NETP_OC2_URL_NEXT")) }

    private fun keys(json: String): List<String> {
        // Top-level keys of a flat-ish object: depth-aware scan, no JSON library in the spike.
        val out = mutableListOf<String>()
        var depth = 0
        var i = 0
        var inString = false
        while (i < json.length) {
            val c = json[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else when (c) {
                '{', '[' -> depth++
                '}', ']' -> depth--
                '"' -> {
                    val end = json.indexOf('"', i + 1)
                    if (depth == 1 && json.getOrNull(end + 1) == ':') { out += json.substring(i + 1, end); i = end } else inString = true
                }
            }
            i++
        }
        return out
    }

    private fun field(json: String, name: String) = Regex("\"$name\":\"([^\"]*)\"").find(json)?.groupValues?.get(1)

    private suspend fun probe(base: String) {
        val oc = Oc2(base)
        val info = oc.get("/api/info").bodyAsText()
        val v = field(info, "version")!!
        val unauth = oc.http.get("$base/api/info")
        Live.result("pn4.$v.unauthenticated=${unauth.status.value} wwwAuthenticate='${unauth.headers["WWW-Authenticate"]}'")
        Live.result("pn4.$v.info.keys=${keys(info)} capabilities=${Regex("\"capabilities\":(\\{[^}]*\\})").find(info)?.groupValues?.get(1)}")
        Live.result("pn4.$v.health=${oc.get("/api/health").status.value}")
        val specUnauth = oc.http.get("$base/openapi.json").status.value
        val spec = oc.get("/openapi.json").bodyAsBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(spec).joinToString("") { "%02x".format(it) }
        Live.result("pn4.$v.openapi.bytes=${spec.size} sha256=$sha unauthenticated=$specUnauth")

        val sid = oc.createSession("netp P-N4 probe")
        val mid = Oc2.clientMessageId()
        val receipt = oc.post("/api/session/$sid/prompt", """{"id":"$mid","text":"netp probe: reply with ok"}""")
        val rb = receipt.bodyAsText()
        val data = rb.substringAfter("\"data\":").dropLast(1)
        Live.result("pn4.$v.receipt.status=${receipt.status.value} type=${field(rb, "type")} idEchoed=${field(rb, "id") == mid} delivery=${field(rb, "delivery")} dataKeys=${keys(data)}")
        assertEquals("user", field(rb, "type"))
        assertEquals(mid, field(rb, "id"))

        val inbox = oc.get("/api/session/$sid/inbox")
        Live.result("pn4.$v.inbox.status=${inbox.status.value} body=${inbox.bodyAsText().take(40).replace(mid, "<id>")}")
        val msg = oc.get("/api/session/$sid/message/$mid")
        val mb = msg.bodyAsText()
        Live.result("pn4.$v.messageLookup.status=${msg.status.value} type=${field(mb, "type")} role=${field(mb, "role")}")
        assertEquals(200, msg.status.value)

        val log = oc.get("/api/experimental/session/$sid/log?after=0&follow=false")
        val lb = log.bodyAsText()
        val types = Regex("\"type\":\"([a-z.]+)\"").findAll(lb).map { it.groupValues[1] }.distinct().take(6).toList()
        Live.result("pn4.$v.sessionLog.status=${log.status.value} contentType=${log.headers["Content-Type"]} firstTypes=$types")
        assertTrue(log.status.value == 200)
    }
}

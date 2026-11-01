package netp.support

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.security.SecureRandom
import netp.HardenedOkHttp
import netp.JvmTransportSpec
import okhttp3.OkHttpClient

/** Thin OpenCode 2 helpers for the live proofs (Basic auth; nothing printed). */
class Oc2(val base: String) {
    val ok: OkHttpClient = HardenedOkHttp.build(JvmTransportSpec(origin = originOf(base), cleartextPermitted = true, pinnedLeafSha256 = null))
    val http: HttpClient = HardenedOkHttp.ktorClient(HardenedOkHttp.ktorEngine(ok))

    suspend fun get(path: String): HttpResponse = http.get(base + path) { header("Authorization", Live.basic()) }

    suspend fun post(path: String, json: String): HttpResponse = http.post(base + path) {
        header("Authorization", Live.basic())
        contentType(ContentType.Application.Json)
        setBody(json)
    }

    suspend fun createSession(title: String): String {
        val body = post("/api/session", """{"title":"$title"}""").bodyAsText()
        return Regex("\"id\":\"(ses[^\"]+)\"").find(body)!!.groupValues[1]
    }

    companion object {
        fun originOf(url: String): String {
            val u = java.net.URI(url)
            val port = if (u.port > 0) u.port else if (u.scheme == "https") 443 else 80
            return "${u.scheme}://${u.host}:$port"
        }

        /** A client-chosen message id, as the delivery engine posts it (`msg_` + 26 base62). */
        fun clientMessageId(): String {
            val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            val r = SecureRandom()
            return "msg_" + (1..26).map { alphabet[r.nextInt(alphabet.length)] }.joinToString("")
        }
    }
}

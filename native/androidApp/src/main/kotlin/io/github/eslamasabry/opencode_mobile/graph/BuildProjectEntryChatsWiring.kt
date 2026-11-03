package io.github.eslamasabry.opencode_mobile.graph

import android.app.ActivityManager
import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.feature.about.managespace.ManageSpaceFacts
import io.github.eslamasabry.oc.feature.about.managespace.ManageSpacePort
import java.io.File
import kotlinx.coroutines.withContext
import dev.zacsweers.metro.ContributesBinding
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.CallKind
import io.github.eslamasabry.oc.core.net.StatusMapping
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportFailures
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.FileSaver
import io.github.eslamasabry.oc.engine.project.session.SessionExportPort
import io.github.eslamasabry.oc.engine.project.session.SessionExporter
import io.github.eslamasabry.oc.engine.project.session.SessionNoteGateway
import io.github.eslamasabry.oc.engine.project.session.SessionNoteWire
import io.github.eslamasabry.oc.engine.project.session.SessionNoteWireReply
import io.github.eslamasabry.oc.engine.project.session.NoteMethod
import io.github.eslamasabry.oc.engine.project.session.SessionNotes
import io.github.eslamasabry.oc.feature.sessiontools.OfflineSessionToolsHub
import io.github.eslamasabry.oc.feature.sessiontools.SessionToolsHub
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlin.io.encoding.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Unit P12-BUILD-PROJECT-ENTRY-CHATS: the Android-only bindings of :feature:about and :engine:project. Today: the
 * manage-space guard page (B-ANDR-035) that Android opens from Settings › Apps › Clear storage.
 */
@ContributesTo(AppScope::class)
interface BuildProjectEntryChatsGraph {
    val manageSpacePort: AndroidManageSpacePort
}

/** Measures and clears the app's cache folders, and hands the full clear to Android. Never reads file contents. */
@SingleIn(AppScope::class)
@Inject
class AndroidManageSpacePort(
    private val application: Application,
    private val dispatchers: AppDispatchers,
    private val connections: ServerConnections,
) : ManageSpacePort {
    private val cacheRoots: List<File> get() = listOfNotNull(application.cacheDir, application.codeCacheDir, application.externalCacheDir)

    override suspend fun measure(): Outcome<ManageSpaceFacts> =
        withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                ManageSpaceFacts(
                    cacheBytes = cacheRoots.sumOf(::sizeOf),
                    savedServers = connections.servers.value.size,
                    phoneServer = false, // the built-in Linux is not in this build
                )
            }
        }

    override suspend fun clearCache(): Outcome<Long> =
        withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                var freed = 0L
                cacheRoots.forEach { root ->
                    root.listFiles()?.forEach { child ->
                        val size = sizeOf(child)
                        if (child.deleteRecursively()) freed += size
                    }
                }
                freed
            }
        }

    override suspend fun clearAllData(): Boolean =
        withContext(dispatchers.main) {
            val cleared =
                attempt({ Failure.Device() }) {
                    application.getSystemService(ActivityManager::class.java).clearApplicationUserData()
                }
            (cleared as? Outcome.Ok)?.value == true
        }

    private fun sizeOf(file: File): Long =
        when {
            !file.exists() -> 0L
            file.isFile -> file.length()
            else -> file.walkTopDown().onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }.filter { it.isFile }.sumOf { it.length() }
        }
}

/**
 * Chat ⋯ › Export conversation and Notes over the conversation's OWN live connection (never the active profile's):
 * `GET /api/session/{id}/export?sanitize=…` and `/api/session/{id}/instructions/entries[/mobile.note]`, with the
 * profile's resolved credentials and the same trust gate as the Git page's client (https, or loopback/consented
 * cleartext; a pinned leaf only over https). One exporter and one notes owner per live graph: a replaced connection
 * gets fresh ones, so a late answer from the old one cannot land in the new one.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [OfflineSessionToolsHub::class])
@Inject
class AndroidSessionToolsHub(
    private val registry: ConnectionRegistry,
    private val http: HttpClientFactory,
    private val credentialSources: Set<ConnectionCredentialSource>,
    private val redactor: Redactor,
    private val savers: Set<FileSaver>,
) : SessionToolsHub {
    private class Live(val graph: ConnectionGraph, val net: NetClient, val exporter: SessionExporter, val notes: SessionNotes)

    private val lock = Any()
    private val live = HashMap<ProfileId, Live>()
    private val revisions = HashMap<ProfileId, Int>()
    private val titles = HashMap<ConversationRef, String>()
    private val locations = HashMap<ConversationRef, ServerLocation>()
    private val noteGateways = HashMap<ConversationRef, SessionNoteGateway>()
    private val authLock = Mutex()
    private val auths = HashMap<ProfileId, String?>()

    override fun exporter(ref: ConversationRef): SessionExporter? = liveFor(ref)?.exporter

    override fun notes(ref: ConversationRef): SessionNotes? = liveFor(ref)?.notes

    override fun title(ref: ConversationRef): String? = synchronized(lock) { titles[ref] }

    override suspend fun save(name: String, mime: String, bytes: ByteArray): Outcome<Boolean> {
        val saver = savers.firstOrNull() ?: return Outcome.Failed(Failure.Unavailable(FeatureId("save")))
        return saver.save(FileData(name, mime, bytes))
    }

    private fun liveFor(ref: ConversationRef): Live? {
        val graph = registry.graphFor(ref) ?: return null
        synchronized(lock) {
            live[ref.profileId]?.let { if (it.graph === graph) return it else it.net.close() }
            val net = clientFor(graph.profile) ?: return null
            revisions[ref.profileId] = (revisions[ref.profileId] ?: 0) + 1
            val revision = revisions.getValue(ref.profileId)
            val exporter = SessionExporter(exportPort(graph, net))
            val notes =
                SessionNotes(
                    current = { synchronized(lock) { noteGateways[ref] }.takeIf { registry.graphFor(ref) === graph } },
                    prepare = { prepareNotes(ref, graph, net) },
                    locationRevision = { if (registry.graphFor(ref) === graph) revision else -1 },
                )
            return Live(graph, net, exporter, notes).also { live[ref.profileId] = it }
        }
    }

    private fun exportPort(graph: ConnectionGraph, net: NetClient): SessionExportPort =
        SessionExportPort { request ->
            val ref = ConversationRef(graph.profile.id, request.sessionId)
            remember(ref, graph)
            call(graph, net, HttpMethod.Get, request.path, request.query) { it }
        }

    private suspend fun prepareNotes(ref: ConversationRef, graph: ConnectionGraph, net: NetClient): SessionNoteGateway? {
        if (registry.graphFor(ref) !== graph) return null
        val location = remember(ref, graph) ?: return null
        synchronized(lock) { noteGateways[ref] }?.let { return it }
        val wire =
            SessionNoteWire { request ->
                val body = request.body
                call(graph, net, method(request.method), request.path, request.query, body) { bytes ->
                    if (request.method == NoteMethod.Get) {
                        SessionNoteWireReply.Read(toPlain(Json.parseToJsonElement(bytes.decodeToString())))
                    } else {
                        SessionNoteWireReply.Done
                    }
                }
            }
        val gateway = SessionNoteGateway(wire, location)
        synchronized(lock) { noteGateways[ref] = gateway }
        return gateway
    }

    /** The conversation's location and title, read once per conversation from its own server. */
    private suspend fun remember(ref: ConversationRef, graph: ConnectionGraph): ServerLocation? {
        synchronized(lock) { locations[ref] }?.let { return it }
        val session = (graph.session.server.session(ref.sessionId) as? Outcome.Ok)?.value ?: return null
        val location = ServerLocation(directory = session.directory, workspace = session.workspaceId?.raw)
        synchronized(lock) {
            locations[ref] = location
            session.title?.takeIf { it.isNotBlank() }?.let { titles[ref] = it }
        }
        return location
    }

    private fun method(method: NoteMethod): HttpMethod =
        when (method) {
            NoteMethod.Get -> HttpMethod.Get
            NoteMethod.Put -> HttpMethod.Put
            NoteMethod.Delete -> HttpMethod.Delete
        }

    /** One call; a non-2xx keeps the server's error name as the failure tag (never its prose). */
    private suspend fun <T> call(
        graph: ConnectionGraph,
        net: NetClient,
        verb: HttpMethod,
        path: String,
        query: Map<String, String>,
        jsonBody: String? = null,
        read: (ByteArray) -> T,
    ): Outcome<T> {
        val auth = authorization(graph.profile) ?: return Outcome.Failed(Failure.SecretUnavailable())
        val origin = graph.profile.origin
        val build: HttpRequestBuilder.() -> Unit = {
            method = verb
            url(origin.toString() + "/api" + path)
            for ((key, value) in query) url.parameters.append(key, value)
            auth.value?.let { header(HttpHeaders.Authorization, it) }
            header(HttpHeaders.Accept, "application/json")
            if (jsonBody != null) {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
        }
        // The body is read in both cases: a failure's `_tag` becomes the failure tag, a success is handed to [read].
        return when (val answered = net.exchangeRaw(build)) {
            is Outcome.Failed -> answered
            is Outcome.Ok -> {
                val (status, bytes) = answered.value
                if (status in 200..299) {
                    attempt({ Failure.UnreadableReply() }) { read(bytes) }
                } else {
                    Outcome.Failed(tagged(StatusMapping.failureFor(status), errorName(bytes)))
                }
            }
        }
    }

    private class Auth(val value: String?)

    private suspend fun authorization(profile: ServerProfile): Auth? =
        authLock.withLock {
            if (profile.id in auths) return@withLock Auth(auths[profile.id])
            val value =
                when (val read = credentialSources.singleOrNull()?.resolve(profile)) {
                    null -> null
                    is Outcome.Failed -> return@withLock null
                    is Outcome.Ok ->
                        read.value.secret?.let { secret ->
                            val token = Base64.encode("${read.value.username ?: "opencode"}:$secret".encodeToByteArray())
                            redactor.registerSecret(secret)
                            redactor.registerSecret(token)
                            "Basic $token"
                        }
                }
            auths[profile.id] = value
            Auth(value)
        }

    private fun clientFor(profile: ServerProfile): NetClient? {
        val origin = profile.origin
        val cleartext = AddressRules.isLoopback(origin.host) || profile.trust.cleartextConsentAtMs != null
        if (origin.scheme != "https" && !cleartext) return null
        val pin = profile.trust.pinnedLeafSha256?.let { decodePin(it) ?: return null }
        if (origin.scheme != "https" && pin != null) return null
        return http.create(TransportSpec(origin, AddressRules.classify(origin.host), cleartext, pin))
    }

    private fun decodePin(value: String): ByteArray? {
        val hex = value.replace(":", "")
        if (hex.length != 64 || hex.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private companion object {
        fun errorName(bytes: ByteArray): String? {
            val text = bytes.decodeToString()
            if (text.isBlank() || text.length > 65_536) return null
            val json = try { Json.parseToJsonElement(text) } catch (_: IllegalArgumentException) { return null }
            val obj = json as? JsonObject ?: return null
            val name = listOf("_tag", "code").firstNotNullOfOrNull { (obj[it] as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            return name?.takeIf { it.length <= 64 && it.all { c -> c.isLetterOrDigit() || c == '_' } }
        }

        fun tagged(failure: Failure, name: String?): Failure {
            val tag = name?.let(::WireTag) ?: return failure
            return when (failure) {
                is Failure.NotFound -> failure.copy(tag = tag)
                is Failure.Conflict -> failure.copy(tag = tag)
                is Failure.Busy -> failure.copy(tag = tag)
                is Failure.Server -> failure.copy(tag = tag)
                is Failure.Rejected -> failure.copy(tag = tag)
                else -> failure
            }
        }

        /** JSON → the Map/List/String/Number/Boolean/null shape the note gateway reads (Dart `jsonDecode`). */
        fun toPlain(element: JsonElement): Any? =
            when (element) {
                is JsonObject -> element.mapValues { toPlain(it.value) }
                is kotlinx.serialization.json.JsonArray -> element.map { toPlain(it) }
                is JsonPrimitive ->
                    when {
                        element is kotlinx.serialization.json.JsonNull -> null
                        element.isString -> element.content
                        element.content == "true" -> true
                        element.content == "false" -> false
                        else -> element.content.toLongOrNull() ?: element.content.toDoubleOrNull()
                    }
            }
    }
}

/**
 * Status and body of one call. [NetClient.exchange] answers a non-2xx before the body is read, but the error envelope's
 * `_tag`/`code` (Dart `Api2Error.tag`) decides absent-route vs missing-session, so this reads both. Transport errors
 * keep the stack's mapping (trust refusals first; a read is never "uncertain").
 */
private suspend fun NetClient.exchangeRaw(build: HttpRequestBuilder.() -> Unit): Outcome<Pair<Int, ByteArray>> {
    val builder = HttpRequestBuilder().apply(build)
    builder.expectSuccess = false
    val kind = CallKind.of(builder.method)
    return attempt({ TransportFailures.of(kind, TransportError.Other, it) }) {
        http.prepareRequest(builder).execute { response -> response.status.value to response.bodyAsBytes() }
    }
}

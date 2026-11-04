package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.ApprovedLink
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.HostClass
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.git.GitHost
import io.github.eslamasabry.oc.engine.git.GitHubDeviceFlow
import io.github.eslamasabry.oc.engine.git.GitHubTokenStore
import io.github.eslamasabry.oc.engine.git.GitServices
import io.github.eslamasabry.oc.engine.git.GitUnavailableReason
import io.github.eslamasabry.oc.engine.git.GitWorkspace
import io.github.eslamasabry.oc.engine.git.GitWorkspaceResolution
import io.github.eslamasabry.oc.engine.git.GitWorkspaces
import io.github.eslamasabry.oc.engine.git.NetGitHubHttp
import io.github.eslamasabry.oc.engine.git.ServerGitShell
import io.github.eslamasabry.oc.engine.git.resolveGitWorkspace
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Android bindings of unit P12-GIT: the Git page's workspace resolver (the conversation's live
 * server + its project folder, reached through `POST /api/shell` with the profile's own resolved
 * credentials), the GitHub device flow over two trust-gated clients (github.com and
 * api.github.com), the token store in the app's encrypted secret store, and the external-link
 * opener. Nothing here logs a value.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object GitWiring {
    @Provides
    @SingleIn(AppScope::class)
    fun gitServices(
        application: Application,
        stores: PreviewStores,
        registry: ConnectionRegistry,
        http: HttpClientFactory,
        credentialSources: Set<ConnectionCredentialSource>,
        redactor: Redactor,
        appScope: AppCoroutineScope,
        dispatchers: AppDispatchers,
        clock: Clock,
    ): GitServices {
        val github =
            http.create(
                TransportSpec(
                    ServerOrigin("https", "github.com", 443),
                    HostClass.Public,
                    cleartextPermitted = false,
                )
            )
        val api =
            http.create(
                TransportSpec(
                    ServerOrigin("https", "api.github.com", 443),
                    HostClass.Public,
                    cleartextPermitted = false,
                )
            )
        val now = { clock.now().toEpochMilliseconds() }
        val tokens = GitHubTokenStore(stores.secrets, redactor)
        val services =
            GitServices(
                workspaces =
                    ServerGitWorkspaces(
                        registry,
                        http,
                        credentialSources,
                        redactor,
                        stores,
                        BuiltinLinux.get(application),
                        dispatchers,
                    ),
                tokens = tokens,
                flow = GitHubDeviceFlow(NetGitHubHttp(github, api), redactor, now),
                redactor = redactor,
                links = ExternalLinkPolicy(),
                opener = AndroidLinkOpener(application),
                scope = appScope,
                nowMs = now,
            )
        // A removed server's GitHub sign-in goes with it (B-SEC-085). Only after the profile list
        // is really loaded:
        // an unread list must never look like "no servers".
        appScope.launch {
            if (stores.warm() !is Outcome.Ok) return@launch
            stores.profiles.profiles.collect { loaded ->
                services.forgetRemovedProfiles(loaded.map { it.id }.toSet())
            }
        }
        return services
    }
}

/**
 * Opens an approved link in the person's browser. Only [ExternalLinkPolicy] makes an
 * [ApprovedLink].
 */
private class AndroidLinkOpener(private val application: Application) : ExternalLinkOpener {
    override fun open(link: ApprovedLink): Outcome<Unit> =
        try {
            application.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link.uri))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Unit.ok()
        } catch (e: ActivityNotFoundException) {
            Outcome.Failed(Failure.Unavailable(FeatureId("browser")))
        }
}

/**
 * B-NAV-022 on the live connection: the conversation's server must be connected, the conversation
 * must have a project folder, and the server must not report the command endpoint absent. The
 * native OC2 gateway does not advertise `remoteShell` yet, so an unadvertised server is tried and a
 * 404 answers "can't run Git" (engine `GitPage`).
 */
private class ServerGitWorkspaces(
    private val registry: ConnectionRegistry,
    private val http: HttpClientFactory,
    private val credentialSources: Set<ConnectionCredentialSource>,
    private val redactor: Redactor,
    private val stores: PreviewStores,
    private val linux: BuiltinLinux,
    private val dispatchers: AppDispatchers,
) : GitWorkspaces {
    private val lock = Mutex()
    private val clients = HashMap<ProfileId, Pair<ConnectionGraph, NetClient>>()

    override suspend fun resolve(ref: ConversationRef): GitWorkspaceResolution {
        val graph =
            registry.graphFor(ref)
                ?: return GitWorkspaceResolution.Unavailable(GitUnavailableReason.NotConnected)
        val session =
            when (val read = graph.session.server.session(ref.sessionId)) {
                is Outcome.Failed ->
                    return GitWorkspaceResolution.Unavailable(GitUnavailableReason.NotConnected)
                is Outcome.Ok -> read.value
            }
        val profile = graph.profile
        // Stored runtime ownership plus the exact managed origin, never merely a server at a
        // familiar port (Dart looksLikeInAppServer). ServerProfile.managedOnPhone is never set by
        // the profile store, so it cannot gate this.
        fun ownsLinux(): Boolean =
            try {
                registry.graphFor(ref) === graph &&
                    profile.origin == ServerOrigin("http", "127.0.0.1", 4097) &&
                    (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value ==
                        profile.id.raw
            } catch (_: Exception) {
                false
            }
        if (ownsLinux()) {
            val folder =
                session.directory?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return GitWorkspaceResolution.Unavailable(GitUnavailableReason.NoProject)
            return GitWorkspaceResolution.Available(
                GitWorkspace(
                    GitHost.BuiltinLinux,
                    folder,
                    BuiltinGitShell(linux, dispatchers, ::ownsLinux),
                    placeName = profile.name,
                    credentials = BuiltinGitCredentialDrop(linux, dispatchers, ::ownsLinux),
                )
            )
        }
        val auth =
            when (val read = credentialSources.singleOrNull()?.resolve(profile)) {
                null -> null
                is Outcome.Failed ->
                    return GitWorkspaceResolution.Unavailable(GitUnavailableReason.NotConnected)
                is Outcome.Ok ->
                    read.value.secret?.let { secret ->
                        val token =
                            Base64.encode(
                                "${read.value.username ?: "opencode"}:$secret".encodeToByteArray()
                            )
                        redactor.registerSecret(secret)
                        redactor.registerSecret(token)
                        "Basic $token"
                    }
            }
        val net =
            clientFor(graph, profile)
                ?: return GitWorkspaceResolution.Unavailable(GitUnavailableReason.NotConnected)
        val advertised = graph.session.capabilities.value.remoteShell
        return resolveGitWorkspace(
            connected = true,
            directory = session.directory,
            remoteShell = if (advertised) true else null,
            placeName = profile.name,
        ) { folder ->
            ServerGitShell(net, profile.origin, auth, folder)
        }
    }

    /**
     * One trust-gated client per live connection; a replaced connection gets a new one and the old
     * one is closed.
     */
    private suspend fun clientFor(graph: ConnectionGraph, profile: ServerProfile): NetClient? =
        lock.withLock {
            clients[profile.id]?.let { (owner, client) ->
                if (owner === graph) return@withLock client
                client.close()
                clients.remove(profile.id)
            }
            val origin = profile.origin
            val loopback = AddressRules.isLoopback(origin.host)
            val cleartext = loopback || profile.trust.cleartextConsentAtMs != null
            if (origin.scheme != "https" && !cleartext) return@withLock null
            val pin = profile.trust.pinnedLeafSha256?.let { decodePin(it) ?: return@withLock null }
            if (origin.scheme != "https" && pin != null) return@withLock null
            val client =
                http.create(
                    TransportSpec(origin, AddressRules.classify(origin.host), cleartext, pin)
                )
            clients[profile.id] = graph to client
            client
        }

    private fun decodePin(value: String): ByteArray? {
        val hex = value.replace(":", "")
        if (hex.length != 64 || hex.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

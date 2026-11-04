package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.IntegrationCommandGateway
import io.github.eslamasabry.oc.core.domain.IntegrationGateway
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.*
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.usage.canonicalUsageProviderId
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import java.net.URI
import kotlinx.collections.immutable.*
import kotlinx.coroutines.withContext

/**
 * Exact saved-server sign-in targets. Replaced profiles, graphs or locations refuse credential
 * writes.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PlanSignInPort>())
class AndroidPlanSignInPort(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
    private val phone: PhoneAgents,
    private val nav: AppNavigator,
    private val application: Application,
) : PlanSignInPort {
    private data class Selected(
        val profile: LoadedProfile,
        val graph: ConnectionGraph,
        val location: ServerLocation,
    )

    private val selected = LinkedHashMap<String, Selected>()
    private val phoneOwners = LinkedHashMap<String, ProfileId>()
    private val installed =
        setOf(
            PhoneAgentStatus.Ready,
            PhoneAgentStatus.SignInNeeded,
            PhoneAgentStatus.PhoneCheck,
            PhoneAgentStatus.Stopped,
            PhoneAgentStatus.Limit,
        )

    private fun hasPhoneAgent(provider: String): Boolean =
        phone.state.value.available &&
            phone.state.value.agents.any { it.id == provider && it.status in installed }

    private fun current(source: Selected): Boolean =
        stores.profiles.profile(source.profile.id) == source.profile &&
            registry.live.value[source.profile.id] === source.graph &&
            source.graph.session.server.location.value == source.location

    override suspend fun choices(provider: String): Outcome<ImmutableList<PlanSignInChoice>> {
        when (val warmed = stores.warm()) {
            is Outcome.Failed -> return warmed
            is Outcome.Ok -> Unit
        }
        val builtin = stores.prefs.string("oc.builtinServerOwner")
        // Adapter eligibility: this credential contract belongs to OpenCode; UI sees capability
        // facts below.
        val rows =
            stores.profiles.profiles.value
                .filter {
                    it.profile.backend == BackendKind.OpenCode2 && !it.profile.transientTransport
                }
                .map { PlanSignInChoice(it.id.raw, it.profile.name, inApp = it.id.raw == builtin) }
                .sortedBy { if (it.inApp) 0 else 1 }
        val owner =
            builtin
                ?.takeIf { it.isNotEmpty() }
                ?.let(::ProfileId)
                ?.takeIf { stores.profiles.profile(it) != null }
        if (provider in setOf("claude", "codex") && owner != null) {
            phone.refresh()
            if (hasPhoneAgent(provider)) {
                phoneOwners[provider] = owner
                return (rows + PlanSignInChoice("phone", "", phone = true)).toImmutableList().ok()
            }
        }
        phoneOwners.remove(provider)
        return rows.toImmutableList().ok()
    }

    override suspend fun openPhone(provider: String): Outcome<Unit> {
        val owner = phoneOwners[provider] ?: return stale()
        if (
            stores.prefs.string("oc.builtinServerOwner") != owner.raw ||
                stores.profiles.profile(owner) == null
        )
            return stale()
        when (val refreshed = phone.refresh()) {
            is Outcome.Failed -> return refreshed
            is Outcome.Ok -> Unit
        }
        if (!hasPhoneAgent(provider))
            return Failure.Unavailable(FeatureId("phoneAgentSignIn")).failed()
        if (
            stores.prefs.string("oc.builtinServerOwner") != owner.raw ||
                stores.profiles.profile(owner) == null
        )
            return stale()
        nav.open(AgentsRoute.SignInTerminal(owner, provider))
        return Unit.ok()
    }

    override suspend fun integrations(
        connectionId: String,
        provider: String,
    ): Outcome<ImmutableList<IntegrationInfo>> {
        val id = ProfileId(connectionId)
        val profile = stores.profiles.profile(id) ?: return stale()
        if (profile.reentry.password || profile.reentry.agentToken)
            return Failure.SecretUnavailable().failed()
        if (profile.cleartextUnconfirmed)
            return Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val live = registry.live.value[id]
        val target =
            profile.toServerProfile(managedOnPhone = live?.profile?.managedOnPhone == true)
                ?: return stale()
        val graph =
            when (val opened = registry.open(target)) {
                is Outcome.Failed -> return opened
                is Outcome.Ok -> opened.value
            }
        if (
            graph.profile != target ||
                stores.profiles.profile(id) != profile ||
                registry.live.value[id] !== graph
        )
            return stale()
        if (
            !graph.session.capabilities.value.integrationCredentials ||
                graph.session.server !is IntegrationGateway
        )
            return Failure.Unavailable(FeatureId("integrationCredentials")).failed()
        selected[connectionId] = Selected(profile, graph, graph.session.server.location.value)
        while (selected.size > 32) selected.remove(selected.keys.first())
        return inSource(connectionId) { gateway ->
            gateway.integrations().map { all ->
                all.filter { canonicalUsageProviderId(it.id) == provider || it.id == provider }
                    .map { integration ->
                        val supported =
                            integration.methods.filter {
                                it.type == "key" ||
                                    (it.type == "oauth" && it.id != null) ||
                                    (it.type == "command" &&
                                        it.id != null &&
                                        graph.session.capabilities.value.integrationCommandAuth &&
                                        gateway is IntegrationCommandGateway)
                            }
                        // Same key-led provider policy as Flutter connect_methods.dart: never
                        // invent a key method.
                        val keys = supported.filter { it.type == "key" }
                        val offered =
                            if (
                                integration.id.lowercase() in setOf("anthropic", "google") &&
                                    keys.isNotEmpty()
                            )
                                keys
                            else supported
                        integration.copy(
                            methods =
                                offered
                                    .sortedBy { method ->
                                        when (method.type) {
                                            "oauth" ->
                                                if (method.label.lowercase().contains("browser")) 2
                                                else 0
                                            "key" -> 1
                                            else -> 3
                                        }
                                    }
                                    .toImmutableList()
                        )
                    }
                    .filter { it.methods.isNotEmpty() }
                    .toImmutableList()
            }
        }
    }

    override suspend fun connectKey(
        connectionId: String,
        integrationId: String,
        key: String,
    ): Outcome<Unit> = inSource(connectionId) { it.connectIntegrationKey(integrationId, key) }

    override suspend fun startOAuth(
        connectionId: String,
        integrationId: String,
        methodId: String,
        inputs: ImmutableMap<String, String>,
    ): Outcome<IntegrationAuthLaunch> =
        inSource(connectionId) { it.startIntegrationOAuth(integrationId, methodId, inputs) }

    override suspend fun startCommand(
        connectionId: String,
        integrationId: String,
        methodId: String,
    ): Outcome<IntegrationAuthLaunch> =
        inSource(connectionId) { gateway ->
            (gateway as? IntegrationCommandGateway)?.startIntegrationCommand(
                integrationId,
                methodId,
            ) ?: Failure.Unavailable(FeatureId("integrationCommandAuth")).failed()
        }

    override suspend fun status(
        connectionId: String,
        integrationId: String,
        attemptId: String,
        command: Boolean,
    ): Outcome<IntegrationAuthStatus> =
        inSource(connectionId) { gateway ->
            if (command)
                (gateway as? IntegrationCommandGateway)?.integrationCommandStatus(
                    integrationId,
                    attemptId,
                ) ?: Failure.Unavailable(FeatureId("integrationCommandAuth")).failed()
            else gateway.integrationOAuthStatus(attemptId)
        }

    override suspend fun complete(
        connectionId: String,
        attemptId: String,
        code: String?,
    ): Outcome<Unit> =
        inSource(connectionId) {
            it.completeIntegrationOAuth(attemptId, code?.let(::completionCode))
        }

    override suspend fun cancel(
        connectionId: String,
        integrationId: String,
        attemptId: String,
        command: Boolean,
    ): Outcome<Unit> =
        inSource(connectionId) { gateway ->
            if (command)
                (gateway as? IntegrationCommandGateway)?.cancelIntegrationCommand(
                    integrationId,
                    attemptId,
                ) ?: Failure.Unavailable(FeatureId("integrationCommandAuth")).failed()
            else gateway.cancelIntegrationOAuth(attemptId)
        }

    private fun completionCode(raw: String): String {
        val trimmed = raw.trim()
        return try {
            URI(trimmed)
                .rawQuery
                ?.split('&')
                ?.firstOrNull { it.substringBefore('=') == "code" }
                ?.substringAfter('=')
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?.trim()
                ?.takeIf { it.isNotEmpty() } ?: trimmed
        } catch (_: Exception) {
            trimmed
        }
    }

    private suspend fun <T> inSource(
        id: String,
        block: suspend (IntegrationGateway) -> Outcome<T>,
    ): Outcome<T> {
        val source = selected[id] ?: return stale()
        if (!current(source)) return stale()
        val gateway =
            source.graph.session.server as? IntegrationGateway
                ?: return Failure.Unavailable(FeatureId("integrationCredentials")).failed()
        return withContext(source.graph.scope.coroutineContext) {
            if (!current(source)) return@withContext stale()
            val result = block(gateway)
            if (current(source)) result else stale()
        }
    }

    override fun copyCode(connectionId: String, code: String): Outcome<Unit> {
        val source = selected[connectionId] ?: return stale()
        if (
            !current(source) ||
                code.length !in 4..24 ||
                code.any { !(it.isUpperCase() || it.isDigit() || it == '-') }
        )
            return stale()
        return try {
            val clipboard = application.getSystemService(ClipboardManager::class.java)
            val clip = ClipData.newPlainText(null, code)
            clip.description.extras =
                PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            clipboard.setPrimaryClip(clip)
            Unit.ok()
        } catch (_: SecurityException) {
            Failure.Device().failed()
        } catch (_: IllegalStateException) {
            Failure.Device().failed()
        }
    }

    override fun openLink(
        connectionId: String,
        url: String,
        confirmed: Boolean,
    ): Outcome<PlanSignInLink> {
        val source = selected[connectionId] ?: return stale()
        if (!current(source)) return stale()
        // OAuth pages are HTTPS only, even when a browser could open an insecure link.
        if (!url.trim().startsWith("https://", ignoreCase = true))
            return Failure.Refused(RefusalReason.InvalidAddress).failed()
        return when (val decision = policy.checkSignInPage(url)) {
            is LinkDecision.Refuse -> Failure.Refused(RefusalReason.InvalidAddress).failed()
            is LinkDecision.Open ->
                if (current(source)) opener.open(decision.link).map { PlanSignInLink.Opened }
                else stale()
            is LinkDecision.AskFirst ->
                if (confirmed && !decision.insecure) {
                    if (current(source)) opener.open(decision.link).map { PlanSignInLink.Opened }
                    else stale()
                } else PlanSignInLink.Ask(decision.shownHost).ok()
        }
    }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()
}

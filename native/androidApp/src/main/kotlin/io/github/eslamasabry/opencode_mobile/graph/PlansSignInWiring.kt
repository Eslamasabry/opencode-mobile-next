package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.IntegrationGateway
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.*
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.usage.canonicalUsageProviderId
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.feature.plans.presentation.*
import java.net.URI
import kotlinx.collections.immutable.*
import kotlinx.coroutines.withContext

/** Exact saved-server sign-in targets. A replaced graph/profile refuses instead of retargeting a credential write. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class,binding=binding<PlanSignInPort>())
class AndroidPlanSignInPort(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
) : PlanSignInPort {
    private data class Selected(val profile: LoadedProfile,val graph: ConnectionGraph)
    private val selected=LinkedHashMap<String,Selected>()

    override suspend fun choices(provider: String): Outcome<ImmutableList<PlanSignInChoice>> {
        when(val warmed=stores.warm()) { is Outcome.Failed -> return warmed; is Outcome.Ok -> Unit }
        val builtin=stores.prefs.string("oc.builtinServerOwner")
        // Adapter eligibility: the integration credential contract belongs to OpenCode; feature UI uses capability facts below.
        val rows=stores.profiles.profiles.value.filter { it.profile.backend==BackendKind.OpenCode2 && !it.profile.transientTransport }
            .map { PlanSignInChoice(it.id.raw,it.profile.name,inApp=it.id.raw==builtin) }
            .sortedBy { if(it.inApp) 0 else 1 }
        return rows.toImmutableList().ok()
    }
    override suspend fun integrations(connectionId: String, provider: String): Outcome<ImmutableList<IntegrationInfo>> {
        val id=ProfileId(connectionId)
        val profile=stores.profiles.profile(id) ?: return stale()
        if(profile.reentry.password || profile.reentry.agentToken) return Failure.SecretUnavailable().failed()
        if(profile.cleartextUnconfirmed) return Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val live=registry.live.value[id]
        val target=profile.toServerProfile(managedOnPhone=live?.profile?.managedOnPhone==true) ?: return stale()
        val graph=when(val opened=registry.open(target)) { is Outcome.Failed -> return opened;is Outcome.Ok -> opened.value }
        if(graph.profile!=target || stores.profiles.profile(id)!=profile || registry.live.value[id] !== graph) return stale()
        if(!graph.session.capabilities.value.integrationCredentials) return Failure.Unavailable(FeatureId("integrationCredentials")).failed()
        if(graph.session.server !is IntegrationGateway) return Failure.Unavailable(FeatureId("integrationCredentials")).failed()
        selected[connectionId]=Selected(profile,graph)
        while(selected.size>32) selected.remove(selected.keys.first())
        return command(connectionId) { gateway -> gateway.integrations().map { all ->
            all.filter { canonicalUsageProviderId(it.id)==provider || it.id==provider }.map { integration ->
                integration.copy(methods=integration.methods.filter { it.type=="key" || (it.type=="oauth" && it.id!=null) }.toImmutableList())
            }.filter { it.methods.isNotEmpty() }.toImmutableList()
        } }
    }
    override suspend fun connectKey(connectionId:String,integrationId:String,key:String):Outcome<Unit> = command(connectionId) { it.connectIntegrationKey(integrationId,key) }
    override suspend fun startOAuth(connectionId:String,integrationId:String,methodId:String,inputs:ImmutableMap<String,String>):Outcome<IntegrationAuthLaunch> = command(connectionId) { it.startIntegrationOAuth(integrationId,methodId,inputs) }
    override suspend fun status(connectionId:String,attemptId:String):Outcome<IntegrationAuthStatus> = command(connectionId) { it.integrationOAuthStatus(attemptId) }
    override suspend fun complete(connectionId:String,attemptId:String,code:String?):Outcome<Unit> = command(connectionId) { it.completeIntegrationOAuth(attemptId,code?.let(::completionCode)) }
    override suspend fun cancel(connectionId:String,attemptId:String):Outcome<Unit> = command(connectionId) { it.cancelIntegrationOAuth(attemptId) }
    private fun completionCode(raw:String):String {
        val trimmed=raw.trim()
        return try { URI(trimmed).rawQuery?.split('&')?.firstOrNull { it.substringBefore('=')=="code" }?.substringAfter('=')?.let { java.net.URLDecoder.decode(it,"UTF-8") }?.trim()?.takeIf { it.isNotEmpty() } ?: trimmed } catch(_:Exception) { trimmed }
    }
    private suspend fun <T> command(id:String,block:suspend (IntegrationGateway)->Outcome<T>):Outcome<T> {
        val source=selected[id] ?: return stale()
        fun current()=stores.profiles.profile(source.profile.id)==source.profile && registry.live.value[source.profile.id] === source.graph
        if(!current()) return stale()
        val gateway=source.graph.session.server as? IntegrationGateway ?: return Failure.Unavailable(FeatureId("integrationCredentials")).failed()
        return withContext(source.graph.scope.coroutineContext) {
            if(!current()) return@withContext stale()
            val result=block(gateway)
            if(current()) result else stale()
        }
    }
    override fun openLink(url:String,confirmed:Boolean):Outcome<PlanSignInLink> {
        // OAuth pages are HTTPS only, even when a browser could open an insecure link.
        if(!url.trim().startsWith("https://",ignoreCase=true)) return Failure.Refused(RefusalReason.InvalidAddress).failed()
        return when(val decision=policy.checkSignInPage(url)) {
            is LinkDecision.Refuse -> Failure.Refused(RefusalReason.InvalidAddress).failed()
            is LinkDecision.Open -> opener.open(decision.link).map { PlanSignInLink.Opened }
            is LinkDecision.AskFirst -> if(confirmed && !decision.insecure) opener.open(decision.link).map { PlanSignInLink.Opened } else PlanSignInLink.Ask(decision.shownHost).ok()
        }
    }
    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()
}

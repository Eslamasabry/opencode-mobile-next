package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.core.domain.InitialSessionDefaultsGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.ProfileKeys
import io.github.eslamasabry.oc.feature.settings.presentation.ServerModelChoice
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The Flutter profile model override applies only when a new conversation is made. */
@SingleIn(AppScope::class)
@Inject
class NativeServerDefaults(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
) {
    fun model(profile: ProfileId): Outcome<ModelRef?> {
        val raw = stores.prefs.get(MODEL + profile.raw) ?: return null.ok()
        if (raw !is PrefValue.Str) return Failure.StorageRefused().failed()
        val parts = raw.value.split('|')
        if (parts.size != 2 || parts.any(String::isBlank)) return Failure.StorageRefused().failed()
        return ModelRef(parts[0], parts[1]).ok()
    }

    private fun current(graph: ConnectionGraph): Boolean =
        registry.live.value[graph.profile.id] === graph &&
            stores.profiles.profile(graph.profile.id)?.toServerProfile() == graph.profile

    suspend fun choices(profile: ProfileId): Outcome<ImmutableList<ServerModelChoice>> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val graph = registry.live.value[profile] ?: return Failure.Network().failed()
        if (!current(graph)) return Failure.Refused(RefusalReason.StaleScope).failed()
        val location = graph.session.server.location.value
        if (!graph.session.capabilities.value.serverCatalog) {
            if (graph.session.server.facet<InitialSessionDefaultsGateway>() == null)
                return Failure.Unavailable(FeatureId("serverCatalog")).failed()
            val reply = graph.session.server.providers()
            if (!current(graph) || graph.session.server.location.value != location)
                return Failure.Refused(RefusalReason.StaleScope).failed()
            return reply.map { catalog ->
                catalog.providers
                    .flatMap { provider ->
                        provider.modelIds.map { id ->
                            ServerModelChoice(
                                ModelRef(provider.id, id),
                                id,
                                provider.name,
                                emptyList<String>().toImmutableList(),
                            )
                        }
                    }
                    .toImmutableList()
            }
        }
        val reply = (graph as ServerSettingsConnection).serverSettingsOperations.catalog()
        if (!current(graph) || graph.session.server.location.value != location)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        return reply.map { catalog ->
            val providers = catalog.providers.filter { it.enabled }.associate { it.id to it.name }
            catalog.models
                .filter { it.enabled && it.providerId in providers }
                .distinctBy { it.providerId to it.id }
                .map {
                    ServerModelChoice(
                        ModelRef(it.providerId, it.id),
                        it.name.ifBlank { it.id },
                        providers[it.providerId].orEmpty(),
                        it.variants
                            .filterNot { v -> v.disabled }
                            .map { v -> v.id }
                            .toImmutableList(),
                    )
                }
                .toImmutableList()
        }
    }

    suspend fun set(profile: ProfileId, selected: ModelRef?, variant: String = ""): Outcome<Unit> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val loaded =
            stores.profiles.profile(profile)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val profileRecord = stores.prefs.get(ProfileKeys.PROFILES)
        if (selected != null) {
            val choices =
                when (val found = choices(profile)) {
                    is Outcome.Failed -> return found
                    is Outcome.Ok -> found.value
                }
            val choice =
                choices.firstOrNull { it.model == selected }
                    ?: return Failure.Refused(RefusalReason.StaleScope).failed()
            if (
                '|' in selected.providerId ||
                    '|' in selected.modelId ||
                    (variant.isNotEmpty() && variant !in choice.variants)
            )
                return Failure.Refused(RefusalReason.StaleScope).failed()
        }
        if (stores.profiles.profile(profile) !== loaded)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val expected =
            listOf(
                    ProfileKeys.PROFILES,
                    MODEL + profile.raw,
                    EXPLICIT + profile.raw,
                    VARIANT + profile.raw,
                )
                .associateWith(stores.prefs::get) + (ProfileKeys.PROFILES to profileRecord)
        val saved =
            stores.prefs.compareAndCommit(stores.lanes.forProfile(profile), expected) {
                if (selected == null) {
                    remove(MODEL + profile.raw)
                    remove(EXPLICIT + profile.raw)
                    remove(VARIANT + profile.raw)
                } else {
                    putString(MODEL + profile.raw, selected.providerId + "|" + selected.modelId)
                    putBool(EXPLICIT + profile.raw, true)
                    if (variant.isEmpty()) remove(VARIANT + profile.raw)
                    else putString(VARIANT + profile.raw, variant)
                }
            }
        return when (saved) {
            is Outcome.Failed -> saved
            is Outcome.Ok ->
                if (saved.value) Unit.ok() else Failure.Refused(RefusalReason.StaleScope).failed()
        }
    }

    /**
     * Caller owns its final project fence. No mutation retry or guessed model fallback occurs here.
     */
    suspend fun create(
        graph: ConnectionGraph,
        fallback: SessionSelection? = null,
    ): Outcome<Session> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        if (!current(graph)) return Failure.Refused(RefusalReason.StaleScope).failed()
        val profile = graph.profile.id
        val location = graph.session.server.location.value
        val selected =
            when (val found = model(profile)) {
                is Outcome.Failed -> return found
                is Outcome.Ok -> found.value
            }
        var defaults = fallback
        if (selected != null) {
            val choices =
                when (val found = choices(profile)) {
                    is Outcome.Failed -> return found
                    is Outcome.Ok -> found.value
                }
            val choice =
                choices.firstOrNull { it.model == selected }
                    ?: return Failure.Unavailable(FeatureId("selectedModel")).failed()
            val variant = stores.prefs.string(VARIANT + profile.raw).orEmpty()
            if (variant.isNotEmpty() && variant !in choice.variants)
                return Failure.Unavailable(FeatureId("selectedVariant")).failed()
            defaults =
                SessionSelection(
                    selected,
                    variant,
                    fallback?.agent,
                    true,
                    fallback?.agentKnown ?: false,
                )
        }
        if (!current(graph) || graph.session.server.location.value != location)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val ready = graph.session.server.awaitActionTransport()
        if (ready is Outcome.Failed) return ready
        if (!current(graph) || graph.session.server.location.value != location)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val server = graph.session.server
        val result =
            when {
                defaults == null -> server.createSession(NewSession(location = location))
                server.facet<SessionSelectionGateway>() != null ->
                    server.facet<SessionSelectionGateway>()!!.createSelectedSession(defaults)
                server.facet<InitialSessionDefaultsGateway>() != null ->
                    server
                        .facet<InitialSessionDefaultsGateway>()!!
                        .createSessionWithDefaults(defaults)
                selected != null -> Failure.Unavailable(FeatureId("newConversationModel")).failed()
                else -> server.createSession(NewSession(location = location))
            }
        return if (current(graph) && graph.session.server.location.value == location) result
        else Failure.Refused(RefusalReason.StaleScope).failed()
    }

    companion object {
        private const val MODEL = "oc.model."
        private const val EXPLICIT = "oc.modelExplicit."
        private const val VARIANT = "oc.variant."

        fun register(scope: ProfileScope) {
            scope.register(
                KeyShape(
                    MODEL,
                    KeyScope.Profile,
                    PrefType.Str,
                    ":feature:settings",
                    "New conversation default",
                )
            )
            scope.register(
                KeyShape(
                    EXPLICIT,
                    KeyScope.Profile,
                    PrefType.Bool,
                    ":feature:settings",
                    "Explicit new conversation model",
                )
            )
            scope.register(
                KeyShape(
                    VARIANT,
                    KeyScope.Profile,
                    PrefType.Str,
                    ":feature:settings",
                    "New conversation default",
                )
            )
        }
    }
}

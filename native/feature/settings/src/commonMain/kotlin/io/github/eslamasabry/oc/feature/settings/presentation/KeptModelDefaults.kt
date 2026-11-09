package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.engine.agents.models.CatalogModel
import io.github.eslamasabry.oc.engine.agents.models.ModelCost
import io.github.eslamasabry.oc.engine.agents.models.ModelLibrary
import io.github.eslamasabry.oc.engine.agents.models.ModelRef
import kotlinx.serialization.Serializable

/*
 * Cache-first (owner rule, 2026-11-09): Model defaults opens on the rows its last read showed, kept per profile and
 * server by the app's last-known store, then refreshes in place (ModelDefaultsHost.kept).
 */
/** Model defaults as last read: what the page's rows draw, nothing else (no provider keys or settings). */
@Serializable
public data class KeptModelDefaults(
    public val serverName: String,
    public val thisPhone: Boolean,
    public val models: List<Model>,
    public val current: Ref? = null,
    public val defaults: List<Ref> = emptyList(),
) {
    @Serializable
    public data class Model(
        public val id: String,
        public val providerId: String,
        public val name: String,
        public val enabled: Boolean = true,
        public val input: Double? = null,
        public val output: Double? = null,
    )

    @Serializable public data class Ref(public val providerId: String, public val modelId: String)

    /** [stored] is this phone's saved choice now; it wins over the kept one, as in a live read. */
    public fun data(library: ModelLibrary, stored: ModelRef?): ModelDefaultsData =
        ModelDefaultsData(
            serverName,
            thisPhone,
            models.map {
                CatalogModel(
                    it.id,
                    it.providerId,
                    it.name,
                    it.enabled,
                    cost = if (it.input != null && it.output != null) ModelCost(it.input, it.output) else null,
                )
            },
            stored ?: current?.let { ModelRef(it.providerId, it.modelId) },
            defaults.map { ModelRef(it.providerId, it.modelId) },
            library,
        )

    public companion object {
        public fun of(data: ModelDefaultsData): KeptModelDefaults =
            KeptModelDefaults(
                data.serverName,
                data.thisPhone,
                data.models.map {
                    Model(it.id, it.providerId, it.name, it.enabled, it.cost?.inputPerMillion, it.cost?.outputPerMillion)
                },
                data.current?.let { Ref(it.providerId, it.modelId) },
                data.defaults.map { Ref(it.providerId, it.modelId) },
            )
    }
}

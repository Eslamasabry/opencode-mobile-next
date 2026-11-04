package io.github.eslamasabry.oc.engine.agents.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** One reasoning/effort variant a catalog model offers. */
public data class CatalogVariant(public val id: String, public val disabled: Boolean = false)

/** A published price per million tokens. */
public data class ModelCost(public val inputPerMillion: Double, public val outputPerMillion: Double)

/**
 * The slice of a catalog model this package needs (Flutter `CatalogModel`). Instances are compared by identity where
 * the Flutter code used `identical` (recommendations), so callers pass the catalog's own list.
 */
public class CatalogModel(
    public val id: String,
    public val providerId: String,
    public val name: String = id,
    public val enabled: Boolean = true,
    variants: List<CatalogVariant> = emptyList(),
    public val cost: ModelCost? = null,
) {
    public val variants: ImmutableList<CatalogVariant> = variants.toImmutableList()

    public val ref: ModelRef get() = ModelRef(providerId, id)

    override fun toString(): String = "CatalogModel($providerId/$id)"
}

/** The models a server lists right now. */
public class ModelCatalog(models: List<CatalogModel> = persistentListOf()) {
    public val models: ImmutableList<CatalogModel> = models.toImmutableList()

    /** Enabled and listed (normalised comparison). */
    public fun available(ref: ModelRef): Boolean = models.any { it.enabled && ref.sameModel(it.ref) }

    /** The empty variant is always allowed; otherwise the exact model must offer it, not disabled. */
    public fun variantAllowed(ref: ModelRef, variant: String): Boolean {
        if (variant.isEmpty()) return true
        val model = models.firstOrNull { it.providerId == ref.providerId && it.id == ref.modelId } ?: return false
        return model.variants.any { it.id == variant && !it.disabled }
    }
}

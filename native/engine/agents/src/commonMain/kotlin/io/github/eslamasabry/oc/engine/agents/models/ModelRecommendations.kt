package io.github.eslamasabry.oc.engine.agents.models

/**
 * Why a model is offered on the model sheet's first view (Flutter `ModelReason`). Each reason is a fact the server or
 * the app knows, never a judgement; there is no "fastest" because no catalog sends a speed (B-NAV-036).
 */
public enum class ModelReason { InUse, ServerDefault, Recent, Cheapest }

/** One recommended model and why. [alsoCheapest]: the cheapest is this row's model too. [free]: priced at zero. */
public data class ModelRecommendation(
    public val model: CatalogModel,
    public val reason: ModelReason,
    public val alsoCheapest: Boolean = false,
    public val free: Boolean = false,
) {
    public val ref: ModelRef get() = model.ref
}

/** A published price of zero for input and output. */
public fun modelIsFree(model: CatalogModel): Boolean =
    model.cost?.let { it.inputPerMillion == 0.0 && it.outputPerMillion == 0.0 } ?: false

/**
 * At most [limit] rows from [models] (catalog display order): the model in use when nothing else shows it, the server
 * default (the first of [defaults] the list has), one recent choice, the cheapest (only among priced models, only when
 * prices differ; ties go to in use, default, recent, favorite, then list order). One model is never two rows; unknown
 * or disabled refs are skipped, never padded.
 */
public fun recommendModels(
    models: List<CatalogModel>,
    current: ModelRef? = null,
    defaults: List<ModelRef> = emptyList(),
    recent: List<ModelRef> = emptyList(),
    favorites: List<ModelRef> = emptyList(),
    limit: Int = 3,
): List<ModelRecommendation> {
    val usable = models.filter { it.enabled }
    if (usable.isEmpty() || limit <= 0) return emptyList()
    fun same(model: CatalogModel, ref: ModelRef?) = ref != null && ref.sameModel(model.ref)
    fun find(ref: ModelRef?): CatalogModel? = usable.firstOrNull { same(it, ref) }

    val serverDefault = defaults.firstNotNullOfOrNull(::find)

    val priced = usable.filter { it.cost != null }
    fun price(model: CatalogModel) = model.cost!!.inputPerMillion + model.cost.outputPerMillion
    var cheapest: CatalogModel? = null
    if (priced.size > 1) {
        val low = priced.minOf(::price)
        val high = priced.maxOf(::price)
        if (low < high) {
            val tied = priced.filter { price(it) == low }
            fun rank(model: CatalogModel): Int {
                if (same(model, current)) return 0
                if (model === serverDefault) return 1
                val recentAt = recent.indexOfFirst { same(model, it) }
                if (recentAt >= 0) return 2 + recentAt
                val favoriteAt = favorites.indexOfFirst { same(model, it) }
                if (favoriteAt >= 0) return 100 + favoriteAt
                return 1000 + tied.indexOf(model)
            }
            cheapest = tied.sortedBy(::rank).first()
        }
    }

    val inUse = find(current)
    val rows = mutableListOf<ModelRecommendation>()
    if (inUse != null && inUse !== serverDefault && inUse !== cheapest) {
        rows += ModelRecommendation(inUse, ModelReason.InUse, free = modelIsFree(inUse))
    }
    if (serverDefault != null) {
        rows += ModelRecommendation(
            serverDefault,
            ModelReason.ServerDefault,
            alsoCheapest = serverDefault === cheapest,
            free = modelIsFree(serverDefault),
        )
    }
    val cheapestRow = cheapest?.takeIf { it !== serverDefault }
        ?.let { ModelRecommendation(it, ModelReason.Cheapest, free = modelIsFree(it)) }
    val room = limit - rows.size - (if (cheapestRow == null) 0 else 1)
    if (room > 0) {
        val pick = recent.asSequence().mapNotNull(::find)
            .firstOrNull { it !== inUse && it !== serverDefault && it !== cheapest }
        if (pick != null) rows += ModelRecommendation(pick, ModelReason.Recent, free = modelIsFree(pick))
    }
    if (cheapestRow != null) rows += cheapestRow
    return rows.take(limit)
}

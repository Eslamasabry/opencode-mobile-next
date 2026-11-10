package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.kitScrollEdges
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.motion.STAGGER_MS
import io.github.eslamasabry.oc.ui.kit.motion.STAGGER_TRAVEL_DP
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabPages
import io.github.eslamasabry.oc.ui.kit.nav.TabRules
import io.github.eslamasabry.oc.ui.kit.nav.TabStrip
import io.github.eslamasabry.oc.ui.kit.nav.rememberKitTabsState
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitStackScope
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatModelsRecent
import io.github.eslamasabry.oc.ui.words.res.chatModelsSearch
import io.github.eslamasabry.oc.ui.words.res.chat_model_favorite_add
import io.github.eslamasabry.oc.ui.words.res.chat_model_favorite_remove
import io.github.eslamasabry.oc.ui.words.res.chat_model_favorites
import io.github.eslamasabry.oc.ui.words.res.chat_model_favorites_empty
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelDefault
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelDefaultHint
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsTitle
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiChina
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiGlobal
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiPlanChina
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiPlanGlobal
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** One model a person can pick; [supporting] is a second line that tells equal names apart (a host app). */
@Immutable
public data class KitModelOption(
    val providerId: String,
    val modelId: String,
    val label: String,
    val providerName: String = "",
    val supporting: String? = null,
    /** Shown but not choosable, saying why ("Too long for this model"); null = choosable. */
    val disabledReason: String? = null,
) {
    val key: String
        get() = "$providerId/$modelId"
}

public enum class KitModelSectionKind {
    /** One pre-selected suggestion, above everything else (B-169 switch sheet). */
    Suggested,
    Favorites,
    Recent,
    Provider,
}

/** One group of the model list: [providerName] names a [KitModelSectionKind.Provider] group. */
@Immutable
public data class KitModelSection(
    val kind: KitModelSectionKind,
    val rows: List<KitModelOption>,
    val providerName: String? = null,
)

/**
 * The model list's order (N-51, QA wave 1 B3): favourites, then recent picks, then each provider under its name with the
 * current model's provider first and the current model leading its group. A model shows once. Search matches the
 * name, the id or the provider. Pure, so the order is tested without Compose.
 */
public object KitModelOrder {
    public fun sections(
        options: List<KitModelOption>,
        favorites: List<KitModelOption> = emptyList(),
        recent: List<KitModelOption> = emptyList(),
        /** The chosen model's [KitModelOption.key]; null = the server default. */
        current: String? = null,
        query: String = "",
        /** One suggestion's [KitModelOption.key], listed first under its own heading; it shows once. */
        suggested: String? = null,
    ): List<KitModelSection> {
        val q = query.trim().lowercase()
        fun matches(m: KitModelOption) =
            q.isEmpty() ||
                q in m.label.lowercase() ||
                q in m.modelId.lowercase() ||
                q in m.providerName.lowercase() ||
                q in m.providerId.lowercase()
        val known = options.mapTo(HashSet()) { it.key }
        val listed = HashSet<String>()
        val out = ArrayList<KitModelSection>()
        options
            .firstOrNull { it.key == suggested && matches(it) }
            ?.also { listed += it.key }
            ?.let { out += KitModelSection(KitModelSectionKind.Suggested, listOf(it)) }
        val favs = favorites.filter { it.key in known && matches(it) && listed.add(it.key) }
        if (favs.isNotEmpty()) out += KitModelSection(KitModelSectionKind.Favorites, favs)
        val recents = recent.filter { it.key in known && matches(it) && listed.add(it.key) }
        if (recents.isNotEmpty()) out += KitModelSection(KitModelSectionKind.Recent, recents)
        val currentProvider = options.firstOrNull { it.key == current }?.providerId
        options
            .filter { matches(it) && it.key !in listed }
            .groupBy { it.providerId }
            .values
            .sortedWith(
                compareBy<List<KitModelOption>>({ it.first().providerId != currentProvider })
                    .thenBy { it.first().providerName.ifEmpty { it.first().providerId }.lowercase() }
            )
            .forEach { list ->
                val sorted = list.sortedWith(compareBy<KitModelOption>({ it.key != current }, { it.label.lowercase() }))
                out +=
                    KitModelSection(
                        KitModelSectionKind.Provider,
                        sorted,
                        list.first().providerName.ifEmpty { list.first().providerId },
                    )
            }
        return out
    }
}

/** Lists of this length or more get a search field. */
public const val KIT_MODEL_SEARCH_FROM: Int = 8

/**
 * One page of the model sheet: the person's picks (favourites and recent) or one provider's models. [key] names it
 * whatever its place among the tabs.
 */
@Immutable
public data class KitModelPage(
    val key: String,
    /** Null for the picks page. */
    val providerId: String?,
    val providerName: String,
    val sections: List<KitModelSection>,
)

/**
 * The model sheet as pages (owner 2026-11-10: tabs a finger drags, flat rows, no boxed groups). First the picks page
 * (favourites, then recent picks), then one page per provider in [KitModelOrder]'s provider order, each listing every
 * model of that provider (a favourite stays on its provider's page too: a tab that hid some of its models would lie).
 * Pure, so the layout is tested without Compose.
 */
public object KitModelPages {
    /** The picks page's key. */
    public const val PICKS: String = "kit.model.picks"

    /**
     * The pages. The picks page is there when it has something to show, and also, empty, when favourites can be
     * saved here ([canFavorite]) and there is more than one provider: it then says how to fill it. One provider with
     * no picks is a single page, and the sheet shows no tab row for it.
     */
    public fun pages(
        options: List<KitModelOption>,
        favorites: List<KitModelOption> = emptyList(),
        recent: List<KitModelOption> = emptyList(),
        current: String? = null,
        canFavorite: Boolean = false,
    ): List<KitModelPage> {
        val providers = KitModelOrder.sections(options, current = current).filter { it.kind == KitModelSectionKind.Provider }
        val picks =
            KitModelOrder.sections(options, favorites, recent, current)
                .filter { it.kind == KitModelSectionKind.Favorites || it.kind == KitModelSectionKind.Recent }
        val out = ArrayList<KitModelPage>(providers.size + 1)
        if (picks.isNotEmpty() || (canFavorite && providers.size > 1)) out += KitModelPage(PICKS, null, "", picks)
        providers.forEach { section ->
            val first = section.rows.first()
            out += KitModelPage("provider:${first.providerId}", first.providerId, section.providerName.orEmpty(), listOf(section))
        }
        return out
    }

    /** The page the sheet opens on: the picks when there are any, else the chosen model's provider, else the first provider. */
    public fun opening(pages: List<KitModelPage>, current: String?): Int {
        val first = pages.firstOrNull() ?: return 0
        if (first.key == PICKS && first.sections.isNotEmpty()) return 0
        val mine = pages.indexOfFirst { page -> page.key != PICKS && page.sections.any { s -> s.rows.any { it.key == current } } }
        if (mine >= 0) return mine
        return pages.indexOfFirst { it.key != PICKS }.coerceAtLeast(0)
    }

    /** The models that share their name with another model of the same provider: only those rows show their id. */
    public fun twins(options: List<KitModelOption>): Set<String> =
        options
            .groupBy { it.providerId to it.label.trim().lowercase() }
            .values
            .filter { it.size > 1 }
            .flatMapTo(HashSet()) { group -> group.map { it.key } }

    /** What a search finds, as one flat list across every provider (picks first, each model once). */
    public fun results(
        options: List<KitModelOption>,
        favorites: List<KitModelOption>,
        recent: List<KitModelOption>,
        current: String?,
        query: String,
    ): List<KitModelOption> = KitModelOrder.sections(options, favorites, recent, current, query).flatMap { it.rows }

    /**
     * A row's quiet second line. [provider] is given where providers mix (picks, search results) and null on a
     * provider's own page. A [twin] adds its id, so two "GPT-5.5" can be told apart.
     */
    public fun secondLine(option: KitModelOption, provider: String?, twin: Boolean): String? {
        val base = option.supporting ?: provider
        return when {
            !twin -> base
            base == null -> option.modelId
            else -> "$base · ${option.modelId}"
        }
    }
}

/**
 * "Choose a model" (N-51; pages 2026-11-10): the one model sheet for a conversation and for a new one. The title, the
 * search field and the tab row stay put; under them the pages lie side by side and are dragged with a finger, the
 * strip's pill following ([KitTabPages]). First tab the person's favourites and recent picks, then one per provider.
 * Rows are flat with a hairline between them; the chosen one carries its plate and tick across the whole row, and each
 * row's star adds or removes a favourite. Server default leads every page. Typing in the search field turns the pages
 * into one list of what was found across every provider; clearing it brings the pages back where they were. One
 * provider with no picks shows no tab row. [current] is the chosen model's key, null for Server default. [above]
 * holds the caller's notices: they lead each list and scroll with it (the list itself draws once [options] is not
 * empty), [below] the caller's further choices (thinking, agent) after each list. [morphFrom] is the key of the control the sheet grows out of
 * (`kitMorphSource`), if any.
 */
@Composable
public fun KitModelSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    options: List<KitModelOption>,
    current: String?,
    onChoose: (KitModelOption?) -> Unit,
    favorites: List<KitModelOption> = emptyList(),
    recent: List<KitModelOption> = emptyList(),
    /** Null hides the stars (favourites cannot be saved here). */
    onToggleFavorite: ((KitModelOption) -> Unit)? = null,
    /** Server default is offered once the caller knows what it means for this server. */
    offersServerDefault: Boolean = true,
    /** Resets the search and the page when the sheet now serves another conversation. */
    resetKey: Any? = null,
    /** B-169: one suggestion's key, leading every page under [suggestedLabel]. */
    suggested: String? = null,
    suggestedLabel: String = "",
    /** The sheet's title; null = "Choose a model". */
    title: String? = null,
    above: @Composable ColumnScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
    morphFrom: Any? = null,
) {
    KitSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = title ?: stringResource(Res.string.composerPreviewModelsTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Full,
        morphFrom = morphFrom,
        bodyFills = true,
    ) {
        ModelSheetBody(
            options, current, onChoose, favorites, recent, onToggleFavorite, offersServerDefault, resetKey, suggested,
            suggestedLabel, above, below,
        )
    }
}

@Composable
private fun ColumnScope.ModelSheetBody(
    options: List<KitModelOption>,
    current: String?,
    onChoose: (KitModelOption?) -> Unit,
    favorites: List<KitModelOption>,
    recent: List<KitModelOption>,
    onToggleFavorite: ((KitModelOption) -> Unit)?,
    offersServerDefault: Boolean,
    resetKey: Any?,
    suggested: String?,
    suggestedLabel: String,
    above: @Composable ColumnScope.() -> Unit,
    below: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalKitTokens.current
    val rail = FlutterKitTokens.RAIL.dp
    val gap = Arrangement.spacedBy(tokens.labelGap.dp)
    if (options.isEmpty()) {
        // Nothing to list yet: the caller's notices and further choices, on the rail, as one scrolling column.
        KitStackScope {
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = rail, end = rail, bottom = rail),
                verticalArrangement = gap,
            ) {
                above()
                below()
            }
        }
        return
    }
    val search = rememberTextFieldState()
    var query by remember(resetKey) { mutableStateOf("") }
    // The search field stands directly under the title; the caller's notices lead each list instead (they scroll
    // with it), so the sheet's head is always the same three things: title, search, tabs.
    if (options.size >= KIT_MODEL_SEARCH_FROM)
        KitSearchField(
            label = stringResource(Res.string.chatModelsSearch),
            onSearch = { query = it },
            state = search,
            // P-008: a step (8 dp) between the field and what follows it.
            modifier = Modifier.fillMaxWidth().padding(start = rail, end = rail, bottom = Space.S2.dp).testTag("chat-model-search"),
        )
    val canFavorite = onToggleFavorite != null
    val pages = remember(options, favorites, recent, current, canFavorite) { KitModelPages.pages(options, favorites, recent, current, canFavorite) }
    val twins = remember(options) { KitModelPages.twins(options) }
    val favoriteKeys = remember(favorites) { favorites.mapTo(HashSet()) { it.key } }
    val lead = remember(options, suggested) { options.firstOrNull { it.key == suggested } }
    // The page is kept by its key, so a tab that appears beside it (the first favourite) does not move the person.
    val opening = remember(resetKey) { KitModelPages.opening(pages, current) }
    var pageKey by remember(resetKey) { mutableStateOf<String?>(null) }
    val index = (pages.indexOfFirst { it.key == pageKey }.takeIf { it >= 0 } ?: opening).coerceIn(0, pages.size - 1)
    val tabbed = pages.size >= TabRules.MIN_TABS
    val tabs = if (tabbed) rememberKitTabsState(index, pages.size) { pageKey = pages.getOrNull(it)?.key } else null
    val picksLabel = stringResource(Res.string.chat_model_favorites)
    val labels = pages.map { if (it.providerId == null) picksLabel else kitProviderName(it.providerId, it.providerName) }
    // The rows of the page the sheet opened on rise in once, one after another; a page reached later is simply there.
    val entrance = rememberRowEntrance()
    val entrancePage = remember(resetKey) { pages.getOrNull(index)?.key }
    val list: @Composable (List<ModelItem>, RowEntrance?) -> Unit = { items, rising ->
        ModelList(items, current, favoriteKeys, onChoose, onToggleFavorite, suggestedLabel, rising, above, below)
    }
    // While the results fade out after a clear, they keep showing what was last found, not every model.
    var lastQuery by remember(resetKey) { mutableStateOf("") }
    if (query.isNotBlank()) lastQuery = query
    KitSwap(query.isNotBlank(), Modifier.weight(1f).fillMaxWidth(), KitPace.Standard, Alignment.TopStart) { searching ->
        when {
            searching -> {
                val shown = lastQuery
                val found = remember(options, favorites, recent, current, shown) { KitModelPages.results(options, favorites, recent, current, shown) }
                if (found.isEmpty())
                    Box(Modifier.fillMaxSize().padding(horizontal = rail)) {
                        KitSearchNoMatch(
                            query = shown,
                            onClear = {
                                search.clearText()
                                query = ""
                            },
                        )
                    }
                else {
                    val items = remember(found, twins, current) { modelItems(found.map { ModelItem.Model(it, mixed = true, twin = it.key in twins) }, current) }
                    list(items, null)
                }
            }
            tabs != null ->
                Column(Modifier.fillMaxSize()) {
                    TabStrip(
                        tabs = pages.mapIndexed { i, page -> modelTab(page, labels[i]) },
                        selected = tabs.index.coerceIn(0, pages.size - 1),
                        onSelected = tabs::choose,
                        position = tabs.place,
                        elastic = TabRules.pillStretches(KitMotion.state.level),
                        modifier = Modifier.testTag("chat-model-tabs"),
                        semanticsLabel = null,
                        gutter = rail,
                        maxTabs = Int.MAX_VALUE,
                    )
                    KitTabPages(tabs, Modifier.weight(1f).fillMaxWidth(), pageTitle = { labels.getOrNull(it).orEmpty() }) { i ->
                        val page = pages.getOrNull(i) ?: return@KitTabPages
                        val items = remember(page, offersServerDefault, lead, twins, current) { pageItems(page, offersServerDefault, lead, twins, current) }
                        list(items, entrance.takeIf { page.key == entrancePage })
                    }
                }
            else -> {
                val page = pages.first()
                val items = remember(page, offersServerDefault, lead, twins, current) { pageItems(page, offersServerDefault, lead, twins, current) }
                list(items, entrance)
            }
        }
    }
}

/** A page's tab: the picks under their own word with a star, a provider under its short name with its mark before it. */
@Composable
private fun modelTab(page: KitModelPage, label: String): KitTab {
    // The picks carry the rows' own star, so every tab reads mark, then word.
    val id = page.providerId ?: return remember(label) { KitTab(label, icon = KitIcons.starFilled) }
    return remember(id, label) {
        KitTab(label, mark = { tint -> KitProviderLogo(id, label, markSize = TAB_MARK.dp, tint = tint, slot = TAB_MARK.dp) })
    }
}

/** A provider's mark before its tab's words. */
private const val TAB_MARK: Int = 16

/** One line of a model list. */
private sealed interface ModelItem {
    val key: String

    /** A hairline under this row: between two rows, never beside the chosen row's plate, a heading or the list's end. */
    val rule: Boolean

    data class Default(override val rule: Boolean = false) : ModelItem {
        override val key: String get() = "default"
    }

    data class Heading(val kind: KitModelSectionKind, val provider: String = "") : ModelItem {
        override val key: String get() = "heading:$kind"
        override val rule: Boolean get() = false
    }

    data class Model(val option: KitModelOption, val mixed: Boolean, val twin: Boolean, override val rule: Boolean = false, val suggested: Boolean = false) : ModelItem {
        override val key: String get() = if (suggested) "suggested:${option.key}" else option.key
    }

    data object NoPicks : ModelItem {
        override val key: String get() = "no-picks"
        override val rule: Boolean get() = false
    }
}

/** Sets each row's hairline: under a row that is followed by another row, unless either of the two is the chosen one. */
private fun modelItems(items: List<ModelItem>, current: String?): List<ModelItem> {
    fun chosen(item: ModelItem): Boolean =
        when (item) {
            is ModelItem.Default -> current == null
            is ModelItem.Model -> item.option.key == current
            else -> false
        }
    return items.mapIndexed { i, item ->
        val next = items.getOrNull(i + 1)
        val rule = (next is ModelItem.Model || next is ModelItem.Default) && !chosen(item) && !chosen(next)
        when (item) {
            is ModelItem.Default -> item.copy(rule = rule)
            is ModelItem.Model -> item.copy(rule = rule)
            else -> item
        }
    }
}

/** A page as lines: Server default, the suggestion, then its sections. The picks' first section needs no heading (its tab says it). */
private fun pageItems(page: KitModelPage, offersDefault: Boolean, lead: KitModelOption?, twins: Set<String>, current: String?): List<ModelItem> {
    val out = ArrayList<ModelItem>()
    if (offersDefault) out += ModelItem.Default()
    if (lead != null) {
        out += ModelItem.Heading(KitModelSectionKind.Suggested)
        out += ModelItem.Model(lead, mixed = true, twin = lead.key in twins, suggested = true)
    }
    val picks = page.providerId == null
    page.sections.forEachIndexed { i, section ->
        val rows = section.rows.filter { it.key != lead?.key }
        if (rows.isEmpty()) return@forEachIndexed
        if (lead != null || (picks && (i > 0 || section.kind == KitModelSectionKind.Recent))) out += ModelItem.Heading(section.kind, page.providerName)
        rows.forEach { out += ModelItem.Model(it, mixed = picks, twin = it.key in twins) }
    }
    if (picks && page.sections.isEmpty()) out += ModelItem.NoPicks
    return modelItems(out, current)
}

@Composable
private fun ModelList(
    items: List<ModelItem>,
    current: String?,
    favoriteKeys: Set<String>,
    onChoose: (KitModelOption?) -> Unit,
    onToggleFavorite: ((KitModelOption) -> Unit)?,
    suggestedLabel: String,
    entrance: RowEntrance?,
    above: @Composable ColumnScope.() -> Unit,
    below: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalKitTokens.current
    val rail = FlutterKitTokens.RAIL.dp
    val density = LocalDensity.current.density
    val hairline = LocalKitColors.current.hairline
    val list = rememberLazyListState()
    // Rows that have scrolled up melt away under the tabs instead of being cut by them. The fade (and its layer) is
    // there only while the list is away from its top, so a page being dragged sideways at rest pays nothing for it.
    val noticesHeight = remember { IntArray(1) }
    val scrolled by remember(list) { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    val topFade =
        if (!scrolled) Modifier
        else
            Modifier.kitScrollEdges(
                clearAbove = { 0f },
                topReach = LIST_TOP_FADE.dp,
                fadeFromBottom = null,
                bottomReach = 0.dp,
                // It eases in over the first few dp of scroll. The notices' slot is the first item and is usually
                // empty: the list is then already "on its second item" at its very top.
                strength = {
                    val first = list.firstVisibleItemIndex
                    if (first == 0 || (first == 1 && noticesHeight[0] == 0)) list.firstVisibleItemScrollOffset / (LIST_TOP_FADE * density) else 1f
                },
            )
    LazyColumn(
        Modifier.fillMaxSize().then(topFade).testTag("chat-model-list"),
        state = list,
        contentPadding = PaddingValues(start = rail, end = rail, bottom = rail),
    ) {
        item(key = "above", contentType = "above") {
            // The caller's notices, with a step under them only when there are any.
            KitStackScope {
                Column(Modifier.fillMaxWidth().onSizeChanged { noticesHeight[0] = it.height }.stepUnder(Space.S2.dp), verticalArrangement = Arrangement.spacedBy(tokens.labelGap.dp)) { above() }
            }
        }
        items(count = items.size, key = { items[it].key }, contentType = { items[it]::class }) { i ->
            val item = items[i]
            val row = entrance.rise(i).rowRule(item.rule, hairline)
            when (item) {
                is ModelItem.Default ->
                    KitChoiceRow(
                        title = stringResource(Res.string.composerPreviewModelDefault),
                        supporting = stringResource(Res.string.composerPreviewModelDefaultHint),
                        selected = current == null,
                        mark = KitChoiceMark.Tick,
                        onClick = { onChoose(null) },
                        modifier = row.testTag("chat-model-default"),
                    )
                is ModelItem.Heading ->
                    KitSectionLabel(
                        when (item.kind) {
                            KitModelSectionKind.Suggested -> suggestedLabel
                            KitModelSectionKind.Favorites -> stringResource(Res.string.chat_model_favorites)
                            KitModelSectionKind.Recent -> stringResource(Res.string.chatModelsRecent)
                            KitModelSectionKind.Provider -> item.provider
                        },
                        modifier = row,
                        gapBefore = if (i == 0) Space.S2.dp else tokens.sectionGap.dp,
                    )
                is ModelItem.Model -> {
                    val option = item.option
                    val provider = if (item.mixed) kitProviderName(option.providerId, option.providerName) else null
                    val favorite = option.key in favoriteKeys
                    KitChoiceRow(
                        title = option.label,
                        selected = option.key == current,
                        mark = KitChoiceMark.Tick,
                        enabled = KitEnabled.of(option.disabledReason == null, option.disabledReason),
                        supporting = KitModelPages.secondLine(option, provider, item.twin),
                        // The star stays quieter than the tick; its words are what TalkBack says.
                        trailing = if (onToggleFavorite != null) ({ FavoriteStar(favorite) { onToggleFavorite(option) } }) else null,
                        onClick = { onChoose(option) },
                        modifier = row.testTag("chat-model-choice"),
                    )
                }
                ModelItem.NoPicks ->
                    KitText(
                        stringResource(Res.string.chat_model_favorites_empty),
                        row.padding(vertical = Space.S4.dp),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                    )
            }
        }
        item(key = "below", contentType = "below") {
            KitStackScope { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tokens.labelGap.dp)) { below() } }
        }
    }
}

/** Adds [step] under this box when it has any height: an empty slot takes no room at all. */
private fun Modifier.stepUnder(step: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val more = if (placeable.height > 0) step.roundToPx() else 0
        layout(placeable.width, placeable.height + more) { placeable.place(0, 0) }
    }

/** How far down from the tabs a scrolled list fades in, in dp. */
private const val LIST_TOP_FADE: Int = Space.S4

/** One physical pixel under a row, rail to rail. */
private fun Modifier.rowRule(show: Boolean, color: Color): Modifier =
    if (!show) this
    else drawBehind { drawLine(color, Offset(0f, size.height - .5f), Offset(size.width, size.height - .5f), 1f) }

/**
 * A model's star. Tapping it adds or removes the favourite; when the answer comes back the star springs out from a
 * smaller size (the release spring, the one that may overshoot), so the change is felt where the finger was. Off
 * changes the glyph only.
 *
 * Motion: scale on the release spring (class Settle); Off = instant
 */
@Composable
private fun FavoriteStar(favorite: Boolean, onToggle: () -> Unit) {
    val motion = KitMotion.state
    val specs = KitMotion.specs
    val pop = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(favorite) }
    LaunchedEffect(favorite) {
        if (favorite == seen) return@LaunchedEffect
        seen = favorite
        if (!motion.reduced && !motion.stillFrame) {
            pop.snapTo(if (favorite) STAR_POP_FROM else STAR_DROP_FROM)
            pop.animateTo(1f, specs.release())
        }
    }
    Box(
        Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
    ) {
        KitIconButton(
            icon = if (favorite) KitIcons.starFilled else KitIcons.star,
            label = stringResource(if (favorite) Res.string.chat_model_favorite_remove else Res.string.chat_model_favorite_add),
            onClick = onToggle,
            tone = TextTone.Secondary,
            haptic = if (favorite) KitHaptic.ToggleOff else KitHaptic.ToggleOn,
        )
    }
}

private const val STAR_POP_FROM: Float = 0.6f
private const val STAR_DROP_FROM: Float = 0.85f

/**
 * The first rows of a list rising into place one after another, once (the kit's stagger: [STAGGER_TRAVEL_DP] up and
 * fading in, [STAGGER_MS] apart, each on the spatial spring, as `KitStaggerRow`'s faces arrive). Paint only: a row's
 * place and size are final from the first frame. Rows composed after the entrance is over are simply there.
 *
 * Motion: rise and fade at Full; Calm = the staggered fade alone; Off and still frames = the rows where they are (class Travel)
 */
private class RowEntrance(val rises: Boolean, val timeScale: Float) {
    var active: Boolean by mutableStateOf(true)
}

@Composable
private fun rememberRowEntrance(): RowEntrance? {
    val motion = KitMotion.state
    val entrance = remember(motion.travels) { RowEntrance(motion.travels, motion.debugTimeScale) }
    LaunchedEffect(entrance) {
        delay(((ROW_RISE_ROWS * STAGGER_MS + ROW_RISE_SETTLE_MS) * entrance.timeScale).toLong())
        entrance.active = false
    }
    return entrance.takeIf { !motion.reduced && !motion.stillFrame }
}

/** The modifier of the row that is [order]th from the top; nothing once the entrance is over or past its last row. */
@Composable
private fun RowEntrance?.rise(order: Int): Modifier {
    if (this == null || !active || order >= ROW_RISE_ROWS) return Modifier
    val specs = KitMotion.specs
    val shown = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        delay((order * STAGGER_MS * timeScale).toLong())
        shown.animateTo(1f, specs.spatial())
    }
    val rises = rises
    return Modifier.graphicsLayer {
        val v = shown.value
        alpha = v.coerceIn(0f, 1f)
        if (rises) translationY = STAGGER_TRAVEL_DP.dp.toPx() * (1f - v)
        // Each row's ink is faded where it is drawn: a buffer of the row's own box would cut its plate and its star.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** How many rows from the top take part, and how long the last one is given to settle. */
private const val ROW_RISE_ROWS: Int = 12
private const val ROW_RISE_SETTLE_MS: Int = 600

/** A provider as people read it: regional routes stay explicit while their wire ids differ. */
@Composable
public fun kitProviderName(id: String, fallback: String): String =
    when (id.lowercase()) {
        "zai" -> stringResource(Res.string.stateProviderZaiGlobal)
        "zhipuai" -> stringResource(Res.string.stateProviderZaiChina)
        "zai-coding-plan" -> stringResource(Res.string.stateProviderZaiPlanGlobal)
        "zhipuai-coding-plan" -> stringResource(Res.string.stateProviderZaiPlanChina)
        else -> fallback.trim().ifEmpty { id }
    }

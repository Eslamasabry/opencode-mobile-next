package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatModelsRecent
import io.github.eslamasabry.oc.ui.words.res.chatModelsSearch
import io.github.eslamasabry.oc.ui.words.res.chat_model_favorite_add
import io.github.eslamasabry.oc.ui.words.res.chat_model_favorite_remove
import io.github.eslamasabry.oc.ui.words.res.chat_model_favorites
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelDefault
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelDefaultHint
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsTitle
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiChina
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiGlobal
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiPlanChina
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiPlanGlobal
import org.jetbrains.compose.resources.stringResource

/** One model a person can pick; [supporting] is a second line that tells equal names apart (a host app). */
@Immutable
public data class KitModelOption(
    val providerId: String,
    val modelId: String,
    val label: String,
    val providerName: String = "",
    val supporting: String? = null,
) {
    val key: String
        get() = "$providerId/$modelId"
}

public enum class KitModelSectionKind {
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
 * "Choose a model" (N-51): the one model sheet for a conversation and for a new one. Close at the end of its header,
 * Server default first with what it means, then [KitModelOrder]'s groups, each row's star adding or removing a
 * favourite, the chosen model's band and tick. [current] is the chosen model's key, null for Server default.
 * [above] holds the caller's notices before the list (the list itself draws once [options] is not empty), [below]
 * the caller's further choices (thinking, agent).
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
    /** Resets the search when the sheet now serves another conversation. */
    resetKey: Any? = null,
    above: @Composable ColumnScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    KitSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.composerPreviewModelsTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Full,
    ) {
        above()
        if (options.isNotEmpty()) {
            val search = rememberTextFieldState()
            var query by remember(resetKey) { mutableStateOf("") }
            if (options.size >= KIT_MODEL_SEARCH_FROM)
                KitSearchField(
                    label = stringResource(Res.string.chatModelsSearch),
                    onSearch = { query = it },
                    state = search,
                    modifier = Modifier.fillMaxWidth().testTag("chat-model-search"),
                )
            val sections =
                remember(options, favorites, recent, current, query) {
                    KitModelOrder.sections(options, favorites, recent, current, query)
                }
            if (offersServerDefault && query.isBlank())
                KitRowGroup(Modifier.testTag("chat-model-default")) {
                    KitChoiceRow(
                        title = stringResource(Res.string.composerPreviewModelDefault),
                        supporting = stringResource(Res.string.composerPreviewModelDefaultHint),
                        selected = current == null,
                        mark = KitChoiceMark.Tick,
                        onClick = { onChoose(null) },
                    )
                }
            if (sections.isEmpty() && query.isNotBlank())
                KitSearchNoMatch(
                    query = query,
                    onClear = {
                        search.clearText()
                        query = ""
                    },
                )
            val favoriteKeys = remember(favorites) { favorites.mapTo(HashSet()) { it.key } }
            sections.forEach { section ->
                KitRowGroup(
                    label =
                        when (section.kind) {
                            KitModelSectionKind.Favorites -> stringResource(Res.string.chat_model_favorites)
                            KitModelSectionKind.Recent -> stringResource(Res.string.chatModelsRecent)
                            KitModelSectionKind.Provider ->
                                kitProviderName(section.rows.first().providerId, section.providerName.orEmpty())
                        },
                    section = true,
                ) {
                    // The provider's mark and name only where providers mix (Favorites, Recent); under a provider's
                    // own heading they would repeat down the whole list.
                    val mixed = section.kind != KitModelSectionKind.Provider
                    section.rows.forEach { option ->
                        val favorite = option.key in favoriteKeys
                        KitChoiceRow(
                            title = option.label,
                            leading = if (mixed) ({ KitProviderLogo(option.providerId, option.providerName) }) else null,
                            selected = option.key == current,
                            mark = KitChoiceMark.Tick,
                            supporting =
                                option.supporting
                                    ?: if (mixed) kitProviderName(option.providerId, option.providerName) else null,
                            // The star stays quieter than the tick; its words are what TalkBack says.
                            trailing =
                                if (onToggleFavorite != null) ({
                                        KitIconButton(
                                            icon = if (favorite) KitIcons.starFilled else KitIcons.star,
                                            label =
                                                stringResource(
                                                    if (favorite) Res.string.chat_model_favorite_remove
                                                    else Res.string.chat_model_favorite_add
                                                ),
                                            onClick = { onToggleFavorite(option) },
                                            tone = TextTone.Secondary,
                                        )
                                    })
                                else null,
                            onClick = { onChoose(option) },
                            modifier = Modifier.testTag("chat-model-choice"),
                        )
                    }
                }
            }
        }
        below()
    }
}

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

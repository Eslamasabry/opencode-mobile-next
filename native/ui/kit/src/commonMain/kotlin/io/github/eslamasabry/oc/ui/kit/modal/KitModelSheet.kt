package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsClose
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsTitle
import org.jetbrains.compose.resources.stringResource

/** One model in [KitModelSheet]. [detail] is the line under it inside its provider's group (a host app, say). */
public data class KitModelChoice(
    val providerId: String,
    val modelId: String,
    val label: String,
    val providerName: String,
    val current: Boolean = false,
    val favorite: Boolean = false,
    val detail: String? = null,
)

public enum class KitModelGroupKind {
    Favorites,
    Recent,
    Provider,
}

/** A heading's models, in the order shown. [name] is the provider's name for [KitModelGroupKind.Provider]. */
public data class KitModelGroup(
    val kind: KitModelGroupKind,
    val choices: List<KitModelChoice>,
    val name: String = "",
)

/**
 * N-51: the one "Choose a model" sheet, the same in a conversation and on New conversation: a close
 * mark, search, Server default with what it means, then Favorites, Recent and each provider's
 * models (the current one first, ticked), each with its star.
 *
 * [groups] answers the list for what the search holds. [onChoose] gets null for Server default.
 * [listShown] false leaves only [above] (loading, failure, empty). [below] follows the list (thinking
 * level, agent).
 */
@Composable
public fun KitModelSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    groups: @Composable (query: String) -> List<KitModelGroup>,
    usesDefault: Boolean,
    onChoose: (KitModelChoice?) -> Unit,
    onFavorite: ((KitModelChoice) -> Unit)? = null,
    offersDefault: Boolean = true,
    listShown: Boolean = true,
    resetKey: Any? = null,
    above: @Composable ColumnScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    KitSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.composerPreviewModelsTitle),
        closeLabel = stringResource(Res.string.composerPreviewModelsClose),
    ) {
        above()
        if (listShown) {
            // A new target starts with an empty search.
            key(resetKey) { KitModelList(groups, usesDefault, onChoose, onFavorite, offersDefault) }
            below()
        }
    }
}

@Composable
private fun KitModelList(
    groups: @Composable (query: String) -> List<KitModelGroup>,
    usesDefault: Boolean,
    onChoose: (KitModelChoice?) -> Unit,
    onFavorite: ((KitModelChoice) -> Unit)?,
    offersDefault: Boolean,
) {
    val search = rememberTextFieldState()
    var query by remember { mutableStateOf("") }
    KitSearchField(
        label = stringResource(Res.string.chatModelsSearch),
        onSearch = { query = it },
        state = search,
        modifier = Modifier.fillMaxWidth().testTag("chat-model-search"),
    )
    val shown = groups(query).filter { it.choices.isNotEmpty() }
    // Server default is a choice like any model: a row with what it means and the same tick when chosen.
    if (offersDefault && query.isBlank()) {
        KitRowGroup(Modifier.testTag("chat-model-default")) {
            KitChoiceRow(
                title = stringResource(Res.string.composerPreviewModelDefault),
                supporting = stringResource(Res.string.composerPreviewModelDefaultHint),
                selected = usesDefault,
                mark = KitChoiceMark.Tick,
                onClick = { onChoose(null) },
            )
        }
    }
    if (query.isNotBlank() && shown.isEmpty())
        KitSearchNoMatch(
            query = query,
            onClear = {
                search.clearText()
                query = ""
            },
        )
    shown.forEach { group -> KitModelGroupRows(group, onChoose, onFavorite) }
}

@Composable
private fun KitModelGroupRows(
    group: KitModelGroup,
    onChoose: (KitModelChoice?) -> Unit,
    onFavorite: ((KitModelChoice) -> Unit)?,
) {
    KitRowGroup(
        label =
            when (group.kind) {
                KitModelGroupKind.Favorites -> stringResource(Res.string.chat_model_favorites)
                KitModelGroupKind.Recent -> stringResource(Res.string.chatModelsRecent)
                KitModelGroupKind.Provider -> group.name
            },
        section = true,
    ) {
        // The provider's mark and name only where providers mix (Favorites, Recent); under a provider's own heading
        // they would repeat down the whole list.
        val mixed = group.kind != KitModelGroupKind.Provider
        group.choices.forEach { choice ->
            KitChoiceRow(
                title = choice.label,
                leading =
                    if (mixed) ({ KitProviderLogo(choice.providerId, choice.providerName) })
                    else null,
                selected = choice.current,
                mark = KitChoiceMark.Tick,
                supporting = if (mixed) choice.providerName else choice.detail,
                // The star stays quieter than the tick; its words are what TalkBack says.
                trailing =
                    if (onFavorite != null) ({
                        KitIconButton(
                            icon = if (choice.favorite) KitIcons.starFilled else KitIcons.star,
                            label =
                                stringResource(
                                    if (choice.favorite) Res.string.chat_model_favorite_remove
                                    else Res.string.chat_model_favorite_add
                                ),
                            onClick = { onFavorite(choice) },
                            tone = TextTone.Secondary,
                        )
                    })
                    else null,
                onClick = { onChoose(choice) },
                modifier = Modifier.testTag("chat-model-choice"),
            )
        }
    }
}

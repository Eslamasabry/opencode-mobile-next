package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.eslamasabry.oc.feature.chat.composer.presentation.PhraseSuggestionsUi
import io.github.eslamasabry.oc.feature.chat.composer.presentation.PhraseSuggestionsViewModel
import io.github.eslamasabry.oc.ui.kit.chip.KitPhraseChips
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.phraseChipsRowLabel
import io.github.eslamasabry.oc.ui.words.res.phraseChipsHide
import io.github.eslamasabry.oc.ui.words.res.phraseChipsRetryStarter
import io.github.eslamasabry.oc.ui.words.res.returnBriefContinue
import io.github.eslamasabry.oc.ui.words.res.phraseSuggestionsInsert
import io.github.eslamasabry.oc.ui.words.res.phraseSuggestionsOptions
import io.github.eslamasabry.oc.ui.words.res.phraseSuggestionsChangeFailed
import org.jetbrains.compose.resources.stringResource

@Composable
public fun PhraseSuggestionsRow(state: PhraseSuggestionsUi, actions: PhraseSuggestionsViewModel, onInsert: (String) -> Unit) {
    val continueWord = stringResource(Res.string.returnBriefContinue)
    val retryWord = stringResource(Res.string.phraseChipsRetryStarter)
    LaunchedEffect(state.moment, state.continueShown, state.revision, continueWord, retryWord) { actions.starters(continueWord, retryWord) }
    if (!state.enabled || state.errorShown) return
    if (state.failed) KitErrorNotice(stringResource(Res.string.phraseSuggestionsChangeFailed))
    val inserts = state.phrases.associate { it.text to stringResource(Res.string.phraseSuggestionsInsert, it.text) }
    val options = state.phrases.associate { it.text to stringResource(Res.string.phraseSuggestionsOptions, it.text) }
    KitPhraseChips(state.phrases.map { it.text }, stringResource(Res.string.phraseChipsRowLabel),
        insertLabel = { inserts.getValue(it) }, menuLabel = { options.getValue(it) },
        hideLabel = stringResource(Res.string.phraseChipsHide), onChoose = onInsert, onHide = actions::hide)
}

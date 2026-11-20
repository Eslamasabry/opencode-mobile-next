package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasText

/**
 * The Language list's row for [name]. A row says the language in its own name and, under it, the name in the app's
 * language, so the System default row also reads "English" when the phone is English: a bare text search finds two
 * nodes. The language's own row is the one that is not the System row.
 */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteractionsProvider.languageRow(name: String): SemanticsNodeInteraction =
    onNode(hasText(name).and(hasText("Use system language").not()))

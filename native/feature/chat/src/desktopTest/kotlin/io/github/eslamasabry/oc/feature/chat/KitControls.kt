package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag

/**
 * The press target of a kit control tagged [tag]. Kit buttons (KitIconButton, KitComposerSend) put
 * the caller's modifier, and so its test tag, on an outer box around the press target; the enabled
 * state lives on the press target itself.
 */
internal fun SemanticsNodeInteractionsProvider.onControl(tag: String): SemanticsNodeInteraction =
    onNode(hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

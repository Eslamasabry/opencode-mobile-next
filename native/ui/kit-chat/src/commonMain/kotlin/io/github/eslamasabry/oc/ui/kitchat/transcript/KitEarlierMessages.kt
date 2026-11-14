package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth

/**
 * "Show earlier messages": the first row of a transcript whose older history is not loaded yet. Audit 1 (2026-11-14):
 * it was a pill floating under the bar, its own level, that cut the reading area at its bottom, changed its count while
 * scrolling and was cut by the bar's menu. Now it is a row of the transcript: it scrolls with the messages, nothing
 * floats over the words, and the reader's place stays where it is when the earlier messages arrive above it (a newest
 * first list keeps its bottom row in place). A quiet text action as wide as the conversation's column at the gutter,
 * its target 48 dp tall; [loading] shows the working mark while the read is in flight (a second tap does nothing).
 */
@Composable
public fun KitEarlierMessages(label: String, onClick: () -> Unit, loading: Boolean, modifier: Modifier = Modifier) {
    KitButton(
        label = label,
        onClick = onClick,
        modifier =
            modifier.kitMaxWidth(CapRole.Conversation)
                .fillMaxWidth()
                .padding(horizontal = Space.S4.dp)
                .testTag(EARLIER_MESSAGES_TAG),
        role = KitButtonRole.Tertiary,
        working = loading,
        maxLines = 1,
    )
}

/** The test tag of [KitEarlierMessages]. */
public const val EARLIER_MESSAGES_TAG: String = "transcript-earlier-messages"

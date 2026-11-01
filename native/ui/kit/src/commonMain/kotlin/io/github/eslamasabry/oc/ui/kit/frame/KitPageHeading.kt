package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * A page's own large title and one quiet line under it (Dart `KitPageHeading`): the body of a shell tab names itself
 * here, since the shell's bar has no title. Reading text: it follows the person's size.
 *
 * States: default, with line
 * Text: reading
 * Cap: Reading
 * Motion: none
 * Hidden: none
 * Replaces: KitPageHeading (lib/ui/kit/kit_page_heading.dart)
 */
@Composable
public fun KitPageHeading(title: String, modifier: Modifier = Modifier, line: String? = null) {
    val tokens = LocalKitTokens.current
    Column(
        modifier
            .kitMaxWidth(CapRole.Reading, io.github.eslamasabry.oc.ui.design.layout.CapAlign.Start)
            .fillMaxWidth()
            .padding(start = tokens.gutter.dp, end = tokens.gutter.dp, top = Space.S2.dp, bottom = Space.S3.dp)
            .testTag("kit-page-heading"),
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        KitText(title, Modifier.semantics { heading() }, role = TypeRole.LargeTitle)
        line?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
    }
}

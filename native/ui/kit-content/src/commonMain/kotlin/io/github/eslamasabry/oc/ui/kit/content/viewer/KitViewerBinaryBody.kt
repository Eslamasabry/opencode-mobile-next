package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitViewerCantShow
import io.github.eslamasabry.oc.ui.words.res.kitViewerCantShowBody
import io.github.eslamasabry.oc.ui.words.res.kitViewerUnknownSize
import io.github.eslamasabry.oc.ui.words.res.kitViewerUnknownType
import org.jetbrains.compose.resources.stringResource

/**
 * The viewer's Binary body (plan 10 §11.3): "Can't show this file", then the type and size in words
 * ("application/zip · 2.4 MB"), and the caller's [action] stays (Save, Share on Wi-Fi). Never red; never a hex dump.
 *
 * States: one (unknown type and size say so)
 * Text: frame (title), reading (the type · size line)
 * Cap: Fill, start-aligned on the gutter
 * Motion: none
 * Hidden: none
 * Replaces: KitViewer binary body (lib/ui/kit/kit_viewer_build.dart)
 */
@Composable
public fun KitViewerBinaryBody(
    mimeType: String?,
    sizeBytes: Long?,
    modifier: Modifier = Modifier,
    action: KitViewerAction? = null,
) {
    val gutter = LocalKitTokens.current.gutter.dp
    val type = mimeType?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.kitViewerUnknownType)
    val size = ViewerText.sizeInWords(sizeBytes) ?: stringResource(Res.string.kitViewerUnknownSize)
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(gutter),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            KitText(stringResource(Res.string.kitViewerCantShow), role = TypeRole.RowTitle, kind = KitTextKind.Frame)
            KitText(
                stringResource(Res.string.kitViewerCantShowBody, isolateLtr(type), size),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
        if (action != null) KitButton(action.label, action.onClick, Modifier.padding(top = Space.S2.dp).tagged(action.tag))
    }
}

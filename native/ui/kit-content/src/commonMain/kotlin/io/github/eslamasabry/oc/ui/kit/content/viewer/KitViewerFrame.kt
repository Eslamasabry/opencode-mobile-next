package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.content.foundation.ContentTextAction
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTopBarClose
import org.jetbrains.compose.resources.stringResource

/** A labelled action the host puts in the viewer's bar. [tag] only where a test needs a handle. */
@Immutable
public data class KitViewerAction(val label: String, val onClick: () -> Unit, val tag: String? = null)

/** Test tags of the frame. */
public object KitViewerTags {
    public const val NAME: String = "kit-viewer-name"
    public const val FOLDER: String = "kit-viewer-folder"
    public const val CLOSE: String = "kit-viewer-close"
    public const val BODY: String = "kit-viewer-body"
}

/** The viewer's widest frame (plan 06 viewer cap; prose inside keeps its own 720 dp rails). */
private const val VIEWER_MAX_WIDTH_DP = 960

/**
 * The file viewer's frame (plan 10 §11.3, MEDIA-VOICE-37): the same for every body. The name in file-name style, the
 * muted parent folder under it ([ViewerPaths.folderOf], B-NAV-028), Close at the end of the header, the body, and at the
 * bottom where the thumb is, one labelled [primary] and an optional [secondary]. The host places the frame as a page,
 * a sheet or a pane by window size; the frame does not decide.
 *
 * Paths and names are isolated left to right inside an Arabic interface (a path never reorders).
 *
 * States: content (the body decides loading, error, empty and its own states)
 * Text: frame (name, folder, buttons); the body is reading text
 * Cap: Viewer (960 dp)
 * Motion: none of its own
 * Hidden: none
 * Replaces: KitViewer frame (lib/ui/kit/kit_viewer.dart)
 */
@Composable
public fun KitViewerFrame(
    name: String,
    modifier: Modifier = Modifier,
    path: String? = null,
    primary: KitViewerAction? = null,
    secondary: KitViewerAction? = null,
    onClose: (() -> Unit)? = null,
    body: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val folder = path?.let { ViewerPaths.folderOf(it, name) }
    Column(
        modifier.widthIn(max = VIEWER_MAX_WIDTH_DP.dp).background(colors.ground),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = tokens.gutter.dp, end = Space.S2.dp, top = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                KitText(
                    isolateLtr(name),
                    Modifier.testTag(KitViewerTags.NAME).semantics { heading() },
                    role = TypeRole.RowTitle,
                    kind = KitTextKind.Frame,
                    maxLines = 2,
                )
                if (folder != null) {
                    KitText(
                        isolateLtr(folder),
                        Modifier.testTag(KitViewerTags.FOLDER),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                        kind = KitTextKind.Frame,
                        maxLines = 2,
                    )
                }
            }
            if (onClose != null) {
                ContentTextAction(stringResource(Res.string.kitTopBarClose), onClose, Modifier.testTag(KitViewerTags.CLOSE))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).testTag(KitViewerTags.BODY), content = body)
        if (primary != null || secondary != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (primary != null) {
                    KitButton(primary.label, primary.onClick, Modifier.weight(1f).tagged(primary.tag))
                }
                if (secondary != null) {
                    ContentTextAction(secondary.label, secondary.onClick, Modifier.tagged(secondary.tag))
                }
            }
        }
    }
}

internal fun Modifier.tagged(tag: String?): Modifier = if (tag == null) this else testTag(tag)

/** U+2066 LEFT-TO-RIGHT ISOLATE … U+2069 POP DIRECTIONAL ISOLATE: a path keeps its order in a right-to-left line. */
internal fun isolateLtr(text: String): String = "⁦$text⁩"

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** The project and branch each keep their own visible 48 dp action under the title. */
@Composable
public fun KitTopBarScope(
    project: String,
    server: String?,
    projectLabel: String,
    onProject: (() -> Unit)?,
    branch: String? = null,
    onBranch: (() -> Unit)? = null,
) {
    // "project · branch" read as one line (Flutter's chat header); each word keeps its own target.
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f, fill = false)) {
            val style = LocalKitTypography.current.style(TypeRole.Secondary)
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val whole = if (server.isNullOrEmpty()) project else "$project · $server"
            val needed = with(density) { measurer.measure(whole, style).size.width.toDp() }
            val words = if (needed <= maxWidth) whole else project
            ScopeWord(words, projectLabel, onProject)
        }
        if (branch != null) {
            KitText("·", role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
            ScopeWord(branch, branch, onBranch)
        }
    }
}

@Composable
private fun ScopeWord(words: String, label: String, action: (() -> Unit)?) {
    val body: @Composable (Modifier) -> Unit = { place ->
        KitText(
            words,
            place.padding(horizontal = Space.S1.dp).semantics { contentDescription = label },
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
            kind = KitTextKind.Frame,
            maxLines = 1,
        )
    }
    // A short word ("Git") sits at the start of its 48 dp target, not centred in it: centred, the dot before it hung
    // off the project and a gap opened before the word (owner 2026-11-09, "new · This phone ·   Git").
    if (action == null) body(Modifier)
    else
        KitTappable(onClick = action, onClickLabel = label, shape = KitShapeId.Button) {
            body(Modifier.align(androidx.compose.ui.Alignment.CenterStart))
        }
}

/** Keeps the distinguishing port visible before truncating a long host name. */
@Composable
public fun KitServerName(name: String, modifier: Modifier = Modifier) {
    val match = Regex(":\\d+(?:\\s.*)?$").find(name)
    if (match == null || match.range.first == 0) {
        KitText(name, modifier, role = TypeRole.RowTitle, maxLines = 1)
        return
    }
    val tail = match.value
    val style = LocalKitTypography.current.style(TypeRole.RowTitle)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val measured = measurer.measure(tail, style)
    val needed = with(density) { (measured.size.width + 2 * measured.size.height).toDp() }
    BoxWithConstraints(modifier) {
        if (maxWidth < needed) KitText(name, role = TypeRole.RowTitle, maxLines = 1)
        else
            Row(Modifier.fillMaxWidth()) {
                KitText(
                    name.take(match.range.first),
                    Modifier.weight(1f),
                    role = TypeRole.RowTitle,
                    maxLines = 1,
                )
                KitText(tail, role = TypeRole.RowTitle, maxLines = 1)
            }
    }
}

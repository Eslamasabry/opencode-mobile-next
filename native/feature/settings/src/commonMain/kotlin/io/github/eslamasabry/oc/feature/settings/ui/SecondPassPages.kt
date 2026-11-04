package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.*
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ColumnScope.PhraseChipsBody(on: Boolean, actions: SettingsActions) {
    KitRowGroup {
        KitSwitchRow(
            title = stringResource(Res.string.nativePhraseChipsTitle),
            supporting = stringResource(Res.string.nativePhraseChipsDescription),
            checked = on,
            onCheckedChange = actions::onPhraseChips,
        )
    }
}

/** Omit the maintainer introduction and regeneration instructions exactly as Flutter does. */
public fun readerNotices(markdown: String): String {
    val lines = markdown.lines()
    val start = lines.indexOfFirst { it.startsWith("## ") }
    if (start < 0) return markdown
    val end =
        (start until lines.size).firstOrNull { lines[it].startsWith("## Regenerating") }
            ?: lines.size
    return lines.subList(start, end).joinToString("\n").trim()
}

@Composable
internal fun ColumnScope.NoticesBody() {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        text = readerNotices(Res.readBytes("files/THIRD_PARTY_NOTICES.md").decodeToString())
    }
    val content = text
    if (content == null)
        KitStateView(KitStateKind.Loading, stringResource(Res.string.aboutAllLicences))
    else KitText(content, kind = KitTextKind.Reading)
}

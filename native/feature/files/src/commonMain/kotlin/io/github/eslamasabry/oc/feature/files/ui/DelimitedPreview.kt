package io.github.eslamasabry.oc.feature.files.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.files.DelimitedFailure
import io.github.eslamasabry.oc.engine.files.FileBody
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitDelimitedPreview
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerTextBody
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.fileSource
import io.github.eslamasabry.oc.ui.words.res.fileTable
import io.github.eslamasabry.oc.ui.words.res.fileTableFieldTooLong
import io.github.eslamasabry.oc.ui.words.res.fileTableMalformed
import io.github.eslamasabry.oc.ui.words.res.fileTableTooLarge
import io.github.eslamasabry.oc.ui.words.res.fileTableTooWide
import io.github.eslamasabry.oc.ui.words.res.kitViewerCopyContents
import io.github.eslamasabry.oc.ui.words.res.kitViewerPartial
import org.jetbrains.compose.resources.stringResource

/**
 * A CSV/TSV projection never interprets formulas or markup, and Source/Copy preserve its original
 * text.
 */
@Composable
internal fun DelimitedPreview(body: FileBody.Delimited) {
    var source by remember(body) { mutableStateOf(body.table.failure != null) }
    var copy by remember(body) { mutableStateOf(false) }
    KitClipboardEffect(if (copy) 1L else null, if (copy) body.source else null) { copy = false }
    Column(Modifier.fillMaxSize()) {
        val tableLabel = stringResource(Res.string.fileTable)
        val sourceLabel = stringResource(Res.string.fileSource)
        if (body.table.failure == null) {
            KitSegmented(
                segments = listOf(KitSegment(false, tableLabel), KitSegment(true, sourceLabel)),
                selected = source,
                semanticsLabel = tableLabel,
                modifier = Modifier.padding(horizontal = Space.S4.dp),
                onChange = { source = it },
            )
        }
        body.table.failure?.let { reason ->
            KitNotice(
                stringResource(
                    when (reason) {
                        DelimitedFailure.Malformed -> Res.string.fileTableMalformed
                        DelimitedFailure.TooLarge -> Res.string.fileTableTooLarge
                        DelimitedFailure.TooWide -> Res.string.fileTableTooWide
                        DelimitedFailure.FieldTooLong -> Res.string.fileTableFieldTooLong
                    }
                ),
                Modifier.padding(horizontal = Space.S4.dp),
            )
        }
        if (body.table.hasMore && !source)
            KitNotice(
                stringResource(Res.string.kitViewerPartial),
                Modifier.padding(horizontal = Space.S4.dp),
            )
        KitButton(
            stringResource(Res.string.kitViewerCopyContents),
            { copy = true },
            Modifier.padding(horizontal = Space.S4.dp),
            role = KitButtonRole.Tertiary,
        )
        Box(Modifier.weight(1f)) {
            if (source || body.table.rows.isEmpty()) KitViewerTextBody(body.source)
            else
                KitDelimitedPreview(
                    headers = body.table.rows.first(),
                    rows = body.table.rows.drop(1),
                    truncated = body.table.hasMore,
                )
        }
    }
}

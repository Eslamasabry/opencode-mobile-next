package io.github.eslamasabry.oc.feature.search.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.WebSourceSelection
import io.github.eslamasabry.oc.engine.composer.web.WebSourcesReview
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
public fun WebSourcesScreen(state: WebSourcesUi, actions: WebSourcesActions) {
    val held =
        if (state.changed || state.busy)
            KitEnabled.Disabled(
                stringResource(
                    if (state.changed) Res.string.webSourcesScopeChanged
                    else Res.string.webSearchBusy
                )
            )
        else KitEnabled.Enabled
    val draftLabel = stringResource(Res.string.webSourcesDraftLabel)
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.webSourcesTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitText(
                stringResource(
                    if (state.available) Res.string.webSearchDisclosure
                    else Res.string.webSourcesDisclosure
                )
            )
            state.problem?.let { problem ->
                KitNotice(
                    stringResource(
                        when (problem) {
                            WebSourcesProblem.Changed -> Res.string.webSourcesDraftChanged
                            WebSourcesProblem.Invalid -> Res.string.webSourcesInvalidUrl
                            WebSourcesProblem.Duplicate -> Res.string.webSourcesDuplicateNative
                            WebSourcesProblem.Full -> Res.string.webSourcesFullNative
                            WebSourcesProblem.Failed -> Res.string.webSearchFailed
                        }
                    )
                )
            }
            if (state.available && !state.changed) {
                val query = rememberTextFieldState()
                KitField(
                    query,
                    stringResource(Res.string.webSearchQuery),
                    maxLength = WebSourcesReview.MAX_QUERY,
                    enabled = held,
                )
                KitRowGroup(label = stringResource(Res.string.webSearchProvider)) {
                    for (provider in state.providers) KitRow(
                        title = provider.name,
                        selected = state.provider == provider.id,
                        enabled = !state.busy,
                        disabledReason = stringResource(Res.string.webSearchBusy),
                        onClick = { actions.chooseProvider(provider.id) },
                    )
                }
                KitButton(
                    stringResource(Res.string.webSearchSubmit),
                    { actions.search(query.text.toString()) },
                    enabled =
                        if (state.provider == null)
                            KitEnabled.Disabled(stringResource(Res.string.webSearchNeedsProvider))
                        else held,
                    working = state.busy,
                )
                KitButton(
                    stringResource(Res.string.webSearchRefresh),
                    actions::discover,
                    enabled = held,
                )
                if (state.searched && state.results.isEmpty())
                    KitNotice(stringResource(Res.string.webSearchEmpty))
                if (state.omitted > 0) KitNotice(stringResource(Res.string.webSearchOmitted))
                for (source in state.results) {
                    KitRow(title = source.title, supporting = source.url, onClick = null)
                    source.excerpt?.let { KitText(it) }
                    KitButton(
                        stringResource(Res.string.webSourcesAddNamed, source.title),
                        { actions.addResult(source.url) },
                        enabled = held,
                    )
                }
            }
            val url = rememberTextFieldState()
            val title = rememberTextFieldState()
            val excerpt = rememberTextFieldState()
            KitRowGroup(label = stringResource(Res.string.webSearchManual)) {
                KitField(
                    url,
                    stringResource(Res.string.webSourcesUrl),
                    maxLength = WebSourceSelection.MAX_URL,
                    enabled = held,
                )
                KitField(
                    title,
                    stringResource(Res.string.webSourcesLabel),
                    maxLength = WebSourceSelection.MAX_TITLE,
                    enabled = held,
                )
                KitField(
                    excerpt,
                    stringResource(Res.string.webSourcesExcerpt),
                    kind = KitFieldKind.Multiline,
                    helper = stringResource(Res.string.webSourcesExcerptHint),
                    maxLength = WebSourceSelection.MAX_EXCERPT,
                    enabled = held,
                )
                KitButton(
                    stringResource(Res.string.webSourcesAddLink),
                    {
                        actions.addLink(
                            title.text.toString(),
                            url.text.toString(),
                            excerpt.text.toString(),
                        )
                    },
                    enabled = held,
                )
            }
            if (state.sources.isNotEmpty())
                KitRowGroup(label = stringResource(Res.string.webSourcesPastedLinks)) {
                    for (row in state.sources) {
                        KitSwitchRow(
                            row.source.title,
                            row.selected,
                            supporting = row.source.url,
                            enabled = held,
                            onCheckedChange = { actions.select(row.source.url, it) },
                        )
                        row.source.excerpt?.let { KitText(it) }
                        KitButton(
                            stringResource(Res.string.webSourcesRemoveNamed, row.source.title),
                            { actions.remove(row.source.url) },
                            enabled = held,
                        )
                    }
                }
            val count = state.sources.count { it.selected }
            KitButton(
                pluralStringResource(Res.plurals.webSourcesDone, count, count),
                { actions.confirm(draftLabel) },
                enabled =
                    if (!state.ready || count == 0)
                        KitEnabled.Disabled(stringResource(Res.string.webSourcesChooseNative))
                    else held,
                working = state.busy,
            )
        }
    }
}

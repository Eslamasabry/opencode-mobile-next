package io.github.eslamasabry.oc.feature.chat.start.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.composer.phrases.StartFact
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind
import io.github.eslamasabry.oc.engine.composer.phrases.Starters
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartActions
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatStartAddTests
import io.github.eslamasabry.oc.ui.words.res.chatStartBuildWebPage
import io.github.eslamasabry.oc.ui.words.res.chatStartChangeCount
import io.github.eslamasabry.oc.ui.words.res.chatStartEmptyFolder
import io.github.eslamasabry.oc.ui.words.res.chatStartExplainProject
import io.github.eslamasabry.oc.ui.words.res.chatStartFindBug
import io.github.eslamasabry.oc.ui.words.res.chatStartGit
import io.github.eslamasabry.oc.ui.words.res.chatStartItemCount
import io.github.eslamasabry.oc.ui.words.res.chatStartListFolder
import io.github.eslamasabry.oc.ui.words.res.chatStartLooking
import io.github.eslamasabry.oc.ui.words.res.chatStartNodeProject
import io.github.eslamasabry.oc.ui.words.res.chatStartPythonScript
import io.github.eslamasabry.oc.ui.words.res.chatStartReadme
import io.github.eslamasabry.oc.ui.words.res.chatStartServerFolder
import io.github.eslamasabry.oc.ui.words.res.chatStartSuggestionsLabel
import io.github.eslamasabry.oc.ui.words.res.chatStartTip
import io.github.eslamasabry.oc.ui.words.res.chatStartWhatChanged
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Empty-chat facts and visible starter choices; every choice fills the field and leaves sending to
 * the person.
 */
@Composable
public fun ChatStartArea(
    state: ChatStartUi,
    actions: ChatStartActions,
    modifier: Modifier = Modifier,
) {
    val facts =
        Starters.factsLine(state.facts)
            .map { fact ->
                when (fact) {
                    StartFact.Looking -> stringResource(Res.string.chatStartLooking)
                    StartFact.EmptyFolder -> stringResource(Res.string.chatStartEmptyFolder)
                    StartFact.Git -> stringResource(Res.string.chatStartGit)
                    is StartFact.Items ->
                        pluralStringResource(Res.plurals.chatStartItemCount, fact.count, fact.count)
                    is StartFact.Changes ->
                        pluralStringResource(
                            Res.plurals.chatStartChangeCount,
                            fact.count,
                            fact.count,
                        )
                }
            }
            .joinToString(" · ")
    val ways = stringResource(Res.string.chatStartSuggestionsLabel)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState(), reverseScrolling = true)
                .padding(vertical = Space.S4.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.facts.projectName == null)
                KitText(
                    stringResource(Res.string.chatStartServerFolder),
                    role = TypeRole.Title,
                    modifier =
                        Modifier.padding(horizontal = Space.S4.dp).testTag("chat-start-name"),
                )
            if (facts.isNotEmpty())
                KitText(
                    facts,
                    tone = TextTone.Secondary,
                    modifier =
                        Modifier.padding(horizontal = Space.S4.dp).testTag("chat-start-facts"),
                )
            KitText(
                stringResource(Res.string.chatStartTip),
                role = TypeRole.Caption,
                tone = TextTone.Secondary,
                modifier = Modifier.padding(horizontal = Space.S4.dp).testTag("chat-start-tip"),
            )
            if (state.showSuggestions) {
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Space.S4.dp)
                        .semantics { contentDescription = ways }
                        .testTag("chat-starters"),
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    Starters.suggestions(state.facts).forEach { kind ->
                        val label = starterLabel(kind)
                        KitChip(label = label, onClick = { actions.chooseStarter(kind, label) })
                    }
                }
            }
        }
    }
}

@Composable
private fun starterLabel(kind: StarterKind): String =
    stringResource(
        when (kind) {
            StarterKind.ListFolder -> Res.string.chatStartListFolder
            StarterKind.FindBug -> Res.string.chatStartFindBug
            StarterKind.BuildWebPage -> Res.string.chatStartBuildWebPage
            StarterKind.PythonScript -> Res.string.chatStartPythonScript
            StarterKind.NodeProject -> Res.string.chatStartNodeProject
            StarterKind.Readme -> Res.string.chatStartReadme
            StarterKind.ExplainProject -> Res.string.chatStartExplainProject
            StarterKind.WhatChanged -> Res.string.chatStartWhatChanged
            StarterKind.AddTests -> Res.string.chatStartAddTests
        }
    )

package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.ui.kit.frame.kitPageListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.team.ui.HostGuide
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Connect a team on a computer / Change address (Flutter `TeamHostForm`): the address and an optional team name, one
 * "Test and turn on". The test is progress with Cancel test; a failed test says why in one notice and keeps the address
 * under Connection details; an address that did not answer can be saved anyway. The same page serves the first connect
 * (from the set-up guide's "Enter the address") and Change address in Team settings.
 */
@Composable
public fun FleetConnectScreen(state: FleetConnectUi, actions: FleetConnectActions) {
    KitSheetHost(Modifier.fillMaxSize()) {
        // The fields are drawn once the saved address is read, so they start with it and not with a blank.
        if (state.ready) {
            val url = rememberTextFieldState(state.initialUrl)
            val city = rememberTextFieldState(state.initialCity)
            val submit = { actions.test(url.text.toString(), city.text.toString()) }
            KitScreen(
                topBar = {
                    KitTopBar(
                        title = stringResource(Res.string.teamUiAddTitle),
                        exit = KitTopBarExit.Back,
                        exitLabel = stringResource(Res.string.kitTopBarBack),
                        onExit = actions::back,
                    )
                },
                bottom = {
                    KitActionBlock(
                        Modifier.padding(vertical = Space.S2.dp).testTag("team-address-actions"),
                        primary =
                            KitAction(
                                stringResource(if (state.testing) Res.string.teamUiAddTesting else Res.string.teamUiAddSubmit),
                                submit,
                                working = state.testing || state.saving,
                            ),
                        secondary =
                            when {
                                state.testing -> KitAction(stringResource(Res.string.teamHostFormCancelTest), actions::cancelTest)
                                state.canSaveAnyway -> KitAction(stringResource(Res.string.teamHostFormSaveAnyway), actions::saveAnyway)
                                else -> null
                            },
                    )
                },
                width = KitScreenWidth.Reading,
            ) {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("team-address"),
                    contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S4.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
                    state = kitPageListState(),
                ) {
                    item(key = "url") {
                        KitField(
                            url,
                            stringResource(Res.string.teamUiAddAddressLabel),
                            Modifier.testTag("team-address-url"),
                            kind = KitFieldKind.Url,
                            placeholder = stringResource(Res.string.teamUiAddAddressHint),
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                            onTextChanged = { actions.edited() },
                        )
                    }
                    item(key = "city") {
                        KitField(
                            city,
                            stringResource(Res.string.teamHostFormTeamLabel),
                            Modifier.testTag("team-address-city"),
                            kind = KitFieldKind.Mono,
                            helper = stringResource(Res.string.teamHostFormTeamHelper),
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                            onSubmit = submit,
                            onTextChanged = { actions.edited() },
                        )
                    }
                    state.problem?.let { problem ->
                        item(key = "problem") {
                            KitNotice(
                                stringResource(problemWords(problem)),
                                Modifier.testTag("team-address-verdict"),
                                tone = StatusTone.Failure,
                                notes = if (state.canSaveAnyway) listOf(stringResource(Res.string.teamHostFormSaveAnywayNote)) else emptyList(),
                                actions =
                                    if (FleetConnectRules.showsGuide(problem)) {
                                        listOf(KitLineAction(stringResource(Res.string.teamHostFormHowAction), actions::openGuide, testTag = "team-address-how"))
                                    } else {
                                        emptyList()
                                    },
                            )
                        }
                    }
                    // The address that was asked, last and folded (the raw cause stays out of the way).
                    state.asked?.let { asked ->
                        item(key = "details") {
                            KitDetailsFold(
                                label = stringResource(Res.string.teamHostFormConnectionDetails),
                                copyLabel = stringResource(Res.string.kitCopyDetails),
                                modifier = Modifier.testTag("team-address-details"),
                                values = listOf(KitTechnicalValue(stringResource(Res.string.teamUiLabelAddress), asked)),
                            )
                        }
                    }
                }
            }
            // The guide's "Enter the address" comes back to this form.
            HostGuide(state.guide, actions::closeGuide, onEnterAddress = actions::closeGuide)
        } else {
            KitScreen(
                topBar = {
                    KitTopBar(
                        title = stringResource(Res.string.teamUiAddTitle),
                        exit = KitTopBarExit.Back,
                        exitLabel = stringResource(Res.string.kitTopBarBack),
                        onExit = actions::back,
                    )
                },
                loading = true,
                loadingLabel = stringResource(Res.string.teamProjectLoad),
                width = KitScreenWidth.Reading,
            ) {}
        }
    }
}

/** Dart `teamVerdictCopy` and the form's own lines. */
private fun problemWords(problem: FleetConnectProblem) =
    when (problem) {
        FleetConnectProblem.AddressRequired -> Res.string.teamUiAddressRequired
        FleetConnectProblem.TailnetRequired -> Res.string.teamUiTailnetRequired
        FleetConnectProblem.BadAddress -> Res.string.teamUiVerdictUnreachable
        FleetConnectProblem.NotGasCity -> Res.string.teamUiVerdictNotGasCity
        FleetConnectProblem.CityNotRunning -> Res.string.teamUiVerdictCityNotRunning
        FleetConnectProblem.SaveFailed -> Res.string.scheduleSaveFailed
    }

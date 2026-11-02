package io.github.eslamasabry.oc.feature.chat.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.cards.CardAgent
import io.github.eslamasabry.oc.engine.cards.CardsProblem
import io.github.eslamasabry.oc.engine.cards.CardsStatus
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentNamesPair
import io.github.eslamasabry.oc.ui.words.res.cardsFromAgentsSupporting
import io.github.eslamasabry.oc.ui.words.res.cardsFromAgentsTitle
import io.github.eslamasabry.oc.ui.words.res.cardsProblemBusy
import io.github.eslamasabry.oc.ui.words.res.cardsProblemConflict
import io.github.eslamasabry.oc.ui.words.res.cardsProblemInstallationFailed
import io.github.eslamasabry.oc.ui.words.res.cardsProblemNotQualified
import io.github.eslamasabry.oc.ui.words.res.cardsProblemNotQualifiedFor
import io.github.eslamasabry.oc.ui.words.res.cardsProblemPermissionDenied
import io.github.eslamasabry.oc.ui.words.res.cardsProblemRegistrationFailed
import io.github.eslamasabry.oc.ui.words.res.cardsProblemRegistrationFailedFor
import io.github.eslamasabry.oc.ui.words.res.cardsProblemRemovalFailed
import io.github.eslamasabry.oc.ui.words.res.cardsProblemRemovalFailedFor
import io.github.eslamasabry.oc.ui.words.res.cardsProblemRuntimeMissing
import io.github.eslamasabry.oc.ui.words.res.cardsProblemRuntimeMissingFor
import io.github.eslamasabry.oc.ui.words.res.cardsProblemStorageFailed
import io.github.eslamasabry.oc.ui.words.res.cardsProblemUnsupportedHost
import io.github.eslamasabry.oc.ui.words.res.cardsProblemVerificationFailed
import io.github.eslamasabry.oc.ui.words.res.cardsProblemVerificationFailedFor
import io.github.eslamasabry.oc.ui.words.res.cardsSettingFailed
import io.github.eslamasabry.oc.ui.words.res.cardsStatusChecking
import io.github.eslamasabry.oc.ui.words.res.cardsStatusFailed
import io.github.eslamasabry.oc.ui.words.res.cardsStatusOff
import io.github.eslamasabry.oc.ui.words.res.cardsStatusOn
import io.github.eslamasabry.oc.ui.words.res.cardsStatusPartial
import io.github.eslamasabry.oc.ui.words.res.cardsStatusRestart
import io.github.eslamasabry.oc.ui.words.res.cardsStatusUnavailable
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One server's Cards row: what the person chose and what is true now. */
public data class CardsRowUi(val profileId: String, val server: String, val on: Boolean, val status: CardsStatus, val pending: Boolean, val failed: Boolean)

public data class CardsSettingsUi(val rows: ImmutableList<CardsRowUi>)

public interface CardsSettingsActions {
    public fun setEnabled(profileId: String, on: Boolean)

    public fun back()
}

/**
 * Settings › Cards from agents (Flutter `CardsFromAgentsRow`): one switch per connected server, its status under it.
 * Off blocks card actions at once; the status says what is still to do rather than claiming more than is verified.
 */
@Composable
public fun CardsSettingsScreen(state: CardsSettingsUi, actions: CardsSettingsActions) {
    KitScreen(
        modifier = Modifier.fillMaxSize(),
        page = true,
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.cardsFromAgentsTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.S2.dp)) {
            KitText(
                stringResource(Res.string.cardsFromAgentsSupporting),
                Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            KitRowGroup(Modifier.testTag("cards-from-agents")) {
                state.rows.forEach { row ->
                    val status = if (row.failed) stringResource(Res.string.cardsSettingFailed) else cardsStatusText(row.status)
                    KitSwitchRow(
                        title = if (state.rows.size > 1) row.server else stringResource(Res.string.cardsFromAgentsTitle),
                        checked = row.on,
                        supporting = status,
                        enabled = KitEnabled.of(!row.pending, status),
                        modifier = Modifier.testTag("cards-switch-${row.profileId}"),
                        onCheckedChange = { actions.setEnabled(row.profileId, it) },
                    )
                }
            }
        }
    }
}

/** The names of the agents a status covers, once each, as a natural list. */
@Composable
private fun agentNames(agents: List<CardAgent>): String {
    val names = agents.map { it.displayName }.distinct()
    if (names.size < 2) return names.joinToString("")
    return stringResource(Res.string.agentNamesPair, names.dropLast(1).joinToString(", "), names.last())
}

/** Why a step stopped, in plain words; a problem about particular agents names them. */
@Composable
internal fun cardsProblemText(problem: CardsProblem, affected: List<CardAgent>): String {
    val names = if (affected.isEmpty()) null else agentNames(affected)
    val count = affected.map { it.displayName }.distinct().size
    return when (problem) {
        CardsProblem.UnsupportedHost -> stringResource(Res.string.cardsProblemUnsupportedHost)
        CardsProblem.RuntimeMissing ->
            if (names == null) stringResource(Res.string.cardsProblemRuntimeMissing) else stringResource(Res.string.cardsProblemRuntimeMissingFor, names)
        CardsProblem.NotQualified ->
            if (names == null) stringResource(Res.string.cardsProblemNotQualified)
            else pluralStringResource(Res.plurals.cardsProblemNotQualifiedFor, count, count, names)
        CardsProblem.PermissionDenied -> stringResource(Res.string.cardsProblemPermissionDenied)
        CardsProblem.Conflict -> stringResource(Res.string.cardsProblemConflict)
        CardsProblem.InstallationFailed -> stringResource(Res.string.cardsProblemInstallationFailed)
        CardsProblem.RegistrationFailed ->
            if (names == null) stringResource(Res.string.cardsProblemRegistrationFailed)
            else stringResource(Res.string.cardsProblemRegistrationFailedFor, names)
        CardsProblem.VerificationFailed ->
            if (names == null) stringResource(Res.string.cardsProblemVerificationFailed)
            else stringResource(Res.string.cardsProblemVerificationFailedFor, names)
        CardsProblem.RemovalFailed ->
            if (names == null) stringResource(Res.string.cardsProblemRemovalFailed)
            else stringResource(Res.string.cardsProblemRemovalFailedFor, names)
        CardsProblem.StorageFailed -> stringResource(Res.string.cardsProblemStorageFailed)
        CardsProblem.Busy -> stringResource(Res.string.cardsProblemBusy)
    }
}

/** The status line under the switch. */
@Composable
internal fun cardsStatusText(status: CardsStatus): String =
    when (status) {
        CardsStatus.Off -> stringResource(Res.string.cardsStatusOff)
        CardsStatus.Checking -> stringResource(Res.string.cardsStatusChecking)
        is CardsStatus.On -> stringResource(Res.string.cardsStatusOn, agentNames(status.agents))
        is CardsStatus.Partial ->
            stringResource(Res.string.cardsStatusPartial, agentNames(status.agents), cardsProblemText(status.reason, status.affected))
        is CardsStatus.RestartRequired -> stringResource(Res.string.cardsStatusRestart, agentNames(status.agents))
        is CardsStatus.Unavailable -> stringResource(Res.string.cardsStatusUnavailable, cardsProblemText(status.reason, status.affected))
        is CardsStatus.Failed -> stringResource(Res.string.cardsStatusFailed, cardsProblemText(status.reason, status.affected))
    }


package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.team.TeamAction
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamCommandResult
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamHealth
import io.github.eslamasabry.oc.engine.team.TeamHost
import io.github.eslamasabry.oc.engine.team.TeamHostMode
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.TeamRead
import io.github.eslamasabry.oc.engine.team.TeamWorkspace

/** An engine that lists every command and may run work (a phone team that passed its checks). */
internal val READY_HEALTH: TeamHealth =
    TeamHealth(
        engineVersion = "1.0.0",
        execution = true,
        boundary = true,
        oc2 = true,
        oc2Verified = true,
        commandActions = TeamAction.all() + TeamCommand.EXECUTION_ACTIONS,
        boundaryTier = "landlock",
    )

/** An engine that answers but cannot run work yet (its boundary is not proven). */
internal val BLOCKED_HEALTH: TeamHealth = READY_HEALTH.copy(boundary = false)

/**
 * The phone engine as a fake: answers [workspace] with [health], records every command and its body, and keeps the
 * editor drafts in memory. [onCommand] lets a test change the workspace the next read returns.
 */
internal class FakeHost(
    var workspace: TeamWorkspace? = null,
    var health: TeamHealth = READY_HEALTH,
) : TeamHost {
    val commands = mutableListOf<TeamCommand>()
    val bodies = mutableListOf<Map<String, Any?>>()
    var answer: TeamCommandResult = TeamCommandResult(accepted = true, revision = 99)
    var drafts: String? = null
    var onCommand: (TeamCommand) -> Unit = {}

    override suspend fun config(profileId: String): TeamConfig =
        TeamConfig(TeamProvider.PhoneEngine, "http://127.0.0.1:4098", hostMode = TeamHostMode.Phone)

    override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> =
        workspace?.let { Outcome.Ok(TeamRead(health, it)) } ?: Outcome.Failed(Failure.Unexpected())

    override suspend fun command(
        profileId: String,
        config: TeamConfig,
        command: TeamCommand,
        body: Map<String, Any?>,
    ): Outcome<TeamCommandResult> {
        commands += command
        bodies += body
        onCommand(command)
        return Outcome.Ok(answer)
    }

    override suspend fun readDrafts(profileId: String): String? = drafts

    override suspend fun writeDrafts(profileId: String, json: String?): Outcome<Unit> {
        drafts = json
        return Outcome.Ok(Unit)
    }
}

/** Records what a page opened and whether it went back. */
internal class FakeNav : io.github.eslamasabry.oc.ui.routes.AppNavigator {
    val opened = mutableListOf<io.github.eslamasabry.oc.ui.routes.AppRoute>()
    var backs = 0
    override val top: kotlinx.coroutines.flow.StateFlow<io.github.eslamasabry.oc.ui.routes.AppRoute?> =
        kotlinx.coroutines.flow.MutableStateFlow(null)

    override fun open(route: io.github.eslamasabry.oc.ui.routes.AppRoute) {
        opened += route
    }

    override fun openChat(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef, focusComposer: Boolean) = Unit

    override fun land() = Unit

    override fun replaceRoot(root: io.github.eslamasabry.oc.ui.routes.AppRoute) = Unit

    override fun back() {
        backs++
    }
}

internal val FIXED_CLOCK: kotlin.time.Clock =
    object : kotlin.time.Clock {
        override fun now(): kotlin.time.Instant = kotlin.time.Instant.fromEpochMilliseconds(NOW)
    }

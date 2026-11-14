package io.github.eslamasabry.oc.feature.chats.lamp

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.dock.DockListHidden
import io.github.eslamasabry.oc.engine.attention.dock.DockListModel
import io.github.eslamasabry.oc.engine.attention.dock.DockListRow
import io.github.eslamasabry.oc.engine.attention.dock.DockListState
import io.github.eslamasabry.oc.engine.attention.dock.DockListSubAgent
import io.github.eslamasabry.oc.engine.attention.dock.DockProgress
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestKind
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestWords
import kotlinx.collections.immutable.toImmutableList

internal fun lref(id: String) = ConversationRef(ProfileId("p1"), SessionId(id))

internal fun perm(id: String = "r1", action: String = "edit", target: String = "wait7.txt") =
    DockRequestWords(DockRequestKind.Permission, id, action, target, "")

internal fun question(id: String = "q1", text: String = "Which branch?") =
    DockRequestWords(DockRequestKind.Question, id, "", "", text)

internal fun kid(
    id: String,
    state: DockListState = DockListState.Working,
    progress: DockProgress? = null,
    thinking: Boolean = false,
    request: DockRequestWords? = null,
) = DockListSubAgent(lref(id), id, state, progress, thinking, request)

internal fun lrow(
    id: String,
    state: DockListState = DockListState.Idle,
    name: String? = "Title $id",
    progress: DockProgress? = null,
    thinking: Boolean = false,
    request: DockRequestWords? = null,
    causedBy: String? = null,
    newest: String? = null,
    kids: List<DockListSubAgent> = emptyList(),
    more: Int = 0,
    server: String? = null,
    pinned: Boolean = false,
    last: Long? = null,
    open: Boolean = false,
) = DockListRow(
    ref = lref(id),
    name = name,
    state = state,
    progress = progress,
    thinking = thinking,
    request = request,
    causedBy = causedBy?.let(::lref),
    newestLine = newest,
    subAgents = kids.toImmutableList(),
    subAgentsMore = more,
    serverName = server,
    serverMark = null,
    agent = null,
    pinned = pinned,
    lastActivityMs = last,
    isOpen = open,
    pulse = 0,
)

internal fun lmodel(vararg rows: DockListRow, more: Int = 0, hidden: List<DockListHidden> = emptyList()) =
    DockListModel(rows = rows.toList().toImmutableList(), more = more, hidden = hidden.toImmutableList())

internal fun LampUi.rows(kind: LampGroupKind): List<LampRowUi> = groups.firstOrNull { it.kind == kind }?.rows.orEmpty()

internal fun LampUi.row(id: String): LampRowUi = groups.flatMap { it.rows }.first { it.ref == lref(id) }

internal fun present(model: DockListModel, input: LampInputs = LampInputs(nowMs = 1_000_000)) = LampListPresenter.present(model, input)

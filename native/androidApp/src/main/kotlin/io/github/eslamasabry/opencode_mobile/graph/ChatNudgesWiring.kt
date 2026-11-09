package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.settings.teaching.NudgeRegistry
import io.github.eslamasabry.oc.feature.chat.nudges.ChatNudgeSource
import io.github.eslamasabry.oc.feature.chat.nudges.NoChatNudges
import kotlin.time.Clock

/**
 * The conversation's one-time nudges (Flutter `_conn.nudges`; Approvals, Review changes, Compact): the app's single
 * [NudgeRegistry] over the Flutter keys `oc.nudges.*` in the shared preference store, so what Flutter already
 * showed stays shown after the switch. Until the store has warmed, no registry: the chat shows no nudge rather than
 * one it may already have shown.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, replaces = [NoChatNudges::class])
class AndroidChatNudges(
    private val stores: PreviewStores,
    private val clock: Clock,
    private val scope: AppCoroutineScope,
) : ChatNudgeSource {
    private val registry: NudgeRegistry by lazy { NudgeRegistry(stores.prefs, stores.lanes, clock, scope) }

    override suspend fun registry(): NudgeRegistry? = if (stores.warm() is Outcome.Failed) null else registry
}

package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.settings.teaching.StarPromptAnswer
import io.github.eslamasabry.oc.engine.settings.teaching.StarPromptLedger
import io.github.eslamasabry.oc.engine.settings.teaching.StarPromptUse
import io.github.eslamasabry.oc.feature.chat.starprompt.ChatStarPrompt
import io.github.eslamasabry.oc.feature.chat.starprompt.ReplyAskAnswer
import io.github.eslamasabry.oc.feature.chat.starprompt.ReplyAskUse
import io.github.eslamasabry.oc.feature.chat.starprompt.StarPromptEvent
import io.github.eslamasabry.opencode_mobile.telemetry.Telemetry
import kotlin.time.Clock

/**
 * The one-time first-reply ask: the counters and on-device answer/use live in the app-wide preference `oc.starPrompt`
 * ([StarPromptLedger]); the three events carry no properties and leave only while usage analytics is on.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ChatStarPrompt>())
@SingleIn(AppScope::class)
@Inject
class AndroidStarPrompt(stores: PreviewStores, clock: Clock) : ChatStarPrompt {
    private val ledger = StarPromptLedger(stores.prefs, stores.lanes) { clock.now().toEpochMilliseconds() }

    override suspend fun promptStarted() = ledger.promptStarted()

    override suspend fun replyEnded(success: Boolean): Boolean = ledger.replyEnded(success)

    override suspend fun answered(answer: ReplyAskAnswer) {
        val mapped =
            when (answer) {
                ReplyAskAnswer.Yes -> StarPromptAnswer.Yes
                ReplyAskAnswer.No -> StarPromptAnswer.No
                ReplyAskAnswer.Skip -> StarPromptAnswer.Skip
            }
        ledger.answered(mapped)
    }

    override suspend fun usedFor(use: ReplyAskUse) {
        val mapped =
            when (use) {
                ReplyAskUse.Everyday -> StarPromptUse.Everyday
                ReplyAskUse.Code -> StarPromptUse.Code
                ReplyAskUse.Both -> StarPromptUse.Both
                ReplyAskUse.Looking -> StarPromptUse.Looking
            }
        ledger.usedFor(mapped)
    }

    override fun report(event: StarPromptEvent) {
        val name =
            when (event) {
                StarPromptEvent.Shown -> ProductEvent.StarPromptShown
                StarPromptEvent.Tapped -> ProductEvent.StarPromptTapped
                StarPromptEvent.Dismissed -> ProductEvent.StarPromptDismissed
            }
        Telemetry.service.track(name)
    }
}

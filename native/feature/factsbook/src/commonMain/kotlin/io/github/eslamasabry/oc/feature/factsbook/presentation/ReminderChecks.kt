package io.github.eslamasabry.oc.feature.factsbook.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.engine.factsbook.ReminderPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What a reminder check opened from another page came to (F: `showFactsBookReminderCheck`'s answer). */
public sealed interface ReminderCheckResult {
    /** The node read the reminder as the person set it: for Add something to save with the note. */
    public data class Checked(val preview: ReminderPreview) : ReminderCheckResult

    /** "Change what I wrote", or Back: nothing is saved. */
    public data object Cancelled : ReminderCheckResult

    /** A proposed reminder was changed; it stays proposed. */
    public data object ProposalChanged : ReminderCheckResult
}

/** From the reminder check page to the page that opened it; a result nobody listens for is dropped. */
@SingleIn(AppScope::class)
@Inject
public class ReminderChecks {
    private val flow = MutableSharedFlow<ReminderCheckResult>(extraBufferCapacity = 4)

    public val results: SharedFlow<ReminderCheckResult> = flow.asSharedFlow()

    public fun tell(result: ReminderCheckResult) {
        flow.tryEmit(result)
    }
}

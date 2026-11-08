package io.github.eslamasabry.oc.ui.shell

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.ui.routes.NewChatOutcome
import io.github.eslamasabry.oc.ui.routes.NewChatReport
import io.github.eslamasabry.oc.ui.routes.NewChatReports
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The app's one [NewChatReports]: reports go to whoever listens now, never replayed. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class ShellNewChatReports : NewChatReports {
    private val flow = MutableSharedFlow<NewChatReport>(extraBufferCapacity = 16)

    override val reports: SharedFlow<NewChatReport> = flow.asSharedFlow()

    override fun report(key: String, outcome: NewChatOutcome) {
        flow.tryEmit(NewChatReport(key, outcome))
    }
}

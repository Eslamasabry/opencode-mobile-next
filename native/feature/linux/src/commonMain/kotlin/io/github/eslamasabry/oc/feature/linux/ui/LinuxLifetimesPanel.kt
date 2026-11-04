package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.runtime.diagnostics.ServiceExitReason
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxLifetimesUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeEmpty
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeExit
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeExited
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeFailed
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeKilled
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeLastUptime
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeReading
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeRefresh
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeRestarts
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeRunning
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeStopped
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeTitle
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeUnknown
import io.github.eslamasabry.oc.ui.words.res.linuxLifetimeUptime
import org.jetbrains.compose.resources.stringResource

/**
 * Service names and native observations only; this panel never guesses a kill cause from an exit
 * code.
 */
@Composable
public fun LinuxLifetimesPanel(state: LinuxLifetimesUi, onRefresh: () -> Unit) {
    KitRowGroup(label = stringResource(Res.string.linuxLifetimeTitle)) {
        KitRow(
            title =
                stringResource(
                    if (state.loading) Res.string.linuxLifetimeReading
                    else Res.string.linuxLifetimeRefresh
                ),
            trailing = KitRowTrailing.Chevron,
            onClick = if (state.loading) null else onRefresh,
        )
        state.records.forEach { record ->
            val status =
                stringResource(
                    if (record.running) Res.string.linuxLifetimeRunning
                    else Res.string.linuxLifetimeStopped
                )
            val reason =
                stringResource(
                    when (record.exitReason) {
                        ServiceExitReason.Unknown -> Res.string.linuxLifetimeUnknown
                        ServiceExitReason.Exited -> Res.string.linuxLifetimeExited
                        ServiceExitReason.MemoryOrPhantomKill -> Res.string.linuxLifetimeKilled
                    }
                )
            val detail =
                listOfNotNull(
                        status,
                        record.uptimeMs?.let { stringResource(Res.string.linuxLifetimeUptime, it) },
                        record.lastUptimeMs?.let {
                            stringResource(Res.string.linuxLifetimeLastUptime, it)
                        },
                        record.lastExitCode?.let {
                            stringResource(Res.string.linuxLifetimeExit, it)
                        },
                        stringResource(Res.string.linuxLifetimeRestarts, record.restartCount),
                        reason,
                    )
                    .joinToString(" · ")
            KitRow(title = record.name, supporting = detail, supportingMaxLines = 5)
        }
    }
    state.failure?.let {
        KitErrorNotice(
            message = stringResource(Res.string.linuxLifetimeFailed),
            failure = it,
            detailsLabel = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
            retry = KitLineAction(stringResource(Res.string.commonRetry), onRefresh),
        )
    }
    if (state.read && !state.loading && state.records.isEmpty())
        KitNotice(message = stringResource(Res.string.linuxLifetimeEmpty))
}

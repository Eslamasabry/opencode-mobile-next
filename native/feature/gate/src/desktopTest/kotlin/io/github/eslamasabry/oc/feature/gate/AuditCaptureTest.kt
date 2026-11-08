package io.github.eslamasabry.oc.feature.gate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.gate.ui.GateScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import kotlin.test.Test

/** Audit capture only: the start-up gate screens (migration, failures, sign-in re-entry). */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    @Composable
    private fun Gate(state: GateUi) =
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            GateScreen(
                ui = state,
                onRow = {},
                onConfirm = {},
                onCancelConfirm = {},
                onReentry = {},
                onReentryLater = {},
                onReentrySubmit = {},
                onReentryBack = {},
                onReentryOpen = {},
                onDismissDeferredNotice = {},
                content = { KitText("App content") },
            )
        }

    private fun shot(name: String, state: GateUi) =
        Audit.guard("gate__gate__${name}__build") {
            Audit.shoot("gate", "gate", name) { Gate(state) }
        }

    @Test
    fun gate() {
        val items = listOf(
            GateReentry("p1", "Home server", GateReentryKind.Password),
            GateReentry("p2", "Work", GateReentryKind.Token),
            GateReentry(null, "GitHub", GateReentryKind.GitHubSignIn),
            GateReentry("p3", "A laptop with a deliberately very long display name", GateReentryKind.ByoSetup),
        )
        val home = items[0]
        shot("opening-wordless", GateUi.Opening(wordless = true, offerRetry = false))
        shot("opening-words", GateUi.Opening(wordless = false, offerRetry = false))
        shot("opening-retry", GateUi.Opening(wordless = false, offerRetry = true))
        shot("updating", GateUi.Updating(offerRetry = false))
        shot("updating-retry", GateUi.Updating(offerRetry = true))
        for (reason in listOf(GateBlockReason.StepCrashed, GateBlockReason.PrefsUnreadable, GateBlockReason.AuthorityChanged, GateBlockReason.BackupInvalid)) {
            shot(
                "failed-${reason.name.lowercase()}",
                GateUi.Failed(
                    GateFailureScreen.Failed,
                    GatePresenter.rowsFor(reason, restoreAvailable = reason == GateBlockReason.StepCrashed),
                    detail = "M2 · UnwrapFailed ×1",
                ),
            )
        }
        shot("failed-confirm-start-fresh", GateUi.Failed(GateFailureScreen.Failed, GatePresenter.rowsFor(GateBlockReason.PrefsUnreadable, restoreAvailable = false), confirming = GateRow.StartFresh))
        shot("low-space", GateUi.Failed(GateFailureScreen.LowSpace, GatePresenter.rowsFor(GateBlockReason.LowSpace, restoreAvailable = false), neededBytes = 120L * 1024 * 1024))
        shot("save-failed", GateUi.Failed(GateFailureScreen.SaveFailed, GatePresenter.rowsFor(GateBlockReason.WriteRefused, restoreAvailable = false)))
        shot("reenter-list", GateUi.Open(items))
        shot("reenter-password", GateUi.Open(listOf(home), entry = GateEntry(home)))
        shot("reenter-password-saving", GateUi.Open(listOf(home), entry = GateEntry(home, saving = true)))
        shot("reenter-password-failed", GateUi.Open(listOf(home), entry = GateEntry(home, failed = true)))
        shot("reenter-github", GateUi.Open(listOf(items[2]), entry = GateEntry(items[2])))
        shot("reenter-byo", GateUi.Open(listOf(items[3]), entry = GateEntry(items[3])))
        shot("deferred-all", GateUi.Open(emptyList(), deferred = listOf(GateDeferredFeature.AiTeam, GateDeferredFeature.Memory, GateDeferredFeature.Gemma)))
        shot("open-nothing", GateUi.Open(emptyList()))
    }
}

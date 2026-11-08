package io.github.eslamasabry.oc.feature.updates

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.updates.presentation.NoticeUi
import io.github.eslamasabry.oc.feature.updates.presentation.OfferUi
import io.github.eslamasabry.oc.feature.updates.presentation.UpdateNoticeActions
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatePromptActions
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesActions
import io.github.eslamasabry.oc.feature.updates.presentation.updatesUi
import io.github.eslamasabry.oc.feature.updates.ui.UpdateNotice
import io.github.eslamasabry.oc.feature.updates.ui.UpdateOfferSheet
import io.github.eslamasabry.oc.feature.updates.ui.UpdatePromptSheet
import io.github.eslamasabry.oc.feature.updates.ui.UpdatesScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Audit capture only: the Updates page, notice, offer sheet and start-up prompt. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val now = Instant.fromEpochMilliseconds(1_767_225_600_000)
    private val available =
        AppUpdateState(phase = UpdatePhase.Available, release = RELEASE, downloadSize = APK_SIZE, installed = INSTALLED)

    private object Taps : UpdatesActions, UpdateNoticeActions, UpdatePromptActions {
        override fun onBack() {}
        override fun onUpdate() {}
        override fun onCancel() {}
        override fun onInstall() {}
        override fun onTryAgain() {}
        override fun onOpenRelease() {}
        override fun onCheckNow() {}
        override fun onChecks(on: Boolean) {}
        override fun onNotNow() {}
        override fun onPromptUpdate() {}
        override fun onPromptLater() {}
        override fun onPromptClose() {}
    }

    private fun page(name: String, state: AppUpdateState) =
        Audit.guard("updates__updates__${name}__build") {
            Audit.shoot("updates", "updates", name) { UpdatesScreen(updatesUi(state, now), Taps) }
        }

    @Test
    fun updatesPage() {
        val hash = "b".repeat(64)
        page("up-to-date", AppUpdateState(installed = INSTALLED, checkedAt = now))
        page("up-to-date-5-min", AppUpdateState(installed = INSTALLED, checkedAt = now - 5.minutes))
        page("never-checked", AppUpdateState(installed = INSTALLED))
        page("checking", AppUpdateState(installed = INSTALLED, phase = UpdatePhase.Checking))
        page("checking-off", AppUpdateState(installed = INSTALLED, enabled = false))
        page("available", available)
        page("downloading", available.copy(phase = UpdatePhase.Downloading, progress = 0.425f))
        page("downloading-unknown-size", available.copy(phase = UpdatePhase.Downloading, progress = null))
        page("verifying", available.copy(phase = UpdatePhase.Verifying))
        page("ready-to-install", available.copy(phase = UpdatePhase.ReadyToInstall))
        page("needs-permission", available.copy(phase = UpdatePhase.ReadyToInstall, needsPermission = true))
        page("installing", available.copy(phase = UpdatePhase.Installing))
        page("failed-checksum", available.copy(phase = UpdatePhase.Failed, failure = UpdateFailure.Checksum, detail = "sha256 $hash, expected ${"a".repeat(64)}"))
        page("failed-signer", available.copy(phase = UpdatePhase.Failed, failure = UpdateFailure.Signer, detail = "signer BB22, expected AA11"))
        page("failed-network", available.copy(phase = UpdatePhase.Failed, failure = UpdateFailure.Network))
        page("failed-storage", available.copy(phase = UpdatePhase.Failed, failure = UpdateFailure.Storage))
    }

    private fun notice(name: String, ui: NoticeUi) =
        Audit.guard("updates__notice__${name}__build") {
            Audit.shoot("updates", "notice", name) { UpdateNotice(ui, Taps) }
        }

    @Test
    fun noticeAndSheets() {
        notice("ready", NoticeUi.Ready("2.2.0"))
        notice("downloading", NoticeUi.Downloading("2.2.0", 42))
        notice("ready-to-install", NoticeUi.ReadyToInstall("2.2.0", needsPermission = true))
        notice("verifying", NoticeUi.Verifying)
        Audit.guard("updates__offer-sheet__summary__build") {
            Audit.shoot("updates", "offer-sheet", "summary") {
                KitSheetHost {
                    UpdateOfferSheet(
                        version = "2.2.0",
                        onUpdateNow = {},
                        onOpenSettings = {},
                        onDismiss = {},
                        summary = "This release is faster and fixes the long wait when a conversation opens.",
                    )
                }
            }
        }
        Audit.guard("updates__prompt-sheet__summary__build") {
            Audit.shoot("updates", "prompt-sheet", "summary") {
                KitSheetHost { UpdatePromptSheet(OfferUi("2.2.0", "This release is faster."), Taps) }
            }
        }
    }
}

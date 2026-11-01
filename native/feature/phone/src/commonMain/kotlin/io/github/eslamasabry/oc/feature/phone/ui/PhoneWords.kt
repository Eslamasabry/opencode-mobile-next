package io.github.eslamasabry.oc.feature.phone.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.phone.presentation.AboutWait
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerHeadline
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerLine
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerTone
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveStepKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneAct
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneMaker
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneOp
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessKind
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7SetupOpenAppSettings
import io.github.eslamasabry.oc.ui.words.res.inAppServerOwnershipRecoveryBody
import io.github.eslamasabry.oc.ui.words.res.keepRunningAutostartDetail
import io.github.eslamasabry.oc.ui.words.res.keepRunningAutostartHuawei
import io.github.eslamasabry.oc.ui.words.res.keepRunningAutostartTitle
import io.github.eslamasabry.oc.ui.words.res.keepRunningBackgroundOppo
import io.github.eslamasabry.oc.ui.words.res.keepRunningBackgroundOther
import io.github.eslamasabry.oc.ui.words.res.keepRunningBackgroundSamsung
import io.github.eslamasabry.oc.ui.words.res.keepRunningBackgroundTitle
import io.github.eslamasabry.oc.ui.words.res.keepRunningBackgroundVivo
import io.github.eslamasabry.oc.ui.words.res.keepRunningBackgroundXiaomi
import io.github.eslamasabry.oc.ui.words.res.keepRunningBatteryDetail
import io.github.eslamasabry.oc.ui.words.res.keepRunningBatteryDone
import io.github.eslamasabry.oc.ui.words.res.keepRunningBatteryTitle
import io.github.eslamasabry.oc.ui.words.res.keepRunningLockNubia
import io.github.eslamasabry.oc.ui.words.res.keepRunningLockOther
import io.github.eslamasabry.oc.ui.words.res.keepRunningLockSamsung
import io.github.eslamasabry.oc.ui.words.res.keepRunningLockTitle
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardShowProgress
import io.github.eslamasabry.oc.ui.words.res.phoneServerRestartFailed
import io.github.eslamasabry.oc.ui.words.res.phoneServerRowNotAnswering
import io.github.eslamasabry.oc.ui.words.res.phoneServerStartFailed
import io.github.eslamasabry.oc.ui.words.res.phoneServerStopFailed
import io.github.eslamasabry.oc.ui.words.res.termuxProcsKindAiTeam
import io.github.eslamasabry.oc.ui.words.res.termuxProcsKindClaudeCode
import io.github.eslamasabry.oc.ui.words.res.termuxProcsKindDevService
import io.github.eslamasabry.oc.ui.words.res.termuxProcsKindHelper
import io.github.eslamasabry.oc.ui.words.res.termuxProcsKindOpenCode
import io.github.eslamasabry.oc.ui.words.res.termuxProcsKindTerminal
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAboutMinutes
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAboutSeconds
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerAndroidClosed
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerChecking
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerGaveUp
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerInstallFailed
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerNotSetUp
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerOff
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerOffLine
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerRecovery
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerRestartNow
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerRestarting
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerRestartsIn
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerRestartsSoon
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerRunning
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerSettingUp
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerStartFailed
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerStartFailedLine
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerStarting
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerStopping
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerStoppedItself
import io.github.eslamasabry.oc.ui.words.res.thisPhoneAnswerStorage
import io.github.eslamasabry.oc.ui.words.res.thisPhoneInstallAgain
import io.github.eslamasabry.oc.ui.words.res.thisPhoneSetUp
import io.github.eslamasabry.oc.ui.words.res.thisPhoneStart
import io.github.eslamasabry.oc.ui.words.res.thisPhoneStartAgain
import org.jetbrains.compose.resources.stringResource

/*
 * Value → :ui:words. The presentation layer carries no copy (native AGENTS rule 3); every sentence below is the
 * Flutter app's own ARB string, converted once into strings_phone.xml.
 */

internal fun AnswerTone.status(): StatusTone =
    when (this) {
        AnswerTone.Ok -> StatusTone.Ok
        AnswerTone.Working -> StatusTone.Progress
        AnswerTone.Off -> StatusTone.Neutral
        AnswerTone.Paused -> StatusTone.Attention
        AnswerTone.NeedsYou -> StatusTone.Failure
    }

@Composable
internal fun AnswerHeadline.words(): String =
    stringResource(
        when (this) {
            AnswerHeadline.Checking -> Res.string.thisPhoneAnswerChecking
            AnswerHeadline.NotSetUp -> Res.string.thisPhoneAnswerNotSetUp
            AnswerHeadline.SettingUp -> Res.string.thisPhoneAnswerSettingUp
            AnswerHeadline.InstallFailed -> Res.string.thisPhoneAnswerInstallFailed
            AnswerHeadline.Starting -> Res.string.thisPhoneAnswerStarting
            AnswerHeadline.Stopping -> Res.string.thisPhoneAnswerStopping
            AnswerHeadline.Running -> Res.string.thisPhoneAnswerRunning
            AnswerHeadline.NotAnswering -> Res.string.phoneServerRowNotAnswering
            AnswerHeadline.Off -> Res.string.thisPhoneAnswerOff
            AnswerHeadline.AndroidClosed -> Res.string.thisPhoneAnswerAndroidClosed
            AnswerHeadline.StoppedItself -> Res.string.thisPhoneAnswerStoppedItself
            AnswerHeadline.Restarting -> Res.string.thisPhoneAnswerRestarting
            AnswerHeadline.StartFailed -> Res.string.thisPhoneAnswerStartFailed
            AnswerHeadline.Recovery -> Res.string.thisPhoneAnswerRecovery
        },
    )

@Composable
internal fun AboutWait.words(): String =
    when (this) {
        is AboutWait.Seconds -> stringResource(Res.string.thisPhoneAboutSeconds, value)
        is AboutWait.Minutes -> stringResource(Res.string.thisPhoneAboutMinutes, value)
    }

@Composable
internal fun AnswerLine.words(): String =
    when (this) {
        AnswerLine.StaysOff -> stringResource(Res.string.thisPhoneAnswerOffLine)
        AnswerLine.RestartsSoon -> stringResource(Res.string.thisPhoneAnswerRestartsSoon)
        is AnswerLine.RestartsIn -> stringResource(Res.string.thisPhoneAnswerRestartsIn, wait.words())
        AnswerLine.GaveUp -> stringResource(Res.string.thisPhoneAnswerGaveUp)
        AnswerLine.StartFailedLine -> stringResource(Res.string.thisPhoneAnswerStartFailedLine)
        AnswerLine.RecoveryBody -> stringResource(Res.string.inAppServerOwnershipRecoveryBody)
        is AnswerLine.Storage -> stringResource(Res.string.thisPhoneAnswerStorage, PhoneSizes.text(bytes))
        is AnswerLine.Because -> headline.words()
    }

@Composable
internal fun PhoneAct.words(): String =
    stringResource(
        when (this) {
            PhoneAct.SetUp -> Res.string.thisPhoneSetUp
            PhoneAct.SetUpAgain -> Res.string.thisPhoneInstallAgain
            PhoneAct.Start -> Res.string.thisPhoneStart
            PhoneAct.StartAgain -> Res.string.thisPhoneStartAgain
            PhoneAct.RestartNow -> Res.string.thisPhoneAnswerRestartNow
            PhoneAct.OpenAppSettings -> Res.string.e7SetupOpenAppSettings
            PhoneAct.ShowProgress -> Res.string.phoneServerCardShowProgress
        },
    )

@Composable
internal fun PhoneOp.failedWords(): String =
    stringResource(
        when (this) {
            PhoneOp.Start -> Res.string.phoneServerStartFailed
            PhoneOp.Stop -> Res.string.phoneServerStopFailed
            PhoneOp.Restart -> Res.string.phoneServerRestartFailed
            PhoneOp.Update -> Res.string.thisPhoneAnswerInstallFailed
        },
    )

/** The kind word under a process row, or null for a plain program. */
@Composable
internal fun ProcessKind.words(): String? =
    when (this) {
        ProcessKind.OpenCode -> stringResource(Res.string.termuxProcsKindOpenCode)
        ProcessKind.ClaudeCode -> stringResource(Res.string.termuxProcsKindClaudeCode)
        ProcessKind.AiTeam -> stringResource(Res.string.termuxProcsKindAiTeam)
        ProcessKind.DevService -> stringResource(Res.string.termuxProcsKindDevService)
        ProcessKind.Terminal -> stringResource(Res.string.termuxProcsKindTerminal)
        ProcessKind.Helper -> stringResource(Res.string.termuxProcsKindHelper)
        ProcessKind.Other -> null
    }

/** A keep-alive step's title and detail on [maker]'s phones (port of `keepAliveStepText`). */
@Composable
internal fun KeepAliveStepKind.words(maker: PhoneMaker, done: Boolean): Pair<String, String> =
    when (this) {
        KeepAliveStepKind.Battery ->
            stringResource(Res.string.keepRunningBatteryTitle) to
                stringResource(if (done) Res.string.keepRunningBatteryDone else Res.string.keepRunningBatteryDetail)
        KeepAliveStepKind.LockInRecents ->
            stringResource(Res.string.keepRunningLockTitle) to
                stringResource(
                    when (maker) {
                        PhoneMaker.Nubia -> Res.string.keepRunningLockNubia
                        PhoneMaker.Samsung -> Res.string.keepRunningLockSamsung
                        else -> Res.string.keepRunningLockOther
                    },
                )
        KeepAliveStepKind.Autostart ->
            stringResource(Res.string.keepRunningAutostartTitle) to
                stringResource(
                    if (maker == PhoneMaker.Huawei) Res.string.keepRunningAutostartHuawei else Res.string.keepRunningAutostartDetail,
                )
        KeepAliveStepKind.Background ->
            stringResource(Res.string.keepRunningBackgroundTitle) to
                stringResource(
                    when (maker) {
                        PhoneMaker.Xiaomi -> Res.string.keepRunningBackgroundXiaomi
                        PhoneMaker.Oppo -> Res.string.keepRunningBackgroundOppo
                        PhoneMaker.Vivo -> Res.string.keepRunningBackgroundVivo
                        PhoneMaker.Samsung -> Res.string.keepRunningBackgroundSamsung
                        else -> Res.string.keepRunningBackgroundOther
                    },
                )
    }

/** "410 MB", "1.2 GB": a size in round figures, isolated left to right (port of `thisPhoneSize`). */
internal object PhoneSizes {
    private const val LRI = '⁦'
    private const val PDI = '⁩'
    private const val NBSP = ' '

    fun text(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        val body =
            if (mb < 1000) {
                val rounded = if (mb < 100) kotlin.math.round(mb).toLong() else kotlin.math.round(mb / 10).toLong() * 10
                "$rounded${NBSP}MB"
            } else {
                val tenths = kotlin.math.round(mb / 1024 * 10).toLong()
                "${tenths / 10}.${tenths % 10}${NBSP}GB"
            }
        return "$LRI$body$PDI"
    }
}

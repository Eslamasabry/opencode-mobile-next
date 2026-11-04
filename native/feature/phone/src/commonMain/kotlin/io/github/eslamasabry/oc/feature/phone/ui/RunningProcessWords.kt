package io.github.eslamasabry.oc.feature.phone.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.runtime.procs.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DurationWords.words(): String =
    when (this) {
        is DurationWords.Seconds -> stringResource(Res.string.termuxProcsDurationSeconds, seconds)
        is DurationWords.Minutes -> stringResource(Res.string.termuxProcsDurationMinutes, minutes)
        is DurationWords.Hours ->
            stringResource(Res.string.termuxProcsDurationHours, hours, minutes)
    }

@Composable
internal fun CpuWords.words(): String =
    stringResource(
        when (level) {
            CpuLevel.Idle -> Res.string.runningPhoneCpu__idle
            CpuLevel.Light -> Res.string.runningPhoneCpu__light
            CpuLevel.Busy -> Res.string.runningPhoneCpu__busy
            CpuLevel.VeryBusy -> Res.string.runningPhoneCpu__other
        },
        percent,
    )

@Composable
internal fun RunawayMessage.words(): String =
    when (this) {
        is RunawayMessage.OpenCode -> stringResource(Res.string.workRunaway, busyFor.words())
        is RunawayMessage.OpenCodeInProject ->
            stringResource(Res.string.workRunawayInProject, project, busyFor.words())
        is RunawayMessage.Helper ->
            stringResource(Res.string.workRunawayHelper, helper, busyFor.words())
        is RunawayMessage.HelperInProject ->
            stringResource(Res.string.workRunawayHelperInProject, helper, project, busyFor.words())
        is RunawayMessage.StopFailed -> stringResource(Res.string.workRunawayStopFailed, helper)
    }

@Composable
private fun RunningProcessName.words(): String =
    when (this) {
        is RunningProcessName.Kernel -> name
        is RunningProcessName.Plain ->
            stringResource(
                when (kind) {
                    RunningNameKind.FlutterTest -> Res.string.runningPhoneName__flutterTest
                    RunningNameKind.Command -> Res.string.runningPhoneName__command
                    RunningNameKind.Linux -> Res.string.runningPhoneName__linux
                    RunningNameKind.OpenCode -> Res.string.runningPhoneName__openCode
                    RunningNameKind.ClaudeCode -> Res.string.runningPhoneName__claudeCode
                    RunningNameKind.AgentHelper -> Res.string.runningPhoneName__agentHelper
                    RunningNameKind.SignIn -> Res.string.runningPhoneName__signIn
                    RunningNameKind.Team -> Res.string.runningPhoneName__team
                    RunningNameKind.Node -> Res.string.runningPhoneName__node
                    RunningNameKind.Python -> Res.string.runningPhoneName__python
                    RunningNameKind.Git -> Res.string.runningPhoneName__git
                    RunningNameKind.Npm -> Res.string.runningPhoneName__npm
                    RunningNameKind.Dart -> Res.string.runningPhoneName__dart
                    RunningNameKind.Flutter -> Res.string.runningPhoneName__flutter
                    RunningNameKind.Gradle -> Res.string.runningPhoneName__gradle
                    RunningNameKind.Java -> Res.string.runningPhoneName__java
                    RunningNameKind.Search -> Res.string.runningPhoneName__search
                    RunningNameKind.App -> Res.string.runningPhoneName__app
                    RunningNameKind.Other -> Res.string.runningPhoneName__other
                }
            )
    }

@Composable
internal fun RunningRowStatus.words(): String =
    when (this) {
        is RunningRowStatus.LeftBehind ->
            pluralStringResource(Res.plurals.runningPhoneRowLeftBehind, count, count)
        is RunningRowStatus.Heavy ->
            pluralStringResource(
                Res.plurals.runningPhoneRowHeavy,
                count,
                count,
                name.words(),
                percent,
            )
        is RunningRowStatus.NearLimit ->
            stringResource(Res.string.runningPhoneRowNearLimit, count, limit)
        is RunningRowStatus.Normal ->
            pluralStringResource(
                Res.plurals.runningPhoneRowNormal,
                count,
                count,
                stringResource(Res.string.termuxProcsMemoryMb, rssBytes / 1_048_576),
            )
    }

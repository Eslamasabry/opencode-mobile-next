# Twins: native copies of Flutter-side files (plan 02 §5.6)

One row per copied file. `native/tool/twin_check.sh` lists the Flutter commits that touched a source path since its
"last synced" commit; the native owner mirrors each within the week and updates that column. Rows are added by the unit
that copies the file; the coordinator owns this file. "Source commit at copy" is the last commit that changed the
source file in this repository when it was copied (copy base `6627d320`).

## :android:linux (S1-LINUX, 2026-11-01)

Copied byte for byte (checked with `cmp`). Resource files are subsets: only the entries the copied Kotlin reads, each
entry verbatim.

| Native path | Flutter source path | Source commit at copy | Last synced Flutter commit | Native owner |
|---|---|---|---|---|
| native/android/linux/src/androidTest/java/io/github/eslamasabry/opencode_mobile/RuntimeQaGuard.java | android/app/src/androidTest/java/io/github/eslamasabry/opencode_mobile/RuntimeQaGuard.java | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinComponentFilesystemAcceptance.kt | android/app/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinComponentFilesystemAcceptance.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinComponentUpdateAcceptance.kt | android/app/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinComponentUpdateAcceptance.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinIdleAcceptance.kt | android/app/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinIdleAcceptance.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinInstallerProducerQaService.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinInstallerProducerQaService.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinRestoreAcceptance.kt | android/app/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinRestoreAcceptance.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinRuntimeAcceptance.kt | android/app/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinRuntimeAcceptance.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinRuntimeReclaimAcceptance.kt | android/app/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinRuntimeReclaimAcceptance.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinWorkLeaseAcceptance.kt | android/app/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinWorkLeaseAcceptance.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/AgentPriority.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/AgentPriority.kt | 580803d4 | 580803d4 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/AndroidGroupNames.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/AndroidGroupNames.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinGitConfig.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinGitConfig.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt | 580803d4 | 7455fb98 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinProjectStorage.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinProjectStorage.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinRestoreReceiver.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinRestoreReceiver.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinServerService.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinServerService.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ChildProcessLimit.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ChildProcessLimit.kt | 580803d4 | 580803d4 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/CodingAlertRequest.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/CodingAlertRequest.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/IdleStopPolicy.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/IdleStopPolicy.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeAgentHostAdmission.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeAgentHostAdmission.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeComponentUpdateRecovery.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeComponentUpdateRecovery.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeEngineReadiness.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeEngineReadiness.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleHeartbeat.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleHeartbeat.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleReturnNotification.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleReturnNotification.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleReturnNotificationHost.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleReturnNotificationHost.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleState.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleState.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleTimer.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleTimer.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerAdmission.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerAdmission.kt | 15fef911 | c4a340f4 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerOwnership.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerOwnership.kt | 15fef911 | 7455fb98 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerVisibility.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerVisibility.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeOrphanReaper.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeOrphanReaper.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeOrphanReaperHost.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeOrphanReaperHost.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcRace.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcRace.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcScan.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcScan.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcessList.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcessList.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcessTree.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcessTree.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeRecoveryBudget.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeRecoveryBudget.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeRuntimeOwnership.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeRuntimeOwnership.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRecipe.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRecipe.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRestoreEvent.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRestoreEvent.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeStrings.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeStrings.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeWorkLeaseHost.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeWorkLeaseHost.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PaseoStaleLock.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PaseoStaleLock.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthCapture.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthCapture.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthColdOwner.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthColdOwner.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthDeadline.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthDeadline.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthInventory.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthInventory.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthJson.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthJson.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthLock.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthLock.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthOtherOwners.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthOtherOwners.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthProbe.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthProbe.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthProcessTree.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthProcessTree.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthProjection.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthProjection.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthRequest.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthRequest.kt | e44b581a | 4a75d859 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthRuntime.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthRuntime.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentCheckOutput.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentCheckOutput.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentChecks.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentChecks.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentClaudeHome.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentClaudeHome.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentHost.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentHost.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentInventory.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentInventory.kt | cf86762f | 4a75d859 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentPaths.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentPaths.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentSignIn.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentSignIn.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneEngineAttestation.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneEngineAttestation.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneEngineNative.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneEngineNative.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/RestartBackoff.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/RestartBackoff.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ScheduledPromptPhoneLease.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ScheduledPromptPhoneLease.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ScheduledPromptPolicy.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ScheduledPromptPolicy.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ServiceDiagnostics.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ServiceDiagnostics.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ServiceNoticeGate.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ServiceNoticeGate.kt | 580803d4 | 580803d4 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupJob.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupJob.kt | 15fef911 | cd11845a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupPersistence.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupPersistence.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupRunner.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupRunner.kt | cf86762f | cd11845a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupScriptCompletion.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupScriptCompletion.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupService.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupService.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupStepRetry.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupStepRetry.kt | cf86762f | cd11845a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupWorkScopes.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupWorkScopes.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/StatusNotification.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/StatusNotification.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/StatusNotificationModel.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/StatusNotificationModel.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/StatusNotificationRenderer.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/StatusNotificationRenderer.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/WorkLeases.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/WorkLeases.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/AgentPriorityTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/AgentPriorityTest.kt | 580803d4 | 580803d4 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinGitObjectsTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinGitObjectsTest.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/ChildProcessLimitTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/ChildProcessLimitTest.kt | 580803d4 | 580803d4 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/IdleStopPolicyTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/IdleStopPolicyTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeAgentHostAdmissionTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeAgentHostAdmissionTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeComponentUpdateRecoveryTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeComponentUpdateRecoveryTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeEngineReadinessTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeEngineReadinessTest.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleHeartbeatTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleHeartbeatTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleReceiverDispatchTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleReceiverDispatchTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleReturnNotificationTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleReturnNotificationTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleStateTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleStateTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleTimerTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleTimerTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerAdmissionTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerAdmissionTest.kt | 15fef911 | c4a340f4 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerOwnershipTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerOwnershipTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerVisibilityTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeInstallerVisibilityTest.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeOrphanReaperTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeOrphanReaperTest.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcRaceTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcRaceTest.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcessListTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcessListTest.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcessTreeTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeProcessTreeTest.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeRecoveryBudgetTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeRecoveryBudgetTest.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeRuntimeOwnershipTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeRuntimeOwnershipTest.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRecipeTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRecipeTest.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRestoreEventTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRestoreEventTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeWorkLeaseHostTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeWorkLeaseHostTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/PaseoStaleLockTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/PaseoStaleLockTest.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentClaudeHomeTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentClaudeHomeTest.kt | cf86762f | cf86762f | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/RestartBackoffTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/RestartBackoffTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/ServiceDiagnosticsTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/ServiceDiagnosticsTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/ServiceNoticeGateTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/ServiceNoticeGateTest.kt | 580803d4 | 580803d4 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/SetupScriptCompletionTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/SetupScriptCompletionTest.kt | 65f2b037 | 65f2b037 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/SetupStepRetryTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/SetupStepRetryTest.kt | cf86762f | cd11845a | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/SetupWorkScopesTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/SetupWorkScopesTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/StatusNotificationModelTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/StatusNotificationModelTest.kt | e44b581a | e44b581a | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/WorkLeasesTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/WorkLeasesTest.kt | 15fef911 | 15fef911 | S runtime |
| native/android/linux/src/main/res/values/strings.xml | android/app/src/main/res/values/strings.xml | e44b581a | e44b581a | S runtime |
| native/android/linux/src/main/res/values-ar/strings.xml | android/app/src/main/res/values-ar/strings.xml | e44b581a | e44b581a | S runtime |

## Flutter 2.1.4 sync (native/sync-linux, 2026-11-06)

Copied from release/2.1.4 at cd11845a (the #117 fetch/unpack fallbacks, compat retry, link-following
security fix, app-made projects, password on stdin). BuiltinLinux.kt took these changes by hand, not as a copy.
Flutter 7455fb98 (cited before its rebase as 276c7bda) then moved InstallerOptions into NativeInstallerOwnership.kt;
native/s1-twins mirrored it, so the rows above now name the release-line commit each copy is synced to.

| Native path | Flutter source path | Source commit at copy | Last synced Flutter commit | Native owner |
|---|---|---|---|---|
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupAppFetch.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupAppFetch.kt | cd11845a | cd11845a | android-linux |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupAppUnpack.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupAppUnpack.kt | cd11845a | cd11845a | android-linux |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinNewProject.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinNewProject.kt | cd11845a | cd11845a | android-linux |
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ServerPasswordWrite.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ServerPasswordWrite.kt | cd11845a | cd11845a | android-linux |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/SetupAppFetchTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/SetupAppFetchTest.kt | cd11845a | cd11845a | android-linux |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/SetupAppUnpackTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/SetupAppUnpackTest.kt | cd11845a | cd11845a | android-linux |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinNewProjectTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinNewProjectTest.kt | cd11845a | cd11845a | android-linux |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/ServerPasswordWriteTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/ServerPasswordWriteTest.kt | cd11845a | cd11845a | android-linux |

## Flutter fix ledger: release line since the freeze (E20-S06, native/s1-twins, 2026-11-06)

Every Flutter commit on the release line (master, 2.1.1+58 to 2.1.4+62) since the freeze fork point `16170653d`
(the parent of the freeze snapshot `580803d4`), each with its native twin or why none is needed. There is no
`release/2.1.x` branch: master carries the 2.1.x releases. Merges are left out (`9c50bd5ed` merged `074dcda37` and
`eabb3e940`). `native/tool/twin_check.sh --fixes` lists any newer commit that has no row here; add the row when the twin
lands or is ruled out. A row matches by SHA, or by subject when the commit was rebased since.

<!-- fix-ledger since=16170653d -->
| Flutter commit | Subject | Native twin or not-applicable reason |
|---|---|---|
| ec2633fbd | docs(privacy): replace two screenshots with personal chat titles, strip phone EXIF, example values for the build server IP and phone serial [skip ci] | not applicable: docs and screenshots only |
| c4a340f43 | chore(release): OpenCode Mobile 2.1.1+58 [skip ci] | mixed, item by item: password on stdin → ServerPasswordWrite.kt (native/sync-linux, f7ebd3c51); app-made projects → BuiltinNewProject.kt (native/sync-linux); start probe password on stdin (lib/builtin/setup/components.dart) → :engine:runtime OpenCodeNativeInstall.kt and :engine:linuxsetup SetupScripts.kt (native/s1-twins); a failed launch clears its own unpermitted ticket → NativeInstallerAdmission.kt + BuiltinLinux.kt (native/s1-twins); Termux transfer and git repair token as curl config → :android:termux (already on base); deferred with AI Team (plan/01 §12 D6): #111 boundary proof steps, NativeGateHeader (Landlock-tier launcher warnings), PhoneBoundaryProofSteps, engine libraries, team start and phone_chat "chat status" gate; not applicable: #112 Termux PATH (Termux setup is migrate-out only, D7) |
| d6defab22 | chore(release): OpenCode Mobile 2.1.1+59 [skip ci] | not applicable: version bump and test fixtures |
| e58c85769 | chore(release): OpenCode Mobile 2.1.1+59, full quality gate [skip ci] | not applicable: detekt baseline, CI flags and a Dart file split (builtin_linux_types.dart), no behaviour change |
| 8302de537 | test(coverage): read the engine status fields from their new file [skip ci] | not applicable: Flutter coverage test only |
| 074dcda37 | feat(github): add repository fetching functionality and UI integration | #114 → native/sync-features: :engine:git GitHub repository list (dc61bb704), :feature:git remote picker (1a5d8ec46) |
| eabb3e940 | fix(git): GitHub repo picker on kit parts, localized, with widget tests [skip ci] | #114 → native/sync-features: :feature:git remote picker on kit parts, English and Arabic (1a5d8ec46) |
| 4a75d8593 | fix(agents): add Antigravity CLI setup and sign-in [skip ci] | #116 → native/sync-features (38e4fc4f6, 6b97e4a1a, 7771dab9d, c62c92bf8, 63915c026); PhoneAgentAuthRequest.kt and PhoneAgentInventory.kt are byte-equal twins synced to 4a75d859 |
| b7ef1c8b4 | feat(chats): an Add an agent card on New conversation [skip ci] | native/sync-features: :feature:chats Add an agent leads New conversation agents (f649c8630) |
| 46a381155 | chore(release): OpenCode Mobile 2.1.2+60 [skip ci] | not applicable: version bump and changelog |
| 59e2c99da | test(agents): OpenCode alone shows its card beside Add an agent [skip ci] | not applicable: Flutter widget test; native/sync-features carries its own :feature:chats test |
| fb8827345 | test(goldens): agent screens with Antigravity and the Add an agent card [skip ci] | not applicable: Flutter goldens |
| 0c92c52e4 | docs(qa): release 2.1.2 device sanity record [skip ci] | not applicable: QA record |
| 99eb4c369 | fix(agents): each agent's card and About line say what its maker says [skip ci] | native/sync-features: :ui:words agent maker copy (cb96a77d0) |
| e876d828f | test(kit): register KitAddAgentCard with the kit gates [skip ci] | not applicable: Flutter kit gate registration (native kit gates are :ui:kit tests) |
| cda36dfcc | test(goldens): the issue-116 Add an agent shot shows the new agent lines [skip ci] | not applicable: Flutter golden |
| d4c8361a1 | fix(setup): hand a crashing curl's download to the app (#117) [skip ci] | #117 → native/sync-linux: SetupAppFetch.kt, SetupJob.kt, SetupRunner.kt (9c1e42325); script side in the native SetupPrelude copies (oc_app_fetch) |
| 2986548c3 | test(setup): refresh the six aiteam script snapshots for the prelude change [skip ci] | not applicable: AI Team script snapshot hashes (AI Team deferred) |
| 8bc90469d | docs(qa): issue 117 evidence, curl crash fallback proven on the emulator [skip ci] | not applicable: QA evidence |
| a91bd8d16 | chore(release): OpenCode Mobile 2.1.3+61 [skip ci] | not applicable: version bump and changelog |
| 68aac8076 | docs(qa): release sanity for 2.1.3+61, normal-curl path and setup neighbours [skip ci] | not applicable: QA record |
| 1bbc4a3f5 | fix(setup): clear detekt findings and the native runner harness for the app download [skip ci] | native/sync-linux: SetupAppFetch.kt and SetupJob.kt copied at cd11845a, which includes this |
| 70c877432 | feat(git): initialize a project and publish it to a new GitHub repository (#118) [skip ci] | #118 → native/sync-features: :engine:git create and git init (dc61bb704), :feature:git Initialize and Publish (1a5d8ec46) |
| 1adb2e17c | fix(setup): unpack in the app when tar crashes, rerun a segfaulted step in compatibility mode (#117) [skip ci] | #117 → native/sync-linux: SetupAppUnpack.kt, SetupStepRetry.kt, SetupRunner.kt, BuiltinLinux.kt compat mode (9c1e42325); script side in the native SetupPrelude copies (oc_app_unpack) |
| 96da82d87 | docs(qa): issue 117 unpack fallback evidence and emulator proof script [skip ci] | not applicable: QA evidence |
| c325c1de8 | docs(qa): correct the setup_scripts test count [skip ci] | not applicable: QA record |
| f6488ba34 | docs(qa): issue 117 emulator proof of the unpack fallback and the compatibility retry [skip ci] | not applicable: QA evidence |
| c7b6e2fa5 | build(linux): bundle Termux proot 5.1.107.102 (was 5.1.107.94) [skip ci] | native/sync-linux: :android:linux jniLibs libproot, loader and libtalloc are byte-equal to Flutter 2.1.4 (a74dc6878) |
| d0ce8f400 | docs(qa): issue 117 proof with the new proot, from-scratch setup and app unpacking [skip ci] | not applicable: QA evidence |
| 5a71d8aa9 | chore(l10n): regenerate localizations after rebasing onto the GitHub publish merge [skip ci] | not applicable: generated Flutter localizations |
| 99a27d6a1 | build: drop the Linux and Windows desktop builds [skip ci] | pending, :ui:words (l10n-owner): e7SettingsAlphaBody in values, values-ar and values-zh still says desktop builds are experimental; Flutter now says "OpenCode Mobile is an Android app." — proposal in the native/s1-twins report. Desktop packaging, workflows and docs: not applicable |
| 9e8b8aa54 | docs(readme): current agents, Git and Inbox, Android only, native Kotlin rewrite coming [skip ci] | not applicable: README |
| 5bab9d150 | fix(setup): app downloads and unpacks never follow a link out of built-in Linux [skip ci] | native/sync-linux: SetupAppFetch.kt, SetupAppUnpack.kt, SetupJob.kt, SetupRunner.kt copied at cd11845a (9c1e42325) |
| cd11845a6 | chore(release): OpenCode Mobile 2.1.4+62 [skip ci] | not applicable: version bump, changelog and release notes |
| 7455fb986 | test: About says Android only; native harnesses follow InstallerOptions [skip ci] | native/s1-twins: InstallerOptions moved into NativeInstallerOwnership.kt (byte-equal twin); the Flutter kotlinc harness stubs and About test are not applicable |
| 2dec68a25 | test: hygiene and Settings About goldens follow the desktop drop [skip ci] | not applicable: Flutter goldens and issue template |
| 902852599 | fix(quota): Antigravity 5-hour and weekly limits from its own CLI (#125) | native/s22-twins: engine/integrations-usage AntigravityQuota.kt (client, script, parser, `_paReadUsage` merge) wired into androidApp PhoneUsageReader.kt |
| 1ecab8e8b | feat(updater): in-app updater for GitHub releases, checksum-verified (#126) | native/s22-twins: engine/updates AppRelease.kt + AppUpdates.kt (downloads-only URLs, fail closed without SHA256SUMS, fixed file name, release builds only, delayed cancellable check, start-up prompt) and feature/updates UpdatePromptSheet; native's own installer (AndroidUpdates.kt) already checks package, signer and versionCode |
| d4d989966 | fix(quota): import the Antigravity quota client where the usage hook uses it (#127) | not applicable: Flutter import fix; the native wiring is PhoneUsageReader.kt (native/s22-twins) |
| 9dff28fe2 | refactor(updater): one function per installer call, so detekt passes (#131) [skip ci] | not applicable: Flutter MethodChannel installer; native installs through PackageInstaller in AndroidUpdates.kt |
| 629884c8c | feat(ui): a visible line between conversations on dark screens (#129) | native/twins-1109: KitRowPanel(prominentDividers) draws the KitDivider prominent rule (1 dp, tertiary ink at 30 %, style rules kept) between the home's Right now and Recent rows; KitRowPanelProminentTest |
| 5ec844e47 | feat(usage): display logged-in account name on subscription details view (#136) | already native (d119dfcfb, native/s22-twins): the probe's ACCOUNT_EMAIL line, strict parser, Details row; closed by native/twins-1109 as done |
| 39e15ae77 | feat(files): open phone files in Android, say why a file will not open [skip ci] | native/twins-1109: most of it was already native (viewer Open with and Install, chat file: links, phone path kinds); added the plain reasons (MediaProblem FileTooBig, FileMissing, FileDenied; Paseo refusals sorted by words; viewer failed state in en and ar) and android/platform LocalFileGate (shared storage only, no Android/ folder, symlinks and .. resolved) for both openers. The `.localfiles` provider stays pending in sec-pending.txt |
| c1e564d17 | feat(pairing): QR scanner without Google ML Kit; F-Droid build switches [skip ci] | already native: CameraX + zxing-cpp (:android:camera), no ML Kit; QrScannerGoogleFreeTest keeps it so. F-Droid switches not taken: the native build ships Firebase telemetry and the Play services-free build needs its own flavour (separate slice) |

package io.github.eslamasabry.oc.engine.analytics

/** One instance of every event class. [specOf] is an exhaustive `when`: a new event class fails compilation here. */
internal object EventSamples {
    val all: List<AnalyticsEvent> =
        listOf(
            AppStart(StartKind.Cold, DurationBucket.Under1S),
            AppUpdated(VersionCode(57)),
            MigrationDone(MigrationOutcome.Done, MigrationStep.None, CountBucket.Zero, DurationBucket.Under3S),
            BackendConnected(ServerKind.OpenCode2, AddressClass.Tailnet),
            UpdateOutcome(UpdateResult.Cancelled),
            CrashSeen(CrashCategory.Kotlin, fatal = true),
            FreezeSeen(FreezeKind.NotResponding, DurationBucket.Under10S, foreground = true),
            ErrorSeen(ErrorCategory.Network),
            DailyUse(CountBucket.TwoToFive, CountBucket.One, CountBucket.SixToTwenty),
            ServerKinds.of(mapOf(ServerKind.OpenCode2 to 2)),
            AgentKinds.of(mapOf(AgentKind.ClaudeCode to 1)),
            ScreenOpened(ScreenId.Chat),
            FeatureUsed(UsedFeature.Voice),
            FlowStepped(FlowId.AddServer, FlowStep.Completed),
            ActionTimed(TimedAction.OpenChat, DurationBucket.Under300Ms),
            SettingChanged(SettingId.Theme),
        )

    fun specOf(event: AnalyticsEvent): EventSpec =
        when (event) {
            is AppStart -> AppStart.SPEC
            is AppUpdated -> AppUpdated.SPEC
            is MigrationDone -> MigrationDone.SPEC
            is BackendConnected -> BackendConnected.SPEC
            is UpdateOutcome -> UpdateOutcome.SPEC
            is CrashSeen -> CrashSeen.SPEC
            is FreezeSeen -> FreezeSeen.SPEC
            is ErrorSeen -> ErrorSeen.SPEC
            is DailyUse -> DailyUse.SPEC
            is ServerKinds -> ServerKinds.SPEC
            is AgentKinds -> AgentKinds.SPEC
            is ScreenOpened -> ScreenOpened.SPEC
            is FeatureUsed -> FeatureUsed.SPEC
            is FlowStepped -> FlowStepped.SPEC
            is ActionTimed -> ActionTimed.SPEC
            is SettingChanged -> SettingChanged.SPEC
        }
}

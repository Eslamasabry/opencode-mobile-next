package io.github.eslamasabry.oc.migration.readers.model

/** Who a key belongs to (plan/05 §3.3). */
public enum class KeyScope { Profile, Owner, App, Shared, Other }

/** The type the Dart (or Kotlin) writer uses for the key. */
public enum class ExpectedType { Bool, Int64, Dbl, Str, StrList, StrOrList }

/** plan/05 §7.8 handling codes. */
public enum class Handling {
    /** adopted in place, same format */
    A,
    /** adopted, also read or written by Kotlin in an `:android:*` module */
    AK,
    /** touched by runner transform L1/L2/L3 */
    L1, L2, L3,
    /** retired (removed by L1) */
    R,
    /** ignored, removed by the v3 cleanup */
    I,
    /** deferred feature, untouched until its port */
    D,
    /** dropped feature, untouched, removed by v3 */
    X,
    /** moved into Room by `native.migration.v2` */
    DbAtV2,
    /** not expected on Android (desktop-only) */
    N,
    /** new native key */
    New,
}

/** How the rest of the key after the fixed pattern is shaped. */
public enum class IdForm {
    /** The whole key is fixed. */
    Exact,
    /** `pattern + id`, where id may also be `<phoneId>.agents` (agent-backend pseudo-profile). */
    Suffix,
    /** `pattern + id + "." + more` (`oc.promptStash.<id>.<stashId>`, `oc.orchestration.<id>.<field>`). */
    IdThenMore,
    /** `pattern + target + "." + (id | "app")` (`oc.draft.<target>.<id|app>`; target may contain dots). */
    LastSegment,
    /** `pattern + something that is not a profile id` (agent id, job id, lease key). */
    Opaque,
}

/** What the id in the key names. */
public enum class IdKind { None, Profile, Owner, Team, Connection, Agent, Job, Lease }

/**
 * Every SharedPreferences key shape the Flutter app (Dart and Kotlin) writes into
 * `FlutterSharedPreferences.xml`, from `map/state-data.md` §4 and plan/05 Appendix A (A.1 ledger keys, A.2
 * Kotlin-only keys, A.3 keys outside `oc.`, A.4 new native keys). Logical names (no `flutter.` prefix).
 *
 * `FlutterKeyRegistryTest` checks this list against the Flutter repo's own gate,
 * `test/fixtures/coverage/settings_preferences_ledger.json`, so a key added on the Flutter side fails the
 * build here until it gets a row.
 */
public enum class FlutterKey(
    public val pattern: String,
    public val form: IdForm,
    public val type: ExpectedType,
    public val scope: KeyScope,
    public val idKind: IdKind,
    public val ownerModule: String,
    public vararg val handling: Handling,
) {
    // ---- A.1 ledger keys (Dart writers) ----
    A2aTasks("oc.a2aTasks.", IdForm.Opaque, ExpectedType.Str, KeyScope.Other, IdKind.Agent, "A2A (dropped)", Handling.X),
    ActiveProfile("oc.activeProfile", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:profiles", Handling.AK),
    Agent("oc.agent.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    AgentFeed("oc.agentFeed.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Owner, ":engine:agents", Handling.A, Handling.DbAtV2),
    AgentHandoffs("oc.agentHandoffs.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:agents", Handling.A),
    AgentInstall("oc.agentInstall.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Owner, ":engine:agents", Handling.A),
    AgentModel("oc.agentModel.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:agents", Handling.A),
    AgentPhoneGate("oc.agentPhoneGate.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Owner, ":engine:agents", Handling.A),
    AgentSignedOut("oc.agentSignedOut.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:agents", Handling.A),
    AgentTurnFailures("oc.agentTurnFailures.", IdForm.Suffix, ExpectedType.StrList, KeyScope.Profile, IdKind.Profile, ":engine:agents", Handling.A),
    Appearance("oc.appearance", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    AppearanceAccent("oc.appearanceAccent", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    AppearanceGuide("oc.appearanceGuide", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    AppLocale("oc.appLocale", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.AK),
    AttentionTile("oc.attentionTile", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:attention", Handling.AK),
    AutoApprove("oc.autoApprove.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:requests", Handling.A),
    AutomaticActivity("oc.automaticActivity.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:settings", Handling.A),
    Automation("oc.automation.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:settings", Handling.AK),
    BackgroundPauseDismissed("oc.backgroundPauseDismissed", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:attention", Handling.A),
    Budgets("oc.budgets.", IdForm.Suffix, ExpectedType.Str, KeyScope.Other, IdKind.Profile, "-", Handling.R, Handling.L1),
    BuiltinRecovery("oc.builtinRecovery.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:runtime", Handling.AK),
    BuiltinServerOwner("oc.builtinServerOwner", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:runtime", Handling.AK),
    ByoHost("oc.byoHost.", IdForm.Suffix, ExpectedType.Str, KeyScope.Other, IdKind.Profile, ":engine:integrations", Handling.A),
    CertificatePin("oc.certificatePin.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:profiles", Handling.A),
    ChatAgent("oc.chatAgent.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:agents", Handling.A),
    ChatListShown("oc.chatListShown.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    ChatNoticesSeen("oc.chatNoticesSeen.", IdForm.Suffix, ExpectedType.StrList, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    ChatTail("oc.chatTail.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A, Handling.DbAtV2),
    CleartextOk("oc.cleartextOk.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:profiles", Handling.A),
    ConsumptionBudgets("oc.consumptionBudgets.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:integrations", Handling.A),
    ContextUsage("oc.contextUsage.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    ConversationTitles("oc.conversationTitles.", IdForm.Suffix, ExpectedType.StrList, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    DefaultNotices("oc.defaultNotices.", IdForm.Suffix, ExpectedType.StrList, KeyScope.Profile, IdKind.Profile, ":engine:settings", Handling.A),
    DefaultProject("oc.defaultProject.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:settings", Handling.A),
    DevFactsbook("oc.dev.factsbook", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, "FactsBook (deferred)", Handling.D),
    DevelopmentServices("oc.developmentServices.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:project", Handling.A),
    Draft("oc.draft.", IdForm.LastSegment, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:composer", Handling.A),
    DraftAttachmentVault("oc.draftAttachmentVault", IdForm.Exact, ExpectedType.Bool, KeyScope.Shared, IdKind.None, ":engine:composer", Handling.A),
    EffectsActivityGlow("oc.effectsActivityGlow", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    EffectsBackgroundOn("oc.effectsBackgroundOn", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    EffectsCelebrations("oc.effectsCelebrations", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    EffectsCorners("oc.effectsCorners", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    EffectsDensity("oc.effectsDensity", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    EffectsDepth("oc.effectsDepth", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.I),
    EffectsEdges("oc.effectsEdges", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.I),
    EffectsGlowColours("oc.effectsGlowColours", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.I),
    EffectsGlowSpeed("oc.effectsGlowSpeed", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.I),
    EffectsGlowStyle("oc.effectsGlowStyle", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    EffectsLiveBackground("oc.effectsLiveBackground", IdForm.Exact, ExpectedType.Bool, KeyScope.Other, IdKind.None, "-", Handling.R, Handling.L1),
    EffectsLiveHeldByStyle("oc.effectsLiveHeldByStyle", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    EffectsLiveIntensity("oc.effectsLiveIntensity", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    EffectsLiveStyle("oc.effectsLiveStyle", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    EffectsMotion("oc.effectsMotion", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    EffectsSurfaceStyle("oc.effectsSurfaceStyle", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    ExternalAgents("oc.externalAgents", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, "A2A (dropped)", Handling.X),
    FactsbookNodeStopped("oc.factsbook-node.stopped", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, "FactsBook (deferred)", Handling.D),
    FactsbookNode("oc.factsbookNode.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, "FactsBook (deferred)", Handling.D),
    FirstRunNotifyAsk("oc.firstRun.notifyAsk", IdForm.Exact, ExpectedType.Str, KeyScope.Other, IdKind.None, ":engine:settings", Handling.A),
    FirstRunState("oc.firstRun.state", IdForm.Exact, ExpectedType.Str, KeyScope.Other, IdKind.None, ":engine:settings", Handling.A),
    FreeModelNoteDismissed("oc.freeModelNoteDismissed.", IdForm.Suffix, ExpectedType.StrList, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    GenuiCovered("oc.genui.covered.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:requests", Handling.A),
    GenuiEnabled("oc.genui.enabled.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:requests", Handling.A),
    GenuiJournal("oc.genui.journal.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:requests", Handling.A),
    Glass("oc.glass", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.I),
    GlassLiquidActive("oc.glassLiquidActive", IdForm.Exact, ExpectedType.Bool, KeyScope.Other, IdKind.None, "-", Handling.R, Handling.L1),
    GlassLiquidOffUntil("oc.glassLiquidOffUntil", IdForm.Exact, ExpectedType.Str, KeyScope.Other, IdKind.None, "-", Handling.R, Handling.L1),
    GlassLiquidStrikes("oc.glassLiquidStrikes", IdForm.Exact, ExpectedType.Int64, KeyScope.Other, IdKind.None, "-", Handling.R, Handling.L1),
    InFlowConsent("oc.inFlowConsent.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:settings", Handling.A),
    KeepLiveInBackground("oc.keepLiveInBackground", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:attention", Handling.AK),
    KeptQueuedPrompts("oc.keptQueuedPrompts", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:delivery", Handling.A, Handling.DbAtV2),
    LastProject("oc.lastProject.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    LinuxResetPending("oc.linuxReset.pending", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:runtime", Handling.A),
    Location("oc.location.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    MainFeed("oc.mainFeed.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:agents", Handling.A, Handling.DbAtV2),
    ManagedServerRecovery("oc.managedServerRecovery.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, "Termux (deferred)", Handling.D),
    MobileDownloadConsent("oc.mobileDownloadConsent.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:media", Handling.A),
    Model("oc.model.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    ModelExplicit("oc.modelExplicit.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    ModelLibrary("oc.modelLibrary.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    ModelNames("oc.modelNames.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    MonitorAlerts("oc.monitorAlerts.", IdForm.Suffix, ExpectedType.StrList, KeyScope.Profile, IdKind.Profile, ":engine:attention", Handling.A),
    MonitorBusy("oc.monitorBusy.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:attention", Handling.A),
    MonitorRoutes("oc.monitorRoutes.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:attention", Handling.A),
    NotifyCheckInAfterMinutes("oc.notify.checkInAfterMinutes", IdForm.Exact, ExpectedType.Int64, KeyScope.App, IdKind.None, ":engine:attention", Handling.A, Handling.L2),
    NotifyFinishedRuns("oc.notify.finishedRuns", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:attention", Handling.A),
    NotifyQuietEnd("oc.notify.quietEnd", IdForm.Exact, ExpectedType.Int64, KeyScope.App, IdKind.None, ":engine:attention", Handling.A, Handling.L2),
    NotifyQuietStart("oc.notify.quietStart", IdForm.Exact, ExpectedType.Int64, KeyScope.App, IdKind.None, ":engine:attention", Handling.A, Handling.L2),
    NotifyQuotaAlerts("oc.notify.quotaAlerts", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:attention", Handling.A, Handling.L2),
    NotifyRequests("oc.notify.requests", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:attention", Handling.A),
    NotifyVersion("oc.notify.version", IdForm.Exact, ExpectedType.Int64, KeyScope.App, IdKind.None, ":engine:attention", Handling.L2),
    NotifyWifiOnly("oc.notify.wifiOnly", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:attention", Handling.A, Handling.L2),
    NotifyRules("oc.notifyRules.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:attention", Handling.A, Handling.L2),
    NudgesFirstReplySeen("oc.nudges.firstReplySeen", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    NudgesProjects("oc.nudges.projects", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    NudgesShown("oc.nudges.shown", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    OfflineQueue("oc.offlineQueue", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:delivery", Handling.A, Handling.DbAtV2),
    OlderDraftsBackupV1("oc.olderDraftsBackupV1.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:composer", Handling.I),
    Orchestration("oc.orchestration.", IdForm.IdThenMore, ExpectedType.StrOrList, KeyScope.Profile, IdKind.Profile, ":engine:team (deferred)", Handling.D),
    PaseoUsage("oc.paseoUsage.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:integrations", Handling.A),
    PendingAuth("oc.pendingAuth.", IdForm.Suffix, ExpectedType.Str, KeyScope.Other, IdKind.Profile, ":engine:integrations", Handling.A),
    PendingCommands("oc.pendingCommands.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:delivery", Handling.A, Handling.DbAtV2),
    PendingPromptPhoto("oc.pendingPromptPhoto", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:composer", Handling.A),
    PermissionConsent("oc.permissionConsent.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:requests", Handling.A),
    PhoneAgentGateMigration("oc.phoneAgentGateMigration.", IdForm.Suffix, ExpectedType.StrList, KeyScope.Profile, IdKind.Owner, ":engine:agents", Handling.A),
    PhoneAgentOwner("oc.phoneAgentOwner.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:agents", Handling.AK),
    PhoneAgentsUsed("oc.phoneAgentsUsed.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:agents", Handling.A),
    PhoneServerStopped("oc.phoneServerStopped.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:runtime", Handling.A),
    PhraseChips("oc.phraseChips.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:composer", Handling.A),
    PhraseChipsEnabled("oc.phraseChips.enabled", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:composer", Handling.A),
    PinnedShortcuts("oc.pinnedShortcuts", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:attention", Handling.AK),
    Profiles("oc.profiles", IdForm.Exact, ExpectedType.Str, KeyScope.Other, IdKind.None, ":engine:profiles", Handling.AK),
    PromptHistory("oc.promptHistory.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:composer", Handling.A, Handling.DbAtV2),
    PromptStash("oc.promptStash.", IdForm.IdThenMore, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:composer", Handling.A, Handling.DbAtV2),
    ProviderRuntimeRefresh("oc.providerRuntimeRefresh.v1.", IdForm.IdThenMore, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    ProviderRuntimeUnloadable("oc.providerRuntimeUnloadable.", IdForm.IdThenMore, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    QuotaCollector("oc.quotaCollector.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:integrations", Handling.A),
    QuotaMonitor("oc.quotaMonitor.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:integrations", Handling.A, Handling.L2),
    ReadAloudConsent("oc.readAloudConsent", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:media", Handling.A),
    ReaderPreferences("oc.readerPreferences.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:settings", Handling.A),
    ReadStateConsentMigration("oc.readStateConsentMigration.v1", IdForm.Exact, ExpectedType.Str, KeyScope.Other, IdKind.None, ":engine:connection", Handling.L3),
    RecentLocations("oc.recentLocations.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    ReplySpeedInApp("oc.replySpeed.inApp", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:runtime", Handling.A),
    ReturnBrief("oc.returnBrief.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    SavedPromptMigrationV1("oc.savedPromptMigrationV1.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:composer", Handling.A),
    ScheduleDeleted("oc.scheduleDeleted.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:schedule", Handling.AK),
    ScheduledPrompts("oc.scheduledPrompts.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:schedule", Handling.AK),
    SessionAgents("oc.sessionAgents.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    SessionDisplayModels("oc.sessionDisplayModels.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    SessionDrafts("oc.sessionDrafts", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:composer", Handling.A, Handling.DbAtV2),
    SessionInventory("oc.sessionInventory.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A, Handling.DbAtV2),
    SessionLinkBinding("oc.sessionLinkBinding.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    SessionModels("oc.sessionModels.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    SessionPins("oc.sessionPins.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    SessionViews("oc.sessionViews.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    SetupAudit("oc.setupAudit.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:integrations", Handling.A),
    SetupRegistry("oc.setupRegistry.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:integrations", Handling.A),
    SharedProjects("oc.sharedProjects.", IdForm.Suffix, ExpectedType.StrList, KeyScope.Profile, IdKind.Profile, ":engine:runtime", Handling.A),
    ShareSessionViewsLegacy("oc.shareSessionViews", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:connection", Handling.L3),
    ShareSessionViews("oc.shareSessionViews.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A, Handling.L3),
    ShareSessionViewsDefault("oc.shareSessionViewsDefault.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A, Handling.L3),
    ShareSessionViewsOrigin("oc.shareSessionViewsOrigin.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A, Handling.L3),
    SideFeed("oc.sideFeed.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A, Handling.DbAtV2),
    StashAttachmentVault("oc.stashAttachmentVault.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:composer", Handling.A),
    Tailscale("oc.tailscale.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:profiles", Handling.A),
    TeamEditorDrafts("oc.teamEditorDrafts.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:team (deferred)", Handling.D),
    TeamEngineDeleted("oc.teamEngineDeleted.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:team (deferred)", Handling.D),
    TeamMigrationKept("oc.teamMigrationKept.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:team (deferred)", Handling.D),
    TeamModel("oc.teamModel.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:team (deferred)", Handling.D),
    TeamRoles("oc.teamRoles.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:team (deferred)", Handling.D),
    TeamTaskRoles("oc.teamTaskRoles.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:team (deferred)", Handling.D),
    TeamWorkerStart("oc.teamWorkerStart.", IdForm.Suffix, ExpectedType.Int64, KeyScope.Profile, IdKind.Profile, ":engine:team (deferred)", Handling.D),
    TeamWorkspace("oc.teamWorkspace.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:team (deferred)", Handling.D),
    TermuxMigration("oc.termuxMigration.", IdForm.Opaque, ExpectedType.Str, KeyScope.Other, IdKind.Job, "Termux (deferred)", Handling.D),
    TermuxMigrationOffer("oc.termuxMigrationOffer.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, "Termux (deferred)", Handling.D),
    TextSize("oc.textSize", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    ThemePack("oc.themePack", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    ThermalGuard("oc.thermalGuard", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:runtime", Handling.A),
    ThermalPause("oc.thermalPause.", IdForm.Suffix, ExpectedType.Str, KeyScope.Other, IdKind.Team, ":engine:runtime", Handling.A),
    Tips("oc.tips", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    TranscriptReasoningExpanded("oc.transcript.reasoningExpanded", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    TranscriptTimestampsVisible("oc.transcript.timestampsVisible", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:settings", Handling.A),
    UsageAlerts("oc.usageAlerts.v1", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:integrations", Handling.A),
    UsageAlertSeen("oc.usageAlertSeen.v1", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:integrations", Handling.A),
    UsageComputerReader("oc.usageComputerReader.", IdForm.Suffix, ExpectedType.Bool, KeyScope.Profile, IdKind.Profile, ":engine:integrations", Handling.A),
    UsageLast("oc.usageLast.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Connection, ":engine:integrations", Handling.A),
    UsageWidget("oc.usageWidget.v1", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:attention", Handling.AK),
    Variant("oc.variant.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),
    WidgetFollowup("oc.widgetFollowup.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:attention", Handling.AK),
    WidgetSessions("oc.widgetSessions", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:attention", Handling.AK),
    WidgetTarget("oc.widgetTarget.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:attention", Handling.AK),
    WidgetUsage("oc.widgetUsage.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:attention", Handling.AK),
    WidgetUsageSubs("oc.widgetUsageSubs", IdForm.Exact, ExpectedType.Str, KeyScope.Shared, IdKind.None, ":engine:attention", Handling.AK),
    WithdrawnPrompts("oc.withdrawnPrompts.", IdForm.Suffix, ExpectedType.StrList, KeyScope.Profile, IdKind.Profile, ":engine:connection", Handling.A),

    // ---- A.2 keys written only by Kotlin (same file) ----
    WidgetStyle("oc.widgetStyle.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Owner, ":android:widgets", Handling.AK),
    WidgetActions("oc.widgetActions.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":android:widgets", Handling.AK),
    ScheduleIndex("oc.scheduleIndex.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":android:schedule", Handling.AK),
    ScheduleClaims("oc.scheduleClaims.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":android:schedule", Handling.AK),
    ScheduleReceipts("oc.scheduleReceipts.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":android:schedule", Handling.AK),
    SchedulePhoneLease("oc.schedulePhoneLease.", IdForm.Opaque, ExpectedType.Str, KeyScope.App, IdKind.Lease, ":android:schedule", Handling.AK),

    // ---- A.3 keys outside the oc. namespace ----
    VoiceSelectedPack("voice.selected_pack", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:media", Handling.A),
    VoiceLanguage("voice.language", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":engine:media", Handling.A),
    VoiceAutoSend("voice.auto_send", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:media", Handling.A),
    VoiceReadAloudNatural("voice.read_aloud_natural", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":engine:media", Handling.A),
    DesktopWindowState("desktop_window_state_v1", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, "desktop phase", Handling.N),

    // ---- A.4 new native keys ----
    SecretReentry("oc.secretReentry.", IdForm.Suffix, ExpectedType.Str, KeyScope.Profile, IdKind.Profile, ":engine:profiles", Handling.New),
    NativeFlutterReadable("oc.native.flutterReadable", IdForm.Exact, ExpectedType.Bool, KeyScope.App, IdKind.None, ":android:migration", Handling.New),
    NativeReturnedAt("oc.native.returnedAt", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, "R1 rollback build", Handling.New),
    NativeMovedToDb("oc.native.movedToDb", IdForm.Exact, ExpectedType.Str, KeyScope.App, IdKind.None, ":android:migration", Handling.New),
    ;

    public val isNew: Boolean get() = Handling.New in handling

    public companion object {
        /**
         * Ledger names that are not stored preferences (route names, server metadata fields, a desktop keyring
         * probe). Listed so the ledger cross-check can account for them.
         */
        public val NOT_STORED: Set<String> = setOf("oc.chat", "oc.mobile.permissionRequests", "oc.session_id")

        /** Non-preference names the ledger also lists: an AndroidKeyStore alias family. */
        public val KEYSTORE_ALIAS_PREFIXES: Set<String> = setOf("oc.byoHostSsh.")

        private val exact: Map<String, FlutterKey> = entries.filter { it.form == IdForm.Exact }.associateBy { it.pattern }
        private val prefixed: List<FlutterKey> = entries.filter { it.form != IdForm.Exact }.sortedByDescending { it.pattern.length }

        internal fun exactMatch(key: String): FlutterKey? = exact[key]
        internal fun prefixCandidates(key: String): List<FlutterKey> = prefixed.filter { key.startsWith(it.pattern) && key.length > it.pattern.length }
    }
}

/**
 * Secure-storage names (plan/05 Appendix B, map/state-data.md §4.3). These live in
 * `FlutterSecureStorage.xml`, never in the preferences file.
 */
public enum class SecretKey(public val pattern: String, public val form: IdForm, public val idKind: IdKind, public val ownerModule: String) {
    Password("pw.", IdForm.Suffix, IdKind.Profile, ":engine:profiles"),
    CodexToken("oc.codexToken.", IdForm.Suffix, IdKind.Profile, ":engine:profiles"),
    TeamEngineAuth("oc.teamEngineAuth.", IdForm.Suffix, IdKind.Profile, ":engine:team (deferred)"),
    QuotaToken("oc.quotaToken.", IdForm.Suffix, IdKind.Profile, ":engine:integrations"),
    GithubToken("oc.githubToken.", IdForm.Suffix, IdKind.Profile, ":engine:project"),
    ByoHostSecrets("oc.byoHostSecrets.", IdForm.Suffix, IdKind.Profile, ":engine:integrations"),
    AgentHostSecret("oc.agentHostSecret.", IdForm.Suffix, IdKind.Owner, ":engine:agents"),
    Factsbook("oc.factsbook.", IdForm.Suffix, IdKind.Profile, "FactsBook (deferred)"),
    FactsbookOwner("oc.factsbookOwner.", IdForm.Suffix, IdKind.Profile, "FactsBook (deferred)"),
    FactsbookOutbox("oc.factsbookOutbox.", IdForm.Suffix, IdKind.Profile, "FactsBook (deferred)"),
    FactsbookNodePassphrase("oc.factsbook-node.passphrase", IdForm.Exact, IdKind.None, "FactsBook (deferred)"),
    A2aBearer("oc.a2aBearer.", IdForm.Opaque, IdKind.Agent, "A2A (dropped)"),
    OrchestrationGrant("oc.orchestration.", IdForm.IdThenMore, IdKind.Profile, "reserved, never written"),
    SecureProbe("oc.secure.probe", IdForm.Exact, IdKind.None, "desktop phase"),
    ;

    public companion object {
        /** Prefixes `resetSavedSignIns` deletes (map/state-data.md §4.3). */
        public val RESET_SAVED_SIGN_INS: List<SecretKey> =
            listOf(Password, CodexToken, TeamEngineAuth, QuotaToken, GithubToken, AgentHostSecret, ByoHostSecrets)
    }
}

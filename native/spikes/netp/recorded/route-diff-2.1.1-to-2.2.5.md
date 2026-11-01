Operations: 144 -> 169; schemas: 290 -> 363

### Added operations

- `DELETE /api/experimental/artifacts/{artifactID}`
- `DELETE /api/experimental/fs/watch`
- `DELETE /api/experimental/schedule/{scheduleID}`
- `GET /api/experimental/artifacts`
- `GET /api/experimental/artifacts/providers`
- `GET /api/experimental/artifacts/{artifactID}`
- `GET /api/experimental/artifacts/{artifactID}/view/*`
- `GET /api/experimental/client-mcp`
- `GET /api/experimental/media/{hash}`
- `GET /api/experimental/schedule`
- `GET /api/experimental/schedule/{scheduleID}`
- `GET /api/experimental/usage/limits`
- `GET /api/experimental/voice/models`
- `GET /api/plugin/gui`
- `PATCH /api/experimental/artifacts/{artifactID}`
- `POST /api/debug/heap-dump`
- `POST /api/experimental/artifacts`
- `POST /api/experimental/client-mcp/token`
- `POST /api/experimental/fs/watch`
- `POST /api/experimental/generate/decisions`
- `POST /api/experimental/generate/image`
- `POST /api/experimental/generate/text`
- `POST /api/experimental/schedule`
- `POST /api/experimental/schedule/{scheduleID}/pause`
- `POST /api/experimental/schedule/{scheduleID}/resume`

### Removed operations

- none

### Changed operations (same method + path, different definition)

- `DELETE /api/experimental/persistent-pty/{ptyID}`
- `DELETE /api/experimental/session/{sessionID}/instructions/entries/{key}`
- `DELETE /api/permission/saved/{id}`
- `GET /api/agent`
- `GET /api/event`
- `GET /api/experimental/migration/v1`
- `GET /api/experimental/persistent-pty/{ptyID}`
- `GET /api/experimental/persistent-pty/{ptyID}/connect`
- `GET /api/experimental/persistent-pty/{ptyID}/snapshot`
- `GET /api/experimental/session/{sessionID}/instructions/entries`
- `GET /api/experimental/session/{sessionID}/log`
- `GET /api/experimental/session/{sessionID}/terminal`
- `GET /api/experimental/session/{sessionID}/terminal/read`
- `GET /api/model`
- `GET /api/project`
- `GET /api/shell/{id}/output`
- `GET /api/vcs/diff`
- `PATCH /api/permission/saved/{id}`
- `PATCH /api/project/{projectID}`
- `POST /api/credential`
- `POST /api/experimental/generate`
- `POST /api/experimental/persistent-pty/handoff`
- `POST /api/experimental/persistent-pty/shutdown`
- `POST /api/experimental/persistent-pty/{ptyID}/connect-token`
- `POST /api/experimental/session/{sessionID}/terminal`
- `POST /api/experimental/session/{sessionID}/wait`
- `POST /api/session/{sessionID}/agent`
- `POST /api/session/{sessionID}/background`
- `POST /api/session/{sessionID}/fork`
- `POST /api/session/{sessionID}/interrupt`
- `POST /api/session/{sessionID}/model`
- `POST /api/session/{sessionID}/revert/commit`
- `POST /api/shell`
- `PUT /api/experimental/persistent-pty/{ptyID}`
- `PUT /api/experimental/session/{sessionID}/instructions/entries/{key}`
- `PUT /api/pty/{ptyID}`

### Removed schemas

- `Form.Fields_1`
- `Form.Fields_2`
- `Form.Fields_3`
- `Session.Message.Native_1`
- `Session.Message.Native_2`
- `Session.Message.Native_3`
- `Session.Message.Native_4`

### Changed schemas

- `Agent.Info`
- `Config.AgentEncoded`
- `Config.InfoEncoded`
- `Config.Model.CostEncoded`
- `Config.Model.Reasoning`
- `Config.Model.Settings`
- `Config.ModelEncoded`
- `Config.Provider.Settings`
- `Credential.OAuth`
- `FileDiff.Info`
- `Form.MultiselectField`
- `Form.StringField`
- `Form.Value`
- `Form.When`
- `InstructionEntryValueTooLargeErrorEncoded`
- `Integration.ExternalMethod`
- `Integration.KeyMethod`
- `Integration.OAuthMethod`
- `Mcp.LocalConfigEncoded`
- `Mcp.RemoteConfigEncoded`
- `Mcp.Server`
- `Model.Compatibility`
- `Model.Cost`
- `Model.Info`
- `Model.Settings`
- `Model.Variant`
- `PairingCode`
- `PersistentPty.CreateInput`
- `PersistentPty.Info`
- `PersistentPty.ReadResult`
- `PersistentPty.Snapshot`
- `PersistentPty.UpdateInput`
- `Plugin.Features`
- `Plugin.Info`
- `Project.Time`
- `PromptInput.FileAttachment`
- `Provider.Compatibility`
- `Provider.Settings`
- `Pty`
- `PtyTicket.ConnectToken`
- `ServerInfo`
- `Session.Info`
- `Session.Message.Assistant`
- `Session.Message.Assistant.Reasoning`
- `Session.Message.Assistant.Retry`
- `Session.Message.Assistant.Tool`
- `Session.Message.Compaction.Completed`
- `Session.Message.Shell`
- `Session.StructuredError`
- `SessionStats.Activity`
- `SessionStats.Info`
- `SessionStats.ModelUsage`
- `SessionStats.ToolTotals`
- `SessionStats.ToolUsage`
- `Shell.Info`
- `Vcs.FileStatus`
- `VoiceWebRTCTransportInput`

### Added schemas (80)

`Artifact.Capability`, `Artifact.Content`, `Artifact.CreateInput`, `Artifact.ID`, `Artifact.Info`, `Artifact.Provider`, `Artifact.ProviderIDEncoded`, `Artifact.Time`, `Artifact.UpdateInput`, `ArtifactNotFoundErrorEncoded`, `Config.Compaction.Percent`, `Config.Model.VariantSettings`, `Generate.ChoiceAnswer`, `Generate.ChoiceQuestion`, `Generate.ChoiceValue`, `Generate.DecisionAnswer`, `Generate.DecisionFilePart`, `Generate.DecisionJsonPart`, `Generate.DecisionPart`, `Generate.DecisionQuestion`, `Generate.DecisionRequest`, `Generate.DecisionResult`, `Generate.DecisionTextPart`, `Generate.ErrorAnswer`, `Generate.File`, `Generate.ImageAspect`, `Generate.ImageOutput`, `Generate.ImageRequest`, `Generate.ImageResolution`, `Generate.ImageResult`, `Generate.Native`, `Generate.PredicateAnswer`, `Generate.PredicateQuestion`, `Generate.ScoreAnswer`, `Generate.ScoreQuestion`, `Generate.Usage`, `GenerateDecisionsResponse`, `GenerateImageResponse`, `MediaHash`, `Model.MediaInput`, `Model.Type`, `Model.VariantSettings`, `ModelNotFoundErrorEncoded`, `Plugin.GuiBundle`, `Plugin.ID`, `PluginNotFoundErrorEncoded`, `Provider.Cache`, `Provider.CacheTTL`, `ProviderRequestErrorEncoded`, `PtyFeatureUnsupportedErrorEncoded`, `Schedule.Action`, `Schedule.Action.Prompt`, `Schedule.Condition`, `Schedule.Condition.Code`, `Schedule.Condition.Shell`, `Schedule.ConditionInput`, `Schedule.CreatePayload`, `Schedule.Duration`, `Schedule.Ends`, `Schedule.ID`, `Schedule.Info`, `Schedule.Outcome`, `Schedule.Status`, `Schedule.When`, `Schedule.When.At`, `Schedule.When.Cron`, `Schedule.When.Every`, `Schedule.When.In`, `Schedule.WhenInput`, `ScheduleNotFoundErrorEncoded`, `UnsupportedModelErrorEncoded`, `UsageLimits.Budget`, `UsageLimits.BudgetModel`, `UsageLimits.Go`, `UsageLimits.Info`, `UsageLimits.Meter`, `Vcs.Untracked`, `VoiceModel`, `VoiceModelsResponse`, `VoiceTransportType`

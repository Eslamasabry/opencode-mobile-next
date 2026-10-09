# Install-cert2 storage feedback follow-up on2198

The existing backend contract already reports `AgentSetupProgress.failure == AgentHostFailure.storage` only for the exact authored native low-storage error, with `phase == AgentSetupPhase.failed`. No new API or raw-error surface is required.

The current `AgentSheet._setupFrame` renders generic `agentsSetupFailed` whenever the phase is failed; it does not read this failure reason. `ChannelSetupEngine.run()` acknowledges dispatch and later publishes terminal progress, so a later guard refusal need not throw from `install()`. Rendering only `_noticeLine` exceptions cannot reliably show storage guidance.

Coordinator UI follow-up: when this agent's failed progress has `failure == storage`, use the existing localized `agentsFailStorage`: “Couldn't save this setup. Free some space and run it again.” Keep the current target-specific Install action for retry. Other failed states retain existing generic guidance. Technical native text stays out of the visible frame.

Device qualification requires a reviewed raised-floor QA APK: `OC_QA_AGENT_INSTALL_MIN_FREE_BYTES=8589934592`, different hash from normal2198, same signer. Native guard refusal, zero downloaded bytes across all components, unchanged target inventory and visible free-space guidance are separate observations. No guard APK has been supplied yet; normal2198 cannot enable this compile-time flag at runtime. This source finding is not claimed as executed low-storage device proof.

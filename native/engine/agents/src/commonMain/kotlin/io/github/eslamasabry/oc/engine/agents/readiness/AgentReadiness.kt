package io.github.eslamasabry.oc.engine.agents.readiness

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCapabilities
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.catalog.AgentUnavailableReason
import io.github.eslamasabry.oc.engine.agents.gate.CheckRequiredReason
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Port of lib/domain/phone_agents.dart (AGENTS-BACKENDS-02, plan 17 §1.4). Pure. The first matching line wins and the
// order is the freeze tip's: it must not change. No copy here: rows carry an [AgentStatusKey] that :ui:words words.

/** Sign-in phases as the host reports them (lib/domain/agent_sign_in.dart `AgentSignInPhase`). */
public enum class AgentSignInPhase { SignedOut, UrlReady, AwaitingCode, SignedIn, LimitReached, Failed }

/** Legacy hide callers also show labelled agents under the owner's new rule ("Don't hide", 2026-10-03). */
public enum class UnverifiedResumePolicy { Hide, Label }

public enum class PhoneAgentStatus {
    StoppedInBackground, SignedOut, LimitReached, Ready, Unavailable, NeedsInstall, NeedsQualification,
}

/** An agent behind the app's pin is moved to it by the app when idle; the row says so instead of asking for a check. */
public enum class AgentAutoUpdate {
    /** Will start once nothing is running. */
    Waiting,

    /** A big download that waits for Wi-Fi. */
    WaitingWifi,

    /** The install and the phone check are running now. */
    Updating,
}

/** The one next action of a row (words: Install, Sign in, Resume, Run phone check). */
public enum class AgentFixAction { Install, SignIn, Resume, RunPhoneCheck }

/** Why an agent is absent from chat selection (not from setup inventory). */
public enum class AgentHiddenReason {
    CatalogUnavailable, RuntimeUnknown, NeedsInstall, HostUnavailable, NeedsQualification, SignedOut, LimitReached,
    ResumeUnverified, StoppedInBackground,

    /** The catalog has no download for this phone's processor. */
    UnsupportedArchitecture,

    /** The agent has no verified download recipe yet. */
    UnverifiedDownload,
}

/** The row's status line as a words key; the English of each is in the S3-AGENTSE report. */
public enum class AgentStatusKey {
    /** "This agent is not available yet." */
    AgentUnavailable,

    /** "This phone cannot run this agent." */
    AgentUnsupportedArchitecture,

    /** "This agent has no verified download yet." */
    AgentNoVerifiedDownload,

    /** "Install this agent on your phone to get started." */
    AgentInstallToStart,

    /** "Run the phone check before using this agent." */
    AgentCheckNeeded,

    /** "Stopped while the app was in the background." */
    AgentStoppedInBackground,

    /** "The host is not available. Resume it and check again." */
    AgentHostStopped,

    /** "Checking sign-in on your phone." */
    AgentCheckingSignIn,

    /** "Usage limit reached. Try again when it resets." */
    AgentUsageLimit,

    /** "Sign-in could not be checked. Try signing in again." */
    AgentSignInFix,

    /** "Set up a provider key on the host to use this agent." */
    AgentSignedOutApiKey,

    /** "Sign in on the host to use this agent." */
    AgentSignedOut,

    /** "Can't reopen old chats" (the ready line when resume is unverified). */
    AgentResumeUnverified,

    /** "Ready to use." */
    AgentReady,
}


/**
 * Sanitized host facts for one agent. An installed artifact or an agent name cannot supply capability proof. The host
 * producer owns freshness. Defaults are unqualified with every capability off.
 */
public data class PhoneAgentRuntime(
    val agentId: AgentId,
    val installed: Boolean = false,
    /** Fresh authored installation files exist, independently of version health. */
    val payloadPresent: Boolean = false,
    val hostAvailable: Boolean = false,
    val architectureQualified: Boolean = false,
    val capabilities: AgentCapabilities = AgentCapabilities.None,
    /** null = not inspected, never "signed out". */
    val signInPhase: AgentSignInPhase? = null,
    val stoppedInBackground: Boolean = false,
    /** Host-supplied reset time only (epoch millis). No estimate is made when it is absent. */
    val resetAtEpochMillis: Long? = null,
    val checkRequiredReason: CheckRequiredReason? = null,
    /** Installed at an older, already-qualified version than the pin; work runs on it while the update waits. */
    val behindPin: Boolean = false,
)

/** Safe presentation data for kit-only UI. Setup inventory and chat selection are distinct. */
public data class AgentRow(
    val id: AgentId,
    val name: String,
    val iconKey: String,
    val status: PhoneAgentStatus,
    val statusKey: AgentStatusKey,
    val setupVisible: Boolean,
    val chatVisible: Boolean,
    val chatSelectable: Boolean,
    val installable: Boolean,
    val capabilities: AgentCapabilities,
    val fixAction: AgentFixAction? = null,
    val hiddenReason: AgentHiddenReason? = null,
    /** True when the row shows "Can't reopen old chats" with the note "Starts a new chat". */
    val resumeUnverified: Boolean = false,
    val resetAtEpochMillis: Long? = null,
    val checkRequiredReason: CheckRequiredReason? = null,
    val hasPartialPayload: Boolean = false,
    val autoUpdate: AgentAutoUpdate? = null,
    /** The pinned version being installed ("2.1.316"); null when only the shared helper changed. */
    val autoUpdateVersion: String? = null,
    /** Runs on an older tested version until the automatic update finishes. */
    val behindPin: Boolean = false,
) {
    /** The agent is installed on this phone, whether or not its host runs now. */
    val installedOnPhone: Boolean
        get() {
            if (status == PhoneAgentStatus.Ready) return true
            return when (hiddenReason) {
                null, AgentHiddenReason.CatalogUnavailable, AgentHiddenReason.RuntimeUnknown,
                AgentHiddenReason.NeedsInstall, AgentHiddenReason.UnsupportedArchitecture,
                AgentHiddenReason.UnverifiedDownload,
                -> false
                else -> true
            }
        }

    /** This row with an automatic update pending or running. The phone-check action goes away: the app is doing it. */
    public fun withAutoUpdate(state: AgentAutoUpdate, version: String? = null): AgentRow =
        copy(
            fixAction = if (status == PhoneAgentStatus.NeedsQualification) null else fixAction,
            autoUpdate = state,
            autoUpdateVersion = version,
        )
}

public object AgentReadiness {
    /**
     * Intersect only semantically matching server gates. The server has no model-list, durable resume or cancel
     * switch; those stay subject to runtime proof. `cliSessionResume` is a terminal command, never a resume gate.
     */
    public fun phoneAgentCapabilities(runtime: AgentCapabilities, server: ServerCapabilities): AgentCapabilities =
        AgentCapabilities(
            resumeVerified = runtime.resumeVerified,
            modelList = runtime.modelList,
            permissions = runtime.permissions && server.hostAgentPermissionActions,
            images = runtime.images && server.promptAttachments,
            cancel = runtime.cancel,
        )

    /** The pure row function (Dart `buildAgentRow`). [policy] is kept for legacy callers; it never hides. */
    @Suppress("UNUSED_PARAMETER")
    public fun row(
        descriptor: AgentDescriptor,
        architecture: AgentArchitecture,
        serverCapabilities: ServerCapabilities,
        runtime: PhoneAgentRuntime? = null,
        policy: UnverifiedResumePolicy = UnverifiedResumePolicy.Label,
    ): AgentRow {
        val row = build(descriptor, architecture, serverCapabilities, runtime)
        return if (runtime?.agentId == descriptor.id && runtime.behindPin) row.copy(behindPin = true) else row
    }

    private fun build(
        descriptor: AgentDescriptor,
        architecture: AgentArchitecture,
        server: ServerCapabilities,
        runtime: PhoneAgentRuntime?,
    ): AgentRow {
        val fact = runtime?.takeIf { it.agentId == descriptor.id }
        val installable = descriptor.installableOn(architecture)
        // Server types (OpenCode) are not phone agents: only agents the phone host runs are listed.
        val catalogVisible = descriptor.runsOnPhoneHost && descriptor.availability != AgentAvailability.Hidden
        val cannotInstall: AgentHiddenReason? = when {
            installable -> null
            descriptor.recipe == null ||
                descriptor.unavailableReason == AgentUnavailableReason.RecipeUnverified ||
                descriptor.unavailableReason == AgentUnavailableReason.DependencyClosureUnverified ->
                AgentHiddenReason.UnverifiedDownload
            else -> AgentHiddenReason.UnsupportedArchitecture
        }

        fun blocked(
            status: PhoneAgentStatus,
            key: AgentStatusKey,
            reason: AgentHiddenReason,
            action: AgentFixAction? = null,
        ): AgentRow = AgentRow(
            id = descriptor.id,
            name = descriptor.name,
            iconKey = descriptor.iconKey,
            status = status,
            statusKey = key,
            fixAction = action,
            setupVisible = catalogVisible,
            chatVisible = false,
            chatSelectable = false,
            installable = installable,
            hasPartialPayload = fact?.payloadPresent == true && !fact.installed,
            capabilities = AgentCapabilities.None,
            hiddenReason = reason,
            checkRequiredReason = fact?.checkRequiredReason,
            resumeUnverified = fact?.capabilities?.resumeVerified != true,
            resetAtEpochMillis = if (status == PhoneAgentStatus.LimitReached) fact?.resetAtEpochMillis else null,
        )

        // 1. Catalog hidden (or not a phone agent).
        if (!catalogVisible) {
            return blocked(
                PhoneAgentStatus.Unavailable,
                AgentStatusKey.AgentUnavailable,
                AgentHiddenReason.CatalogUnavailable,
            )
        }
        // 2. Nothing known about the host, or not installed: Install, unless a real blocker stops it.
        if (fact == null || !fact.installed) {
            if (cannotInstall != null) {
                return blocked(
                    PhoneAgentStatus.Unavailable,
                    if (cannotInstall == AgentHiddenReason.UnsupportedArchitecture) {
                        AgentStatusKey.AgentUnsupportedArchitecture
                    } else {
                        AgentStatusKey.AgentNoVerifiedDownload
                    },
                    cannotInstall,
                )
            }
            return blocked(
                PhoneAgentStatus.NeedsInstall,
                AgentStatusKey.AgentInstallToStart,
                if (fact == null) AgentHiddenReason.RuntimeUnknown else AgentHiddenReason.NeedsInstall,
                AgentFixAction.Install,
            )
        }
        // 3. A fresh install has no running host yet: the check starts it, so never Resume here.
        if (!fact.architectureQualified) {
            return blocked(
                PhoneAgentStatus.NeedsQualification,
                AgentStatusKey.AgentCheckNeeded,
                AgentHiddenReason.NeedsQualification,
                AgentFixAction.RunPhoneCheck,
            )
        }
        // 4.
        if (fact.stoppedInBackground) {
            return blocked(
                PhoneAgentStatus.StoppedInBackground,
                AgentStatusKey.AgentStoppedInBackground,
                AgentHiddenReason.StoppedInBackground,
                AgentFixAction.Resume,
            )
        }
        // 5.
        if (!fact.hostAvailable) {
            return blocked(
                PhoneAgentStatus.Unavailable,
                AgentStatusKey.AgentHostStopped,
                AgentHiddenReason.HostUnavailable,
                AgentFixAction.Resume,
            )
        }
        // 6. Not inspected yet ("Checking…", re-read after 5 s).
        if (descriptor.signInMethod != AgentSignInMethod.None && fact.signInPhase == null) {
            return blocked(
                PhoneAgentStatus.Unavailable,
                AgentStatusKey.AgentCheckingSignIn,
                AgentHiddenReason.RuntimeUnknown,
            )
        }
        // 7.
        if (fact.signInPhase == AgentSignInPhase.LimitReached) {
            return blocked(
                PhoneAgentStatus.LimitReached,
                AgentStatusKey.AgentUsageLimit,
                AgentHiddenReason.LimitReached,
            )
        }
        // 8.
        if (fact.signInPhase == AgentSignInPhase.Failed) {
            return blocked(
                PhoneAgentStatus.Unavailable,
                AgentStatusKey.AgentSignInFix,
                AgentHiddenReason.SignedOut,
                AgentFixAction.SignIn,
            )
        }
        // 9.
        if (descriptor.signInMethod != AgentSignInMethod.None && fact.signInPhase != AgentSignInPhase.SignedIn) {
            return blocked(
                PhoneAgentStatus.SignedOut,
                if (descriptor.signInMethod == AgentSignInMethod.ApiKeyHost) {
                    AgentStatusKey.AgentSignedOutApiKey
                } else {
                    AgentStatusKey.AgentSignedOut
                },
                AgentHiddenReason.SignedOut,
                AgentFixAction.SignIn,
            )
        }
        // 10. Ready: capabilities are runtime proof AND server flags.
        val capabilities = phoneAgentCapabilities(fact.capabilities, server)
        val unverifiedResume = !capabilities.resumeVerified
        return AgentRow(
            id = descriptor.id,
            name = descriptor.name,
            iconKey = descriptor.iconKey,
            status = PhoneAgentStatus.Ready,
            statusKey = if (unverifiedResume) AgentStatusKey.AgentResumeUnverified else AgentStatusKey.AgentReady,
            setupVisible = true,
            chatVisible = true,
            chatSelectable = true,
            installable = installable,
            capabilities = capabilities,
            resumeUnverified = unverifiedResume,
        )
    }
}

/** Plan 17 §1.4 timers (one `AgentReadinessClock` per owner uses these; cancelled on profile close). */
public object AgentReadinessTimers {
    /** Sign-in recheck of a "Checking…" row, once per row state. */
    public val signInRecheck: Duration = 5.seconds

    /** Helper watchdog; offline twice → recover (start only a stopped helper). */
    public val helperWatchdog: Duration = 10.seconds
    public const val WATCHDOG_MISSES_BEFORE_RECOVER: Int = 2

    /** Reconnect re-check, at most this often. */
    public val reconnectRecheck: Duration = 20.seconds

    /** Auto-resume of a stoppedInBackground row, only when the app was not backgrounded. */
    public val autoResume: Duration = 1.minutes

    /** Helper start budget, polling `GET /api/health` (no password). */
    public val helperStartBudget: Duration = 30.seconds

    /** First-paint hold of the feed; the loading line stops after [feedLoadingLine]. */
    public val feedFirstPaintHold: Duration = 4.seconds
    public val feedLoadingLine: Duration = 30.seconds

    /** Folder re-read while the helper starts: every 3 s for 30 s, then 15 s for 2 min (≤ 18 tries). */
    public val folderRereadFast: Duration = 3.seconds
    public val folderRereadFastFor: Duration = 30.seconds
    public val folderRereadSlow: Duration = 15.seconds
    public val folderRereadSlowFor: Duration = 2.minutes
    public const val FOLDER_REREAD_MAX_TRIES: Int = 18

    /** Rows still "reading" are re-read this often. */
    public val readingRowReread: Duration = 5.seconds
}

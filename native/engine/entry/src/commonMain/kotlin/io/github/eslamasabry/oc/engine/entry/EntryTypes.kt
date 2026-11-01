package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.jvm.JvmInline
import kotlin.time.Instant

// Plan 07 §7.2: the entry contract. Every type here is neutral (no Android, no routes); :ui:shell maps EntryTarget to
// navigator calls (07 §7.3 table). Ids are validated before they get here (EntryIntentParser, 07 §7.1).

/**
 * Who sent a request (Astra M2, 16 §1.8.1). Only an app-owned PendingIntent aimed at the non-exported
 * `.InternalEntryActivity` is [Internal]; everything reaching the exported `MainActivity` (static and pinned launcher
 * shortcuts included) is [Untrusted] and only navigates: no connect, no CreateSession, no gateway call before a tap.
 */
public enum class EntryOrigin { Untrusted, Internal }

public enum class EntryChannel { Shortcut, Link, Share, Alert, Schedule, Internal }

public sealed interface EntryRequest {
    public val channel: EntryChannel
    public val receivedAt: Instant
    public val origin: EntryOrigin

    public data class Launch(
        val action: LaunchAction,
        override val receivedAt: Instant,
        override val origin: EntryOrigin = EntryOrigin.Untrusted,
    ) : EntryRequest {
        override val channel: EntryChannel get() = EntryChannel.Shortcut
    }

    public data class PinnedSession(
        val profileId: ProfileId,
        val sessionId: SessionId,
        override val receivedAt: Instant,
        override val origin: EntryOrigin = EntryOrigin.Untrusted,
    ) : EntryRequest {
        override val channel: EntryChannel get() = EntryChannel.Shortcut
    }

    /** [raw] already passed `SessionLinkIngress` (non-empty, ≤ 1,024 chars, `0x21..0x7e`). Links are never trusted. */
    public data class Link(val raw: String, override val receivedAt: Instant) : EntryRequest {
        override val channel: EntryChannel get() = EntryChannel.Link
        override val origin: EntryOrigin get() = EntryOrigin.Untrusted
    }

    /** Subject + body joined, trimmed, ≤ [EntryLimits.SHARED_TEXT_MAX] chars; memory only. Never sent (07 §7.7). */
    public data class SharedText(val text: String, override val receivedAt: Instant) : EntryRequest {
        override val channel: EntryChannel get() = EntryChannel.Share
        override val origin: EntryOrigin get() = EntryOrigin.Untrusted

        override fun toString(): String = "SharedText(chars=${text.length}, receivedAt=$receivedAt)" // never log the text
    }

    public data class AlertOpen(
        val kind: AlertKind,
        val profileId: ProfileId?,
        val sessionId: SessionId?,
        val monitorToken: MonitorToken?,
        val reply: Boolean,
        override val receivedAt: Instant,
        override val origin: EntryOrigin = EntryOrigin.Untrusted,
    ) : EntryRequest {
        override val channel: EntryChannel get() = EntryChannel.Alert
    }

    public data class ScheduledOpen(
        val profileId: ProfileId,
        val scheduleId: String,
        val sessionId: SessionId?,
        override val receivedAt: Instant,
        override val origin: EntryOrigin = EntryOrigin.Untrusted,
    ) : EntryRequest {
        override val channel: EntryChannel get() = EntryChannel.Schedule
    }

    public data class TilePreferences(val tile: TileId, override val receivedAt: Instant) : EntryRequest {
        override val channel: EntryChannel get() = EntryChannel.Shortcut
        override val origin: EntryOrigin get() = EntryOrigin.Untrusted
    }

    /** From app start-up only (a resumed Linux reset opens setup progress, 07 §2.6); never from an Intent. */
    public data class Internal(val target: EntryTarget, override val receivedAt: Instant) : EntryRequest {
        override val channel: EntryChannel get() = EntryChannel.Internal
        override val origin: EntryOrigin get() = EntryOrigin.Internal
    }
}

public enum class UsageTargetKind(public val wire: String) {
    SignIn("sign_in"),
    AddKey("add_key"),
    SetUp("set_up"),
}

public sealed interface LaunchAction {
    public data object Connect : LaunchAction

    public data object NewTask : LaunchAction

    public data object Activity : LaunchAction

    public data object PhoneSetup : LaunchAction

    public data object PhoneSetupDone : LaunchAction

    public data object Usage : LaunchAction

    public data object UsageSettings : LaunchAction

    public data object Linux : LaunchAction

    public data object BackgroundSettings : LaunchAction

    public data class UsageTarget(val kind: UsageTargetKind, val providerId: String, val profileId: ProfileId) :
        LaunchAction
}

/** The `CodingAlertKind` wire values the Flutter app posts (07 §7.8); unknown kinds are dropped by the parser. */
public enum class AlertKind(public val wire: String) {
    Permission("permission"),
    Question("question"),
    Complete("complete"),
    Error("error"),
    Checkin("checkin"),
    Quota("quota"),
    QuotaReset("quota_reset"),
    TeamDecision("team_decision"),
    TeamReview("team_review"),
    TeamRunFailed("team_run_failed"),
    TeamCompleted("team_completed"),
    TeamProgress("team_progress"),
    Test("test"),
    ;

    public companion object {
        public fun fromWire(value: String): AlertKind? = entries.firstOrNull { it.wire == value }
    }
}

/** `^[a-f0-9]{64}$`, checked by the parser; the stored-token match is the effect's job. Never logged. */
@JvmInline
public value class MonitorToken(public val raw: String) {
    override fun toString(): String = "MonitorToken(redacted)"
}

public enum class TileId { Attention, Pause }

/** The stored schedule's project directory (07 §7.9). */
@JvmInline public value class ProjectDir(public val raw: String)

public enum class ConversationsFilter { None, Running }

public sealed interface LandOn {
    public data object OldestRequest : LandOn

    public data object Failure : LandOn
}

/** How the chat was reached; NewlyCreated marks a CreateSession result (07 §7.4 `new_task`). */
public enum class Opened { Existing, NewlyCreated }

public sealed interface ServersRequestTarget {
    public data class Edit(val profileId: ProfileId, val focusPassword: Boolean) : ServersRequestTarget

    public data object Add : ServersRequestTarget
}

public enum class SetupStage { Progress, Ready }

public enum class UsageSectionTarget { Plans }

public enum class SettingsSectionTarget { Notifications, Background }

public enum class SwitchReason { Link, Alert, Schedule, UsageRow }

/** Neutral targets; :ui:shell maps them to routes (ADR §4 row 26, 07 §7.3 table). */
public sealed interface EntryTarget {
    public data class Chat(
        val ref: ConversationRef,
        val focusComposer: Boolean = false,
        val landOn: LandOn? = null,
        val opened: Opened = Opened.Existing,
    ) : EntryTarget

    public data class Conversations(val filter: ConversationsFilter) : EntryTarget

    public data object NeedsYou : EntryTarget

    public data class Servers(val request: ServersRequestTarget?) : EntryTarget

    public data class PhoneSetup(val stage: SetupStage) : EntryTarget

    public data object ThisPhone : EntryTarget

    public data class Usage(val section: UsageSectionTarget) : EntryTarget

    public data object UsageWidgetSettings : EntryTarget

    public data class ProviderSignIn(val profileId: ProfileId, val providerId: String) : EntryTarget

    public data class AgentSignIn(val agentId: String) : EntryTarget

    public data class UsageReadFrom(val profileId: ProfileId, val providerId: String) : EntryTarget

    public data class Settings(val section: SettingsSectionTarget) : EntryTarget

    public data class ScheduledPrompts(
        val profileId: ProfileId,
        val projectDir: ProjectDir,
        val highlightScheduleId: String?,
    ) : EntryTarget

    /** The address sheet for a link naming a server this phone has not saved; [linkTicket] keys [EntryRouter.linkFor]. */
    public data class SessionAddress(val linkTicket: Long) : EntryTarget

    /** Asks the person before switching; the sheet resubmits [then] after the switch (never switches silently). */
    public data class SwitchServerThen(val profileId: ProfileId, val reason: SwitchReason, val then: EntryTarget) :
        EntryTarget

    public data class Land(val then: EntryTarget?) : EntryTarget

    /**
     * Untrusted `new_task` (Astra M2, 07 §7.4): the new-conversation sheet with the server named and a **Start**
     * button; nothing is created until the person taps it. Not in 07 §7.2's list (deviation D-E2 in the unit report).
     */
    public data class NewConversation(val profileId: ProfileId) : EntryTarget

    /**
     * Untrusted `usage_target` for a server that is not connected: [then]'s page with "Connect to <server>?"; the
     * connection starts only on the person's tap (Astra M2). Deviation D-E2.
     */
    public data class ConfirmConnect(val profileId: ProfileId, val then: EntryTarget) : EntryTarget
}

/** Message keys only; :ui:words owns the sentences (07 §7.3 rule 4). */
public enum class EntryNotice {
    AddServerFirst,
    ChooseServerFirst,
    SignInAgain,
    ConnectionFailed,
    OtherServer,
    ConversationNotFound,
    RequestGone,
    PlanChanged,
    UsageRowGone,
    CreateFailed,
    ShareNeedsServer,
    ShareFailed,
}

/** One acknowledged handoff (03 S12). [target] null = stay where the person is and only show [notice] (D-E3). */
public data class EntryHandoff(val id: Long, val target: EntryTarget?, val notice: EntryNotice?)

public enum class WaitReason { Bootstrap, Connection, ScheduleSettling }

/** Drives the one "Waiting for <server>…" status line ([profileId] null while bootstrap runs). */
public data class EntryWaiting(val reason: WaitReason, val profileId: ProfileId?)

public object EntryLimits {
    public const val SHARED_TEXT_MAX: Int = 8_000
    public const val LINK_MAX: Int = 1_024

    /** 07 §7.9: a scheduled-run tap waits this long for the start-up connection to settle. */
    public const val SCHEDULE_HOLD_SECONDS: Long = 30
}

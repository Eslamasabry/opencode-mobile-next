package io.github.eslamasabry.oc.engine.analytics

import io.github.eslamasabry.oc.core.common.Failure

/*
 * The closed vocabularies events may use. Every value an event can put on the wire is a constant below, so the
 * scrubber can check each value against an allowlist. Append-only: renaming a constant renames a wire value.
 */

/** How the app started. */
public enum class StartKind { Cold, Warm }

/** Backend kinds as counted in BASIC. Mirrors `:core:model` BackendKind without depending on it. */
public enum class ServerKind { OpenCode2, Paseo, Codex }

/** Agent kinds as counted in BASIC. TODO(move-to-core): align with the agents engine's own enum when it lands. */
public enum class AgentKind { OpenCode, ClaudeCode, Codex, Gemini, Acp, Other }

/** Where a server is, as a class of address (plan 16 §5.3). Never the address itself. */
public enum class AddressClass { ThisPhone, Tailnet, LocalNetwork, Internet }

public enum class MigrationOutcome { Done, ReentryNeeded, Failed }

public enum class MigrationStep { None, S0, M1, M2, M3, M4, M5, M6 }

public enum class UpdateResult { Cancelled, SignerMismatch, ChecksumMismatch, NoSpace, Failed }

/** Crash kinds. A crash is reported by kind only: no message, no stack, no thread name. */
public enum class CrashCategory { Kotlin, Native, OutOfMemory, StartupLoop, Other }

/** Where a freeze (a main-thread stall or an ANR) happened, by app state only. */
public enum class FreezeKind { MainThreadStall, NotResponding }

/** Error categories: the [Failure] subtype name, never its text (owner rule "error categories never text"). */
public enum class ErrorCategory {
    Refused, Unavailable, Network, TimedOut, Certificate, SignIn, NotFound, Conflict, Busy, Server, Rejected, Incompatible,
    ComputerDidNotAnswer, UnreadableReply, StorageRefused, KeystoreUnavailable, Media, Runtime, Device, Folder, ProjectGit,
    StagedRevert, Termux, Unexpected, Unknown;

    public companion object {
        /** Exhaustive over core's flat Failure (plan 03 §10.1), so a new subtype fails compilation here. */
        public fun of(failure: Failure): ErrorCategory =
            when (failure) {
                is Failure.Refused -> Refused
                is Failure.Unavailable -> Unavailable
                is Failure.Network -> Network
                is Failure.TimedOut -> TimedOut
                is Failure.Certificate -> Certificate
                is Failure.SignIn -> SignIn
                is Failure.NotFound -> NotFound
                is Failure.Conflict -> Conflict
                is Failure.Busy -> Busy
                is Failure.Server -> Server
                is Failure.Rejected -> Rejected
                is Failure.Incompatible -> Incompatible
                is Failure.ComputerDidNotAnswer -> ComputerDidNotAnswer
                is Failure.UnreadableReply -> UnreadableReply
                is Failure.StorageRefused -> StorageRefused
                is Failure.SecretUnavailable -> KeystoreUnavailable // the wire never carries the word "secret" (leak detector)
                is Failure.Media -> Media
                is Failure.Runtime -> Runtime
                is Failure.Device -> Device
                is Failure.Folder -> Folder
                is Failure.ProjectGit -> ProjectGit
                is Failure.StagedRevert -> StagedRevert
                is Failure.Termux -> Termux
                is Failure.Unexpected -> Unexpected
                is Failure.Unknown -> Unknown
            }
    }
}

/**
 * DETAILED: screens by kind. TODO(move-to-core): generated from `:ui:routes` once routes exist; an engine cannot see
 * route classes (ADR §6.1), so this list is the analytics side of that mapping. Never route arguments.
 */
public enum class ScreenId {
    Home, Chats, Chat, Servers, ServerEdit, Agents, Attention, Schedule, Files, Terminal, Settings, Privacy, Updates,
    Help, About, Onboarding, Other,
}

/** DETAILED: features by kind. */
public enum class UsedFeature {
    Voice, ReadAloud, Attachment, Camera, QrScan, Share, Widget, Shortcut, Search, Steer, Undo, Review, Terminal,
    BuiltInLinux, Schedule, Handoff, AiTeam, Other,
}

/** DETAILED: multi-step flows. */
public enum class FlowId { AddServer, PairByQr, FirstChat, BuiltInLinuxSetup, Migration, Update, SignIn }

public enum class FlowStep { Started, Completed, Abandoned, Failed }

/** DETAILED: timed actions. */
public enum class TimedAction { OpenChat, LoadSessions, SendMessage, FirstReply, Reconnect, SearchResults }

/** DETAILED: settings by id; the value is only sent for enumerated settings (plan 16 §5.3). */
public enum class SettingId { Theme, Language, TextSize, Notifications, ReadAloudVoice, DetailedUsage, Other }

/** Coarse device class for BASIC system props. Never a model or manufacturer. */
public enum class DeviceClass { Phone, Tablet, Foldable, Desktop, Unknown }

/** OS family for BASIC system props. */
public enum class OsFamily { Android, Linux, MacOs, Windows, Ios, Other }

/** The app's release channel as sent (plan 16 §5.2 `channel`). */
public enum class ReleaseChannel { Stable, Preview, Desktop }

package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.engine.runtime.storage.PhoneStorageSnapshot
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The commands This phone runs, one at a time. */
public enum class PhoneOp {
    Start,
    Stop,
    Restart,
    Update,
}

/** A server row under the answer; each names its object ("Stop OpenCode on this phone"). */
public enum class ServerRow {
    Stop,
    Restart,
}

/**
 * The version row: what is installed and whether the pinned version is offered (B-LINUX-078/079).
 */
public data class VersionRowUi(val installed: String?, val update: UpdateRow?)

public sealed interface UpdateRow {
    public data object UpToDate : UpdateRow

    public data class Available(val version: String) : UpdateRow

    /** The automatic try failed; the manual row stays (B-LINUX-079: no automatic retry). */
    public data class Failed(val version: String) : UpdateRow

    public data object Updating : UpdateRow
}

/**
 * The "Running on this phone" row: how close the app is to Android's child-process limit (plan 11
 * §7.2).
 */
public data class RunningRowUi(
    val count: Int? = null,
    val cap: Int = 32,
    val nearLimit: Boolean = false,
    val status: io.github.eslamasabry.oc.engine.runtime.procs.RunningRowStatus? = null,
)

/** What the page asks before acting (KitConfirm). */
public sealed interface ThisPhoneConfirm {
    public data class Restart(val replyRunning: Boolean) : ThisPhoneConfirm

    public data class Update(val version: String) : ThisPhoneConfirm
}

/** One line said in place, never a snackbar. */
public sealed interface ThisPhoneNotice {
    public data class Failed(val op: PhoneOp) : ThisPhoneNotice

    /** "A reply is still being written. Stop it or let it finish, then update." */
    public data object UpdateBusy : ThisPhoneNotice

    /** Android's settings screen did not open. */
    public data object SettingsMissing : ThisPhoneNotice
}

/**
 * PhoneRoute.ThisPhone's state (plan 07 F01). No copy: `ThisPhoneScreen` turns each value into
 * `:ui:words`.
 */
public data class ThisPhoneUi(
    val termuxMigration: List<PhoneTermuxMigrationOffer> = emptyList(),
    val lifecycle: io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReport? = null,
    val storage: io.github.eslamasabry.oc.engine.runtime.lifecycle.AppStorageFacts? = null,
    val storageFailed: Boolean = false,
    val answer: ThisPhoneAnswer = ThisPhoneAnswer(AnswerTone.Working, AnswerHeadline.Checking),
    val serverRows: ImmutableList<ServerRow> = persistentListOf(),
    val version: VersionRowUi? = null,
    val running: RunningRowUi = RunningRowUi(),
    val working: PhoneOp? = null,
    val confirm: ThisPhoneConfirm? = null,
    val notice: ThisPhoneNotice? = null,
    val storageSupported: Boolean = false,
    val phoneStorage: PhoneStorageSnapshot? = null,
    val measuringStorage: Boolean = false,
    val exitNotice: PhoneExitNotice? = null,
    val leftover: io.github.eslamasabry.oc.engine.runtime.procs.RunawayNotice? = null,
)

/** Intents only (ADR §9.1). */
public interface ThisPhoneActions {
    /** The answer's one act. */
    public fun act(act: PhoneAct)

    public fun dismissRecovery()

    public fun refreshStorage()

    public fun stopServer()

    public fun askRestart()

    public fun askUpdate()

    /** Runs what [ThisPhoneUi.confirm] asked. */
    public fun confirm()

    /** Closes the confirm or the notice. */
    public fun dismiss()

    public fun measureStorage()

    public fun openRunning()

    public fun openKeepRunning()

    /** The "Linux" row: the Linux manager of the phone's own Ubuntu. */
    public fun openLinux() {}

    public fun openTerminal() {}

    public fun back()

    /** The page became the top entry (re-read the status, tick the countdown) or left it. */
    public fun shown(visible: Boolean)
}

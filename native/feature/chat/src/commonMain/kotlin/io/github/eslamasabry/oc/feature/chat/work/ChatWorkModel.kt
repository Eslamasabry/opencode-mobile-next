package io.github.eslamasabry.oc.feature.chat.work

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.BackgroundWorkResult
import io.github.eslamasabry.oc.core.model.BackgroundWorkSupport
import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.feature.chat.presentation.BackgroundWork
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/*
 * Flutter `running_work_sheet.dart` and `chat_commands.dart` `_runShellDialog`, for the commands the
 * server runs for this conversation: the list, one command's output, Stop command and Change timeout,
 * and "Run shell command". State and words-free: the screen owns every sentence. Agents the
 * conversation started are the Relations page's (SessionRelationsScreen); only commands live here.
 */

public enum class WorkTimeout(public val limit: Duration?) {
    OneMinute(1.minutes),
    FiveMinutes(5.minutes),
    FifteenMinutes(15.minutes),
    OneHour(1.hours),
    None(null),
}

public data class ShellOutputUi(
    val shell: ManagedShell,
    val text: String = "",
    val cursor: Long = 0,
    /** The server trimmed earlier text; only the newest is shown. */
    val truncated: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** The command is gone from the server (restart, removal); loaded text stays readable. */
    val gone: Boolean = false,
)

public data class ChatWorkUi(
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** This server has background commands at all. */
    val supported: Boolean = true,
    /** This conversation's commands, newest first. */
    val shells: ImmutableList<ManagedShell> = persistentListOf(),
    val output: ShellOutputUi? = null,
    /** The command a Stop confirmation is asking about. */
    val confirmStop: String? = null,
    /** The command whose timeout choices are open. */
    val timeoutFor: String? = null,
    /** A Stop or timeout change did not go through. */
    val actionFailed: Boolean = false,
    /** The "Run shell command" dialog; null = closed. */
    val dialog: ShellDialogUi? = null,
    /** Flutter `canBackground`: a reply runs work the server can move on (the sheet offers it). */
    val canBackground: Boolean = false,
    /** The request is on its way (Flutter `_promoting`). */
    val backgrounding: Boolean = false,
    /** What the server said to the last request; null = none made. */
    val backgroundResult: BackgroundWorkResult? = null,
) {
    /** Flutter `blocked`: the notice stays while it can be asked, is asked, or was answered. */
    val backgroundNotice: Boolean
        get() = canBackground || backgrounding || backgroundResult != null

    val running: Int
        get() = shells.count { it.status == ManagedShellStatus.Running }
}

public data class ShellDialogUi(
    val initial: String = "",
    val running: Boolean = false,
    /** Failed under the field with the command kept (Flutter: failure stays in the dialog). */
    val failed: Boolean = false,
    val empty: Boolean = false,
)

public class ChatWorkModel(
    private val world: ChatWorld,
    private val launch: (suspend () -> Unit) -> Unit,
) {
    private val mutable = MutableStateFlow(ChatWorkUi())
    private var backgroundSupport: BackgroundWorkSupport = BackgroundWorkSupport.Unavailable
    private var supportRead = false
    private var backgroundRequested: Set<String> = emptySet()
    public val state: StateFlow<ChatWorkUi> = mutable

    /** Keeps the running count honest while the chat is open; stops on its own where unsupported. */
    public fun poll() {
        launch {
            while (mutable.value.supported) {
                if (world.capabilities.value.conversationShellOn) load(quiet = true)
                loadBackground()
                delay(POLL_MS)
            }
        }
    }

    public fun refresh() {
        mutable.update { it.copy(backgroundResult = null) }
        launch {
            load(quiet = false)
            loadBackground()
        }
    }

    private fun backgroundParts() =
        (world as? TurnFeedSource)?.turnFeed?.value?.ledger?.messages?.let {
            BackgroundWork.parts(it, backgroundSupport)
        }.orEmpty()

    private fun offersBackground(): Boolean =
        world.transcript.value.run.busy &&
            !mutable.value.backgrounding &&
            world.readOnly.value == null &&
            backgroundParts().any { BackgroundWork.key(it) !in backgroundRequested }

    /** Reads what the server can move on, then whether this reply has such work (a failure offers nothing). */
    private suspend fun loadBackground() {
        if (!supportRead) {
            val support = world.backgroundWorkSupport()
            if (support is Outcome.Ok) {
                backgroundSupport = support.value
                supportRead = true
            }
        }
        mutable.update { it.copy(canBackground = offersBackground()) }
    }

    /** Flutter `_promote`: "Keep chatting while it runs" sends the running work to the background. */
    public fun runInBackground() {
        if (!mutable.value.canBackground || mutable.value.backgrounding) return
        val keys = backgroundParts().map(BackgroundWork::key)
        mutable.update { it.copy(backgrounding = true, canBackground = false) }
        launch {
            when (val r = world.backgroundWork()) {
                is Outcome.Ok -> {
                    if (r.value != BackgroundWorkResult.Unchanged) backgroundRequested = backgroundRequested + keys
                    mutable.update { it.copy(backgrounding = false, backgroundResult = r.value) }
                    world.reload()
                    load(quiet = true)
                }
                is Outcome.Failed -> {
                    // offersBackground() refuses while the request is on its way, so clear that first: the offer stays.
                    mutable.update { it.copy(backgrounding = false) }
                    mutable.update { it.copy(canBackground = offersBackground(), actionFailed = true) }
                }
            }
        }
    }

    private suspend fun load(quiet: Boolean) {
        if (!quiet) mutable.update { it.copy(loading = true, failed = false) }
        when (val r = world.runningShells()) {
            is Outcome.Ok -> {
                val mine =
                    r.value.shells
                        .filter { it.sessionId == world.ref.sessionId.raw }
                        .sortedByDescending { it.startedAtMs }
                mutable.update {
                    it.copy(
                        loading = false,
                        failed = false,
                        supported = r.value.supported,
                        shells = mine.toImmutableList(),
                    )
                }
            }
            is Outcome.Failed -> mutable.update { it.copy(loading = false, failed = !quiet || it.failed) }
        }
    }

    public fun openOutput(id: String) {
        val shell = mutable.value.shells.firstOrNull { it.id == id } ?: return
        mutable.update { it.copy(output = ShellOutputUi(shell), actionFailed = false) }
        launch { readOutput(id, 0, fresh = true) }
    }

    public fun closeOutput() {
        mutable.update { it.copy(output = null, confirmStop = null, timeoutFor = null) }
    }

    /** Refresh = the newest output; Load more = the next page from the cursor. */
    public fun refreshOutput() {
        val o = mutable.value.output ?: return
        launch {
            load(quiet = true)
            mutable.value.shells.firstOrNull { it.id == o.shell.id }?.let { fresh ->
                mutable.update { s -> s.copy(output = s.output?.copy(shell = fresh)) }
            }
            readOutput(o.shell.id, mutable.value.output?.cursor ?: 0, fresh = false)
        }
    }

    private suspend fun readOutput(id: String, cursor: Long, fresh: Boolean) {
        mutable.update { it.copy(output = it.output?.copy(loading = true, failed = false)) }
        when (val r = world.shellOutput(id, cursor)) {
            is Outcome.Ok ->
                mutable.update {
                    val o = it.output?.takeIf { out -> out.shell.id == id } ?: return@update it
                    it.copy(
                        output =
                            o.copy(
                                text = if (fresh) r.value.text else o.text + r.value.text,
                                cursor = r.value.cursor,
                                truncated = r.value.truncated || (!fresh && o.truncated),
                                loading = false,
                                failed = false,
                            )
                    )
                }
            is Outcome.Failed ->
                mutable.update {
                    val o = it.output?.takeIf { out -> out.shell.id == id } ?: return@update it
                    // A command the server no longer has keeps what was loaded.
                    it.copy(output = o.copy(loading = false, failed = o.text.isEmpty(), gone = o.text.isNotEmpty()))
                }
        }
    }

    public fun askStop(id: String?) {
        mutable.update { it.copy(confirmStop = id) }
    }

    public fun confirmStop() {
        val id = mutable.value.confirmStop ?: return
        mutable.update { it.copy(confirmStop = null) }
        launch {
            when (world.stopShell(id)) {
                is Outcome.Ok -> {
                    load(quiet = true)
                    mutable.update { s ->
                        val fresh = s.shells.firstOrNull { it.id == id }
                        s.copy(output = s.output?.let { o -> if (fresh != null) o.copy(shell = fresh, gone = fresh == null) else o.copy(gone = true) })
                    }
                }
                is Outcome.Failed -> mutable.update { it.copy(actionFailed = true) }
            }
        }
    }

    public fun chooseTimeout(id: String?) {
        mutable.update { it.copy(timeoutFor = id) }
    }

    public fun setTimeout(choice: WorkTimeout) {
        val id = mutable.value.timeoutFor ?: return
        mutable.update { it.copy(timeoutFor = null) }
        launch {
            when (val r = world.setShellTimeout(id, choice.limit)) {
                is Outcome.Ok ->
                    mutable.update { s ->
                        s.copy(
                            shells = s.shells.map { if (it.id == id) r.value else it }.toImmutableList(),
                            output = s.output?.let { o -> if (o.shell.id == id) o.copy(shell = r.value) else o },
                        )
                    }
                is Outcome.Failed -> mutable.update { it.copy(actionFailed = true) }
            }
        }
    }

    public fun dismissActionFailure() {
        mutable.update { it.copy(actionFailed = false) }
    }

    // ---- Run shell command ----

    public fun openShellDialog(initial: String = "") {
        if (!world.capabilities.value.conversationShellOn) return
        mutable.update { it.copy(dialog = ShellDialogUi(initial)) }
    }

    public fun closeShellDialog() {
        mutable.update { it.copy(dialog = null) }
    }

    /** Runs from inside the dialog; a failure stays under the field with the command kept. */
    public fun runShell(command: String) {
        val dialog = mutable.value.dialog ?: return
        if (dialog.running) return
        if (command.isBlank()) {
            mutable.update { it.copy(dialog = dialog.copy(empty = true, failed = false)) }
            return
        }
        mutable.update { it.copy(dialog = dialog.copy(running = true, failed = false, empty = false)) }
        launch {
            when (world.runShell(command)) {
                is Outcome.Ok -> {
                    mutable.update { it.copy(dialog = null) }
                    load(quiet = true)
                }
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(dialog = it.dialog?.copy(running = false, failed = true))
                    }
            }
        }
    }

    private companion object {
        const val POLL_MS = 8_000L
    }
}

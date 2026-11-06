package io.github.eslamasabry.oc.android.termux

import io.github.eslamasabry.oc.core.common.AppDispatchers
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What the Flutter app's Termux setup file (`files/termux-setup-v2.json`, plan 05: left untouched) says. */
public enum class TermuxLegacySetup {
    /** The person set up Termux from the Flutter app and that setup is not marked running. */
    SetUp,

    /** The file says a setup job was still running when the Flutter app last wrote it. */
    WasRunning,
}

/** Reads `termux-setup-v2.json` without changing it. Null when the person never set up Termux in the app. */
public class TermuxLegacySetupFile(private val file: File) {
    public fun read(): TermuxLegacySetup? {
        if (!file.isFile) return null
        val state =
            try {
                ((Json.parseToJsonElement(file.readText()) as? JsonObject)?.get("state") as? JsonPrimitive)
                    ?.content
            } catch (_: Exception) {
                // A damaged file still proves Termux was set up from the app.
                null
            }
        return if (state == "running") TermuxLegacySetup.WasRunning else TermuxLegacySetup.SetUp
    }

    public companion object {
        public const val FILE_NAME: String = "termux-setup-v2.json"

        public fun inApp(filesDir: File): TermuxLegacySetupFile = TermuxLegacySetupFile(File(filesDir, FILE_NAME))
    }
}

/** The app-wide "already told" flag (`oc.termuxMoveNoticeSeen`, registered by the app's Termux wiring). */
public interface TermuxMoveNoticeMemory {
    public suspend fun seen(): Boolean

    public suspend fun markSeen()
}

/**
 * The words the notice uses; the app resolves them (English and Arabic) in its notice slot from the Android string
 * named [resource].
 */
public enum class TermuxMoveNoticeWord(public val resource: String) {
    Notice("andr_termuxMoveNotice"),
    NoticeSetupRunning("andr_termuxMoveNoticeSetupRunning"),
    SetUpBuiltin("andr_termuxMoveNoticeSetUpBuiltin"),
}

/** The one notice to show. [setupWasRunning] picks the sentence that says Termux setup carries on in Termux. */
public data class TermuxMoveNoticeState(val setupWasRunning: Boolean) {
    public val message: TermuxMoveNoticeWord
        get() = if (setupWasRunning) TermuxMoveNoticeWord.NoticeSetupRunning else TermuxMoveNoticeWord.Notice

    public val action: TermuxMoveNoticeWord
        get() = TermuxMoveNoticeWord.SetUpBuiltin
}

/**
 * E09-S04: a person who set up Termux in the Flutter app is told once that this version no longer sets up or
 * manages Termux, that their Termux server still connects while Termux runs it, and is offered built-in Linux.
 *
 * It shows only when `termux-setup-v2.json` exists and the person has not closed it yet; "Set up built-in Linux" and
 * Dismiss both close it for good. It never touches profiles: Termux server profiles stay as they are and connect
 * like any other server whenever Termux answers. Results Termux delivered after the update
 * ([TermuxLateResults]) are folded into the sentence and cleared when the notice closes.
 */
public class TermuxMoveNotice(
    private val setupFile: TermuxLegacySetupFile,
    private val late: TermuxLateResults,
    private val memory: TermuxMoveNoticeMemory,
    private val dispatchers: AppDispatchers,
) {
    private val mutable = MutableStateFlow<TermuxMoveNoticeState?>(null)
    public val state: StateFlow<TermuxMoveNoticeState?> = mutable.asStateFlow()
    private var closed = false

    /** Reads the files again; call when the app starts or comes back to the foreground. */
    public suspend fun check() {
        val next =
            withContext(dispatchers.io) {
                if (closed || memory.seen()) return@withContext null
                val setup = setupFile.read() ?: return@withContext null
                val setupAnswered = late.read().any { it.kind == TermuxResultKind.Setup }
                TermuxMoveNoticeState(setupWasRunning = setup == TermuxLegacySetup.WasRunning || setupAnswered)
            }
        if (!closed) mutable.value = next
    }

    /** "Set up built-in Linux" or Dismiss: the notice does not come back. The caller opens the setup page. */
    public suspend fun close() {
        closed = true
        mutable.value = null
        withContext(dispatchers.io) {
            memory.markSeen()
            late.clear()
        }
    }
}

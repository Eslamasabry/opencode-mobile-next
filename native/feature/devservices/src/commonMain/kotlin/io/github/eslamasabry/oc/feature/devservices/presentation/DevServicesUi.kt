package io.github.eslamasabry.oc.feature.devservices.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServiceStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One saved service row. */
public data class ServiceRowUi(
    val id: String,
    val name: String,
    val command: String,
    val url: String,
    val status: DevelopmentServiceStatus,
    val exitCode: Int?,
    val canStart: Boolean,
    val canStop: Boolean,
    /** Ports its own output printed (found by the phone or shell scan). */
    val ports: ImmutableList<Int> = persistentListOf(),
    /** The last read log tail, plain text; null until Logs is asked. */
    val log: String? = null,
)

/** One "Also running" row: a dev server this project did not save. */
public data class FoundRowUi(
    val id: String,
    val name: String,
    val command: String,
    val ports: ImmutableList<Int>,
    val canStop: Boolean,
    val hasLogs: Boolean,
    val canSave: Boolean,
    val log: String? = null,
)

/** Why the page or the last act did not work, in plain words; the failure itself is for Details only. */
public enum class DevServicesProblem { ScopeChanged, Unavailable, Offline, LogFailed, NotSaved, Failed }

/** The add form's checks (Flutter `_ServiceEditor.save`). */
public enum class NameError { Required, Duplicate }

public data class EditorUi(
    val name: String = "",
    val command: String = "",
    val url: String = "",
    val nameError: NameError? = null,
    val commandMissing: Boolean = false,
    val urlInvalid: Boolean = false,
)

/** A question the page asks before an act that ends or forgets something. */
public sealed interface DevServicesAsk {
    public val name: String

    public data class Stop(val id: String, override val name: String) : DevServicesAsk

    public data class Restart(val id: String, override val name: String) : DevServicesAsk

    public data class Forget(val id: String, override val name: String) : DevServicesAsk

    public data class Remove(val id: String, override val name: String, val running: Boolean) : DevServicesAsk

    public data class StopFound(val id: String, override val name: String) : DevServicesAsk
}

/** A short receipt after an act that worked. */
public sealed interface DevServicesReceipt {
    public data class Started(val name: String) : DevServicesReceipt

    public data class Removed(val name: String) : DevServicesReceipt
}

public data class DevServicesUi(
    /** False when the server or folder is not loaded: only the notice shows. */
    val available: Boolean = true,
    val directory: String = "",
    val workspace: String? = null,
    /** The server can start and track commands; without it services are saved and listed only (B-LINUX-061). */
    val supported: Boolean = false,
    val readable: Boolean = true,
    val busy: Boolean = false,
    val loading: Boolean = true,
    val services: ImmutableList<ServiceRowUi> = persistentListOf(),
    val found: ImmutableList<FoundRowUi> = persistentListOf(),
    /** The phone is scanned (this phone's own server with the in-app Linux) rather than only the server's shells. */
    val scansPhone: Boolean = false,
    /** The last phone scan failed; the list shown is the one before it (B-LINUX-056). */
    val foundFailed: Boolean = false,
    val problem: DevServicesProblem? = null,
    val failure: Failure? = null,
    val editor: EditorUi? = null,
    val ask: DevServicesAsk? = null,
    val receipt: DevServicesReceipt? = null,
)

public interface DevServicesActions {
    public fun shown(visible: Boolean)

    public fun refresh()

    public fun add()

    /** Add a found server's command as a saved service (opens the form filled in). */
    public fun saveFound(id: String)

    public fun edit(name: String, command: String, url: String)

    public fun save()

    public fun closeEditor()

    public fun start(id: String)

    public fun askStop(id: String)

    public fun askRestart(id: String)

    public fun askForget(id: String)

    public fun askRemove(id: String)

    public fun logs(id: String)

    public fun visit(id: String)

    public fun askStopFound(id: String)

    public fun foundLogs(id: String)

    public fun confirm()

    public fun cancel()

    public fun dismiss()

    public fun back()
}

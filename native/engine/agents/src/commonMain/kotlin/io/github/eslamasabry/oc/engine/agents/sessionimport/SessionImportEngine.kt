package io.github.eslamasabry.oc.engine.agents.sessionimport

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionTransferImportGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public enum class ImportNotice {
    Invalid,
    TooLarge,
    Changed,
    Unsupported,
    Conflict,
    Authorization,
    ParentMissing,
    Rejected,
    Unconfirmed,
}

public data class SessionImportUi(
    val directory: String,
    val workspace: String,
    val document: SessionImportDocument? = null,
    val working: Boolean = false,
    val notice: ImportNotice? = null,
    val attempted: Boolean = false,
    val imported: SessionId? = null,
    val closed: Boolean = false,
    val filePickerAvailable: Boolean = false,
    val workspaceAllowed: Boolean = false,
)

/**
 * One reviewed import on the exact graph that opened it. No persistence or retries of a mutation.
 */
public class SessionImportEngine(
    private val gateway: ServerGateway,
    private val isCurrent: () -> Boolean,
    private val files: SessionImportFiles?,
    private val dispatchers: AppDispatchers,
    initialLocation: ServerLocation,
) {
    private val mutable =
        MutableStateFlow(
            SessionImportUi(
                initialLocation.directory.orEmpty(),
                initialLocation.workspace.orEmpty(),
                filePickerAvailable = files != null,
                workspaceAllowed = gateway.capabilities.value.managedWorkspaces,
            )
        )
    public val state: StateFlow<SessionImportUi> = mutable.asStateFlow()
    private val lane = Mutex()
    private var generation = 0L
    private val pinnedLocation = gateway.location.value

    private fun current(): Boolean = isCurrent() && gateway.location.value == pinnedLocation

    public fun destination(directory: String, workspace: String) {
        if (state.value.working || state.value.attempted || state.value.closed) return
        mutable.value =
            state.value.copy(directory = directory, workspace = workspace, notice = null)
    }

    public suspend fun choose(): Unit = lane.withLock {
        if (state.value.working || state.value.attempted || state.value.closed) return@withLock
        if (!current()) {
            mutable.value = state.value.copy(notice = ImportNotice.Changed)
            return@withLock
        }
        val before = generation
        mutable.value = state.value.copy(working = true, notice = null)
        val selected = files?.choose()
        if (generation != before || state.value.closed) return@withLock
        if (!current()) {
            mutable.value = state.value.copy(working = false, notice = ImportNotice.Changed)
            return@withLock
        }
        when (selected) {
            is Outcome.Ok ->
                mutable.value =
                    state.value.copy(
                        document = selected.value ?: state.value.document,
                        working = false,
                    )
            is Outcome.Failed ->
                mutable.value =
                    state.value.copy(
                        working = false,
                        notice =
                            if (selected.failure is Failure.StorageRefused) ImportNotice.TooLarge
                            else ImportNotice.Invalid,
                    )
            null -> mutable.value = state.value.copy(working = false, notice = ImportNotice.Invalid)
        }
    }

    public suspend fun review(text: String): Unit = lane.withLock {
        if (state.value.working || state.value.attempted || state.value.closed) return@withLock
        if (!current()) {
            mutable.value = state.value.copy(notice = ImportNotice.Changed)
            return@withLock
        }
        val before = generation
        mutable.value = state.value.copy(working = true, document = null, notice = null)
        val parsed =
            withContext(dispatchers.default) {
                SessionImportDocument.read(sequenceOf(text.encodeToByteArray()))
            }
        if (generation != before || state.value.closed) return@withLock
        if (!current()) {
            mutable.value = state.value.copy(working = false, notice = ImportNotice.Changed)
            return@withLock
        }
        mutable.value =
            when (parsed) {
                is SessionImportResult.Ok ->
                    state.value.copy(document = parsed.value, working = false)
                is SessionImportResult.Refused ->
                    state.value.copy(
                        working = false,
                        notice =
                            if (parsed.problem == SessionImportProblem.TooLarge)
                                ImportNotice.TooLarge
                            else ImportNotice.Invalid,
                    )
            }
    }

    public suspend fun send(): Unit = lane.withLock {
        val ui = state.value
        if (ui.working || ui.attempted || ui.closed) return@withLock
        val doc = ui.document ?: return@withLock
        if (!current()) {
            mutable.value = ui.copy(notice = ImportNotice.Changed)
            return@withLock
        }
        if (ui.workspace.isNotEmpty() && !gateway.capabilities.value.managedWorkspaces) {
            mutable.value = ui.copy(notice = ImportNotice.Unsupported)
            return@withLock
        }
        val destination = SessionImportDestination(ui.directory, ui.workspace.ifEmpty { null })
        val body =
            when (val checked = doc.requestBody(destination)) {
                is SessionImportResult.Ok -> checked.value
                is SessionImportResult.Refused -> {
                    mutable.value = ui.copy(notice = ImportNotice.Invalid)
                    return@withLock
                }
            }
        val importer = gateway as? SessionTransferImportGateway
        if (!gateway.capabilities.value.sessionImportExport || importer == null) {
            mutable.value = ui.copy(notice = ImportNotice.Unsupported)
            return@withLock
        }
        when (gateway.awaitActionTransport()) {
            is Outcome.Failed -> {
                mutable.value = ui.copy(notice = ImportNotice.Changed)
                return@withLock
            }
            is Outcome.Ok -> Unit
        }
        if (!current() || state.value.closed) {
            mutable.value = state.value.copy(notice = ImportNotice.Changed)
            return@withLock
        }
        val before = generation
        mutable.value = ui.copy(working = true, attempted = true, notice = null)
        val sent = importer.importSessionDocument(body)
        if (generation != before || state.value.closed) return@withLock
        mutable.value =
            when (sent) {
                is Outcome.Ok -> {
                    val value = sent.value
                    if (
                        value.id.raw != doc.id ||
                            value.directory != destination.directory ||
                            value.workspaceId?.raw != destination.workspaceId
                    )
                        state.value.copy(working = false, notice = ImportNotice.Unconfirmed)
                    else state.value.copy(working = false, imported = value.id)
                }
                is Outcome.Failed ->
                    state.value.copy(working = false, notice = notice(sent.failure))
            }
    }

    /**
     * An unconfirmed attempt can only be retried by reopening and consciously reviewing the source
     * again.
     */
    public fun close() {
        generation++
        mutable.value = state.value.copy(document = null, closed = true)
    }

    public fun mayOpen(): Boolean =
        current() && !state.value.closed && state.value.imported != null

    private fun notice(f: Failure): ImportNotice =
        when (f) {
            is Failure.Unavailable -> ImportNotice.Unsupported
            is Failure.Conflict -> ImportNotice.Conflict
            is Failure.SignIn -> ImportNotice.Authorization
            is Failure.NotFound ->
                if (f.tag?.raw == "SessionNotFoundError") ImportNotice.ParentMissing
                else ImportNotice.Unconfirmed
            is Failure.Rejected ->
                if (f.status == 400) ImportNotice.Rejected else ImportNotice.Unconfirmed
            else -> ImportNotice.Unconfirmed
        }
}

package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.Redactor

public enum class HandoffEntryKind(public val wire: String) { Directory("directory"), File("file") }

/** `fs.entry.create.response`: [cwd] and [parentPath] echo the request; [path] is set on success. */
public data class HandoffEntryCreated(val cwd: String?, val parentPath: String?, val path: String?, val success: Boolean)

public data class HandoffListEntry(val name: String?, val kind: String?)

/** `file_explorer_response` in list mode. Null [entries]: the reply had no entry list. */
public data class HandoffListing(val cwd: String?, val path: String?, val error: Boolean, val entries: List<HandoffListEntry>?)

public data class HandoffFileData(
    val path: String?,
    val kind: String?,
    val encoding: String?,
    val content: String?,
    val modifiedAt: String?,
    val revision: String?,
)

/** `file_explorer_response` in file mode. */
public data class HandoffFileRead(val cwd: String?, val path: String?, val error: Boolean, val file: HandoffFileData?)

/**
 * The workspace file RPCs of one live connection pinned to one directory (Paseo `fs.entry.create`, `file_explorer`,
 * `fs.file.write`). Every call is a request in [scope]; adapters send `cwd` = the scope.
 */
public interface HandoffWorkspace {
    /** The directory this connection is pinned to. */
    public val scope: String?

    /** Connected and the daemon negotiated `workspaceFileEditing` (ServerCapabilities.agentHandoffFiles). */
    public val handoffFiles: Boolean

    /** Changes on every reconnect or location change; a change voids the transfer. */
    public val epoch: Long

    public suspend fun create(parent: String, name: String, kind: HandoffEntryKind): Outcome<HandoffEntryCreated>

    public suspend fun list(path: String): Outcome<HandoffListing>

    public suspend fun read(path: String, maxBytes: Int): Outcome<HandoffFileRead>

    /** The write status (`written`, `conflict`, ...); null when the reply had none. */
    public suspend fun write(path: String, content: String, expectedModifiedAt: String, expectedRevision: String?): Outcome<String?>
}

/** The host payload lease every file mutation holds (Flutter `beforePayloadUse` / `afterPayloadUse`). */
public interface HandoffHostLease {
    /** Failed: the host refuses payload use now (maintenance, update); nothing is sent. */
    public suspend fun acquire(): Outcome<Unit>

    public fun release()
}

public sealed interface HandoffFileWrite {
    /** The relative record path, returned only after content read-back. */
    public data class Written(val relativePath: String) : HandoffFileWrite

    public data class Refused(val reason: HandoffFileRefusal) : HandoffFileWrite
}

public enum class HandoffFileRefusal {
    /** The host lease was refused; nothing was sent. */
    HostUnavailable,
    /** Feature not negotiated, no connection, other directory, bad record id, empty or oversized record. */
    RecordRefused,
    /** The opaque-token mask would still change the record. */
    NotFullyMasked,
    /** The connection or location changed during the transfer. */
    Stale,
    /** A reply did not echo the request's scope or path, or was malformed. */
    InvalidResponse,
    /** `.handoff` exists but is not a real folder (a symlink or file). */
    NoHandoffFolder,
    /** The per-record folder already exists: never reused. */
    FolderExists,
    /** A new file could not be created exclusively. */
    FileExists,
    /** The freshly created file was not empty. */
    NewFileNotEmpty,
    /** The write was not confirmed as written (conflict or other). */
    WriteNotConfirmed,
    /** The read-back differs from what was written. */
    ReadBackDiffers,
}

/**
 * Port of the Paseo record-file rules (lib/paseo/gateway/handoff.dart): each transfer gets an exclusively created
 * `.handoff/<recordId>/` folder; its own `.gitignore` is written and verified before the record; nothing existing is
 * overwritten or reused (a shared `.handoff/.gitignore` is never touched); a `.handoff` that is not a folder is refused.
 * The feature must be negotiated on a live connection, and the scope, record id and byte limit are checked before any
 * request.
 */
public class HandoffRecordFiles(
    private val workspace: HandoffWorkspace,
    private val lease: HandoffHostLease,
    private val redactor: Redactor = Redactor(),
) {
    private class Stop(val result: Outcome<HandoffFileWrite>) : Exception()

    public suspend fun write(directory: String, recordId: String, markdown: String): Outcome<HandoffFileWrite> {
        if (lease.acquire() is Outcome.Failed) return refused(HandoffFileRefusal.HostUnavailable)
        try {
            return writeLeased(directory, recordId, markdown)
        } finally {
            lease.release()
        }
    }

    private suspend fun writeLeased(directory: String, recordId: String, markdown: String): Outcome<HandoffFileWrite> {
        val scope = workspace.scope
        if (!workspace.handoffFiles || scope == null || directory != scope || !RECORD_ID.matches(recordId) ||
            markdown.isEmpty() || utf8Length(markdown) > MAX_BYTES
        ) {
            return refused(HandoffFileRefusal.RecordRefused)
        }
        // The record builder masks credentials per piece and token-like runs on the whole record; only the second
        // holds for the whole text, so only it is checked here.
        if (redactor.opaqueTokens(markdown) != markdown) return refused(HandoffFileRefusal.NotFullyMasked)
        val epoch = workspace.epoch
        fun current() = workspace.handoffFiles && workspace.scope == scope && workspace.epoch == epoch

        suspend fun <T> request(block: suspend () -> Outcome<T>): T {
            if (!current()) throw Stop(refused(HandoffFileRefusal.Stale))
            val result = block()
            if (!current()) throw Stop(refused(HandoffFileRefusal.Stale))
            return when (result) {
                is Outcome.Ok -> result.value
                is Outcome.Failed -> throw Stop(result)
            }
        }

        suspend fun create(parent: String, name: String, kind: HandoffEntryKind): Boolean {
            val reply = request { workspace.create(parent, name, kind) }
            val path = if (parent == ".") name else "$parent/$name"
            if (reply.cwd != scope || reply.parentPath != parent) throw Stop(refused(HandoffFileRefusal.InvalidResponse))
            return reply.success && reply.path == path
        }

        suspend fun read(path: String): HandoffFileData {
            val reply = request { workspace.read(path, MAX_BYTES) }
            val file = reply.file
            if (reply.error || reply.cwd != scope || reply.path != path || file == null || file.path != path ||
                file.kind != "text" || file.encoding != "utf-8" || file.content == null || file.modifiedAt == null
            ) {
                throw Stop(refused(HandoffFileRefusal.InvalidResponse))
            }
            return file
        }

        suspend fun writeNew(parent: String, name: String, content: String) {
            if (!create(parent, name, HandoffEntryKind.File)) throw Stop(refused(HandoffFileRefusal.FileExists))
            val path = "$parent/$name"
            val empty = read(path)
            if (empty.content != "") throw Stop(refused(HandoffFileRefusal.NewFileNotEmpty))
            val status = request { workspace.write(path, content, empty.modifiedAt!!, empty.revision) }
            if (status != "written") throw Stop(refused(HandoffFileRefusal.WriteNotConfirmed))
            if (read(path).content != content) throw Stop(refused(HandoffFileRefusal.ReadBackDiffers))
        }

        return try {
            // Never alter an existing ignore file: every transfer gets its own exclusively created folder.
            if (!create(".", HANDOFF_FOLDER, HandoffEntryKind.Directory)) {
                val listing = request { workspace.list(".") }
                val entries = listing.entries
                if (listing.error || listing.cwd != scope || listing.path != "." || entries == null ||
                    entries.none { it.name == HANDOFF_FOLDER && it.kind == "directory" }
                ) {
                    throw Stop(refused(HandoffFileRefusal.NoHandoffFolder))
                }
            }
            val folder = "$HANDOFF_FOLDER/$recordId"
            if (!create(HANDOFF_FOLDER, recordId, HandoffEntryKind.Directory)) throw Stop(refused(HandoffFileRefusal.FolderExists))
            writeNew(folder, ".gitignore", IGNORE)
            writeNew(folder, "record.md", markdown)
            HandoffFileWrite.Written("$folder/record.md").ok()
        } catch (stop: Stop) {
            stop.result
        }
    }

    private fun refused(reason: HandoffFileRefusal): Outcome<HandoffFileWrite> = HandoffFileWrite.Refused(reason).ok()

    public companion object {
        public const val HANDOFF_FOLDER: String = ".handoff"
        public const val IGNORE: String = "# OpenCode Mobile conversation handoff\n*\n"
        public const val MAX_BYTES: Int = 98_304
        public val RECORD_ID: Regex = Regex("^[a-zA-Z0-9_-]{1,128}$")

        /** ServerCapabilities.agentHandoffFiles for a Paseo connection. */
        public fun available(connected: Boolean, workspaceFileEditing: Boolean): Boolean = connected && workspaceFileEditing
    }
}

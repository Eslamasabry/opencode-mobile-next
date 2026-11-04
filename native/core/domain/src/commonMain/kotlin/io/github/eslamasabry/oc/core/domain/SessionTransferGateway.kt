package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.serialization.json.JsonObject

/**
 * Complete OpenCode export bytes; neither a transcript projection nor a reconstructed JSON
 * document.
 */
public interface SessionTransferGateway {
    public suspend fun exportSession(
        session: SessionId,
        sanitize: Boolean = true,
    ): Outcome<ByteArray>

    /**
     * Source ids and unknown fields stay intact; destination is an explicit body member. Never
     * retry a write.
     */
    public suspend fun importSession(body: JsonObject): Outcome<Session>
}

/**
 * Only the mobile-owned `mobile.note` entry is exposed. Other instruction entries never leave the
 * adapter.
 */
public interface SessionNotesGateway {
    public suspend fun loadSessionNote(session: SessionId): Outcome<String?>

    public suspend fun saveSessionNote(
        session: SessionId,
        value: String,
    ): Outcome<SessionNoteSaveResult>

    public suspend fun removeSessionNote(session: SessionId): Outcome<Unit>
}

public sealed interface SessionNoteSaveResult {
    public data object Saved : SessionNoteSaveResult

    public data class TooLarge(val maxBytes: Int) : SessionNoteSaveResult
}

/**
 * User-mediated document access. Implementations read/write only the exact system-picker result.
 */
public interface SessionDocuments {
    /** null means cancelled; implementations refuse files larger than [maxBytes] before reading. */
    public suspend fun chooseJson(maxBytes: Long): Outcome<ByteArray?>

    public suspend fun saveJson(name: String, bytes: ByteArray): Outcome<Boolean>
}

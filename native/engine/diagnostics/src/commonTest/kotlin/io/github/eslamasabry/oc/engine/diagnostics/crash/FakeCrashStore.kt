package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome

/** An in-memory crash directory; [refuseWrites] makes every write fail like a full disk. */
internal class FakeCrashStore(vararg initial: Pair<CrashFile, String>) : CrashStore {
    val files: MutableMap<CrashFile, String> = mutableMapOf(*initial)
    var refuseWrites = false
    var touched = 0

    override suspend fun read(file: CrashFile, maxBytes: Int): Outcome<String?> {
        touched++
        val text = files[file] ?: return Outcome.Ok(null)
        if (text.encodeToByteArray().size > maxBytes) return Outcome.Failed(Failure.UnreadableReply())
        return Outcome.Ok(text)
    }

    override suspend fun write(file: CrashFile, text: String): Outcome<Unit> {
        touched++
        if (refuseWrites) return Outcome.Failed(Failure.StorageRefused())
        files[file] = text
        return Outcome.Ok(Unit)
    }

    override suspend fun delete(file: CrashFile): Outcome<Unit> {
        touched++
        files.remove(file)
        return Outcome.Ok(Unit)
    }
}

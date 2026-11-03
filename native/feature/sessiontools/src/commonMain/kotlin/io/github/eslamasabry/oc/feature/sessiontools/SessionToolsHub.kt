package io.github.eslamasabry.oc.feature.sessiontools

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.project.session.SessionExporter
import io.github.eslamasabry.oc.engine.project.session.SessionNotes

/**
 * One conversation's server-side tools (frozen contract; the app graph binds the real one). Every member answers for
 * the conversation's OWN profile and connection, never the active one.
 */
public interface SessionToolsHub {
    /** The conversation's export over its live connection; null when that server is not connected now. */
    public fun exporter(ref: ConversationRef): SessionExporter?

    /** The conversation's notes over its live connection and location; null when not connected now. */
    public fun notes(ref: ConversationRef): SessionNotes?

    /** The conversation's title for file names and headings; null when unknown. */
    public fun title(ref: ConversationRef): String?

    /** The system "create document" picker, then [bytes] into the picked place. Ok(false) = the person cancelled. */
    public suspend fun save(name: String, mime: String, bytes: ByteArray): Outcome<Boolean>
}

/** Desktop and tests: nothing is connected, so the pages say so; the app replaces it. */
@ContributesBinding(AppScope::class)
@Inject
public class OfflineSessionToolsHub : SessionToolsHub {
    override fun exporter(ref: ConversationRef): SessionExporter? = null

    override fun notes(ref: ConversationRef): SessionNotes? = null

    override fun title(ref: ConversationRef): String? = null

    override suspend fun save(name: String, mime: String, bytes: ByteArray): Outcome<Boolean> =
        Outcome.Failed(Failure.Unavailable(FeatureId("save")))
}

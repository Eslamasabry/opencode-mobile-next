package io.github.eslamasabry.oc.engine.connection.adopted

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape

/**
 * The chat keys the Flutter app (2.1.4+62) left in `FlutterSharedPreferences.xml`, read IN PLACE (story E00-S03,
 * plan 05 §7.8 A). Nothing here writes: a value that does not read as Dart wrote it is ignored and left exactly as it
 * is on disk, so a later Flutter-shaped writer (or a downgrade) still finds it.
 *
 * - `oc.lastProject.<profileId>` (`lib/state/connection/chat_feed.dart`): the folder last opened on that server.
 * - `oc.chatTail.<profileId>` (`lib/state/session_tail_cache.dart`): JSON
 *   `{v:1, scope, session, at, rows:[{id, role, text}]}`.
 * - `oc.chatNoticesSeen.<profileId>` (`lib/state/chat_notice_memory.dart`): string list of `<note>:<sessionID>`.
 * - `oc.withdrawnPrompts.<profileId>` (`lib/state/chat_notice_memory.dart`): string list of `<sessionID>|<messageID>`.
 * - `oc.providerRuntimeRefresh.v1.<profileId>.<location>` (`lib/state/profiles.dart`): bool.
 * - `oc.providerRuntimeUnloadable.<profileId>.<location>` (`lib/state/profiles.dart`): sorted comma-joined ids.
 *
 * `<location>` is Dart `Uri.encodeComponent("<directory or <default>>\n<workspace or <default>>")`. Every key carries
 * the profile id as its last or an interior segment, so the generic profile sweep (`ProfileKeyRule`) removes them
 * with the server; [SHAPES] registers them for the ledger check.
 *
 * Reads go through [KeyValueStore], so they are valid only once the migration gate is Open, like every other read.
 */
public class AdoptedChatKeys(private val store: KeyValueStore) {
    /**
     * What the chats screen paints before any network read: the project last used on [profileId] and the chat tail
     * kept for it (null when either is absent or unreadable).
     */
    public fun startState(profileId: ProfileId): AdoptedStartState =
        AdoptedStartState(lastProject = lastProject(profileId), chatTail = chatTail(profileId))

    /** The folder last opened on this server, as Flutter saved it; null when absent, blank or not a plain path. */
    public fun lastProject(profileId: ProfileId): String? =
        (store.get(lastProjectKey(profileId)) as? PrefValue.Str)?.value
            ?.takeUnless { it.isBlank() || it.length > MAX_PATH || it.any { c -> c < ' ' } }

    /**
     * The text-only tail Flutter kept for the most recent conversation on this server, whatever conversation it is.
     * Never authoritative history: tools, reasoning, attachments and older-history cursors are absent.
     */
    public fun chatTail(profileId: ProfileId): ChatTail? {
        val raw = (store.get(chatTailKey(profileId)) as? PrefValue.Str)?.value ?: return null
        return parseChatTail(raw)
    }

    /** The tail only when it belongs to [session] in [scope] (Dart `SessionTailCache.read`). */
    public fun chatTail(profileId: ProfileId, scope: String, session: String): ChatTail? =
        chatTail(profileId)?.takeIf { it.scope == scope && it.session == session }

    /** Whether the standing note [notice] (e.g. [AUTO_APPROVE_NOTICE]) was already said in [sessionId]. */
    public fun noticeSeen(profileId: ProfileId, notice: String, sessionId: String): Boolean =
        "$notice:$sessionId" in list(chatNoticesSeenKey(profileId))

    /** The message ids of [sessionId] the server withdrew and the chat sent again; the chat leaves those copies out. */
    public fun withdrawnPrompts(profileId: ProfileId, sessionId: String): Set<String> {
        val prefix = "$sessionId|"
        return list(withdrawnPromptsKey(profileId)).filter { it.startsWith(prefix) }.mapTo(LinkedHashSet()) {
            it.substring(prefix.length)
        }
    }

    /** Whether the one-time provider runtime refresh already ran at this location. */
    public fun providerRuntimeRefreshed(
        profileId: ProfileId,
        directory: String? = null,
        workspace: String? = null,
    ): Boolean =
        (store.get(providerRuntimeRefreshKey(profileId, directory, workspace)) as? PrefValue.Bool)?.value ?: false

    /** Providers that stayed unloaded after a refresh at this location, as a sorted comma-joined id list. */
    public fun providerRuntimeUnloadable(
        profileId: ProfileId,
        directory: String? = null,
        workspace: String? = null,
    ): String? =
        (store.get(providerRuntimeUnloadableKey(profileId, directory, workspace)) as? PrefValue.Str)?.value

    /** Both Flutter list encodings read as a list; any other type or an undecodable list reads as empty (Dart `[]`). */
    private fun list(key: String): List<String> = (store.get(key) as? PrefValue.StrList)?.value.orEmpty()

    public companion object {
        public const val LAST_PROJECT_PREFIX: String = "oc.lastProject."
        public const val CHAT_TAIL_PREFIX: String = "oc.chatTail."
        public const val CHAT_NOTICES_SEEN_PREFIX: String = "oc.chatNoticesSeen."
        public const val WITHDRAWN_PROMPTS_PREFIX: String = "oc.withdrawnPrompts."
        public const val PROVIDER_RUNTIME_REFRESH_PREFIX: String = "oc.providerRuntimeRefresh.v1."
        public const val PROVIDER_RUNTIME_UNLOADABLE_PREFIX: String = "oc.providerRuntimeUnloadable."

        /** Dart `ChatNoticeMemory.autoApprove`. */
        public const val AUTO_APPROVE_NOTICE: String = "auto-approve"

        /** Dart `SessionTailCache`: a longer stored value is not read. */
        public const val MAX_TAIL_CHARS: Int = 100_000

        /** Dart `SessionTailCache`: at most this many rows. */
        public const val MAX_TAIL_ROWS: Int = 12

        private const val MAX_PATH = 4_096
        private const val OWNER = ":engine:connection"

        public val SHAPES: List<KeyShape> = listOf(
            KeyShape(LAST_PROJECT_PREFIX, KeyScope.Profile, PrefType.Str, OWNER, "project last used per server"),
            KeyShape(CHAT_TAIL_PREFIX, KeyScope.Profile, PrefType.Str, OWNER, "chat tail for the first paint"),
            KeyShape(CHAT_NOTICES_SEEN_PREFIX, KeyScope.Profile, PrefType.StrList, OWNER, "chat notes already said"),
            KeyShape(WITHDRAWN_PROMPTS_PREFIX, KeyScope.Profile, PrefType.StrList, OWNER, "withdrawn copies left out"),
            KeyShape(PROVIDER_RUNTIME_REFRESH_PREFIX, KeyScope.Profile, PrefType.Bool, OWNER, "provider refresh done"),
            KeyShape(PROVIDER_RUNTIME_UNLOADABLE_PREFIX, KeyScope.Profile, PrefType.Str, OWNER, "unloadable providers"),
        )

        public fun lastProjectKey(profileId: ProfileId): String = LAST_PROJECT_PREFIX + profileId.raw

        public fun chatTailKey(profileId: ProfileId): String = CHAT_TAIL_PREFIX + profileId.raw

        public fun chatNoticesSeenKey(profileId: ProfileId): String = CHAT_NOTICES_SEEN_PREFIX + profileId.raw

        public fun withdrawnPromptsKey(profileId: ProfileId): String = WITHDRAWN_PROMPTS_PREFIX + profileId.raw

        public fun providerRuntimeRefreshKey(
            profileId: ProfileId,
            directory: String? = null,
            workspace: String? = null,
        ): String =
            PROVIDER_RUNTIME_REFRESH_PREFIX + profileId.raw + "." + location(directory, workspace)

        public fun providerRuntimeUnloadableKey(
            profileId: ProfileId,
            directory: String? = null,
            workspace: String? = null,
        ): String =
            PROVIDER_RUNTIME_UNLOADABLE_PREFIX + profileId.raw + "." + location(directory, workspace)

        /** Dart `Uri.encodeComponent('${directory ?? '<default>'}\n${workspace ?? '<default>'}')`. */
        private fun location(directory: String?, workspace: String?): String =
            DartJson.encodeComponent("${directory ?: "<default>"}\n${workspace ?: "<default>"}")

        /** Dart `SessionTailCache.read` without the scope/session match: null for anything Dart would not read. */
        internal fun parseChatTail(raw: String): ChatTail? {
            val data = raw.takeIf { it.length <= MAX_TAIL_CHARS }?.let(DartJson::decodeOrNull) as? Map<*, *>
            val rows = (data?.get("rows") as? List<*>)?.takeIf { it.size <= MAX_TAIL_ROWS }?.map(::parseRow)
            val head = data?.takeIf { it["v"] == 1L }?.let(::parseHead)
            return if (head != null && rows != null && null !in rows) head.copy(rows = rows.filterNotNull()) else null
        }

        private fun parseHead(data: Map<*, *>): ChatTail? {
            val scope = data["scope"] as? String
            val session = data["session"] as? String
            val at = data["at"] as? Long
            return if (scope != null && session != null && at != null) {
                ChatTail(scope, session, at, emptyList())
            } else {
                null
            }
        }

        private fun parseRow(row: Any?): ChatTailRow? {
            val map = row as? Map<*, *>
            val id = map?.get("id") as? String
            val role = map?.get("role") as? String
            val text = map?.get("text") as? String
            return if (id != null && role != null && text != null) ChatTailRow(id, role, text) else null
        }
    }
}

/** What [AdoptedChatKeys.startState] gives the first paint. */
public data class AdoptedStartState(val lastProject: String?, val chatTail: ChatTail?) {
    override fun toString(): String = "AdoptedStartState(lastProject=${lastProject != null}, chatTail=$chatTail)"
}

/** The Flutter chat tail: one conversation's newest text excerpts, oldest first. */
public data class ChatTail(val scope: String, val session: String, val fetchedAtMs: Long, val rows: List<ChatTailRow>) {
    override fun toString(): String = "ChatTail(rows=${rows.size}, fetchedAtMs=$fetchedAtMs)"
}

/** One excerpt. [toString] never prints the text (AGENTS.md rule 7). */
public data class ChatTailRow(val id: String, val role: String, val text: String) {
    override fun toString(): String = "ChatTailRow(role=$role, len=${text.length})"
}

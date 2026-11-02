package io.github.eslamasabry.oc.android.tiles

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.json.JSONArray
import org.json.JSONObject

/** One pinned session to publish: its id and its title (blank becomes "Untitled conversation"). */
data class PinnedSession(val sessionId: String, val title: String)

/** What [PinnedShortcuts.withdrawProfile] did (the Flutter `PinnedShortcutClear`). */
enum class ShortcutClear { NothingToClear, Cleared, Failed }

/**
 * Dynamic launcher shortcuts for the connected profile's pinned sessions (B-ANDR-030, B-ANDR-031, B-ANDR-053,
 * B-ANDR-054): a port of the Flutter `PinnedSessionShortcuts.kt` plus the Dart writer's record. Only ids and titles
 * leave the app, at most [MAX_SHORTCUTS], titles cut to [MAX_TITLE_LENGTH]. Shortcut ids are
 * `oc.session:<profileId>:<sessionId>`, as in the Flutter app, so shortcuts pinned before the switch keep working.
 *
 * Each shortcut opens MainActivity with exactly [LaunchActions.EXTRA_PROFILE] and [LaunchActions.EXTRA_SESSION];
 * [LaunchActions.consumeSessionShortcut] reads them. Nothing here connects or sends.
 *
 * The published payload is recorded in [TilesComponents.PREFS] under `oc.pinnedShortcuts.<profileId>`; an unchanged
 * payload is not republished.
 */
object PinnedShortcuts {
    const val MAX_SHORTCUTS: Int = 4
    const val MAX_TITLE_LENGTH: Int = 80
    const val ID_PREFIX: String = "oc.session:"
    const val RECORD_PREFIX: String = "oc.pinnedShortcuts."

    fun recordKey(profileId: String): String = RECORD_PREFIX + profileId

    fun shortcutId(profileId: String, sessionId: String): String = "$ID_PREFIX$profileId:$sessionId"

    /**
     * Replaces the published set with [pins] for [profileId] (other profiles' dynamic entries are withdrawn, as in
     * the Flutter app: one connected profile at a time). An empty list withdraws everything and forgets the record.
     * Returns false when [profileId] is blank or the launcher refused.
     */
    fun publish(context: Context, profileId: String, pins: List<PinnedSession>): Boolean {
        if (profileId.isBlank()) return false
        val sessions = normalize(pins)
        val prefs = TilesComponents.prefs(context)
        val payload = payload(profileId, sessions)
        if (sessions.isNotEmpty() && !shouldPublish(prefs.getString(recordKey(profileId), null), payload)) return true
        val untitled = context.getString(R.string.shortcut_session_untitled)
        val next =
            sessions.map { pin ->
                val title = pin.title.ifEmpty { untitled }
                ShortcutInfoCompat.Builder(context, shortcutId(profileId, pin.sessionId))
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_session))
                    .setIntent(
                        Intent(Intent.ACTION_MAIN)
                            .setClassName(context, TilesComponents.MAIN_ACTIVITY)
                            .putExtra(LaunchActions.EXTRA_PROFILE, profileId)
                            .putExtra(LaunchActions.EXTRA_SESSION, pin.sessionId)
                    )
                    .build()
            }
        val keep = next.map { it.id }.toSet()
        val ok =
            try {
                disable(context, staleIds(launcherIds(context), keep, profileId = null))
                if (next.isNotEmpty()) {
                    ShortcutManagerCompat.setDynamicShortcuts(context, next)
                    // A session unpinned and pinned again may still have a disabled copy on the home screen.
                    try {
                        ShortcutManagerCompat.enableShortcuts(context, next)
                    } catch (_: IllegalArgumentException) {
                        // No pinned copy to re-enable.
                    }
                }
                true
            } catch (_: Exception) {
                // Launcher rate limits and missing launcher support: the entries keep their last state.
                false
            }
        if (ok) {
            val editor = prefs.edit()
            for (key in prefs.all.keys) if (key.startsWith(RECORD_PREFIX)) editor.remove(key)
            if (sessions.isNotEmpty()) editor.putString(recordKey(profileId), payload)
            editor.apply()
        }
        return ok
    }

    /**
     * Profile deletion: withdraws only [profileId]'s shortcuts (dynamic and user-pinned copies) and forgets its
     * record, unreadable or not. Another profile's shortcuts stay.
     */
    fun withdrawProfile(context: Context, profileId: String): ShortcutClear {
        if (profileId.isBlank()) return ShortcutClear.NothingToClear
        val prefs = TilesComponents.prefs(context)
        val hadRecord = prefs.contains(recordKey(profileId))
        return try {
            val stale = staleIds(launcherIds(context), keep = emptySet(), profileId = profileId)
            disable(context, stale)
            prefs.edit().remove(recordKey(profileId)).apply()
            if (hadRecord || stale.isNotEmpty()) ShortcutClear.Cleared else ShortcutClear.NothingToClear
        } catch (_: Exception) {
            ShortcutClear.Failed
        }
    }

    /** Explicit disconnect: withdraws every pinned-session shortcut and forgets every record. */
    fun withdrawAll(context: Context): Boolean {
        val prefs = TilesComponents.prefs(context)
        return try {
            disable(context, staleIds(launcherIds(context), keep = emptySet(), profileId = null))
            val editor = prefs.edit()
            for (key in prefs.all.keys) if (key.startsWith(RECORD_PREFIX)) editor.remove(key)
            editor.apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Pure: drops blank ids and duplicates, keeps pin order, caps at [MAX_SHORTCUTS], trims and cuts titles. */
    fun normalize(pins: List<PinnedSession>): List<PinnedSession> =
        pins
            .map { PinnedSession(it.sessionId.trim(), cutTitle(it.title)) }
            .filter { it.sessionId.isNotEmpty() }
            .distinctBy { it.sessionId }
            .take(MAX_SHORTCUTS)

    /** Pure: the recorded payload for [sessions] (already normalized). */
    fun payload(profileId: String, sessions: List<PinnedSession>): String {
        val list = JSONArray()
        for (s in sessions) list.put(JSONObject().put("id", s.sessionId).put("title", s.title))
        return JSONObject().put("profileID", profileId).put("sessions", list).toString()
    }

    /** Pure: publish unless the record already holds exactly [next]. */
    fun shouldPublish(previous: String?, next: String): Boolean = previous != next

    /**
     * Pure: which of the launcher's [existing] ids to withdraw. Only this app's session shortcuts; with [profileId]
     * only that profile's; never one in [keep].
     */
    fun staleIds(existing: Collection<String>, keep: Set<String>, profileId: String?): List<String> {
        val prefix = if (profileId == null) ID_PREFIX else "$ID_PREFIX$profileId:"
        return existing.filter { it.startsWith(prefix) && it !in keep }.distinct()
    }

    private fun cutTitle(title: String): String {
        val trimmed = title.trim()
        return if (trimmed.length <= MAX_TITLE_LENGTH) trimmed else trimmed.take(MAX_TITLE_LENGTH).trimEnd()
    }

    private fun launcherIds(context: Context): List<String> =
        ShortcutManagerCompat.getShortcuts(
            context,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_PINNED,
        ).map { it.id }

    /** Disabling withdraws a dynamic entry and greys out a user-pinned copy with a reason. */
    private fun disable(context: Context, ids: List<String>) {
        if (ids.isEmpty()) return
        ShortcutManagerCompat.disableShortcuts(
            context,
            ids,
            context.getString(R.string.shortcut_session_unavailable),
        )
    }
}

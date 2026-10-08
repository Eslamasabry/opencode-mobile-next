package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Dynamic launcher shortcuts (long-press the app icon) for the connected
 * profile's pinned sessions. The Dart side owns the payload — session id and
 * title only, at most four — and pushes it over `oc/shortcut`; this object
 * mirrors it onto the launcher and withdraws entries that are no longer
 * listed, including copies the user pinned to the home screen, so a deleted
 * or disconnected server leaves no title behind.
 *
 * Each shortcut's intent carries exactly two extras — the profile id and the
 * session id — and MainActivity.captureSessionLaunch hands them to Dart,
 * which decides whether to open the chat. Nothing here connects or sends.
 */
object PinnedSessionShortcuts {
    private const val ID_PREFIX = "oc.session:"

    /** Replaces the published set with [sessions]; an empty list withdraws all. */
    fun publish(
        context: Context,
        profileID: String,
        sessions: List<Map<String, Any?>>
    ): Boolean {
        val next = sessions.mapNotNull { entry ->
            val sessionID = entry["id"]?.toString()?.trim().orEmpty()
            if (sessionID.isEmpty() || profileID.isEmpty()) return@mapNotNull null
            val title = entry["title"]?.toString()?.trim().orEmpty()
                .ifEmpty { NativeStrings.get(context, R.string.shortcut_session_untitled) }
            ShortcutInfoCompat.Builder(context, "$ID_PREFIX$profileID:$sessionID")
                .setShortLabel(title)
                .setLongLabel(title)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_session))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        putExtra(MainActivity.EXTRA_LAUNCH_PROFILE, profileID)
                        putExtra(MainActivity.EXTRA_LAUNCH_SESSION, sessionID)
                    }
                )
                .build()
        }
        val nextIDs = next.map { it.id }.toSet()
        return try {
            val stale = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_PINNED
            ).map { it.id }.filter { it.startsWith(ID_PREFIX) && it !in nextIDs }
            // Disabling withdraws a dynamic entry and greys out a user-pinned
            // copy with a reason; plain removal would leave pinned copies
            // showing the old title.
            if (stale.isNotEmpty()) {
                ShortcutManagerCompat.disableShortcuts(
                    context,
                    stale,
                    NativeStrings.get(context, R.string.shortcut_session_unavailable)
                )
            }
            if (next.isNotEmpty()) {
                ShortcutManagerCompat.setDynamicShortcuts(context, next)
                // A session unpinned and pinned again may still have a
                // disabled copy on the home screen; bring it back.
                try {
                    ShortcutManagerCompat.enableShortcuts(context, next)
                } catch (_: IllegalArgumentException) {
                    // No pinned copy to re-enable.
                }
            }
            true
        } catch (_: Exception) {
            // Launcher rate limits and missing launcher support are not the
            // app's failure to report; the entries keep their last state.
            false
        }
    }
}

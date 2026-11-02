package io.github.eslamasabry.oc.android.tiles

import android.content.Context
import org.json.JSONObject

/**
 * The needs-attention count the Quick Settings tile shows (B-ANDR-010). The tile may be drawn while the app is not
 * running, so it reads this cached snapshot instead of asking an engine. The snapshot holds only the count, its
 * owner profile id and the time it was written: no titles, no request text, no server address.
 *
 * Stored in [TilesComponents.PREFS] under [KEY] as `{"pendingCount":Int,"owner":String,"updatedAt":Long}`. A
 * snapshot older than [MAX_AGE_MS] says nothing about now; the tile then shows a plain "OpenCode" and no count.
 */
object AttentionTileCache {
    const val KEY: String = "oc.attentionTile"
    const val MAX_AGE_MS: Long = 24L * 60 * 60 * 1000

    /**
     * An unchanged count is re-stamped once it is this old, so a count the app keeps confirming never ages into
     * "stale" (the Flutter writer compared with in-memory values and re-stamped on every process start).
     */
    const val RESTAMP_AFTER_MS: Long = 60L * 60 * 1000

    /**
     * Saves [count] (negative stored as 0) for [owner] and redraws the tile. Rewrites only when the count or owner
     * changed, or the stored stamp is older than [RESTAMP_AFTER_MS]. Returns true when it wrote.
     */
    fun write(context: Context, count: Int, owner: String, nowMs: Long): Boolean {
        val prefs = TilesComponents.prefs(context)
        val previous = prefs.getString(KEY, null)
        val next = encodeIfChanged(previous, count, owner, nowMs) ?: return false
        prefs.edit().putString(KEY, next).apply()
        TilesComponents.requestRedraw(context, TilesComponents.ATTENTION_TILE)
        return true
    }

    /** Drops the snapshot (explicit disconnect): the tile falls back to "OpenCode" with no count. */
    fun clear(context: Context) {
        val prefs = TilesComponents.prefs(context)
        if (!prefs.contains(KEY)) return
        prefs.edit().remove(KEY).apply()
        TilesComponents.requestRedraw(context, TilesComponents.ATTENTION_TILE)
    }

    /**
     * Profile deletion: drops the snapshot only when it belongs to [owner]. A snapshot without an owner, or one that
     * cannot be read, is dropped too (it cannot be shown to be another profile's). Returns true when it dropped one.
     */
    fun clearOwner(context: Context, owner: String): Boolean {
        val prefs = TilesComponents.prefs(context)
        val raw = prefs.getString(KEY, null) ?: return false
        if (!belongsTo(raw, owner)) return false
        prefs.edit().remove(KEY).apply()
        TilesComponents.requestRedraw(context, TilesComponents.ATTENTION_TILE)
        return true
    }

    /** What the tile shows now: the cached count, or null when nothing usable is cached. */
    fun readCached(context: Context, nowMs: Long): Int? =
        read(TilesComponents.prefs(context).getString(KEY, null), nowMs)

    /** Pure: the count in [raw], or null when [raw] is missing, unreadable, unstamped or older than [MAX_AGE_MS]. */
    fun read(raw: String?, nowMs: Long): Int? {
        if (raw == null) return null
        return try {
            val snapshot = JSONObject(raw)
            val updatedAt = snapshot.optLong("updatedAt", 0L)
            if (updatedAt <= 0L || nowMs - updatedAt > MAX_AGE_MS) {
                null
            } else {
                snapshot.optInt("pendingCount", 0).coerceAtLeast(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Pure: the new payload, or null when [previous] already says the same thing recently enough. */
    internal fun encodeIfChanged(previous: String?, count: Int, owner: String, nowMs: Long): String? {
        val clamped = count.coerceAtLeast(0)
        if (previous != null) {
            try {
                val old = JSONObject(previous)
                val sameCount = old.has("pendingCount") && old.optInt("pendingCount", -1) == clamped
                val sameOwner = old.optString("owner", "") == owner
                val age = nowMs - old.optLong("updatedAt", 0L)
                if (sameCount && sameOwner && age in 0 until RESTAMP_AFTER_MS) return null
            } catch (_: Exception) {
                // Unreadable: overwrite it.
            }
        }
        return JSONObject()
            .put("pendingCount", clamped)
            .put("owner", owner)
            .put("updatedAt", nowMs)
            .toString()
    }

    private fun belongsTo(raw: String, owner: String): Boolean =
        try {
            val stored = JSONObject(raw).optString("owner", "")
            stored.isEmpty() || stored == owner
        } catch (_: Exception) {
            true
        }
}

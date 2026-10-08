package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import org.json.JSONObject

/**
 * Quick Settings tile showing how many requests need the user — pending
 * permissions, questions and forms of the connected server, the same count
 * the Activity tab shows. It renders only the snapshot the Flutter side
 * caches (`flutter.oc.attentionTile`), so it works while the app is not
 * running and never polls a server itself. With no snapshot, or a stale one,
 * it shows a plain "OpenCode" and no count.
 *
 * A tap opens the app on Activity through the same whitelisted launch extra
 * the static shortcuts use; nothing is answered or sent from the tile.
 */
class AttentionTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { openActivity() }
        } else {
            openActivity()
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val count = readPendingCount()
        tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_monochrome)
        val subtitle = when {
            count == null -> null
            count == 0 -> NativeStrings.get(this, R.string.tile_all_clear)
            else -> NativeStrings.quantity(this, R.plurals.tile_need_you, count, count)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = NativeStrings.get(this, R.string.tile_label)
            tile.subtitle = subtitle
        } else {
            tile.label = if (subtitle == null) {
                NativeStrings.get(this, R.string.tile_label)
            } else {
                NativeStrings.get(this, R.string.tile_label_with_count, subtitle)
            }
        }
        tile.state = if (count != null && count > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    /** The cached count, or null when nothing usable is cached. */
    private fun readPendingCount(): Int? {
        val raw = getSharedPreferences(FLUTTER_PREFS, Context.MODE_PRIVATE)
            .getString(SNAPSHOT_KEY, null) ?: return null
        return try {
            val snapshot = JSONObject(raw)
            val updatedAt = snapshot.optLong("updatedAt", 0L)
            // A count the app last confirmed a day ago says nothing about now.
            if (updatedAt <= 0L || System.currentTimeMillis() - updatedAt > MAX_AGE_MS) {
                null
            } else {
                snapshot.optInt("pendingCount", 0).coerceAtLeast(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_LAUNCH_ACTION, LAUNCH_ACTION_ACTIVITY)
        }
        TileServiceCompat.startActivityAndCollapse(
            this,
            PendingIntentActivityWrapper(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false // The launch intent is immutable on every supported API.
            )
        )
    }

    companion object {
        private const val SNAPSHOT_KEY = "flutter.oc.attentionTile"
        private const val FLUTTER_PREFS = "FlutterSharedPreferences"
        private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        // Whitelisted by MainActivity.LAUNCH_ACTIONS; Dart routes it to Activity.
        const val LAUNCH_ACTION_ACTIVITY = "activity"
    }
}

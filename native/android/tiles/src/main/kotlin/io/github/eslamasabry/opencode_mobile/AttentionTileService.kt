package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import io.github.eslamasabry.oc.android.tiles.AttentionTileCache
import io.github.eslamasabry.oc.android.tiles.LaunchAction
import io.github.eslamasabry.oc.android.tiles.LaunchActions
import io.github.eslamasabry.oc.android.tiles.R

/**
 * Quick Settings tile showing how many requests need the user — pending
 * permissions, questions and forms of the connected server, the same count
 * the Activity tab shows. It renders only the snapshot the app caches
 * ([AttentionTileCache]), so it works while the app is not running and never
 * polls a server itself. With no snapshot, or a stale one, it shows a plain
 * "OpenCode" and no count.
 *
 * A tap opens the app on Activity through the same whitelisted launch extra
 * the static shortcuts use; nothing is answered or sent from the tile.
 *
 * Copied from the Flutter app with its class name frozen. Seams: the module R,
 * plain getString instead of NativeStrings, the cache in its own preferences
 * file, MainActivity named by FQCN, and this module's tile icon.
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
        val count = AttentionTileCache.readCached(this, System.currentTimeMillis())
        tile.icon = Icon.createWithResource(this, R.drawable.tile_ic_mark)
        val subtitle = when {
            count == null -> null
            count == 0 -> getString(R.string.tile_all_clear)
            else -> resources.getQuantityString(R.plurals.tile_need_you, count, count)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = getString(R.string.tile_label)
            tile.subtitle = subtitle
        } else {
            tile.label = if (subtitle == null) {
                getString(R.string.tile_label)
            } else {
                getString(R.string.tile_label_with_count, subtitle)
            }
        }
        tile.state = if (count != null && count > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun openActivity() {
        val intent = LaunchActions.mainActivityIntent(packageName, LaunchAction.Activity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
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
}

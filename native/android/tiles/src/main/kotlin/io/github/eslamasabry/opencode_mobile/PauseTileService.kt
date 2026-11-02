package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.eslamasabry.oc.android.tiles.LaunchAction
import io.github.eslamasabry.oc.android.tiles.LaunchActions
import io.github.eslamasabry.oc.android.tiles.PauseTileState
import io.github.eslamasabry.oc.android.tiles.R

/**
 * Quick Settings tile for background mode. Running shows as on; paused by
 * the user shows as off; when background mode is not switched on the tile
 * is unavailable and says "Off in Settings". Nothing here reads a server or
 * adds a permission.
 *
 * Copied from the Flutter app with its class name frozen. Background live
 * mode is not built in the native app yet, so the state comes from
 * [PauseTileState] (nothing sets it today) and a tap never pauses or resumes
 * here: it opens the app's background settings, where the switch is one tap.
 */
class PauseTileService : TileService() {

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
        try {
            if (isLocked) {
                unlockAndRun { openApp() }
            } else {
                openApp()
            }
        } catch (_: Exception) {
            // A refused start leaves the shade as it is; the tile must never crash.
        }
    }

    /** Opens the app and closes the shade. Android 14+ takes only a
     * PendingIntent here; the Intent form throws there, so it is used only
     * below Android 14 (the lint check cannot see the version guard). */
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = LaunchActions.mainActivityIntent(packageName, LaunchAction.BackgroundSettings).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val mode = PauseTileState.mode(this)
        val subtitle = getString(
            when (mode) {
                PauseTileState.Mode.On -> R.string.pause_tile_on
                PauseTileState.Mode.Paused -> R.string.pause_tile_paused
                PauseTileState.Mode.Off -> R.string.pause_tile_off
            }
        )
        tile.icon = Icon.createWithResource(this, R.drawable.tile_ic_mark)
        tile.state = when (mode) {
            PauseTileState.Mode.On -> Tile.STATE_ACTIVE
            PauseTileState.Mode.Paused -> Tile.STATE_INACTIVE
            PauseTileState.Mode.Off -> Tile.STATE_UNAVAILABLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = getString(R.string.pause_tile_label)
            tile.subtitle = subtitle
        } else {
            tile.label = getString(R.string.pause_tile_label_with_state, subtitle)
        }
        tile.updateTile()
    }
}

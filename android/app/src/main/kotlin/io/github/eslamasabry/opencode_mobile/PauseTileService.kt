package io.github.eslamasabry.opencode_mobile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that pauses and resumes background mode — the same
 * pause the live notification's "Pause background" button performs
 * ([LivePauseReceiver.pause]). Running shows as on; paused by the user shows
 * as off and a tap resumes; when background mode is not switched on in the
 * app's settings the tile is unavailable and says so. Nothing here reads a
 * server or adds a permission.
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
        when {
            BackgroundConnectionService.active -> LivePauseReceiver.pause(this)
            LivePauseReceiver.isPausedByUser(this) -> {
                try {
                    LivePauseReceiver.resume(this)
                } catch (_: Exception) {
                    // Android refused a background service start: leave the
                    // person in the app, where the switch is one tap.
                    LivePauseReceiver.setPausedByUser(this, true)
                    openApp()
                    return
                }
            }
        }
        render()
    }

    /** Opens the app and closes the shade. Android 14+ takes only a
     * PendingIntent here; the Intent form throws there, so it is used only
     * below Android 14 (the lint check cannot see the version guard). */
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val running = BackgroundConnectionService.active
        val paused = !running && LivePauseReceiver.isPausedByUser(this)
        val subtitle = NativeStrings.get(
            this,
            when {
                running -> R.string.pause_tile_on
                paused -> R.string.pause_tile_paused
                else -> R.string.pause_tile_off
            }
        )
        tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_monochrome)
        tile.state = when {
            running -> Tile.STATE_ACTIVE
            paused -> Tile.STATE_INACTIVE
            else -> Tile.STATE_UNAVAILABLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = NativeStrings.get(this, R.string.pause_tile_label)
            tile.subtitle = subtitle
        } else {
            tile.label = NativeStrings.get(this, R.string.pause_tile_label_with_state, subtitle)
        }
        tile.updateTile()
    }
}

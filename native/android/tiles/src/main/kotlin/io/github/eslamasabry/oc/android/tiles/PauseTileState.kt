package io.github.eslamasabry.oc.android.tiles

import android.content.Context

/**
 * What the background-mode tile shows. Background live mode is not built in the native app yet, so nothing sets
 * these today and the tile reads [Mode.Off] ("Off in Settings", unavailable). The live-mode owner calls
 * [setRunning] / [setPausedByUser] once it exists; each setter redraws the tile.
 */
object PauseTileState {
    const val KEY_RUNNING: String = "oc.live.running"
    const val KEY_PAUSED_BY_USER: String = "oc.live.pausedByUser"

    enum class Mode { On, Paused, Off }

    fun isRunning(context: Context): Boolean = TilesComponents.prefs(context).getBoolean(KEY_RUNNING, false)

    fun isPausedByUser(context: Context): Boolean =
        TilesComponents.prefs(context).getBoolean(KEY_PAUSED_BY_USER, false)

    fun setRunning(context: Context, running: Boolean) {
        TilesComponents.prefs(context).edit().putBoolean(KEY_RUNNING, running).apply()
        TilesComponents.requestRedraw(context, TilesComponents.PAUSE_TILE)
    }

    fun setPausedByUser(context: Context, paused: Boolean) {
        TilesComponents.prefs(context).edit().putBoolean(KEY_PAUSED_BY_USER, paused).apply()
        TilesComponents.requestRedraw(context, TilesComponents.PAUSE_TILE)
    }

    fun mode(context: Context): Mode = mode(isRunning(context), isPausedByUser(context))

    /** Pure: running wins; paused only counts while not running; neither is off. */
    fun mode(running: Boolean, pausedByUser: Boolean): Mode =
        when {
            running -> Mode.On
            pausedByUser -> Mode.Paused
            else -> Mode.Off
        }
}

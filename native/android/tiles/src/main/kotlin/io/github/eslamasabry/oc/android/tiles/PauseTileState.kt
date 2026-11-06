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

    /** Set once the Flutter build's pause was read (E00-S05); it is never read again after that. */
    const val KEY_FLUTTER_PAUSE_ADOPTED: String = "oc.live.pausedByUserAdopted"

    /** The Flutter build's own marker (LivePauseReceiver): `oc_background_native` / `pausedByUser`. Kept as it is. */
    internal const val FLUTTER_PREFS: String = "oc_background_native"
    internal const val FLUTTER_KEY_PAUSED: String = "pausedByUser"

    enum class Mode { On, Paused, Off }

    fun isRunning(context: Context): Boolean = TilesComponents.prefs(context).getBoolean(KEY_RUNNING, false)

    fun isPausedByUser(context: Context): Boolean {
        adoptFlutterPause(context)
        return TilesComponents.prefs(context).getBoolean(KEY_PAUSED_BY_USER, false)
    }

    /**
     * Once per install: a live mode the person paused on the Flutter build stays paused here. The Flutter file is only
     * read, never changed, so a rollback to the Flutter build sees its own choice. A native choice made before this
     * ran wins.
     */
    fun adoptFlutterPause(context: Context) {
        val prefs = TilesComponents.prefs(context)
        if (prefs.getBoolean(KEY_FLUTTER_PAUSE_ADOPTED, false)) return
        val paused =
            context.getSharedPreferences(FLUTTER_PREFS, Context.MODE_PRIVATE).getBoolean(FLUTTER_KEY_PAUSED, false)
        val edit = prefs.edit().putBoolean(KEY_FLUTTER_PAUSE_ADOPTED, true)
        if (paused && !prefs.contains(KEY_PAUSED_BY_USER)) edit.putBoolean(KEY_PAUSED_BY_USER, true)
        edit.commit()
    }

    fun setRunning(context: Context, running: Boolean) {
        TilesComponents.prefs(context).edit().putBoolean(KEY_RUNNING, running).apply()
        TilesComponents.requestRedraw(context, TilesComponents.PAUSE_TILE)
    }

    fun setPausedByUser(context: Context, paused: Boolean) {
        adoptFlutterPause(context) // a later adoption must not undo this choice
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

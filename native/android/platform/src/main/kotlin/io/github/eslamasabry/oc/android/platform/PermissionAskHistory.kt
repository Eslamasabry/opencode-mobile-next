package io.github.eslamasabry.oc.android.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus

/**
 * Whether the app already asked Android for the camera or the microphone (E00-S05). Android gives no "asked before"
 * answer: before the first request and after a second refusal ("don't ask again") the rationale flag is false in both
 * cases. The Flutter build kept its own flag (MainActivityMedia.kt), and so does native, in the SAME preference files
 * and keys, so a person updating from Flutter is not treated as never asked, and a rollback to Flutter still knows.
 * The files are plain booleans and hold nothing personal; they are app-wide, not per profile.
 */
public class PermissionAskHistory(private val context: Context) {
    /** The two permissions Flutter remembered asking for, with its exact preference file and key. */
    public enum class Asked(public val permission: String, internal val file: String, internal val key: String) {
        Camera(Manifest.permission.CAMERA, "camera_permissions", "camera_requested"),
        Microphone(Manifest.permission.RECORD_AUDIO, "voice_permissions", "microphone_requested"),
    }

    public fun asked(which: Asked): Boolean = prefs(which).getBoolean(which.key, false)

    /** Call just before showing Android's request (Flutter writes it at the same point). */
    public fun markAsked(which: Asked) {
        prefs(which).edit().putBoolean(which.key, true).apply()
    }

    /**
     * The status without asking: granted, blocked (asked before and Android shows no rationale, so only its settings
     * can allow it now), or still askable.
     */
    public fun status(activity: Activity, which: Asked): PermissionStatus =
        statusOf(
            granted = activity.checkSelfPermission(which.permission) == PackageManager.PERMISSION_GRANTED,
            asked = asked(which),
            rationale = activity.shouldShowRequestPermissionRationale(which.permission),
        )

    private fun prefs(which: Asked) = context.getSharedPreferences(which.file, Context.MODE_PRIVATE)

    public companion object {
        /** Flutter MainActivityMedia: `requested && !shouldShowRequestPermissionRationale` = permanently denied. */
        public fun statusOf(granted: Boolean, asked: Boolean, rationale: Boolean): PermissionStatus =
            when {
                granted -> PermissionStatus.Granted
                asked && !rationale -> PermissionStatus.PermanentlyDenied
                else -> PermissionStatus.Denied
            }
    }
}

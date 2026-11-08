package io.github.eslamasabry.oc.core.common.launch

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * B-175 (security audit 2026-11-08): MainActivity is exported, so any app can send it an explicit intent. Every intent
 * the app builds for itself (pinned shortcuts, tiles, widgets, notifications) carries this per-install token in
 * [EXTRA]; MainActivity acts on shortcut, session and action extras only when the token matches, and otherwise opens
 * plainly. It is the monitor-token idea (alert taps) applied to the other launch extras.
 *
 * The token is 32 bytes from [SecureRandom] as lower-case hex, kept in the app's no-backup files, so it never leaves
 * the install (no cloud backup, no device transfer). It is never logged and never shown.
 */
public object LaunchToken {
    /** The intent extra holding the token. */
    public const val EXTRA: String = "oc.launchToken"

    private const val FILE = "oc_launch_token"
    private val SHAPE = Regex("^[0-9a-f]{64}$")

    @Volatile private var cached: String? = null

    /** This install's token, created on first use. */
    public fun value(context: Context): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val file = File(context.applicationContext.noBackupFilesDir, FILE)
            val token = read(file) ?: create(file)
            cached = token
            return token
        }
    }

    /** Adds the token to an intent the app builds for MainActivity. Returns [intent]. */
    public fun stamp(context: Context, intent: Intent): Intent = intent.putExtra(EXTRA, value(context))

    /**
     * Reads and removes the token extra (so a recreated activity cannot replay it). True only when it is this
     * install's token; compared in constant time.
     */
    public fun consume(context: Context, intent: Intent): Boolean {
        val presented = intent.getStringExtra(EXTRA)
        intent.removeExtra(EXTRA)
        if (presented.isNullOrEmpty()) return false
        return MessageDigest.isEqual(presented.encodeToByteArray(), value(context).encodeToByteArray())
    }

    private fun read(file: File): String? =
        try {
            if (file.isFile) file.readText().trim().takeIf(SHAPE::matches) else null
        } catch (_: IOException) {
            null
        }

    private fun create(file: File): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val token = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$FILE.tmp")
            temp.writeText(token)
            if (!temp.renameTo(file)) temp.delete()
        } catch (_: IOException) {
            // Kept for this process only: intents built now still match; after a restart a new token is made and
            // older taps open the app plainly. Safe either way.
        }
        return token
    }
}

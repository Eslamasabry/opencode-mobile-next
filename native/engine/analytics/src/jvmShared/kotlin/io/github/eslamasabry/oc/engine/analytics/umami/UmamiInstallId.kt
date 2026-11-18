package io.github.eslamasabry.oc.engine.analytics.umami

import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The random install id for our own analytics server, and nothing else. It is a fresh UUID made on the phone, not
 * derived from the device, the account, the Android id or Google's app-instance id, so our server's reports cannot be
 * joined to Google's. Its whole life is tied to the usage-reports switch:
 * - it does not exist while the switch is off ([current] answers null and makes nothing);
 * - it is made the first time an event is accepted with the switch on, and kept in one small private file;
 * - switching usage off deletes it ([on] with false), so switching on again makes a NEW one;
 * - clearing the app's data or uninstalling deletes the file with the rest of the app's storage.
 */
@OptIn(ExperimentalUuidApi::class)
public class UmamiInstallId(
    private val file: File,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    private val lock = Any()
    private var enabled = false
    private var cached: String? = null

    /** Called by the sender whenever the switch changes; off deletes the id and its file. */
    public fun on(enabled: Boolean) {
        synchronized(lock) {
            this.enabled = enabled
            if (!enabled) {
                cached = null
                file.delete()
                File(file.parentFile, file.name + ".new").delete()
            }
        }
    }

    /** The id while the switch is on (made on first use), null while it is off. */
    public fun current(): String? =
        synchronized(lock) {
            if (!enabled) return null
            cached ?: stored() ?: make()
        }

    private fun stored(): String? =
        runCatching { file.takeIf { it.isFile }?.readText()?.trim() }
            .getOrNull()
            ?.takeIf { UmamiField.InstallId.accepts(it) }
            ?.also { cached = it }

    private fun make(): String? {
        val id = newId().takeIf { UmamiField.InstallId.accepts(it) } ?: return null
        val written =
            runCatching {
                file.parentFile?.mkdirs()
                val pending = File(file.parentFile, file.name + ".new")
                pending.writeText(id)
                pending.renameTo(file)
            }.getOrDefault(false)
        // An id that could not be stored is not used: it would change at every event.
        return id.takeIf { written }?.also { cached = it }
    }
}

package io.github.eslamasabry.oc.engine.settings.walkthrough

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public enum class WhatsNewTarget {
    Updates,
    Agents,
    PasteImage,
    HoldToTalk,
    AppearanceText,
}

public enum class WhatsNewChange(public val target: WhatsNewTarget) {
    Updates(WhatsNewTarget.Updates),
    Agents(WhatsNewTarget.Agents),
    LargeText(WhatsNewTarget.AppearanceText),
    PasteImages(WhatsNewTarget.PasteImage),
    Voice(WhatsNewTarget.HoldToTalk),
}

/**
 * Release notes are built-in word keys, never server content. Unknown versions replay the newest
 * written notes.
 */
public object WhatsNewCatalog {
    public val byVersion: Map<String, List<WhatsNewChange>> =
        mapOf("2.2.0" to WhatsNewChange.entries)

    public fun versionFor(version: String): String? =
        if (version in byVersion) version
        else
            byVersion.keys.maxWithOrNull { a, b ->
                val x = a.split('.').map { it.toIntOrNull() ?: 0 }
                val y = b.split('.').map { it.toIntOrNull() ?: 0 }
                (0..2).firstNotNullOfOrNull { i ->
                    ((x.getOrNull(i) ?: 0).compareTo(y.getOrNull(i) ?: 0)).takeIf { it != 0 }
                } ?: 0
            }
}

/**
 * One attempt per updated build, never a fresh-install announcement, and only exact versions with
 * written notes.
 */
public class WhatsNewMemory(private val store: SettingsMemory) {
    private val lane = Mutex()

    public suspend fun takeDue(
        version: String,
        build: String,
        existingInstall: Boolean,
    ): Outcome<Boolean> = lane.withLock {
        val seen =
            when (val read = store.read(KEY)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return@withLock read
            }
        if (seen == build) return@withLock false.ok()
        when (val saved = store.write(KEY, build)) {
            is Outcome.Failed -> return@withLock saved
            is Outcome.Ok -> Unit
        }
        (seen != null || existingInstall).let { returning ->
            (returning && version in WhatsNewCatalog.byVersion).ok()
        }
    }

    public companion object {
        public const val KEY: String = "oc.whatsNewSeenBuild"
    }
}

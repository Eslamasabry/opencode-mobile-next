package io.github.eslamasabry.oc.engine.voice.prefs

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane

/**
 * The voice settings, app-wide (plan 05 Appendix A). Keys are Flutter's logical keys, so the adopted
 * `FlutterSharedPreferences.xml` is read in place (B-MEDIA-022).
 */
public interface VoicePrefs {
    public fun selectedPack(): String?

    public fun language(): String?

    public fun autoSend(): Boolean

    public fun readAloudNatural(): Boolean

    public fun readAloudConsent(): Boolean

    public suspend fun setSelectedPack(id: String): Outcome<Unit>

    public suspend fun setLanguage(storedId: String): Outcome<Unit>

    public suspend fun setAutoSend(on: Boolean): Outcome<Unit>

    public suspend fun setReadAloudNatural(on: Boolean): Outcome<Unit>

    public suspend fun setReadAloudConsent(given: Boolean): Outcome<Unit>

    public companion object Keys {
        public const val SELECTED_PACK: String = "voice.selected_pack"
        public const val LANGUAGE: String = "voice.language"
        public const val AUTO_SEND: String = "voice.auto_send"
        public const val READ_ALOUD_NATURAL: String = "voice.read_aloud_natural"
        public const val READ_ALOUD_CONSENT: String = "oc.readAloudConsent"
    }
}

/** Over the one [KeyValueStore] (reads in memory, one commit per change on [lane]). */
public class KeyValueVoicePrefs(private val store: KeyValueStore, private val lane: WriterLane) : VoicePrefs {
    override fun selectedPack(): String? = store.string(VoicePrefs.SELECTED_PACK)

    override fun language(): String? = store.string(VoicePrefs.LANGUAGE)

    override fun autoSend(): Boolean = store.bool(VoicePrefs.AUTO_SEND) ?: false

    override fun readAloudNatural(): Boolean = store.bool(VoicePrefs.READ_ALOUD_NATURAL) ?: false

    override fun readAloudConsent(): Boolean = store.bool(VoicePrefs.READ_ALOUD_CONSENT) ?: false

    override suspend fun setSelectedPack(id: String): Outcome<Unit> = store.commit(lane) { putString(VoicePrefs.SELECTED_PACK, id) }

    override suspend fun setLanguage(storedId: String): Outcome<Unit> = store.commit(lane) { putString(VoicePrefs.LANGUAGE, storedId) }

    override suspend fun setAutoSend(on: Boolean): Outcome<Unit> = store.commit(lane) { putBool(VoicePrefs.AUTO_SEND, on) }

    override suspend fun setReadAloudNatural(on: Boolean): Outcome<Unit> =
        store.commit(lane) { putBool(VoicePrefs.READ_ALOUD_NATURAL, on) }

    override suspend fun setReadAloudConsent(given: Boolean): Outcome<Unit> =
        store.commit(lane) { putBool(VoicePrefs.READ_ALOUD_CONSENT, given) }
}

/** Desktop (no voice) and tests. */
public class InMemoryVoicePrefs : VoicePrefs {
    public val values: MutableMap<String, Any> = mutableMapOf()

    override fun selectedPack(): String? = values[VoicePrefs.SELECTED_PACK] as? String

    override fun language(): String? = values[VoicePrefs.LANGUAGE] as? String

    override fun autoSend(): Boolean = values[VoicePrefs.AUTO_SEND] as? Boolean ?: false

    override fun readAloudNatural(): Boolean = values[VoicePrefs.READ_ALOUD_NATURAL] as? Boolean ?: false

    override fun readAloudConsent(): Boolean = values[VoicePrefs.READ_ALOUD_CONSENT] as? Boolean ?: false

    override suspend fun setSelectedPack(id: String): Outcome<Unit> = put(VoicePrefs.SELECTED_PACK, id)

    override suspend fun setLanguage(storedId: String): Outcome<Unit> = put(VoicePrefs.LANGUAGE, storedId)

    override suspend fun setAutoSend(on: Boolean): Outcome<Unit> = put(VoicePrefs.AUTO_SEND, on)

    override suspend fun setReadAloudNatural(on: Boolean): Outcome<Unit> = put(VoicePrefs.READ_ALOUD_NATURAL, on)

    override suspend fun setReadAloudConsent(given: Boolean): Outcome<Unit> = put(VoicePrefs.READ_ALOUD_CONSENT, given)

    private fun put(key: String, value: Any): Outcome<Unit> {
        values[key] = value
        return Unit.ok()
    }
}

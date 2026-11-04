package io.github.eslamasabry.oc.engine.settings.consent

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock

/** App consent only: an accepted answer is NOT proof of an Android permission. Stored by name. */
public enum class InFlowConsentKind(internal val wire: String, internal val topic: ConsentTopic) {
    BatteryExemption("batteryExemption", ConsentTopic.BatteryExemption),
    MakerAutoStart("makerAutoStart", ConsentTopic.MakerAutoStart),
    NeedsYouNotifications("needsYouNotifications", ConsentTopic.NeedsYouNotifications),
}

/** Stable selectors for the Settings sentence (`:ui:words` owns the copy). */
public enum class InFlowConsentExplanation {
    NotAsked,
    Unfinished,
    AcceptedCheckSystemSettings,
    BatteryMayStopServer,
    MakerMayPreventRestart,
    NeedsYouAlertsOff,
}

public data class InFlowConsentRow(val kind: InFlowConsentKind, val choice: ConsentChoice) {
    public val explanation: InFlowConsentExplanation
        get() =
            when (choice) {
                ConsentChoice.Unseen -> InFlowConsentExplanation.NotAsked
                ConsentChoice.Offered -> InFlowConsentExplanation.Unfinished
                ConsentChoice.Accepted -> InFlowConsentExplanation.AcceptedCheckSystemSettings
                ConsentChoice.Denied ->
                    when (kind) {
                        InFlowConsentKind.BatteryExemption ->
                            InFlowConsentExplanation.BatteryMayStopServer
                        InFlowConsentKind.MakerAutoStart ->
                            InFlowConsentExplanation.MakerMayPreventRestart
                        InFlowConsentKind.NeedsYouNotifications ->
                            InFlowConsentExplanation.NeedsYouAlertsOff
                    }
            }
}

/**
 * The ask-once consents (B-ANDR-025/026, B-SEC-093), `oc.inFlowConsent.<id>` =
 * `{"version":1,"firstStart":<bool>,"choices":{"<kind>":"<choice>"}}`. One instance serves every
 * profile.
 *
 * Claims are committed BEFORE a prompt is returned, so rebuilds, overlapping callers and restarts
 * never repeat an automatic ask; an interrupted ask reads [ConsentChoice.Offered] ("unfinished") in
 * Settings. A corrupt record or a refused write makes the profile's consents unavailable: nothing
 * is offered and nothing reads as granted. No platform request is made here and global notification
 * rules are never touched.
 */
public class InFlowConsentStore(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val savedProfiles: SavedProfiles = SavedProfiles.from(store),
) : ConsentStore {

    private class Entry(
        val firstStart: Boolean,
        val choices: PersistentMap<InFlowConsentKind, ConsentChoice>,
        val writable: Boolean,
    )

    private val entries = AtomicReference<PersistentMap<String, Entry>>(persistentHashMapOf())
    private val closed = AtomicReference<PersistentSet<String>>(persistentHashSetOf())
    private val mutexes = ProfileMutexes()
    private val changes = Revision()
    private val log = AppLog.tagged("InFlowConsent")

    override val topics: Set<ConsentTopic> =
        InFlowConsentKind.entries.mapTo(LinkedHashSet()) { it.topic }
    override val revision: StateFlow<Long> = changes.flow

    override fun read(profileId: ProfileId, topic: ConsentTopic): ConsentRead {
        val kind =
            InFlowConsentKind.entries.firstOrNull { it.topic == topic }
                ?: return ConsentRead.Unknown
        return if (storageAvailable(profileId)) row(profileId, kind).choice.read
        else ConsentRead.Unknown
    }

    /**
     * False for a corrupt record, after a refused write, and for a closed or removed profile:
     * disable the actions.
     */
    public fun storageAvailable(profileId: ProfileId): Boolean =
        open(profileId) && entry(profileId)?.writable == true

    /**
     * The Settings row. An unreadable record reads [ConsentChoice.Unseen]; check [storageAvailable]
     * first.
     */
    public fun row(profileId: ProfileId, kind: InFlowConsentKind): InFlowConsentRow =
        InFlowConsentRow(
            kind,
            (if (open(profileId)) entry(profileId)?.choices?.get(kind) else null)
                ?: ConsentChoice.Unseen,
        )

    /**
     * The first start of a phone-hosted server (never a remote connect or app launch). Returns the
     * asks to present, in order, to the first caller only; later calls, restarts and later changes
     * of exemption or maker return nothing. An exempt battery and a maker without an auto-start
     * step get no ask. No OS grant is inferred or stored.
     */
    public suspend fun phoneServerFirstStart(
        profileId: ProfileId,
        batteryAlreadyExempt: Boolean,
        makerHasAutoStartStep: Boolean,
    ): Outcome<ImmutableList<InFlowConsentKind>> =
        serial(profileId) { entry ->
            if (entry.firstStart)
                return@serial Outcome.Ok(emptyList<InFlowConsentKind>().toImmutableList())
            val candidates =
                listOfNotNull(
                        InFlowConsentKind.BatteryExemption.takeIf { !batteryAlreadyExempt },
                        InFlowConsentKind.MakerAutoStart.takeIf { makerHasAutoStartStep },
                    )
                    .filter { (entry.choices[it] ?: ConsentChoice.Unseen) == ConsentChoice.Unseen }
            var choices = entry.choices
            for (kind in candidates) choices = choices.putting(kind, ConsentChoice.Offered)
            when (val saved = save(profileId, Entry(true, choices, true))) {
                is Outcome.Failed -> saved
                is Outcome.Ok -> Outcome.Ok(candidates.toImmutableList())
            }
        }

    /**
     * "Notify me when the agent needs me": true only for the first caller that may present the
     * question.
     */
    public suspend fun requestNeedsYouPreset(profileId: ProfileId): Outcome<Boolean> =
        serial(profileId) { entry ->
            val kind = InFlowConsentKind.NeedsYouNotifications
            if ((entry.choices[kind] ?: ConsentChoice.Unseen) != ConsentChoice.Unseen)
                return@serial Outcome.Ok(false)
            when (
                val saved =
                    save(
                        profileId,
                        Entry(
                            entry.firstStart,
                            entry.choices.putting(kind, ConsentChoice.Offered),
                            true,
                        ),
                    )
            ) {
                is Outcome.Failed -> saved
                is Outcome.Ok -> Outcome.Ok(true)
            }
        }

    /**
     * The person's answer, recorded before any platform API runs. Cancel or back counts as no.
     * Answered once.
     */
    public suspend fun answer(
        profileId: ProfileId,
        kind: InFlowConsentKind,
        allow: Boolean,
    ): Outcome<Unit> =
        serial(profileId) { entry ->
            if (entry.choices[kind] != ConsentChoice.Offered) {
                return@serial Outcome.Failed(Failure.Refused(RefusalReason.AlreadyAnswered))
            }
            save(
                profileId,
                Entry(entry.firstStart, entry.choices.putting(kind, answerOf(allow)), true),
            )
        }

    /** Settings may revise an answer without resetting the automatic-ask marker. */
    public suspend fun changeFromSettings(
        profileId: ProfileId,
        kind: InFlowConsentKind,
        allow: Boolean,
    ): Outcome<Unit> =
        serial(profileId) { entry ->
            save(
                profileId,
                Entry(entry.firstStart, entry.choices.putting(kind, answerOf(allow)), true),
            )
        }

    override fun closeProfile(profileId: ProfileId) {
        update(closed) { it.adding(profileId.raw) }
        update(entries) { it.removing(profileId.raw) }
        changes.bump()
    }

    /**
     * After a deletion that aborted with the profile kept: a fresh snapshot of storage. False when
     * no longer saved.
     */
    public fun reopenProfile(profileId: ProfileId): Boolean {
        if (!savedProfiles.contains(profileId)) return false
        update(entries) { it.removing(profileId.raw) }
        update(closed) { it.removing(profileId.raw) }
        changes.bump()
        return true
    }

    /**
     * Waits for an admitted consent answer after closeProfile, without deleting a kept server's
     * answer.
     */
    public suspend fun drainProfile(profileId: ProfileId) {
        mutexes.of(profileId).withLock {}
    }

    override suspend fun forgetProfile(profileId: ProfileId): Outcome<Unit> {
        closeProfile(profileId)
        return mutexes.of(profileId).withLock {
            val key = ConsentKeys.inFlow(profileId)
            if (store.get(key) == null) Outcome.Ok(Unit)
            else store.commit(lanes.app) { remove(key) }
        }
    }

    // ---- internals

    private fun answerOf(allow: Boolean) =
        if (allow) ConsentChoice.Accepted else ConsentChoice.Denied

    private suspend fun <T> serial(
        profileId: ProfileId,
        block: suspend (Entry) -> Outcome<T>,
    ): Outcome<T> =
        mutexes.of(profileId).withLock {
            if (!open(profileId)) return@withLock Outcome.Failed(closing())
            val entry = entry(profileId) ?: return@withLock Outcome.Failed(unreadable())
            if (!entry.writable) return@withLock Outcome.Failed(refusedWrite())
            block(entry)
        }

    private suspend fun save(profileId: ProfileId, next: Entry): Outcome<Unit> {
        if (!open(profileId)) return Outcome.Failed(closing())
        val encoded =
            DartJson.encode(
                linkedMapOf(
                    "version" to 1L,
                    "firstStart" to next.firstStart,
                    "choices" to
                        next.choices.entries.associateTo(LinkedHashMap()) { (k, v) ->
                            k.wire to v.wire
                        },
                )
            )
        val written =
            store.commit(lanes.forProfile(profileId)) {
                putString(ConsentKeys.inFlow(profileId), encoded)
            }
        if (!isClosed(profileId)) {
            update(entries) { map ->
                when (written) {
                    is Outcome.Ok -> map.putting(profileId.raw, next)
                    is Outcome.Failed ->
                        map[profileId.raw]?.let {
                            map.putting(profileId.raw, Entry(it.firstStart, it.choices, false))
                        } ?: map
                }
            }
        }
        if (written is Outcome.Failed)
            log.w { "in-flow consent write refused; storage marked unavailable" }
        changes.bump()
        return written
    }

    private fun isClosed(profileId: ProfileId): Boolean = profileId.raw in closed.load()

    private fun open(profileId: ProfileId): Boolean =
        !isClosed(profileId) && profileId.isOpaque() && savedProfiles.contains(profileId)

    private fun entry(profileId: ProfileId): Entry? {
        entries.load()[profileId.raw]?.let {
            return it
        }
        val loaded = load(profileId) ?: return null
        update(entries) {
            if (it.containsKey(profileId.raw) || isClosed(profileId)) it
            else it.putting(profileId.raw, loaded)
        }
        return entries.load()[profileId.raw] ?: loaded
    }

    /** Null for anything Dart's loader would have refused. There is deliberately no reset. */
    private fun load(profileId: ProfileId): Entry? {
        val raw =
            store.get(ConsentKeys.inFlow(profileId)) ?: return Entry(false, persistentMapOf(), true)
        val text = (raw as? PrefValue.Str)?.value ?: return null
        if (text.length > MAX_RECORD) return null
        val value = DartJson.decodeOrNull(text) as? Map<*, *> ?: return null
        val firstStart = value["firstStart"] as? Boolean ?: return null
        val stored = value["choices"] as? Map<*, *> ?: return null
        if (!isVersionOne(value["version"])) return null
        var choices = persistentMapOf<InFlowConsentKind, ConsentChoice>()
        for ((k, v) in stored) {
            val kind = InFlowConsentKind.entries.firstOrNull { it.wire == k } ?: return null
            choices = choices.putting(kind, ConsentChoice.fromWire(v) ?: return null)
        }
        return Entry(firstStart, choices, true)
    }

    private companion object {
        const val MAX_RECORD = 4096
    }
}

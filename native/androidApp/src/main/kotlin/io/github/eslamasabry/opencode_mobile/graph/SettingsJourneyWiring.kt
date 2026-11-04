package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceHelp
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceHelpFacts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Only device-level convenience metadata, committed on the existing app writer lane. Unknown record fields survive. */
@SingleIn(AppScope::class)
@Inject
class SettingsJourney(private val stores: PreviewStores, appScope: AppCoroutineScope) : AppearanceHelp {
    private val mutable = MutableStateFlow<AppearanceHelpFacts?>(null)
    override val facts: StateFlow<AppearanceHelpFacts?> = mutable.asStateFlow()
    private val lane = Mutex()
    init {
        appScope.launch {
            if (stores.warm() is Outcome.Ok) {
                refresh()
                stores.prefs.changes.collect { if (GUIDE in it) refresh() }
            }
        }
    }
    private fun record(): JsonObject = try {
        (stores.prefs.get(GUIDE) as? PrefValue.Str)?.value?.let { Json.parseToJsonElement(it) as? JsonObject } ?: JsonObject(emptyMap())
    } catch (_: IllegalArgumentException) { JsonObject(emptyMap()) }
    private fun refresh() {
        val record = record()
        val step = (record["nativeStep"] as? JsonPrimitive)?.intOrNull
            ?: when ((record["last"] as? JsonPrimitive)?.contentOrNull) {
                "style", "shape" -> 1
                "text", "motion" -> 2
                "glow" -> 3
                else -> 0
            }
        mutable.value = AppearanceHelpFacts((record["done"] as? JsonPrimitive)?.booleanOrNull == true, step.coerceIn(0,3))
    }
    override suspend fun save(step: Int, done: Boolean): Outcome<Unit> = lane.withLock {
        if (stores.warm() is Outcome.Failed) return@withLock Failure.StorageRefused().failed()
        val value = JsonObject(record().toMutableMap().apply {
            put("done", JsonPrimitive(done)); put("nativeStep", JsonPrimitive(step.coerceIn(0,3)))
        })
        val result = stores.prefs.commit(stores.lanes.app) { putString(GUIDE, value.toString()) }
        if (result is Outcome.Ok) refresh()
        result
    }
    override suspend fun resetTips(): Outcome<Unit> = lane.withLock {
        if (stores.warm() is Outcome.Failed) return@withLock Failure.StorageRefused().failed()
        val guide = JsonObject(record().toMutableMap().apply { put("done", JsonPrimitive(false)); put("nativeStep", JsonPrimitive(0)) })
        val result = stores.prefs.commit(stores.lanes.app) { remove("oc.tips"); putString(GUIDE, guide.toString()) }
        if (result is Outcome.Ok) refresh()
        result
    }
    /** A declared release opens once per build, never on a fresh install. Unknown releases only record the build. */
    suspend fun takeWhatsNew(version: String, build: String, existingInstall: Boolean): Boolean = lane.withLock {
        if (stores.warm() is Outcome.Failed) return@withLock false
        val previous = (stores.prefs.get(SEEN_BUILD) as? PrefValue.Str)?.value
        if (previous == build) return@withLock false
        if (stores.prefs.commit(stores.lanes.app) { putString(SEEN_BUILD, build) } is Outcome.Failed) return@withLock false
        (previous != null || existingInstall) && version == "2.2.0"
    }
    companion object { private const val SEEN_BUILD = "oc.whatsNewSeenBuild"; private const val GUIDE = "oc.appearanceGuide" }
}

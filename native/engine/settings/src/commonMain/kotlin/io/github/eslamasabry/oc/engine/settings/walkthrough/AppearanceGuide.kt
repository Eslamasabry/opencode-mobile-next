package io.github.eslamasabry.oc.engine.settings.walkthrough

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Flutter's stored group/question names: unchanged so a migrated guide resumes. */
public enum class AppearanceGroup(public val wire: String) {
    Colours("colours"),
    Style("style"),
    Background("background"),
    Comfort("comfort");

    public val questions: List<AppearanceQuestion>
        get() = AppearanceQuestion.entries.filter { it.group == this }
}

public enum class AppearanceQuestion(
    public val wire: String,
    public val group: AppearanceGroup,
    public val heldBySaver: Boolean = false,
) {
    Mode("mode", AppearanceGroup.Colours),
    Theme("theme", AppearanceGroup.Colours),
    AccentColour("accentColour", AppearanceGroup.Colours),
    Style("style", AppearanceGroup.Style, true),
    Shape("shape", AppearanceGroup.Style, true),
    Look("look", AppearanceGroup.Background, true),
    Strength("strength", AppearanceGroup.Background, true),
    Text("text", AppearanceGroup.Comfort),
    Motion("motion", AppearanceGroup.Comfort),
    Glow("glow", AppearanceGroup.Comfort);

    public companion object {
        public fun byName(name: Any?): AppearanceQuestion? = entries.firstOrNull { it.wire == name }

        public fun after(
            from: AppearanceQuestion?,
            saver: Boolean,
            available: List<AppearanceQuestion> = entries,
        ): AppearanceQuestion? =
            entries.drop(if (from == null) 0 else from.ordinal + 1).firstOrNull {
                it in available && !(saver && it.heldBySaver)
            }
    }
}

public data class AppearanceGuideRecord(
    val done: Boolean = false,
    val seen: List<AppearanceQuestion> = emptyList(),
    val lastIn: Map<AppearanceGroup, AppearanceQuestion> = emptyMap(),
    val last: AppearanceQuestion? = null,
) {
    public fun encode(): String =
        DartJson.encode(
            linkedMapOf<String, Any?>().apply {
                if (done) put("done", true)
                put("seen", seen.map { it.wire })
                put("lastIn", lastIn.entries.associate { it.key.wire to it.value.wire })
                last?.let { put("last", it.wire) }
            }
        )

    public companion object {
        public fun decode(raw: String?): AppearanceGuideRecord {
            val data =
                raw?.let(DartJson::decodeOrNull) as? Map<*, *> ?: return AppearanceGuideRecord()
            val groups = data["lastIn"] as? Map<*, *>
            return AppearanceGuideRecord(
                done = data["done"] == true,
                seen = (data["seen"] as? List<*>)?.mapNotNull(AppearanceQuestion::byName).orEmpty(),
                lastIn =
                    AppearanceGroup.entries
                        .mapNotNull { group ->
                            AppearanceQuestion.byName(groups?.get(group.wire))
                                ?.takeIf { it.group == group }
                                ?.let { group to it }
                        }
                        .toMap(),
                last = AppearanceQuestion.byName(data["last"]),
            )
        }
    }
}

/**
 * One app-owned serial lane. Convenience writes may fail; the visit still remembers the guide, like
 * Flutter.
 */
public class AppearanceGuideMemory(private val store: SettingsMemory) {
    private val lane = Mutex()
    private var loaded = false
    private val mutable = MutableStateFlow(AppearanceGuideRecord())
    public val record: StateFlow<AppearanceGuideRecord> = mutable.asStateFlow()

    public suspend fun load(): Unit = lane.withLock { loadLocked() }

    private suspend fun loadLocked() {
        if (loaded) return
        val read = store.read(KEY)
        if (read is Outcome.Ok) {
            mutable.value = AppearanceGuideRecord.decode(read.value)
        }
        loaded = true
    }

    public suspend fun update(
        change: (AppearanceGuideRecord) -> AppearanceGuideRecord
    ): Outcome<Unit> = lane.withLock {
        loadLocked()
        val next = change(mutable.value)
        mutable.value = next
        store.write(KEY, next.encode())
    }

    public companion object {
        public const val KEY: String = "oc.appearanceGuide"
    }
}

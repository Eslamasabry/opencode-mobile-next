package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.ui.design.effects.GlassKnob
import io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind
import io.github.eslamasabry.oc.ui.design.effects.GlassPreset
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/*
 * Settings › Appearance › Glass lab (preview builds, owner 2026-11-13: "build a page with all the sliders for me to tweak
 * it ... and a preview page with a real content to render below"). The person moves every number of the Glass material
 * over a live preview, can wear the numbers in the whole app, and copies them as a text block to send back.
 */

/** Where the Glass lab keeps its numbers (`oc.glassTuning`), bound by the app in preview builds only. */
public interface GlassLabSetting {
    /** The stored block ([GlassTuning.encode]); null = never tuned. */
    public val stored: StateFlow<String?>

    /** Stores [text]; null removes it (the shipped glass). */
    public fun save(text: String?)

    /** This build's name and number, put at the top of a copied block. */
    public val build: String
}

/** The lab as it stands: the draft numbers, which pane kind the sliders move (null: all linked), and the switch. */
public data class GlassLabUi(
    val tuning: GlassTuning = GlassTuning.Shipped,
    val kind: GlassPaneKind? = null,
    val build: String = "",
    /** The last paste held no glass numbers. */
    val pasteRefused: Boolean = false,
) {
    val inApp: Boolean get() = tuning.inApp

    /** The block "Copy values" puts on the clipboard. */
    val copyBlock: String get() = GlassTuning.encode(tuning, build)
}

/** What the Glass lab page can do. [dark]: the theme the page shows now (each theme has its own numbers). */
public interface GlassLabActions {
    public fun onKind(kind: GlassPaneKind?)

    public fun onKnob(knob: GlassKnob, value: Float, dark: Boolean)

    public fun onPreset(preset: GlassPreset, dark: Boolean)

    public fun onInApp(on: Boolean)

    public fun onResetAll()

    /** Loads a pasted block; false (and [GlassLabUi.pasteRefused]) when it holds no glass numbers. */
    public fun onPaste(text: String?): Boolean
}

/**
 * The lab's state and its saves. The draft is stored on every change (the newest after [SAVE_MS] of quiet, so a slider
 * at frame rate writes once when it rests), with its switch; the app wears it only while the switch is on.
 */
public class GlassLabPresenter(private val scope: CoroutineScope, private val port: GlassLabSetting) : GlassLabActions {
    private val mutable =
        MutableStateFlow(GlassLabUi(tuning = port.stored.value?.let { GlassTuning.decode(it) } ?: GlassTuning.Shipped, build = port.build))
    public val state: StateFlow<GlassLabUi> = mutable.asStateFlow()
    private val pending = MutableStateFlow<GlassTuning?>(null)

    init {
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            pending.filterNotNull().debounce(SAVE_MS).collect { port.save(textOf(it)) }
        }
    }

    private fun textOf(t: GlassTuning): String? = if (t.isShipped && !t.inApp) null else GlassTuning.encode(t.trimmed(), port.build)

    private fun change(now: Boolean = false, f: (GlassTuning) -> GlassTuning) {
        val next = f(mutable.value.tuning)
        mutable.value = mutable.value.copy(tuning = next, pasteRefused = false)
        if (now) {
            pending.value = null
            port.save(textOf(next))
        } else {
            pending.value = next
        }
    }

    override fun onKind(kind: GlassPaneKind?) {
        mutable.value = mutable.value.copy(kind = kind)
    }

    override fun onKnob(knob: GlassKnob, value: Float, dark: Boolean) {
        val kind = mutable.value.kind
        change { t -> if (kind == null) t.linked(GlassPaneKind.Bar, dark, knob, value) else t.with(kind, dark, knob, value) }
    }

    override fun onPreset(preset: GlassPreset, dark: Boolean) {
        change(now = true) { it.preset(preset, dark) }
    }

    override fun onInApp(on: Boolean) {
        change(now = true) { it.copy(inApp = on) }
    }

    override fun onResetAll() {
        change(now = true) { GlassTuning.Shipped.copy(inApp = it.inApp) }
    }

    override fun onPaste(text: String?): Boolean {
        val read = text?.let { GlassTuning.decode(it) }
        if (read == null) {
            mutable.value = mutable.value.copy(pasteRefused = true)
            return false
        }
        // A pasted block's own switch is not the person's: keep theirs.
        change(now = true) { read.copy(inApp = it.inApp) }
        return true
    }

    public companion object {
        /** Quiet time before a moved slider's numbers are stored, ms. */
        public const val SAVE_MS: Long = 400
    }
}

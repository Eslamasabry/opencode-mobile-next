package io.github.eslamasabry.oc.feature.phone.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Phone makers whose Android closes apps on its own (dontkillmyapp.com), grouped by the settings they share. */
public enum class PhoneMaker {
    /** Nubia, RedMagic and ZTE: force-stop an app swiped from Recents. */
    Nubia,
    Xiaomi,
    Oppo,
    Vivo,
    Huawei,
    Samsung,

    /** Everything else, Pixels included: stock Android keeps an app with a running notification alive. */
    Other,
    ;

    /** Swiping the app away from Recents may close it on this phone. */
    public val closesOnSwipe: Boolean get() = this != Other

    public companion object {
        /** Port of `PhoneMaker.of` (B-ANDR-007). */
        public fun of(manufacturer: String, brand: String = ""): PhoneMaker {
            val name = "${manufacturer.lowercase()} ${brand.lowercase()}"
            fun has(vararg words: String) = words.any { it in name }
            return when {
                has("nubia", "redmagic", "zte") -> Nubia
                has("xiaomi", "redmi", "poco") -> Xiaomi
                has("oppo", "oneplus", "realme") -> Oppo
                has("vivo", "iqoo") -> Vivo
                has("huawei", "honor") -> Huawei
                has("samsung") -> Samsung
                else -> Other
            }
        }
    }
}

/** One thing to allow so Android leaves the app running. */
public enum class KeepAliveStepKind { Battery, LockInRecents, Autostart, Background }

/** [setting] is the screen that allows it; locking in Recents has none (the person does it by hand). */
public data class KeepAliveStep(val kind: KeepAliveStepKind, val setting: KeepAliveSetting?)

/** Port of keep_alive_advice.dart `keepAliveSteps` (plan 11 §8.4). Battery first everywhere. */
public object KeepAliveAdvice {
    private val battery = KeepAliveStep(KeepAliveStepKind.Battery, KeepAliveSetting.Battery)
    private val lock = KeepAliveStep(KeepAliveStepKind.LockInRecents, null)
    private val autostart = KeepAliveStep(KeepAliveStepKind.Autostart, KeepAliveSetting.Autostart)
    private val background = KeepAliveStep(KeepAliveStepKind.Background, KeepAliveSetting.AppDetails)

    public fun steps(maker: PhoneMaker): List<KeepAliveStep> =
        when (maker) {
            // Swiping away force-stops even with the battery exemption: the lock helps most there.
            PhoneMaker.Nubia -> listOf(battery, lock, background, autostart)
            PhoneMaker.Xiaomi -> listOf(battery, autostart, background, lock)
            PhoneMaker.Oppo -> listOf(battery, autostart, background, lock)
            PhoneMaker.Vivo -> listOf(battery, background, autostart, lock)
            PhoneMaker.Huawei -> listOf(battery, autostart, lock)
            PhoneMaker.Samsung -> listOf(battery, background, lock)
            PhoneMaker.Other -> listOf(battery, background)
        }
}

/** One row: [done] steps keep their word ("Allowed") and open nothing. */
public data class KeepAliveStepUi(val kind: KeepAliveStepKind, val done: Boolean, val opens: KeepAliveSetting?)

/** PhoneRoute.KeepRunning's state (plan 07 F15). The words per maker are the screen's (`keepRunning*`). */
public data class KeepRunningUi(
    val loaded: Boolean = false,
    val maker: PhoneMaker = PhoneMaker.Other,
    val steps: ImmutableList<KeepAliveStepUi> = persistentListOf(),
    val allSet: Boolean = false,
    val swipeWarning: Boolean = false,
    val openFailed: Boolean = false,
    /** Android 15+'s ~6 h / 24 h background budget, stated plainly (plan 07 F15). */
    val dailyLimit: Boolean = true,
)

public interface KeepRunningActions {
    /** The page came to the front (also back from a Settings screen: the battery switch may have changed). */
    public fun resumed()

    public fun open(step: KeepAliveStepKind)

    public fun back()
}

/** Keep running in the background (port of `KeepRunningSection`, without the AI Team heat and full-speed groups). */
public class KeepRunningModel(
    private val settings: KeepAliveSettings,
    private val nav: PhoneNavigator,
    private val scope: CoroutineScope,
) : KeepRunningActions {
    private val ui = MutableStateFlow(KeepRunningUi())
    public val state: StateFlow<KeepRunningUi> = ui.asStateFlow()

    override fun resumed() {
        scope.launch {
            val info = settings.info() ?: return@launch
            ui.update { build(info).copy(openFailed = it.openFailed) }
        }
    }

    override fun open(step: KeepAliveStepKind) {
        val setting = ui.value.steps.firstOrNull { it.kind == step }?.opens ?: return
        scope.launch {
            val opened = settings.open(setting)
            ui.update { it.copy(openFailed = !opened) }
        }
    }

    override fun back(): Unit = nav.back()

    private fun build(info: KeepAliveInfo): KeepRunningUi {
        val maker = PhoneMaker.of(info.manufacturer, info.brand)
        val batteryAllowed = info.batteryOptimizationIgnored
        // On stock Android, App info › Battery › Unrestricted is the same switch as the exemption, so nothing the app
        // cannot check is left. Other makers add screens the app cannot read, so they never get "You're set".
        val allSet = maker == PhoneMaker.Other && batteryAllowed
        val steps = KeepAliveAdvice.steps(maker).filterNot { allSet && it.kind == KeepAliveStepKind.Background }
        fun done(step: KeepAliveStep) = step.kind == KeepAliveStepKind.Battery && batteryAllowed
        // One list, what is left first; done steps keep the maker's order among themselves (no state sections).
        val ordered = steps.filterNot(::done) + steps.filter(::done)
        return KeepRunningUi(
            loaded = true,
            maker = maker,
            steps = ordered.map { KeepAliveStepUi(it.kind, done(it), if (done(it)) null else it.setting) }.toImmutableList(),
            allSet = allSet,
            swipeWarning = maker.closesOnSwipe,
        )
    }
}

package io.github.eslamasabry.oc.feature.phonesetup.presentation

/**
 * Why the phone, not the app, ended the process the setup was running in (issue #138: a maker's battery manager killed
 * the app at foreground importance). Numbers in, a closed word out: the OS text never reaches the screen or analytics.
 */
public enum class SystemKill(
    /** The closed word sent with the setup failure (`kill`); never free text. */
    public val kind: String,
    /** Which sentence the setup page shows. */
    public val message: SystemKillMessage,
) {
    Other("system_kill_other", SystemKillMessage.Closed),
    LowMemory("low_memory", SystemKillMessage.OutOfMemory),
    Resources("excessive_resources", SystemKillMessage.Closed),
    Freezer("freezer", SystemKillMessage.Closed),
    Signaled("signaled", SystemKillMessage.Closed),
    ;

    public companion object {
        /** `ApplicationExitInfo.REASON_*` numbers. */
        private const val SIGNALED = 2
        private const val LOW_MEMORY = 3
        private const val EXCESSIVE_RESOURCE_USAGE = 9
        private const val OTHER = 13
        private const val FREEZER = 14

        /** A kill must be this recent to be blamed for a setup that is still stopped. */
        public const val WINDOW_MS: Long = 24L * 60 * 60 * 1000

        /**
         * The system kill behind an exit, or null when the exit was the person's, a crash or a normal end.
         * [memoryPressure] is the classifier's own read of the sub-reason (memory trimmed or capped).
         */
        public fun of(reason: Int, memoryPressure: Boolean = false): SystemKill? {
            val kill =
                when (reason) {
                    OTHER -> Other
                    LOW_MEMORY -> LowMemory
                    EXCESSIVE_RESOURCE_USAGE -> Resources
                    FREEZER -> Freezer
                    SIGNALED -> Signaled
                    else -> return null
                }
            return if (memoryPressure) LowMemory else kill
        }

        /**
         * The kill the setup is blamed on: only the newest exit counts (a normal exit after it means the app ran on),
         * and only within [WINDOW_MS]. [exits] is newest first.
         */
        public fun pick(exits: List<ExitFact>, nowMs: Long, sinceMs: Long = 0): SystemKill? {
            val newest = exits.firstOrNull() ?: return null
            if (nowMs - newest.atMs > WINDOW_MS) return null
            // An exit from before this setup began cannot have stopped it (the tester's earlier runs, a data clear).
            if (newest.atMs < sinceMs) return null
            // A swipe from Recents is REASON_OTHER too; the classifier's sub-reason read says it was the person.
            if (!newest.closedBySystem) return null
            return of(newest.reason, newest.memoryPressure)
        }
    }
}

/**
 * One previous-process exit, numbers only. [closedBySystem] is the classifier's verdict (`ExitKind.closedBySystem`):
 * false for a force stop, a Recents swipe or an update, whatever the bare reason number says.
 */
public data class ExitFact(
    val reason: Int,
    val atMs: Long,
    val memoryPressure: Boolean = false,
    val closedBySystem: Boolean = true,
)

/** The sentence the setup page shows for a kill. */
public enum class SystemKillMessage { Closed, OutOfMemory }

/** The phone makers whose battery managers close apps, as far as the steps differ (dontkillmyapp.com). */
public enum class SetupMaker {
    Xiaomi,
    Oppo,
    Realme,
    Vivo,
    Samsung,
    Other,
    ;

    public companion object {
        public fun of(manufacturer: String, brand: String = ""): SetupMaker {
            val name = "${manufacturer.lowercase()} ${brand.lowercase()}"

            fun has(vararg words: String) = words.any { it in name }
            return when {
                has("xiaomi", "redmi", "poco") -> Xiaomi
                // Before Oppo: realme builds are named after their parent in some fields.
                has("realme") -> Realme
                has("oppo", "oneplus") -> Oppo
                has("vivo", "iqoo") -> Vivo
                has("samsung") -> Samsung
                else -> Other
            }
        }
    }
}

/** One thing to change so the phone leaves the app running. */
public enum class BatteryStep { Unrestricted, Autostart, NeverSleeping }

/** Where to open, in order, until one opens. The last is always the app's own settings page. */
public sealed interface SettingsTarget {
    /** The app's settings page: its Battery row is the "Unrestricted" switch. */
    public data object AppDetails : SettingsTarget

    /** A maker's own screen, opened only when this phone resolves it. */
    public data class Screen(val packageName: String, val className: String) : SettingsTarget
}

/** The two buttons of the battery card. */
public enum class BatteryOpen { App, Maker }

public object BatteryGuide {
    public fun steps(maker: SetupMaker): List<BatteryStep> =
        when (maker) {
            SetupMaker.Xiaomi,
            SetupMaker.Oppo,
            SetupMaker.Realme,
            SetupMaker.Vivo -> listOf(BatteryStep.Unrestricted, BatteryStep.Autostart)
            SetupMaker.Samsung -> listOf(BatteryStep.Unrestricted, BatteryStep.NeverSleeping)
            SetupMaker.Other -> listOf(BatteryStep.Unrestricted)
        }

    /** The maker's own screen for its extra step, if this maker has one that we know. */
    public fun makerScreens(maker: SetupMaker): List<SettingsTarget> =
        when (maker) {
            SetupMaker.Xiaomi ->
                listOf(SettingsTarget.Screen("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            SetupMaker.Oppo,
            SetupMaker.Realme ->
                listOf(
                    SettingsTarget.Screen("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    SettingsTarget.Screen("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                )
            SetupMaker.Vivo ->
                listOf(SettingsTarget.Screen("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
            SetupMaker.Samsung ->
                listOf(SettingsTarget.Screen("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"))
            SetupMaker.Other -> emptyList()
        }

    /** What a button opens: the maker's screens first when asked, the app's own page always last. */
    public fun targets(maker: SetupMaker, open: BatteryOpen): List<SettingsTarget> =
        when (open) {
            BatteryOpen.App -> listOf(SettingsTarget.AppDetails)
            BatteryOpen.Maker -> makerScreens(maker) + SettingsTarget.AppDetails
        }
}

/** What the phone says about this app's battery rights; null where it could not be read. */
public data class BatteryFacts(
    val maker: SetupMaker,
    val ignoringOptimizations: Boolean?,
    val backgroundRestricted: Boolean?,
) {
    val restricted: Boolean get() = backgroundRestricted == true || ignoringOptimizations == false
    val unknown: Boolean get() = backgroundRestricted == null && ignoringOptimizations == null
}

/** The one card: the steps for this phone's maker. */
public data class BatteryCard(val maker: SetupMaker, val steps: List<BatteryStep>, val afterKill: Boolean) {
    val hasMakerScreen: Boolean get() = BatteryGuide.makerScreens(maker).isNotEmpty()

    public companion object {
        /**
         * Whether to show the card. After a system kill any restriction (or an unreadable state) shows it. Before a
         * setup starts it shows for a background restriction, or for the default "Optimized" battery on the makers
         * that close apps; a stock phone is not nagged.
         */
        public fun decide(facts: BatteryFacts, afterKill: Boolean): BatteryCard? {
            val show =
                if (afterKill) facts.restricted || facts.unknown
                else facts.backgroundRestricted == true || (facts.ignoringOptimizations == false && facts.maker != SetupMaker.Other)
            return if (show) BatteryCard(facts.maker, BatteryGuide.steps(facts.maker), afterKill) else null
        }
    }
}

/** Opens the first target that opens; a target that fails, even by throwing, falls to the next. */
public object SettingsOpener {
    public fun open(targets: List<SettingsTarget>, tryOpen: (SettingsTarget) -> Boolean): Boolean {
        for (target in targets) {
            val opened = try { tryOpen(target) } catch (_: Exception) { false }
            if (opened) return true
        }
        return false
    }
}

/**
 * The phone half of the keep-alive help: what ended the last process, the battery state, and opening Settings. The app
 * binds it over ApplicationExitInfo, PowerManager and ActivityManager; without a binding the page says none of it.
 */
public interface SetupKeepAlive {
    /** The system kill behind the last exit that the setup is blamed on, or null. */
    public suspend fun interruption(sinceMs: Long = 0): SystemKill?

    public suspend fun facts(): BatteryFacts?

    /** One attempt at one target; false when it does not resolve or Android refuses. Never throws. */
    public fun tryOpen(target: SettingsTarget): Boolean
}

public class NoSetupKeepAlive : SetupKeepAlive {
    override suspend fun interruption(sinceMs: Long): SystemKill? = null

    override suspend fun facts(): BatteryFacts? = null

    override fun tryOpen(target: SettingsTarget): Boolean = false
}

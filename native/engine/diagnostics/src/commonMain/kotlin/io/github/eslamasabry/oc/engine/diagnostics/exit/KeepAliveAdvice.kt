package io.github.eslamasabry.oc.engine.diagnostics.exit

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Phone makers whose Android closes apps on its own (dontkillmyapp.com), grouped by the settings they share. */
public enum class PhoneMaker {
    /** Nubia and RedMagic (ZTE): force-stop an app swiped from Recents. */
    Nubia,

    /** Xiaomi, Redmi, POCO. */
    Xiaomi,

    /** Oppo, OnePlus, Realme. */
    Oppo,

    /** Vivo and iQOO. */
    Vivo,

    /** Huawei and Honor. */
    Huawei,
    Samsung,

    /** Everything else, Pixels included. */
    Other,
    ;

    /** Whether swiping the app away from Recents may close it on this phone. */
    public val closesOnSwipe: Boolean get() = this != Other

    public companion object {
        /** From `Build.MANUFACTURER` and `Build.BRAND`. */
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

/** A settings screen a step can open (the platform maps it to an intent). */
public enum class KeepAliveSetting { Battery, Autostart, AppDetails }

/** Which sentence a step shows; `:ui:words` owns the words (one per id, B-ANDR-007). */
public enum class KeepAliveWording {
    BatteryDetail, BatteryDone,
    LockNubia, LockSamsung, LockOther,
    AutostartHuawei, AutostartOther,
    BackgroundXiaomi, BackgroundOppo, BackgroundVivo, BackgroundSamsung, BackgroundOther,
}

/** [setting] is null for a step done by hand (locking in Recents). */
public data class KeepAliveStep(val kind: KeepAliveStepKind, val setting: KeepAliveSetting? = null) {
    public fun wording(maker: PhoneMaker, batteryAllowed: Boolean = false): KeepAliveWording =
        when (kind) {
            KeepAliveStepKind.Battery -> if (batteryAllowed) KeepAliveWording.BatteryDone else KeepAliveWording.BatteryDetail
            KeepAliveStepKind.LockInRecents ->
                when (maker) {
                    PhoneMaker.Nubia -> KeepAliveWording.LockNubia
                    PhoneMaker.Samsung -> KeepAliveWording.LockSamsung
                    else -> KeepAliveWording.LockOther
                }
            KeepAliveStepKind.Autostart ->
                if (maker == PhoneMaker.Huawei) KeepAliveWording.AutostartHuawei else KeepAliveWording.AutostartOther
            KeepAliveStepKind.Background ->
                when (maker) {
                    PhoneMaker.Xiaomi -> KeepAliveWording.BackgroundXiaomi
                    PhoneMaker.Oppo -> KeepAliveWording.BackgroundOppo
                    PhoneMaker.Vivo -> KeepAliveWording.BackgroundVivo
                    PhoneMaker.Samsung -> KeepAliveWording.BackgroundSamsung
                    else -> KeepAliveWording.BackgroundOther
                }
        }
}

/** What to allow on [maker]'s phones, most useful first; battery first everywhere (Android's own switch). */
public fun keepAliveSteps(maker: PhoneMaker): ImmutableList<KeepAliveStep> {
    val battery = KeepAliveStep(KeepAliveStepKind.Battery, KeepAliveSetting.Battery)
    val lock = KeepAliveStep(KeepAliveStepKind.LockInRecents)
    val autostart = KeepAliveStep(KeepAliveStepKind.Autostart, KeepAliveSetting.Autostart)
    val background = KeepAliveStep(KeepAliveStepKind.Background, KeepAliveSetting.AppDetails)
    return when (maker) {
        // Swiping away force-stops even with the battery exemption: the lock helps most there.
        PhoneMaker.Nubia -> persistentListOf(battery, lock, background, autostart)
        PhoneMaker.Xiaomi -> persistentListOf(battery, autostart, background, lock)
        PhoneMaker.Oppo -> persistentListOf(battery, autostart, background, lock)
        PhoneMaker.Vivo -> persistentListOf(battery, background, autostart, lock)
        PhoneMaker.Huawei -> persistentListOf(battery, autostart, lock)
        PhoneMaker.Samsung -> persistentListOf(battery, background, lock)
        PhoneMaker.Other -> persistentListOf(battery, background)
    }
}

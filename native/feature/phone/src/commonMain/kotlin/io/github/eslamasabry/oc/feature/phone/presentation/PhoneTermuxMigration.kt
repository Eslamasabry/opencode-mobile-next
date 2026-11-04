package io.github.eslamasabry.oc.feature.phone.presentation

import kotlinx.coroutines.flow.StateFlow

/** Termux remains available only as a source to copy into this app (ADR D7). */
public enum class PhoneTermuxProblem {
    Missing,
    Permission,
    Blocked,
    Unavailable,
    OtherAppsOff,
    Asleep,
    Unknown,
}

public data class PhoneTermuxMigrationOffer(
    val profileId: String,
    val displayName: String,
    val problem: PhoneTermuxProblem?,
)

public interface PhoneTermuxMigration {
    public val migration: StateFlow<List<PhoneTermuxMigrationOffer>>

    public suspend fun refresh()

    public suspend fun fix(profileId: String)

    public fun open(profileId: String)
}

/** Supplementary commands leave existing ThisPhoneActions implementations source compatible. */
public interface ThisPhoneMigrationActions {
    public fun fixTermux(profileId: String)

    public fun openTermuxMigration(profileId: String)
}

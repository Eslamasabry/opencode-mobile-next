package io.github.eslamasabry.oc.feature.phone.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@BindingContainer
@ContributesTo(AppScope::class)
public object DesktopPhoneTermuxMigrationBindings {
    @Provides
    public fun migration(): PhoneTermuxMigration =
        object : PhoneTermuxMigration {
            override val migration: StateFlow<List<PhoneTermuxMigrationOffer>> =
                MutableStateFlow(emptyList())

            override suspend fun refresh() = Unit

            override suspend fun fix(profileId: String) = Unit

            override fun open(profileId: String) = Unit
        }
}

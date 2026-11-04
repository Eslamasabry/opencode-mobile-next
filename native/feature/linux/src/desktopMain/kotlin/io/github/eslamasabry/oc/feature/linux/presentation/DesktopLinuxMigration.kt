package io.github.eslamasabry.oc.feature.linux.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Termux RUN_COMMAND is Android-only; a restored desktop route exposes no command buttons. */
@BindingContainer
@ContributesTo(AppScope::class)
public object DesktopLinuxMigrationBindings {
    @Provides
    public fun offers(): LinuxMigrationOffers =
        object : LinuxMigrationOffers {
            override val offers: StateFlow<List<LinuxMigrationOffer>> =
                MutableStateFlow(emptyList())

            override suspend fun refresh() = Unit
        }

    @Provides
    public fun sessions(): LinuxMigrationSessions = LinuxMigrationSessions {
        object : LinuxMigrationSession {
            override val state: StateFlow<LinuxMigrationUi> =
                MutableStateFlow(
                    LinuxMigrationUi(
                        title = LinuxMigrationWord.migrationUnavailableTitle,
                        paragraphs =
                            listOf(LinuxMigrationText(LinuxMigrationWord.migrationUnavailableBody)),
                    )
                )

            override suspend fun open() = Unit

            override fun toggle(id: String, selected: Boolean) = Unit

            override suspend fun command(command: LinuxMigrationCommand) = Unit

            override fun close() = Unit
        }
    }
}

package io.github.eslamasabry.oc.feature.linux.presentation

import kotlinx.coroutines.flow.StateFlow

public data class LinuxMigrationOffer(val profileId: String, val displayName: String)

public interface LinuxMigrationOffers {
    public val offers: StateFlow<List<LinuxMigrationOffer>>

    public suspend fun refresh()
}

public data class LinuxMigrationText(
    val word: LinuxMigrationWord,
    val arguments: List<String> = emptyList(),
)

public data class LinuxMigrationChoice(
    val id: String,
    val title: LinuxMigrationWord,
    val detail: LinuxMigrationWord,
    val selected: Boolean,
    val enabled: Boolean,
    val bytes: Long?,
    val files: Long?,
)

public enum class LinuxMigrationCommand {
    Retry,
    Start,
    Resume,
    Stop,
    Discard,
    ChooseFewer,
    Setup,
    OpenPhone,
    OpenBuiltin,
    FixTermux,
    SignIn,
}

public enum class LinuxMigrationAccess {
    Missing,
    Permission,
    Blocked,
    Unavailable,
    OtherAppsOff,
    Asleep,
    Unknown,
}

public data class LinuxMigrationButton(
    val word: LinuxMigrationWord,
    val command: LinuxMigrationCommand,
    val enabled: Boolean = true,
)

public data class LinuxMigrationUi(
    val title: LinuxMigrationWord = LinuxMigrationWord.migrationTitle,
    val paragraphs: List<LinuxMigrationText> = emptyList(),
    val choices: List<LinuxMigrationChoice> = emptyList(),
    val requiredBytes: Long? = null,
    val availableBytes: Long? = null,
    val showSpace: Boolean = false,
    val buttons: List<LinuxMigrationButton> = emptyList(),
    val busy: Boolean = false,
    val access: LinuxMigrationAccess? = null,
    val confirmation: LinuxMigrationCommand? = null,
)

public interface LinuxMigrationSession {
    public val state: StateFlow<LinuxMigrationUi>

    public suspend fun open()

    public fun toggle(id: String, selected: Boolean)

    public suspend fun command(command: LinuxMigrationCommand)

    public fun close()
}

public fun interface LinuxMigrationSessions {
    public fun session(profileId: String): LinuxMigrationSession
}

public interface LinuxMigrationActions {
    public fun toggle(id: String, selected: Boolean)

    public fun command(command: LinuxMigrationCommand)

    public fun confirm()

    public fun dismiss()

    public fun back()
}

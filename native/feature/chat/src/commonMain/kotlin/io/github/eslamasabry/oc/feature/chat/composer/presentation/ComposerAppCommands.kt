package io.github.eslamasabry.oc.feature.chat.composer.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute

/**
 * Codex composer #5 (B-NAV-051, Flutter `chat_commands.dart` `CommandSheetEntry.app`): `/new` (`/clear`) and
 * `/sessions` (`/resume`, `/continue`) are the app's own commands. They never go to the server as commands, where a
 * server without them refused them and a same-named template ran instead.
 */
public enum class AppCommand { NewConversation, Conversations }

public object AppCommands {
    private val newNames = setOf("new", "clear")
    private val listNames = setOf("sessions", "resume", "continue")

    /** The app command [text] is, or null: only the bare command word (arguments make it a message). */
    public fun of(text: String): AppCommand? {
        val line = text.trim()
        if (!line.startsWith("/") || line.any { it.isWhitespace() }) return null
        return when (line.removePrefix("/").lowercase()) {
            in newNames -> AppCommand.NewConversation
            in listNames -> AppCommand.Conversations
            else -> null
        }
    }
}

/** Runs an app command (the navigation side lives in the app graph). */
public interface ComposerAppCommands {
    public fun run(command: AppCommand, profile: ProfileId)
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ComposerAppCommands>())
public class NavigatorAppCommands(private val nav: AppNavigator) : ComposerAppCommands {
    override fun run(command: AppCommand, profile: ProfileId) {
        when (command) {
            AppCommand.NewConversation -> nav.open(ChatsRoute.New(profile = profile))
            AppCommand.Conversations -> nav.land()
        }
    }
}

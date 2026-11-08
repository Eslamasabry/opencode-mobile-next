package io.github.eslamasabry.oc.feature.chats.rb.o41

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import kotlin.test.Test

/**
 * The New conversation start screen with test/goldens/chats_home_golden_test.dart's scenes (`chats_new_*`): one
 * connected server, the projects alpha, beta and gamma, the server's default model. `project`: the last-used project
 * (alpha) is chosen; `choose`: nothing was used last, so no project is chosen; `copy`: alpha can start in a separate
 * copy (Flutter's `host.copyProject`), so the switch shows, off.
 */
class NewConversationGateTest {
    private object Actions : NewConversationActions {
        override fun back() {}
        override fun addServer() {}
        override fun chooseServer(profile: ProfileId) {}
        override fun draftChanged(text: String) {}
        override fun chooseDirectory(directory: String) {}
        override fun chooseModel(model: ModelRef?) {}
        override fun chooseAgent(agent: String?) {}
        override fun separateCopy(on: Boolean) {}
        override fun sheet(sheet: NewConversationSheet?) {}
        override fun start() {}
        override fun import(conversation: ImportableConversation) {}
        override fun retryLoad() {}
    }

    private val profile = ProfileId("p1")

    private fun state(directory: String?, copy: Boolean = false): NewConversationUi {
        val projects =
            listOf("alpha", "beta", "gamma").map { name ->
                NewConversationProject("/root/projects/$name", name, separateCopy = copy && name == "alpha")
            }
        return NewConversationUi(
            servers = listOf(NewConversationServer(profile, "OpenCode", connected = true)),
            profile = profile,
            setup = NewConversationSetup(directory = directory, projects = projects, models = emptyList(), agents = emptyList()),
            directory = directory,
        )
    }

    private fun shot(name: String, state: NewConversationUi) =
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen) { NewConversationScreen(state, Actions) }

    @Test fun project() { shot("chats_new_project", state("/root/projects/alpha")) }
    @Test fun projectWide() { shot("chats_new_project_1280x800", state("/root/projects/alpha")) }
    @Test fun choose() { shot("chats_new_choose", state(null)) }
    @Test fun chooseWide() { shot("chats_new_choose_1280x800", state(null)) }
    @Test fun copy() { shot("chats_new_copy", state("/root/projects/alpha", copy = true)) }
    @Test fun copyWide() { shot("chats_new_copy_1280x800", state("/root/projects/alpha", copy = true)) }
}

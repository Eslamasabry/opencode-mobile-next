package io.github.eslamasabry.oc.feature.agenttools

import io.github.eslamasabry.oc.core.model.AgentFeature
import io.github.eslamasabry.oc.core.model.AgentFeatureKind
import io.github.eslamasabry.oc.core.model.AgentFeatureOption
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsageStatus
import io.github.eslamasabry.oc.engine.integrations.usage.ProviderUsageMonitorState
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentModelChoice
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentTerminalUi
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsActions
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsUi
import kotlinx.collections.immutable.persistentListOf

internal object AgentToolsTestFixture {
    const val RAW_DIRECTORY = "/root/projects"
    const val PLAIN_FOLDER = "projects"

    val fullState: AgentToolsUi =
        AgentToolsUi(
            connected = true,
            name = "This phone",
            directory = RAW_DIRECTORY,
            canOptions = true,
            terminal =
                AgentTerminalUi(
                    supported = true,
                    listed = true,
                    terminals =
                        persistentListOf(
                            TerminalProcess(
                                id = "t1",
                                title = "Shell 1",
                                command = "bash",
                                arguments = persistentListOf(),
                                directory = RAW_DIRECTORY,
                                running = true,
                                pid = 4210,
                                exitCode = null,
                            )
                        ),
                ),
            monitor =
                ProviderUsageMonitorState(
                    enabled = true,
                    status = PaseoUsageStatus.Current,
                    lastReadAtMs = 1_700_000_000_000L,
                    providerCount = 2,
                ),
            monitorLastRead = "10:15",
            models =
                persistentListOf(
                    AgentModelChoice("claude-sonnet", "Claude Sonnet", selected = true),
                    AgentModelChoice("claude-opus", "Claude Opus", selected = false),
                ),
            features =
                persistentListOf(
                    AgentFeature(
                        id = "speed",
                        label = "Speed",
                        description = "Choose response speed for this conversation.",
                        kind = AgentFeatureKind.Choice,
                        selected = "balanced",
                        options =
                            persistentListOf(
                                AgentFeatureOption("fast", "Fast"),
                                AgentFeatureOption("balanced", "Balanced"),
                                AgentFeatureOption("thorough", "Thorough"),
                            ),
                    ),
                    AgentFeature(
                        id = "auto-review",
                        label = "Auto review",
                        description = "Reviews edits before saving.",
                        kind = AgentFeatureKind.Toggle,
                        on = true,
                    ),
                ),
            canImport = true,
            canUpdate = true,
        )

    class RecordingActions : AgentToolsActions {
        val events: MutableList<String> = mutableListOf()

        override fun setUsageMonitor(on: Boolean) {
            events += "monitor:$on"
        }

        override fun refreshUsageMonitor() {
            events += "monitor-refresh"
        }

        override fun back() {
            events += "back"
        }

        override fun refresh() {
            events += "refresh"
        }

        override fun setToggle(id: String, on: Boolean) {
            events += "toggle:$id:$on"
        }

        override fun setChoice(id: String, value: String) {
            events += "choice:$id:$value"
        }

        override fun setModel(id: String) {
            events += "model:$id"
        }

        override fun readImports() {
            events += "imports"
        }

        override fun importConversation(handle: String, agentId: String) {
            events += "import:$handle"
        }

        override fun requestUpdate() {
            events += "update"
        }

        override fun cancelUpdate() {
            events += "cancel-update"
        }

        override fun confirmUpdate() {
            events += "confirm-update"
        }

        override fun listTerminals() {
            events += "list-terminals"
        }

        override fun askNewTerminal() {
            events += "new-terminal"
        }

        override fun finishTerminalName(name: String?) {
            events += "finish-terminal:$name"
        }

        override fun askRenameTerminal(id: String) {
            events += "rename-terminal:$id"
        }

        override fun finishTerminalRename(name: String?) {
            events += "finish-rename:$name"
        }

        override fun askCloseTerminal(id: String) {
            events += "close-terminal:$id"
        }

        override fun cancelCloseTerminal() {
            events += "cancel-close"
        }

        override fun confirmCloseTerminal() {
            events += "confirm-close"
        }

        override fun openTerminal(id: String) {
            events += "open-terminal:$id"
        }

        override fun leaveTerminal() {
            events += "leave-terminal"
        }

        override fun sendTerminal(value: String) {
            events += "send-terminal:$value"
        }

        override fun terminalAccessible(on: Boolean) {
            events += "accessible:$on"
        }
    }
}

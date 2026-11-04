package io.github.eslamasabry.oc.engine.runtime.setup.registry

import io.github.eslamasabry.oc.engine.runtime.components.ComponentRemovalScripts
import io.github.eslamasabry.oc.engine.runtime.install.OpenCodeInstallFailure
import io.github.eslamasabry.oc.engine.runtime.install.OpenCodeInstallScripts
import io.github.eslamasabry.oc.engine.runtime.install.SetupHost
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import io.github.eslamasabry.oc.engine.runtime.setup.SetupRegistry

/**
 * The registry's display words, from `:ui:words` (Flutter l10n keys in brackets). The registry never authors a
 * sentence; "Python", "Node.js" and "OpenCode" are names, as in Flutter.
 */
public data class PhoneSetupRegistryWords(
    /** phoneSetupLinuxTitle */
    val linuxTitle: String,
    /** phoneSetupLinuxWhy */
    val linuxWhy: String? = null,
    /** phoneSetupEssentialsTitle */
    val essentialsTitle: String,
    /** phoneSetupEssentialsShort */
    val essentialsShort: String,
    /** phoneSetupEssentialsWhy */
    val essentialsWhy: String? = null,
    /** phoneSetupNodeWhy */
    val nodeWhy: String? = null,
    /** phoneSetupOpenCodeWhy */
    val openCodeWhy: String? = null,
    /** phoneSetupStartTitle */
    val startTitle: String,
    /** phoneSetupStartWhy */
    val startWhy: String? = null,
)

/**
 * Port of lib/builtin/setup/components.dart `setupComponents` for the app's own Ubuntu (host `builtin`): the Linux base,
 * Git and SSH, Python, the pinned Node, the pinned native OpenCode 2 and the start step, in dependency order, with
 * Flutter's ids, dependencies, required/default flags, weights and sizes.
 *
 * Not ported here (non-goals of BUILD-RUNTIME-B): the opt-in AI Team and the app-side voice model, which the first
 * setup never selects, and the Termux host's scripts. A saved job naming them still shows (the engine falls back to
 * the id as the row's name).
 */
public class PhoneSetupRegistry(private val words: PhoneSetupRegistryWords) : SetupRegistry {
    override fun components(params: Map<String, Map<String, String>>): List<SetupComponent> {
        val version = params[SetupComponentIds.OPENCODE]?.get("version")
        return listOf(
            SetupComponent(
                id = SetupComponentIds.LINUX,
                title = words.linuxTitle,
                shortTitle = words.linuxTitle,
                why = words.linuxWhy,
                required = true,
                native = true,
                estimatedSeconds = 15,
                downloadBytes = 30 * MB,
                checkScript = PhoneSetupScripts.LINUX_CHECK,
                installScript = "",
            ),
            SetupComponent(
                id = SetupComponentIds.ESSENTIALS,
                title = words.essentialsTitle,
                shortTitle = words.essentialsShort,
                why = words.essentialsWhy,
                dependsOn = listOf(SetupComponentIds.LINUX),
                required = true,
                estimatedSeconds = 60,
                downloadBytes = 45 * MB,
                checkScript = PhoneSetupScripts.ESSENTIALS_CHECK,
                presenceScript = ComponentRemovalScripts.ESSENTIALS_PRESENCE,
                installScript = PhoneSetupScripts.ESSENTIALS_INSTALL,
            ),
            SetupComponent(
                id = SetupComponentIds.PYTHON,
                title = "Python",
                shortTitle = "Python",
                dependsOn = listOf(SetupComponentIds.ESSENTIALS),
                defaultOn = true,
                estimatedSeconds = 40,
                downloadBytes = 25 * MB,
                checkScript = PhoneSetupScripts.PYTHON_CHECK,
                installScript = PhoneSetupScripts.PYTHON_INSTALL,
                removeScript = ComponentRemovalScripts.PYTHON_REMOVE,
                presenceScript = ComponentRemovalScripts.PYTHON_PRESENCE,
                sizeScript = ComponentRemovalScripts.PYTHON_SIZE,
            ),
            SetupComponent(
                id = SetupComponentIds.NODE,
                title = "Node.js",
                shortTitle = "Node.js",
                why = words.nodeWhy,
                // curl comes with the essentials; Ubuntu Base has none.
                dependsOn = listOf(SetupComponentIds.ESSENTIALS),
                required = true,
                estimatedSeconds = 20,
                downloadBytes = 58 * MB,
                checkScript = PhoneSetupScripts.NODE_CHECK,
                presenceScript = ComponentRemovalScripts.NODE_PRESENCE,
                installScript = PhoneSetupScripts.NODE_INSTALL,
            ),
            SetupComponent(
                id = SetupComponentIds.OPENCODE,
                title = "OpenCode",
                shortTitle = "OpenCode",
                why = words.openCodeWhy,
                dependsOn = listOf(SetupComponentIds.NODE),
                required = true,
                estimatedSeconds = 100,
                downloadBytes = 50 * MB,
                // In the app's own Ubuntu: the pinned native program, never npm.
                checkScript = openCodeCheck(version),
                presenceScript = ComponentRemovalScripts.OPENCODE_PRESENCE,
                installScript = openCodeInstall(version),
            ),
            SetupComponent(
                id = SetupComponentIds.START,
                title = words.startTitle,
                shortTitle = words.startTitle,
                why = words.startWhy,
                dependsOn = listOf(SetupComponentIds.OPENCODE),
                required = true,
                jobStep = true,
                estimatedSeconds = 15,
                checkScript = "",
                installScript = "",
            ),
        )
    }

    public companion object {
        private const val MB: Long = 1000L * 1000L

        /**
         * What "Set up" installs when the person changes nothing (Flutter `defaultSetupSelection`): every required
         * component plus the optional ones that are on by default.
         */
        public fun defaultSelection(registry: List<SetupComponent>): Set<String> =
            registry.filter { it.required || it.defaultOn }.map { it.id }.toSet()

        /** Bytes to download for [components] (Flutter `setupTotals`); unknown sizes count as zero, never guessed. */
        public fun downloadBytes(components: Iterable<SetupComponent>): Long = components.sumOf { it.downloadBytes ?: 0L }

        /** Seconds the [components] are estimated to take (Flutter `setupTotals`). */
        public fun estimatedSeconds(components: Iterable<SetupComponent>): Int = components.sumOf { it.estimatedSeconds }

        /**
         * The tools a selection brings along, for "Includes …" (Flutter `includedToolNames`): the Linux base is
         * plumbing the person never meets and OpenCode is what the page is about, so neither is listed; nor is the
         * start step, which is not a tool.
         */
        public fun includedNames(components: Iterable<SetupComponent>): List<String> =
            components.filter { !it.native && !it.jobStep && it.id != SetupComponentIds.OPENCODE }.map { it.shortTitle }

        // Flutter throws on an invalid version; here the registry never throws (the engine calls it unguarded), so an
        // invalid version is the install's own refusal and a check that never passes.
        private fun openCodeCheck(version: String?): String =
            try {
                OpenCodeInstallScripts.nativeCheck(version)
            } catch (e: IllegalArgumentException) {
                "exit 1\n"
            }

        private fun openCodeInstall(version: String?): String =
            try {
                OpenCodeInstallScripts.component(SetupHost.Builtin, version).installScript
            } catch (e: IllegalArgumentException) {
                "echo '${OpenCodeInstallFailure.Unpinned.line}' >&2\nexit 64\n"
            }
    }
}

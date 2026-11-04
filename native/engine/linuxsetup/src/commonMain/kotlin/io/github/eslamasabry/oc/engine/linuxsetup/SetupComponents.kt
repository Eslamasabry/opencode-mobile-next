package io.github.eslamasabry.oc.engine.linuxsetup

/** Component ids (port of SetupComponentIds in lib/builtin/setup/components.dart). */
public object SetupComponentIds {
    public const val LINUX: String = "linux"
    public const val ESSENTIALS: String = "essentials"
    public const val PYTHON: String = "python"
    public const val NODE: String = "node"
    public const val OPENCODE: String = "opencode"
    public const val AI_TEAM: String = "aiteam"
    public const val VOICE: String = "voice"
    public const val START: String = "start"
}

/** The reasons the OpenCode install ends a failed run with (its last log line becomes the component's error). */
public object OpenCodeInstallFailure {
    public const val NO_PROGRAM: String = "[oc] OpenCode's download did not contain its program"
    public const val WONT_RUN: String = "[oc] OpenCode's program does not run on this phone"
    public const val WRONG_VERSION: String = "[oc] OpenCode's program reports another version"
    public const val NO_START: String = "[oc] OpenCode was installed but did not start"
    public const val UNPINNED: String = "[oc] This app can only install its own pinned OpenCode"
    public const val CPU: String = "[oc] OpenCode has no build for this phone's processor"
}

/**
 * The words the registry and the job runner need from the person's language (defaults: app_en.arb). The screen
 * builds this from :ui:words; the engine never chooses a language.
 */
public data class SetupComponentTitles(
    val linux: String = "Linux base", // phoneSetupLinuxTitle
    val linuxWhy: String = "Everything else runs inside it.", // phoneSetupLinuxWhy
    val essentials: String = "Git, SSH and certificates", // phoneSetupEssentialsTitle
    val essentialsShort: String = "Git and SSH", // phoneSetupEssentialsShort
    val essentialsWhy: String = "Agents use Git and SSH to work on your projects.", // phoneSetupEssentialsWhy
    val nodeWhy: String = "OpenCode runs on Node.js.", // phoneSetupNodeWhy
    val openCodeWhy: String = "The coding agent itself.", // phoneSetupOpenCodeWhy
    val aiTeam: String = "AI Team", // aiteamComponentTitle
    val start: String = "Start OpenCode", // phoneSetupStartTitle
    val startWhy: String = "Setup ends with OpenCode running.", // phoneSetupStartWhy
    val voice: String = "Voice typing", // voiceComponentTitle
    val voiceSummary: String = "Speak instead of typing, even offline", // voiceComponentSummary
    val stageDownloadingLinux: String = "Downloading Linux base", // phoneSetupStageDownloadingLinux
    val stageUnpackingLinux: String = "Unpacking Linux base", // phoneSetupStageUnpackingLinux
    val stageStarting: String = "Starting OpenCode", // phoneSetupStageStarting
    val stageRetrying: String = "Interrupted. Trying again", // phoneSetupStageRetrying
    val stageAppDownloading: String = "Downloading", // setupAppStageDownloading
)

private const val MB: Long = 1000L * 1000L

/**
 * Every component the phone setup can install, in dependency order (port of `setupComponents`). [params] are per
 * component id; `opencode` reads `version` (the pin unless a caller asks for another on purpose; only OpenCode 2 is
 * supported). On [SetupHostKind.Termux] the Node check accepts an older working Node, and OpenCode keeps the
 * Termux manager's own script (not ported here: Termux is migrate-out only), so those components carry the check only.
 *
 * AI Team is listed with its dependencies and weight, without scripts (its scripts are not ported to this module);
 * the voice model is an app-side component ([SetupComponent.app]) offered when [supportsVoice].
 */
public fun setupComponentRegistry(
    titles: SetupComponentTitles = SetupComponentTitles(),
    params: Map<String, Map<String, String>> = emptyMap(),
    host: SetupHostKind = SetupHostKind.Builtin,
    supportsVoice: Boolean = false,
): List<SetupComponent> {
    val version = params[SetupComponentIds.OPENCODE]?.get("version")
    val builtin = host == SetupHostKind.Builtin
    return buildList {
        add(
            SetupComponent(
                id = SetupComponentIds.LINUX, title = titles.linux, why = titles.linuxWhy, required = true,
                native = true, estimatedSeconds = 15, downloadBytes = 30 * MB, checkScript = SetupScripts.LINUX_CHECK,
            ),
        )
        add(
            SetupComponent(
                id = SetupComponentIds.ESSENTIALS, title = titles.essentials, shortTitle = titles.essentialsShort,
                why = titles.essentialsWhy, dependsOn = listOf(SetupComponentIds.LINUX), required = true,
                estimatedSeconds = 60, downloadBytes = 45 * MB, checkScript = SetupScripts.ESSENTIALS_CHECK,
                presenceScript = SetupScripts.ESSENTIALS_PRESENCE, installScript = SetupScripts.ESSENTIALS_INSTALL,
            ),
        )
        add(
            SetupComponent(
                id = SetupComponentIds.PYTHON, title = "Python", dependsOn = listOf(SetupComponentIds.ESSENTIALS),
                defaultOn = true, estimatedSeconds = 40, downloadBytes = 25 * MB,
                checkScript = SetupScripts.PYTHON_CHECK, installScript = SetupScripts.PYTHON_INSTALL,
                removeScript = SetupScripts.PYTHON_REMOVE, presenceScript = SetupScripts.PYTHON_PRESENCE,
                sizeScript = SetupScripts.PYTHON_SIZE,
            ),
        )
        add(
            SetupComponent(
                id = SetupComponentIds.NODE, title = "Node.js", why = titles.nodeWhy,
                dependsOn = listOf(SetupComponentIds.ESSENTIALS), required = true, estimatedSeconds = 20,
                downloadBytes = 58 * MB,
                checkScript = if (builtin) SetupScripts.NODE_CHECK else SetupScripts.TERMUX_NODE_CHECK,
                presenceScript = SetupScripts.NODE_PRESENCE, installScript = SetupScripts.NODE_INSTALL,
            ),
        )
        add(
            SetupComponent(
                id = SetupComponentIds.OPENCODE, title = "OpenCode", why = titles.openCodeWhy,
                dependsOn = listOf(SetupComponentIds.NODE), required = true, estimatedSeconds = 100,
                downloadBytes = 50 * MB,
                checkScript =
                    (if (builtin) SetupScripts.openCodeNativeCheck(version) else SetupScripts.openCodeCheck(version)).orEmpty(),
                presenceScript = SetupScripts.OPENCODE_PRESENCE,
                installScript = if (builtin) SetupScripts.openCodeNativeInstall(version).orEmpty() else "",
            ),
        )
        add(
            SetupComponent(
                id = SetupComponentIds.AI_TEAM, title = titles.aiTeam,
                dependsOn = listOf(SetupComponentIds.ESSENTIALS, SetupComponentIds.OPENCODE), estimatedSeconds = 150,
            ),
        )
        add(
            SetupComponent(
                id = SetupComponentIds.START, title = titles.start, why = titles.startWhy,
                dependsOn = listOf(SetupComponentIds.OPENCODE), required = true, jobStep = true, estimatedSeconds = 15,
            ),
        )
        if (supportsVoice) {
            add(
                SetupComponent(
                    id = SetupComponentIds.VOICE, title = titles.voice, summary = titles.voiceSummary,
                    estimatedSeconds = 45, app = true,
                ),
            )
        }
    }
}

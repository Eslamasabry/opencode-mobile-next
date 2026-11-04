package io.github.eslamasabry.oc.engine.runtime.components

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Port of lib/builtin/setup/components.dart and lib/builtin/setup/aiteam_scripts.dart (the component registry as the
 * removal engine sees it: ids, dependency order, presence probes, remove scripts and size scripts, verbatim).
 *
 * Install and version-check scripts belong to the setup engine and are not part of this registry; [checkScript] only
 * exists so a caller (or a test) can prove that removal never runs a pinned version check.
 */
public data class RemovableComponent(
    val id: String,
    /** Exit 0: something of the component is there; 1: nothing is; anything else: unknown. Null: no probe. */
    val presenceScript: String? = null,
    /** Removes the component only (never its dependents). Null: the component cannot be removed. */
    val removeScript: String? = null,
    /** What [removeScript] would free, in kilobytes, on its last line. */
    val sizeScript: String? = null,
    val dependsOn: List<String> = emptyList(),
    /** Setup cannot finish without it (Linux, essentials, Node, OpenCode). */
    val required: Boolean = false,
    /** Installed by the native side (the Linux base itself): present whenever Linux is installed. */
    val native: Boolean = false,
    /** Not installed, a step of every job (start): never in the removal inventory. */
    val jobStep: Boolean = false,
    /** The pinned version check; removal never runs it (presence is not the same as an up-to-date version). */
    val checkScript: String? = null,
)

/** Port of `SetupComponentIds` (lib/builtin/setup/components.dart). */
public object ComponentIds {
    public const val LINUX: String = "linux"
    public const val ESSENTIALS: String = "essentials"
    public const val PYTHON: String = "python"
    public const val NODE: String = "node"
    public const val OPENCODE: String = "opencode"
    public const val AITEAM: String = "aiteam"

    /** Voice typing's speech model, installed by the app itself (not part of this Linux registry). */
    public const val VOICE: String = "voice"
    public const val START: String = "start"
}

/** Port of the removal side of `AiTeamScripts` (lib/builtin/setup/aiteam_scripts.dart). */
public object AiTeamRemovalScripts {
    public const val HOME: String = "/opt/aiteam"
    public const val CACHE: String = "/var/cache/oc-setup/aiteam"

    /** Deletes the programs and the team's own state (its store and settings). Projects and their history stay. */
    public const val REMOVE_SCRIPT: String = """set -eu
rm -f /usr/local/bin/gc /usr/local/bin/bd /usr/local/bin/dolt
rm -rf /opt/aiteam /var/cache/oc-setup/aiteam /root/aiteam /root/.gc
"""

    /** What [REMOVE_SCRIPT] would delete, in kilobytes (disk usage, links not followed). */
    public const val SIZE_SCRIPT: String = """set -u
du -sk /opt/aiteam /var/cache/oc-setup/aiteam /root/aiteam /root/.gc 2>/dev/null | awk '{s+=${'$'}1} END {print s+0}'
"""

    public const val PRESENCE_SCRIPT: String =
        "[ -e /opt/aiteam ] || [ -L /opt/aiteam ] || " +
            "[ -e /root/aiteam ] || [ -e /root/.gc ] || " +
            "[ -e /var/cache/oc-setup/aiteam ] || " +
            "[ -L /usr/local/bin/gc ] || [ -e /usr/local/bin/gc ] || " +
            "[ -L /usr/local/bin/bd ] || [ -e /usr/local/bin/bd ] || " +
            "[ -L /usr/local/bin/dolt ] || [ -e /usr/local/bin/dolt ]"
}

/** Port of the removal-relevant scripts of `SetupScripts` (lib/builtin/setup/components.dart), verbatim. */
public object ComponentRemovalScripts {
    public const val ESSENTIALS_PRESENCE: String =
        "command -v git >/dev/null 2>&1 || " +
            "command -v curl >/dev/null 2>&1 || command -v ssh >/dev/null 2>&1"

    public const val PYTHON_REMOVE: String = """set -eu
export DEBIAN_FRONTEND=noninteractive
apt-get remove -y python3-venv python3-pip
apt-get autoremove -y
"""

    /** What [PYTHON_REMOVE] would delete, in kilobytes: apt's dry run, by installed size. */
    public const val PYTHON_SIZE: String = """set -eu
packages=${'$'}(apt-get -s --auto-remove remove python3-venv python3-pip 2>/dev/null | awk '/^Remv /{print ${'$'}2}')
[ -n "${'$'}packages" ] || { echo 0; exit 0; }
dpkg-query -W -f='${'$'}{Installed-Size}\n' ${'$'}packages 2>/dev/null | awk '{s+=${'$'}1} END {print s+0}'
"""

    /** Python itself belongs to Ubuntu and survives [PYTHON_REMOVE]; this describes the pip/venv component. */
    public const val PYTHON_PRESENCE: String = """set -eu
[ -r /var/lib/dpkg/status ] || exit 2
packages=${'$'}(dpkg-query -W -f='${'$'}{Package} ${'$'}{Status}\n') || exit 2
printf '%s\n' "${'$'}packages" | grep -Eq '^python3-(pip|venv) install ok installed${'$'}'
"""

    public const val NODE_PRESENCE: String =
        "[ -e /opt/node ] || [ -L /opt/node ] || " +
            "command -v node >/dev/null 2>&1"

    public const val OPENCODE_PRESENCE: String =
        "command -v opencode >/dev/null 2>&1 || " +
            "command -v opencode2 >/dev/null 2>&1 || " +
            "[ -e /opt/opencode ] || [ -e /opt/opencode2 ] || " +
            "[ -e /usr/local/lib/node_modules/opencode-ai ] || " +
            "[ -e /usr/local/lib/node_modules/opencode ]"
}

/** The registry of the app's own Ubuntu, in dependency order (voice typing's app-side model is not included). */
public object RemovableComponents {
    public val builtin: ImmutableList<RemovableComponent> =
        persistentListOf(
            RemovableComponent(id = ComponentIds.LINUX, required = true, native = true),
            RemovableComponent(
                id = ComponentIds.ESSENTIALS,
                dependsOn = listOf(ComponentIds.LINUX),
                required = true,
                presenceScript = ComponentRemovalScripts.ESSENTIALS_PRESENCE,
            ),
            RemovableComponent(
                id = ComponentIds.PYTHON,
                dependsOn = listOf(ComponentIds.ESSENTIALS),
                removeScript = ComponentRemovalScripts.PYTHON_REMOVE,
                presenceScript = ComponentRemovalScripts.PYTHON_PRESENCE,
                sizeScript = ComponentRemovalScripts.PYTHON_SIZE,
            ),
            RemovableComponent(
                id = ComponentIds.NODE,
                dependsOn = listOf(ComponentIds.ESSENTIALS),
                required = true,
                presenceScript = ComponentRemovalScripts.NODE_PRESENCE,
            ),
            RemovableComponent(
                id = ComponentIds.OPENCODE,
                dependsOn = listOf(ComponentIds.NODE),
                required = true,
                presenceScript = ComponentRemovalScripts.OPENCODE_PRESENCE,
            ),
            RemovableComponent(
                id = ComponentIds.AITEAM,
                dependsOn = listOf(ComponentIds.ESSENTIALS, ComponentIds.OPENCODE),
                removeScript = AiTeamRemovalScripts.REMOVE_SCRIPT,
                sizeScript = AiTeamRemovalScripts.SIZE_SCRIPT,
                presenceScript = AiTeamRemovalScripts.PRESENCE_SCRIPT,
            ),
            RemovableComponent(
                id = ComponentIds.START,
                dependsOn = listOf(ComponentIds.OPENCODE),
                required = true,
                jobStep = true,
            ),
        )
}

package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor

/**
 * What the phone's install of an agent says it is (issue 147): the install script writes `.oc-pin` =
 * `version|arch|sha256` of the artifact it installed and a `launch` script that runs `<install>/payload/<member>`.
 * A copy whose record or program differs from this app's pin is behind and is installed again, also when the version
 * string is the same (Codex 0.169.0 moved from the bare binary to the official package without a new version; phones
 * that had the old copy kept a Codex that could not run commands, as the version-only check saw nothing behind).
 */
public data class AgentInstallRecorded(
    /** The `.oc-pin` text of the pinned version's install; null when that install has none. */
    val record: String?,
    /** The program the install's launcher runs; null when it has no launcher. */
    val program: String?,
)

public object AgentInstallRecord {
    private val ID = Regex("^[a-z][a-z0-9-]{0,63}$")
    private val VERSION = Regex("^[0-9A-Za-z][0-9A-Za-z.+_-]{0,63}$")

    /** Where the install of [agent]'s pinned version lives. */
    public fun installDir(agent: AgentDescriptor): String? {
        val recipe = agent.recipe ?: return null
        if (!ID.matches(agent.id.raw) || !VERSION.matches(recipe.version)) return null
        return "/home/oc/.local/share/oc-agents/${agent.id.raw}/${recipe.version}"
    }

    /** The record an install of [agent]'s pin on [architecture] writes; null when it has no artifact there. */
    public fun expectedRecord(agent: AgentDescriptor, architecture: AgentArchitecture): String? {
        val recipe = agent.recipe ?: return null
        val artifact = recipe.artifacts[architecture] ?: return null
        return "${recipe.version}|${architecture.wire}|${artifact.sha256}"
    }

    /** The program the pin's launcher runs. */
    public fun expectedProgram(agent: AgentDescriptor, architecture: AgentArchitecture): String? {
        val dir = installDir(agent) ?: return null
        val artifact = agent.recipe?.artifacts?.get(architecture) ?: return null
        return "$dir/payload/${artifact.archiveMember ?: agent.recipe.executable}"
    }

    private val LAUNCH = Regex("^exec .*\"([^\"]*)\" \"\\$@\"$", RegexOption.MULTILINE)

    /**
     * What one install says, from its two small files as the app reads them straight from the phone's Linux tree (no
     * process, no wait for the setup lane): the `.oc-pin` text and the program the `launch` script runs. Null for a
     * file that is not there.
     */
    public fun fromFiles(pin: String?, launch: String?): AgentInstallRecorded =
        AgentInstallRecorded(
            record = pin?.trim()?.takeIf { it.isNotEmpty() && it.length <= 300 },
            program = launch?.let { text -> LAUNCH.find(text)?.groupValues?.get(1) },
        )

    /**
     * The installed agents whose install of the pinned version records another artifact or runs another program than
     * the pin, or that this app's installer set up (it has the launcher) but which has no record at all. A copy of
     * another version is the version check's business; an agent with neither file (installed some other way) is left
     * alone.
     */
    public fun behind(
        installed: Collection<AgentId>,
        recorded: Map<AgentId, AgentInstallRecorded>,
        catalog: AgentCatalog,
        architecture: AgentArchitecture,
    ): Set<AgentId> =
        installed.filterTo(LinkedHashSet()) { id ->
            val agent = catalog.byId(id) ?: return@filterTo false
            val seen = recorded[id] ?: return@filterTo false
            val expected = expectedRecord(agent, architecture) ?: return@filterTo false
            val record = seen.record ?: return@filterTo seen.program != null
            record != expected || (seen.program != null && seen.program != expectedProgram(agent, architecture))
        }
}

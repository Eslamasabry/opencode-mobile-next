package io.github.eslamasabry.oc.engine.runtime.install

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * Port of lib/builtin/agents/agent_components.dart (`phoneAgentComponents`), lib/builtin/agents/paseo_scripts.dart (the
 * pinned Node for the private agent user) and the job assembly of lib/builtin/setup/setup_engine/start.dart
 * (`expandSelection`, the two-pass check, `_spec`): what one phone-agent install hands to the native runner.
 *
 * The agent components never include OpenCode or the server start, so an OpenCode 1 host gets its agent without its
 * OpenCode being touched. On a first install the agent user does not exist yet: its check run is refused, which leaves
 * those components pending (never a failure); the root `agent-user` bootstrap is ordered before `agent-node`, and every
 * agent-user component runs as that user (`agentUser`).
 */
public object AgentHostJob {
    /** A component of the job (Flutter `SetupComponent`, the fields the job reads). */
    public data class Component(
        val id: String,
        val dependsOn: List<String> = emptyList(),
        val required: Boolean = false,
        val native: Boolean = false,
        val agentUser: Boolean = false,
        val checkScript: String = "",
        val installScript: String = "",
    )

    /** One component as the native runner receives it. */
    public data class Spec(
        val id: String,
        val skipped: Boolean,
        val version: String? = null,
        val script: String? = null,
        val agentUser: Boolean = false,
    )

    /** The agent's own scripts and the shared ones this module does not own (Flutter `AgentPhoneScripts`, Paseo). */
    public data class AgentScripts(
        val agentId: String,
        val bootstrapUser: String,
        val paseoCheck: String,
        val paseoInstall: String,
        val agentCheck: String,
        val agentInstall: String,
        val linuxCheck: String = "",
        val essentialsCheck: String = "",
        val essentialsInstall: String = "",
        val pythonCheck: String = "",
        val pythonInstall: String = "",
    )

    /** Flutter `phoneAgentComponents`: Linux, essentials, Python, then the agent user, Node, Paseo and the agent. */
    public fun components(scripts: AgentScripts): List<Component> =
        listOf(
            Component("linux", required = true, native = true, checkScript = scripts.linuxCheck),
            Component(
                "essentials",
                dependsOn = listOf("linux"),
                required = true,
                checkScript = scripts.essentialsCheck,
                installScript = scripts.essentialsInstall,
            ),
            Component("python", dependsOn = listOf("essentials"), checkScript = scripts.pythonCheck, installScript = scripts.pythonInstall),
            Component(
                "agent-user",
                dependsOn = listOf("essentials"),
                checkScript = "[ \"${'$'}(id -u oc)\" = 1000 ] && [ \"${'$'}(id -g oc)\" = 1000 ] && [ -d /home/oc ];",
                installScript = scripts.bootstrapUser,
            ),
            Component(
                "agent-node",
                dependsOn = listOf("agent-user"),
                agentUser = true,
                checkScript = AgentNode.CHECK,
                installScript = AgentNode.INSTALL,
            ),
            Component(
                "agent-paseo",
                dependsOn = listOf("agent-node"),
                agentUser = true,
                checkScript = scripts.paseoCheck,
                installScript = scripts.paseoInstall,
            ),
            Component(
                "agent-${scripts.agentId}",
                dependsOn = listOf("agent-paseo", "python"),
                agentUser = true,
                checkScript = scripts.agentCheck,
                installScript = scripts.agentInstall,
            ),
        )

    /** Flutter `expandSelection`: required ones and [ids] with their dependencies, each after what it depends on. */
    public fun expandSelection(registry: List<Component>, ids: Set<String>): List<Component> {
        val byId = registry.associateBy { it.id }
        val wanted = mutableSetOf<String>()
        fun want(id: String) {
            val component = byId[id] ?: return
            if (!wanted.add(id)) return
            component.dependsOn.forEach(::want)
        }
        registry.filter { it.required || it.id in ids }.forEach { want(it.id) }
        val ordered = mutableListOf<Component>()
        val placed = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        fun place(component: Component) {
            if (component.id in placed) return
            check(visiting.add(component.id)) { "Setup components depend on each other." }
            for (dependency in component.dependsOn) {
                val other = byId[dependency]
                if (other != null && dependency in wanted) place(other)
            }
            visiting.remove(component.id)
            placed.add(component.id)
            ordered.add(component)
        }
        registry.filter { it.id in wanted }.forEach(::place)
        return ordered
    }

    /** The two check lanes inside Ubuntu: as root, and as the private agent user (refused before that user exists). */
    public interface CheckPort {
        public suspend fun ubuntuInstalled(): Boolean

        public suspend fun runAsRoot(script: String): Outcome<String>

        public suspend fun runAsAgentUser(script: String): Outcome<String>
    }

    /**
     * Flutter `_checkUntraced`: one combined check per lane. A refused agent-user lane (no `oc` user on a first install)
     * leaves its components without a result, so they install; a refused root lane is the caller's failure (null).
     * Android's own marker is the truth for the native Linux base.
     */
    public suspend fun check(job: List<Component>, port: CheckPort): Map<String, InstallPrelude.CheckResult>? {
        if (!port.ubuntuInstalled()) return emptyMap()
        val results = linkedMapOf<String, InstallPrelude.CheckResult>()
        for (agentUser in listOf(false, true)) {
            val scripts =
                job.filter { it.agentUser == agentUser && it.checkScript.isNotBlank() }.associate { it.id to it.checkScript }
            if (scripts.isEmpty()) continue
            val script = InstallPrelude.combinedCheckScript(scripts)
            val ran = if (agentUser) port.runAsAgentUser(script) else port.runAsRoot(script)
            when (ran) {
                is Outcome.Ok -> results.putAll(InstallPrelude.parseCombinedChecks(ran.value))
                is Outcome.Failed -> if (!agentUser) return null
            }
        }
        for (component in job.filter { it.native }) {
            results[component.id] = InstallPrelude.CheckResult(ok = true, version = results[component.id]?.version)
        }
        return results
    }

    /** Flutter `_spec` for script components: a passing check is skipped; the rest run after the setup preludes. */
    public fun specs(job: List<Component>, checks: Map<String, InstallPrelude.CheckResult>): List<Spec> =
        job.map { component ->
            val check = checks[component.id]
            if (check?.ok == true) {
                Spec(component.id, skipped = true, version = check.version)
            } else {
                Spec(
                    component.id,
                    skipped = false,
                    script = InstallPrelude.withPrelude(component.installScript),
                    agentUser = component.agentUser,
                )
            }
        }

    /** Selection → checks → the specs handed to the native runner; null when the root check lane was refused. */
    public suspend fun plan(scripts: AgentScripts, port: CheckPort): List<Spec>? {
        val job = expandSelection(components(scripts), setOf("agent-${scripts.agentId}"))
        val checks = check(job, port) ?: return null
        return specs(job, checks)
    }
}

/** Port of lib/builtin/agents/paseo_scripts.dart's pinned Node for the private agent user (`/home/oc/.local/node`). */
public object AgentNode {
    public const val VERSION: String = "v24.21.0"
    public const val ARM64_SHA256: String = "724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5"
    public const val X64_SHA256: String = "6e1db87ef58b8819e5d5402eff1536491b18edd8eb7bee5ef7897876e88dc5ff"

    private const val REQUIRE_AGENT_USER: String = """[ "${'$'}(id -u)" = 1000 ] && [ "${'$'}HOME" = /home/oc ] || {
  echo '[oc] Agent setup needs its private Linux user' >&2
  exit 1
}
case "${'$'}(uname -m)" in
  aarch64|arm64|x86_64|amd64) ;;
  *) echo '[oc] Agent setup needs a 64-bit phone' >&2; exit 1 ;;
esac
umask 077
"""

    /** Flutter `PaseoPhoneScripts.nodeInstall`. */
    public const val INSTALL: String = """set -eu
$REQUIRE_AGENT_USER
case "${'$'}(uname -m)" in
  aarch64|arm64) node_arch=arm64; node_sha=$ARM64_SHA256 ;;
  x86_64|amd64) node_arch=x64; node_sha=$X64_SHA256 ;;
esac
node_dir=/home/oc/.local/node
node_part=/home/oc/.cache/oc-setup/node-$VERSION-linux-${'$'}node_arch.tar.gz
mkdir -p /home/oc/.local/bin /home/oc/.cache/oc-setup
trap 'rm -rf "${'$'}node_dir.new"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
oc_stage 'Downloading Node.js $VERSION'
oc_download "https://nodejs.org/dist/$VERSION/node-$VERSION-linux-${'$'}node_arch.tar.gz" \
  "${'$'}node_part" "${'$'}node_sha"
oc_stage 'Preparing Node.js'
rm -rf "${'$'}node_dir.new"
mkdir -p "${'$'}node_dir.new"
tar -xzf "${'$'}node_part" -C "${'$'}node_dir.new" --strip-components=1
[ "${'$'}("${'$'}node_dir.new/bin/node" --version)" = '$VERSION' ]
rm -rf "${'$'}node_dir.old"
if [ -d "${'$'}node_dir" ]; then mv "${'$'}node_dir" "${'$'}node_dir.old"; fi
if ! mv "${'$'}node_dir.new" "${'$'}node_dir"; then
  [ ! -d "${'$'}node_dir.old" ] || mv "${'$'}node_dir.old" "${'$'}node_dir"
  exit 1
fi
for node_tool in node npm npx; do
  ln -sf "${'$'}node_dir/bin/${'$'}node_tool" "/home/oc/.local/bin/${'$'}node_tool"
done
rm -rf "${'$'}node_dir.old"
rm -f "${'$'}node_part"
oc_version '$VERSION'
"""

    /** Flutter `PaseoPhoneScripts.nodeCheck`. */
    public const val CHECK: String = """set -eu
[ "${'$'}(/home/oc/.local/node/bin/node --version)" = '$VERSION' ]
printf '%s\n' '$VERSION'
"""
}

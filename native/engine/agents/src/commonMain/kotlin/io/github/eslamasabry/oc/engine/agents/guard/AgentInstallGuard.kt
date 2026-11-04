package io.github.eslamasabry.oc.engine.agents.guard

import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor

/**
 * Port of lib/builtin/agents/agent_install_guard.dart. Explicit QA build admission floor
 * (`OC_QA_AGENT_INSTALL_MIN_FREE_BYTES`; the build passes it in, 0 in normal builds). Normal builds keep the pinned
 * estimate. Raising the existing native requirement exercises its real storage guard; this never changes available
 * space or enables a runtime capability.
 */
public class AgentInstallGuard(public val minimumFreeBytes: Long = 0L) {
    /** The largest pinned download of [agent]'s artifacts, raised to half the QA floor (rounded up) for [agentIds]. */
    public fun downloadBytesFor(agent: AgentDescriptor): Long? {
        var bytes: Long? = null
        for (artifact in agent.recipe?.artifacts?.values ?: emptyList()) {
            val size = artifact.downloadBytes
            if (size != null && (bytes == null || size > bytes)) bytes = size
        }
        if (agent.id.raw !in agentIds || minimumFreeBytes <= 0) return bytes
        // Native admission doubles this metadata. Saturate at its signed-long ceiling so a mistyped QA flag cannot
        // overflow into a lower requirement.
        val requested = minimumFreeBytes
        val estimate = requested / 2 + requested % 2
        return if (bytes == null || estimate > bytes) estimate else bytes
    }

    public companion object {
        public val agentIds: Set<String> = setOf("codex", "gemini", "qwen", "kilo", "cline", "goose", "omp-acp", "fx")
    }
}

/**
 * Port of `requiredSetupFreeBytes` (lib/builtin/setup/preflight.dart): free space needed before a download starts. A
 * known extracted payload coexists with its retained archive during installation, plus 64 MiB of scratch space.
 * Unknown payload sizes retain the 300 MB / 2x policy. Saturates at [Long.MAX_VALUE].
 */
public fun requiredSetupFreeBytes(downloadBytes: Long, installedBytes: Long? = null): Long {
    val maximum = Long.MAX_VALUE
    val scratch = 64L * 1024 * 1024
    val nonnegative = maxOf(downloadBytes, 0L)
    val doubled = if (nonnegative > maximum / 2) maximum else nonnegative * 2
    val legacy = maxOf(doubled, 300L * 1000 * 1000)
    if (installedBytes == null || installedBytes <= 0) return legacy
    val retainedAndExtracted = saturated(nonnegative, installedBytes)
    return maxOf(legacy, saturated(retainedAndExtracted, scratch))
}

private fun saturated(first: Long, second: Long): Long =
    if (first >= Long.MAX_VALUE - second) Long.MAX_VALUE else first + second

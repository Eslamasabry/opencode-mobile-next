package io.github.eslamasabry.oc.android.agenthost

import android.content.Context

/**
 * The phone binding of [PrivateAgentAuth]: the kept [PhoneAgentAuthRuntime], launched through [AgentProcesses] with
 * private output, its Claude lock cleared only while the app's same-uid processes are quiet, and the other agent
 * owners excluded from every signal (as `BuiltinLinux.privateAgentAuth` wired it).
 */
fun androidPrivateAuth(context: Context): (AgentProcesses) -> PrivateAgentAuth = { processes ->
    val runtime = PhoneAgentAuthRuntime(
        context,
        processes::authLaunch,
        processes::authClearLock,
        processes::authOtherOwnerSnapshot,
    )
    object : PrivateAgentAuth {
        override fun run(arguments: Map<*, *>): Map<String, Any?> = runtime.run(arguments)

        override fun blockProfile(profileId: String): Boolean = runtime.blockProfile(profileId)
    }
}

package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.termux.AndroidGitRepairTermuxFiles
import io.github.eslamasabry.oc.android.termux.GitObjectRepair
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.git.GitLinkRepairReport
import io.github.eslamasabry.oc.engine.git.GitLinkRepairService
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The repair reaches only this saved phone owner's current conversation directory, never a remote
 * host.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidGitLinkRepairService(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
) : GitLinkRepairService {
    private val lane = Mutex()

    override fun available(ref: ConversationRef): Boolean =
        try {
            val graph = registry.graphFor(ref)
            graph != null &&
                graph.profile.managedOnPhone &&
                graph.profile.origin.scheme == "http" &&
                AddressRules.isLoopback(graph.profile.origin.host) &&
                graph.profile.origin.port == PhoneServerScripts.PORT &&
                stores.profiles.profile(ref.profileId) != null &&
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value ==
                    ref.profileId.raw
        } catch (_: Exception) {
            false
        }

    override suspend fun repair(
        ref: ConversationRef,
        directory: String,
    ): Outcome<GitLinkRepairReport> = lane.withLock {
        if (!available(ref)) return@withLock refused()
        val graph = registry.graphFor(ref) ?: return@withLock refused()
        val session =
            when (val loaded = graph.session.server.session(ref.sessionId)) {
                is Outcome.Ok -> loaded.value
                is Outcome.Failed -> return@withLock loaded
            }
        if (session.directory != directory) return@withLock refused()
        withContext(dispatchers.io) {
            try {
                val linux = BuiltinLinux.get(app)
                val projects = File(app.filesDir, "projects")
                val current = { registry.graphFor(ref) === graph && available(ref) }
                val repair =
                    GitObjectRepair(
                        projects,
                        File(app.filesDir, "git-repair"),
                        AndroidGitRepairTermuxFiles(app, dispatchers),
                        current,
                    ) { path ->
                        if (!current()) false
                        else {
                            val quoted = "'" + path.replace("'", "'\"'\"'") + "'"
                            val result =
                                linux.run(
                                    "cd -- $quoted 2>/dev/null || exit 3\ngit fsck --connectivity-only --no-progress >/dev/null 2>&1 || exit 4\ngit status --porcelain >/dev/null 2>&1\n",
                                    300,
                                )
                            result.exitCode == 0 && current()
                        }
                    }
                Outcome.Ok(repair.repair(directory))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                refused()
            }
        }
    }

    override fun openTermuxSettings() {
        try {
            app.startActivity(
                Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${app.packageName}"),
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            /* The repair remains refused until Android grants the permission. */
        }
    }

    private fun refused(): Outcome.Failed = Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
}

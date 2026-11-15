package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.runtime.paths.SharedStoragePath
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderReach
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderSide
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SharedFolderReach
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * Issue 147 (the first customer: "selecting a folder must not give the false impression that the agent has access"):
 * the check New conversation runs when a folder in this phone's storage is chosen and again before the first message,
 * and an open conversation runs when its page comes back and before each message.
 *
 * The chosen folder joins the folders the phone's Linux binds (the one list [NativeSharedProjectFolders] writes), then
 * the side that will work in it lists and writes it as its own Linux identity: the phone's OpenCode as root, the phone
 * agents' helper as uid 1000. A process started before the choice does not see it until it starts again, and that is
 * said too. Only this phone's own server is asked: a remote server's `/sdcard` is its own.
 */
@SingleIn(AppScope::class)
@Inject
class PhoneFolderPreflight(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val roots: NativeSharedProjectFolders,
    private val export: AndroidProjectExport,
    private val server: PhoneServerSource,
    private val helper: AppLocalAgentHostStarter,
    appScope: AppCoroutineScope,
) {
    /** W3: one restart for a folder restarts every side that runs without it; work running there is never cut. */
    private val restarts =
        PhoneFolderRestarts(
            scope = CoroutineScope(appScope.coroutineContext + dispatchers.io),
            misses = { side, path -> withContext(dispatchers.io) { runsWithout(side, path) } },
            serverBusy = {
                server.refresh()
                server.facts.value.replyRunning
            },
            restartServer = { server.restart() },
            restartAgents = { helper.restartForFolder() },
            every = WAIT_EVERY,
        )

    /**
     * What [directory] is to the side that will work in it; null when it is not this phone's storage or unknown. A
     * [thorough] check binds the folder for the next start, lists and writes it from that side, and gives a Git
     * repository there the settings Android's storage needs; otherwise (before each message) only the access, the
     * folder and the running binds are read, with no Linux started.
     */
    suspend fun check(
        owner: ProfileId,
        phoneAgent: Boolean,
        directory: String,
        thorough: Boolean = true,
    ): PhoneFolderReach? {
        if (!SharedStoragePath.isShared(directory) || !runsHere(owner)) return null
        if (stores.warm() is Outcome.Failed) return null
        if (export.storagePermissionGranted() == false) return PhoneFolderReach.AppAccess
        // The choice is the person's consent to this folder: it is bound from the next start of either side. A whole
        // volume is never bound on its own; the check below then says the side can't reach it.
        val root = SharedStoragePath.projectRoot(directory)
        // A list that could not be saved only matters for the next start; the check below still answers for now.
        if (thorough && root != null) remember(owner, root)
        val code =
            withContext(dispatchers.io) {
                attempt({ Failure.Device() }) {
                    val linux = BuiltinLinux.get(app)
                    val answer =
                        linux.sharedFolderReach(directory, if (phoneAgent) helperOf(owner) else null, probe = thorough)
                    // A repository copied from a computer would otherwise show every file as changed (best effort:
                    // the folder works either way).
                    if (thorough && answer == SharedFolderReach.Ready.code) linux.prepareSharedFolderGit(directory)
                    answer
                }
            }
        return when (code) {
            is Outcome.Failed -> null
            is Outcome.Ok -> reachOf(code.value)
        }
    }

    /**
     * The check for an open conversation of [profile] (this phone's server, or its agents' helper) in [directory].
     */
    suspend fun checkConversation(profile: ProfileId, directory: String?, thorough: Boolean): PhoneFolderReach? {
        if (directory == null) return null
        val phone = stores.phoneOfHelper(profile)
        return check(phone ?: profile, phoneAgent = phone != null, directory, thorough)
    }

    /**
     * W3: whether the other side of this phone (its OpenCode for a phone agent, else the agents' helper) also runs
     * without [directory], so the one restart restarts both.
     */
    suspend fun bothSides(owner: ProfileId, phoneAgent: Boolean, directory: String): Boolean {
        if (!SharedStoragePath.isShared(directory) || !runsHere(owner)) return false
        val other = if (phoneAgent) PhoneFolderSide.Server else PhoneFolderSide.Agents
        return withContext(dispatchers.io) {
            try {
                runsWithout(other, directory)
            } catch (_: Exception) {
                false
            }
        }
    }

    /** W3: the sides of [owner]'s phone whose restart waits for their work to end. */
    fun waiting(owner: ProfileId): Set<PhoneFolderSide> = if (runsHere(owner)) restarts.waiting.value else emptySet()

    /** Whether [side]'s process now running was started without [path] (false when it does not run). */
    private fun runsWithout(side: PhoneFolderSide, path: String): Boolean {
        val linux = BuiltinLinux.get(app)
        return when (side) {
            PhoneFolderSide.Server -> !linux.serverCanReadSharedFolder(path)
            PhoneFolderSide.Agents -> {
                val owner = (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value ?: return false
                linux.agentHost.storageView(helperOf(ProfileId(owner)))?.covers(path) == false
            }
        }
    }

    /** [fix] for an open conversation of [profile]. */
    suspend fun fixConversation(profile: ProfileId, reach: PhoneFolderReach): Outcome<Unit> {
        val phone = stores.phoneOfHelper(profile)
        return fix(phone ?: profile, phoneAgent = phone != null, reach)
    }

    /**
     * The way forward: Android's All files access page, or a new start of the side started before the choice; with
     * [directory] known, the other side restarts too when it also runs without it (W3).
     */
    suspend fun fix(
        owner: ProfileId,
        phoneAgent: Boolean,
        reach: PhoneFolderReach,
        directory: String? = null,
    ): Outcome<Unit> =
        when (reach) {
            PhoneFolderReach.AppAccess ->
                when (val asked = export.requestStoragePermission()) {
                    is Outcome.Failed -> asked
                    // The answer is read again by the check that follows; a "no" is said there.
                    is Outcome.Ok -> roots.syncNow()
                }
            PhoneFolderReach.Restart ->
                when (val synced = roots.syncNow()) {
                    is Outcome.Failed -> synced
                    is Outcome.Ok ->
                        if (!runsHere(owner)) Outcome.Failed(Failure.Unavailable(FeatureId("phoneFolder")))
                        else
                            restarts.restart(
                                if (phoneAgent) PhoneFolderSide.Agents else PhoneFolderSide.Server,
                                directory?.takeIf(SharedStoragePath::isShared),
                            )
                }
            else -> Outcome.Failed(Failure.Unavailable(FeatureId("phoneFolder")))
        }

    private fun runsHere(profile: ProfileId): Boolean =
        (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value == profile.raw

    /** Adds [root] to [owner]'s granted shared folders ([SharedProjectList]) and rewrites the runtime's list. */
    private suspend fun remember(owner: ProfileId, root: String): Outcome<Unit> =
        when (val added = SharedProjectList.add(stores.prefs, stores.lanes.forProfile(owner), owner, root)) {
            is Outcome.Failed -> added
            is Outcome.Ok -> roots.syncNow()
        }

    /** The helper's own id in the phone's Linux (the one its agents run under): `<owner>`, as its start names it. */
    private fun helperOf(owner: ProfileId): String =
        (stores.prefs.get("oc.phoneAgentOwner.${owner.raw}") as? PrefValue.Str)
            ?.value
            ?.takeIf { ID.matches(it) } ?: owner.raw

    private companion object {
        val WAIT_EVERY = 5.seconds
        val ID = Regex("[A-Za-z0-9_-]{1,80}")

        fun reachOf(code: String): PhoneFolderReach? =
            when (code) {
                SharedFolderReach.Ready.code -> PhoneFolderReach.Ready
                SharedFolderReach.AppAccess.code -> PhoneFolderReach.AppAccess
                SharedFolderReach.Restart.code -> PhoneFolderReach.Restart
                SharedFolderReach.Missing.code -> PhoneFolderReach.Missing
                SharedFolderReach.Denied.code -> PhoneFolderReach.Denied
                else -> null
            }
    }
}

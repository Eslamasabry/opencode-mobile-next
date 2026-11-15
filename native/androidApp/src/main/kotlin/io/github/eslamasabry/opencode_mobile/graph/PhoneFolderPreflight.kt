package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.stringList
import io.github.eslamasabry.oc.engine.runtime.paths.SharedStoragePath
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderReach
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SharedFolderReach
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
) {
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

    /** [fix] for an open conversation of [profile]. */
    suspend fun fixConversation(profile: ProfileId, reach: PhoneFolderReach): Outcome<Unit> {
        val phone = stores.phoneOfHelper(profile)
        return fix(phone ?: profile, phoneAgent = phone != null, reach)
    }

    /** The way forward: Android's All files access page, or a new start of the side started before the choice. */
    suspend fun fix(owner: ProfileId, phoneAgent: Boolean, reach: PhoneFolderReach): Outcome<Unit> =
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
                        if (phoneAgent) helper.restartForFolder()
                        else if (runsHere(owner)) server.restart()
                        else Outcome.Failed(Failure.Unavailable(FeatureId("phoneFolder")))
                }
            else -> Outcome.Failed(Failure.Unavailable(FeatureId("phoneFolder")))
        }

    private fun runsHere(profile: ProfileId): Boolean =
        (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value == profile.raw

    /** Adds [root] to [owner]'s granted shared folders (`oc.sharedProjects.<owner>`) and rewrites the runtime's list. */
    private suspend fun remember(owner: ProfileId, root: String): Outcome<Unit> {
        val key = "$SHARED_PROJECTS${owner.raw}"
        // Compared and set: another writer of this list (a Files grant, a project opened from storage) adding its
        // folder at the same moment never drops this one, nor this one theirs.
        repeat(LIST_TRIES) {
            val before = stores.prefs.get(key)
            if (before != null && before !is PrefValue.StrList) return Outcome.Failed(Failure.StorageRefused())
            val known = (before as? PrefValue.StrList)?.value.orEmpty()
            if (root in known) return roots.syncNow()
            when (
                val saved =
                    stores.prefs.compareAndCommit(stores.lanes.forProfile(owner), mapOf(key to before)) {
                        putStringList(key, (known + root).distinct().sorted())
                    }
            ) {
                is Outcome.Failed -> return saved
                is Outcome.Ok -> if (saved.value) return roots.syncNow()
            }
        }
        return Outcome.Failed(Failure.Conflict())
    }

    /** The helper's own id in the phone's Linux (the one its agents run under): `<owner>`, as its start names it. */
    private fun helperOf(owner: ProfileId): String =
        (stores.prefs.get("oc.phoneAgentOwner.${owner.raw}") as? PrefValue.Str)
            ?.value
            ?.takeIf { ID.matches(it) } ?: owner.raw

    private companion object {
        const val SHARED_PROJECTS = "oc.sharedProjects."
        const val LIST_TRIES = 3
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

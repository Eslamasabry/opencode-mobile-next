package io.github.eslamasabry.opencode_mobile

import java.io.File

/** Only the exact empty profile lock may be removed, never account files. */
internal object PhoneAgentAuthLock {
    /**
     * signin-truth: removes the lock only when [cold] (the cold-start process census: no task of this app's user runs
     * but the app's own registered processes) proves no CLI is alive that could hold it. A census that cannot be read
     * proves nothing, and the lock stays. Both places that clear the lock (a sign-in check, a helper start) go through
     * here.
     */
    fun clearWhenCold(filesRoot: File, profileId: String, cold: () -> Boolean): Boolean {
        val proven = try { cold() } catch (_: Exception) { false }
        return proven && clear(filesRoot, profileId)
    }

    fun clear(filesRoot: File, profileId: String): Boolean {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profileId))
        val lock = PhoneAgentPaths.resolve(filesRoot,
            "linux/ubuntu/home/oc/.oc-profiles/$profileId/claude/.oauth_refresh.lock")
        val contents = lock.listFiles()
        return lock.isDirectory && contents != null && contents.isEmpty() && lock.delete()
    }
}

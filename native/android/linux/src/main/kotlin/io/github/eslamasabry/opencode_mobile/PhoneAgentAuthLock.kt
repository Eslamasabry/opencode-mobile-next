package io.github.eslamasabry.opencode_mobile

import java.io.File

/** Only the exact empty profile lock may be removed, never account files. */
internal object PhoneAgentAuthLock {
    fun clear(filesRoot: File, profileId: String): Boolean {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profileId))
        val lock = PhoneAgentPaths.resolve(filesRoot,
            "linux/ubuntu/home/oc/.oc-profiles/$profileId/claude/.oauth_refresh.lock")
        val contents = lock.listFiles()
        return lock.isDirectory && contents != null && contents.isEmpty() && lock.delete()
    }
}

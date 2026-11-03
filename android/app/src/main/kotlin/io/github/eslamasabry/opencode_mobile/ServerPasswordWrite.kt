package io.github.eslamasabry.opencode_mobile

/**
 * Writes the phone server password inside Ubuntu. The script holds no secret:
 * it runs as a `/bin/sh -c` argument, which every process of this app's user
 * can read in /proc. The password arrives on stdin; an empty stdin fails and
 * leaves the old file alone. No chmod: the protected sandbox denies chmod
 * (the boundary proof requires it), and umask 077 already makes the folder
 * 0700 and the file 0600 when they are created.
 */
internal object ServerPasswordWrite {
    const val FILE: String = "/root/.oc-builtin/server.password"

    fun script(file: String = FILE): String {
        val dir = file.substringBeforeLast('/')
        return "set -eu\n" +
            "umask 077\n" +
            "mkdir -p '$dir'\n" +
            "cat > '$file.tmp'\n" +
            "[ -s '$file.tmp' ] || { rm -f '$file.tmp'; exit 78; }\n" +
            "mv '$file.tmp' '$file'\n"
    }
}

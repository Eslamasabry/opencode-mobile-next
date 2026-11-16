package io.github.eslamasabry.opencode_mobile

import java.io.File

/** Installed helper version, with the existing bounded executable probe as a fallback. */
internal object InstalledHelperVersion {
    fun read(rootfs: File, fallback: () -> String?): String? = fallback()
}

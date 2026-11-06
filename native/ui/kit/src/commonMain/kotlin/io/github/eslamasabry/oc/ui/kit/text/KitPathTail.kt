package io.github.eslamasabry.oc.ui.kit.text

/**
 * The two folders above a project's own, enough to tell same-named projects apart ("…/scratch/walk"): the end of a
 * path is what differs, so a list never shows a long path cut off mid-name. Used by Projects and the new
 * conversation's project sheet alike.
 */
public fun kitPathTail(directory: String): String {
    val parents = directory.trim().trimEnd('/', '\\').split('/', '\\').filter { it.isNotEmpty() }.dropLast(1)
    return if (parents.isEmpty()) directory else "…/" + parents.takeLast(2).joinToString("/")
}

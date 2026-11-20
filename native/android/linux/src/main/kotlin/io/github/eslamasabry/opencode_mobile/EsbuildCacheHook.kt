package io.github.eslamasabry.opencode_mobile

/**
 * The app's own preload for the agents' helper (`node --require`). It remembers what esbuild built for the helper's
 * built-in plug-ins, so the next start skips that work. The bytes are ours: written into the phone's Linux at every
 * launch, never downloaded, and no package file is edited. Kept free of the dollar sign so the text is the same
 * everywhere it is copied.
 */
internal object EsbuildCacheHook {
    const val SOURCE: String = ""
}

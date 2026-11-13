package io.github.eslamasabry.opencode_mobile

/**
 * Which loopback port this app's phone server takes.
 *
 * B-232: each build has its own default, set by the app's build (PHONE_SERVER_PORT: the installed app 4098, preview
 * 4100, dev 4102), so two of them on one phone never reach for the same port. Before, only a package ending in
 * `.native` got its own port and the dev build fell through to the installed app's 4098.
 *
 * B-239 / B-119: the default comes first on every start. A port the server once moved to (its default was held for a
 * moment) is no longer kept for good, so the server comes back home as soon as it can and the ports of the other
 * builds are tried last.
 */
internal object PhoneServerPorts {
    /** The installed app's port. */
    const val INSTALLED: Int = 4098

    /** The ports a server moves to when its own is held. */
    val SPARE: IntRange = 4100..4199

    /** The other builds' defaults (preview, dev): tried last when this build's own port is held. */
    private val BUILD_DEFAULTS: List<Int> = listOf(4100, 4102)

    fun valid(port: Int): Boolean = port == INSTALLED || port in SPARE

    /** [configured] is the build's own setting; a package name only decides when no build said. */
    fun default(packageName: String, configured: Int?): Int =
        configured?.takeIf(::valid)
            ?: when {
                packageName.endsWith(".native.dev") -> 4102
                packageName.endsWith(".native") -> 4100
                else -> INSTALLED
            }

    /**
     * The port the last start chose, as saved (a running server may be on it, so a spare port is kept). The installed
     * app's 4098 saved by another build (a dev build before B-232) is not this build's: it reads as the default.
     */
    fun saved(saved: Int?, default: Int): Int = saved?.takeIf { it == default || it in SPARE } ?: default

    /** The order a start tries: this build's own port, then a spare one, the other builds' defaults last. */
    fun candidates(default: Int): List<Int> =
        (listOf(default) + SPARE.filterNot { it in BUILD_DEFAULTS } + BUILD_DEFAULTS).distinct()
}

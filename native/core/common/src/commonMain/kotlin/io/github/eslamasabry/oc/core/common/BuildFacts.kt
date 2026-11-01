package io.github.eslamasabry.oc.core.common

/** Build facts (KMP modules have no BuildConfig; ADR §5.2). */
public enum class Channel { Stable, Preview, Desktop }

public data class AppBuildInfo(val versionName: String, val versionCode: Int, val channel: Channel, val debuggable: Boolean)

public enum class LaunchMode { Normal, SafeMode }

/** ADR §7.3 item 3: only kit androidMain casts `native`. */
public interface PlatformViewHandle {
    public val native: Any
}

/** Platform name for diagnostics, the ADR's other allowed `expect`. */
public expect val platformName: String

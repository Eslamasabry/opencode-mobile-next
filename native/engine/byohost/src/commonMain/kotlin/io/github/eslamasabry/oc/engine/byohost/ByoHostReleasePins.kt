package io.github.eslamasabry.oc.engine.byohost

import io.github.eslamasabry.oc.core.common.dart.DartJson

/** One upstream artifact the review pinned (ledger `artifacts.<arch>`). */
public data class ByoHostPinnedArtifact(val url: String, val sha256: String)

/**
 * The reviewed pin ledger (scripts/byo-host/release-pins.json, Dart lib/host/byo_host_bundle_pins.dart).
 * [artifacts] are keyed by Debian architecture (`amd64`, `arm64`); [appVersions] maps an exact app version
 * (`2.1.0+57`) to the bundle reviewed for it. Publishing an entry requires owner review, so the shipped map is empty.
 */
public data class ByoHostPinLedger(
    val schemaVersion: Int,
    val openCodeVersion: String,
    val bundleVersion: String,
    val artifacts: Map<String, ByoHostPinnedArtifact>,
    val appVersions: Map<String, ByoHostBundle>,
) {
    /** Dart `byoHostBundleForAppVersion`: only an exact version match; never a prefix, `latest` or the empty string. */
    public fun bundleForAppVersion(appVersion: String): ByoHostBundle? = appVersions[appVersion]

    /** Whether a bundle offered for [appVersion] is exactly the reviewed one (same versions, URLs and digests). */
    public fun verifyBundle(appVersion: String, bundle: ByoHostBundle?): ByoHostPinCheck {
        val reviewed = bundleForAppVersion(appVersion) ?: return ByoHostPinCheck.NotReviewed
        if (bundle == null) return ByoHostPinCheck.NotReviewed
        if (bundle.version != reviewed.version || bundle.openCodeVersion != reviewed.openCodeVersion) {
            return ByoHostPinCheck.VersionMismatch
        }
        if (bundle.artifacts.keys != reviewed.artifacts.keys) return ByoHostPinCheck.UnknownArchitecture
        for ((arch, artifact) in bundle.artifacts) {
            val pinned = reviewed.artifacts.getValue(arch)
            if (artifact.url != pinned.url) return ByoHostPinCheck.UrlMismatch
            if (artifact.sha256 != pinned.sha256) return ByoHostPinCheck.DigestMismatch
        }
        return ByoHostPinCheck.Match
    }

    /** Checks downloaded upstream bytes for a bundle architecture (`x64` / `arm64`) against the ledger's SHA-256. */
    public fun verifyUpstream(architecture: String, url: String, bytes: ByteArray): ByoHostPinCheck {
        val pinned = artifacts[debianArchitecture(architecture) ?: return ByoHostPinCheck.UnknownArchitecture]
            ?: return ByoHostPinCheck.UnknownArchitecture
        if (url != pinned.url) return ByoHostPinCheck.UrlMismatch
        return if (DartJson.sha256Hex(bytes) == pinned.sha256) ByoHostPinCheck.Match else ByoHostPinCheck.DigestMismatch
    }

    public companion object {
        /** Bundle architecture (`x64`, `arm64`) to ledger architecture (`amd64`, `arm64`). */
        public fun debianArchitecture(architecture: String): String? =
            when (architecture) {
                "x64" -> "amd64"
                "arm64" -> "arm64"
                else -> null
            }
    }
}

/** Plain-word result of a pin check; anything but [Match] is [ByoHostFailureCode.Checksum] or BundleUnavailable. */
public enum class ByoHostPinCheck {
    Match, NotReviewed, VersionMismatch, UnknownArchitecture, UrlMismatch, DigestMismatch,
}

/** The shipped ledger, verbatim from scripts/byo-host/release-pins.json (schema 1). */
public object ByoHostReleasePins {
    public val Ledger: ByoHostPinLedger =
        ByoHostPinLedger(
            schemaVersion = 1,
            openCodeVersion = "2.1.1",
            bundleVersion = "2.0.0",
            artifacts =
                mapOf(
                    "amd64" to
                        ByoHostPinnedArtifact(
                            url =
                                "https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/" +
                                    "cli-linux-x64-baseline-2.1.1.tgz",
                            sha256 = "216d3861d0a167adad5850282439bae13483203f4b110f0510f260118bba2858",
                        ),
                    "arm64" to
                        ByoHostPinnedArtifact(
                            url = "https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.1.1.tgz",
                            sha256 = "cfa3ae6363fc3082f03072297069d81f53559a761371fdb4b56fe6bfa01ca066",
                        ),
                ),
            // No reviewed OpenCode 2 bundle is published for any app version yet; older bundles must never install.
            appVersions = emptyMap(),
        )

    /** The bundle a [ByoHostService] may use for this exact app version (`<version>+<build>`), or null. */
    public fun bundleForAppVersion(appVersion: String): ByoHostBundle? = Ledger.bundleForAppVersion(appVersion)
}

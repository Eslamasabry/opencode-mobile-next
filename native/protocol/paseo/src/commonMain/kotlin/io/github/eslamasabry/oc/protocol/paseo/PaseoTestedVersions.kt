package io.github.eslamasabry.oc.protocol.paseo

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * The one definition of which Paseo daemon and Claude Code versions this app is qualified against. A verbatim port of
 * `lib/paseo/tested_versions.dart` (plan 04 §8 pins; the 2026-10-31 additions carry their evidence in
 * `docs/qa/pins-2026-10-31/README.md`). Add a version only with runtime evidence. The previous daemon stays listed so a
 * phone that has not updated yet keeps working.
 */
public object PaseoTestedVersions {
    /** Daemon versions. 0.11.2 is the version the phone installs (plan 04 §8 "phone pin"). */
    public val paseo: ImmutableSet<String> = persistentSetOf("0.9.2", "0.11.2", "0.12.0", "0.13.2", "0.14.0")

    /** The daemon version the phone helper installs. */
    public const val PHONE_PIN: String = "0.11.2"

    /** Claude Code builds. 2.1.316 is the current pin; 2.1.294 (the 0.11.2 pin) and 2.1.283 stay accepted. */
    public val claudeCode: ImmutableSet<String> = persistentSetOf("2.1.283", "2.1.294", "2.1.316")

    /** Whether [version] (the number the daemon reported) is a tested release. */
    public fun paseoSupports(version: String?): Boolean = version != null && version in paseo

    /** Whether [version] is a tested Claude Code release. */
    public fun claudeCodeSupports(version: String?): Boolean = version != null && version in claudeCode
}

package io.github.eslamasabry.oc.engine.runtime.install

/**
 * Port of lib/state/phone_host.dart `InAppPhoneHost.runtime`: which OpenCode runtime the phone host is on. A saved
 * profile wins; a host that was installed before runtime selection existed is legacy (OpenCode 1); only a fresh host
 * adopts OpenCode 2.
 */
public object PhoneHostRuntime {
    /** A saved phone-server profile, as far as runtime choice reads it. */
    public data class SavedProfile(val requiresOpenCode2Migration: Boolean)

    /** The in-app host: the saved profile's runtime, else installed-before-selection = OpenCode 1, else the new default. */
    public fun inApp(profile: SavedProfile?, ubuntuInstalled: Boolean?): ManagedRuntime =
        when {
            profile != null -> ManagedRuntime.ofProfile(profile.requiresOpenCode2Migration)
            ubuntuInstalled == true -> ManagedRuntime.OpenCode1
            else -> ManagedRuntime.defaultForNewInstall
        }
}

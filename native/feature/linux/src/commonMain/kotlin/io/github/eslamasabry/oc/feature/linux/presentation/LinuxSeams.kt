package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.pace.Cadence
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobKind
import kotlin.time.Duration.Companion.seconds

/*
 * The seams the Linux pages need besides LinuxHost. Each is bound once in androidApp's LinuxWiring; feature code never
 * sees Android types.
 */

/**
 * The pages of the manager (and the This phone pages it hands off to). The entries map them to
 * routes.
 */
public enum class LinuxPage {
    Home,
    Ports,
    Storage,
    Packages,
    Backups,
    KeptServices,
    Running,
    ThisPhone,
}

public interface LinuxNavigator {
    public fun open(page: LinuxPage)

    public fun back()
}

public interface LinuxMigrationNavigator {
    public fun migrate(profileId: String)
}

/**
 * Opens a port's loopback preview in the browser (app-authored URL, through ExternalLinkPolicy in
 * the binding).
 */
public fun interface PortPreviewOpener {
    /** False when nothing could open it. */
    public fun open(url: String): Boolean
}

public fun interface LinuxAddressCopy {
    public suspend fun copy(address: String): io.github.eslamasabry.oc.core.common.Outcome<Unit>
}

/**
 * Phone setup / repair as the runtime offers it (plan 11 §5.2 setup engine). Unavailable until that
 * engine is bound.
 */
public interface LinuxLifecycle {
    /** True when installed, false when not, null when the phone could not say. */
    public suspend fun installed(): Boolean?

    /** Erases Linux; projects stay (`BuiltinLinux.uninstall(alsoDeleteProjects = false)`). */
    public suspend fun erase(): io.github.eslamasabry.oc.core.common.Outcome<Unit>

    /**
     * Starts phone setup with the first-setup selection. True when it runs (or already finished).
     */
    public suspend fun startSetup(): io.github.eslamasabry.oc.core.common.Outcome<Boolean>

    /** Repair: setup's check-and-fix pass over Linux, OpenCode and the added tools. */
    public suspend fun repair(): io.github.eslamasabry.oc.core.common.Outcome<Unit>
}

/**
 * The one device-wide reset marker `oc.linuxReset.pending` (Linux is one per app, so not
 * profile-scoped).
 */
public interface LinuxResetMarker {
    public suspend fun read(): String?

    public suspend fun write(value: String)

    public suspend fun clear()

    /** The old Reply speed reading goes with the erased Linux. */
    public suspend fun forgetReplySpeed()
}

/**
 * The notice text the long job's Android service shows (the binding resolves it from :ui:words).
 */
public interface LinuxJobNotices {
    public suspend fun notice(kind: LinuxJobKind): String
}

/** Every repeating job of :feature:linux (plan 13 §11.2). None runs while the app is hidden. */
public object LinuxTimers {
    /**
     * A running package job is read every 2 s while a Linux page shows it (Flutter
     * `LinuxJobController.interval`).
     */
    public val jobPoll: TimerSpec =
        TimerSpec(
            name = "linux.jobPoll",
            cadence = Cadence(foreground = 2.seconds, hiddenWatching = null, hiddenIdle = null),
            reason = "A package job's progress and its end stop showing.",
            rows = listOf("B-LINUX-091"),
        )

    public val ALL: List<TimerSpec> = listOf(jobPoll)
}

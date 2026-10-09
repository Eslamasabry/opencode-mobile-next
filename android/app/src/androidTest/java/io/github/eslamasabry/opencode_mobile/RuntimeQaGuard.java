package io.github.eslamasabry.opencode_mobile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Private acceptance guards, also exercised on the host without Android/Gradle. */
final class RuntimeQaGuard {
    interface Operation { void run() throws Throwable; }

    static void runWithCleanup(Operation work, Operation cleanup, Consumer<Throwable> reportCleanup)
            throws Throwable {
        Throwable primary = null;
        try {
            work.run();
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                cleanup.run();
            } catch (Throwable failure) {
                if (primary == null) throw failure;
                // A cleanup failure remains visible, but must not replace the scenario's cause.
                try { reportCleanup.accept(failure); } catch (Throwable ignored) { }
            }
        }
    }

    static final long STARTUP_SETTLEMENT_MILLIS = 15_000L;

    static void awaitStartupSettlement(LongSupplier clock, Operation authority,
            Operation quiescence, Operation pause) throws Throwable {
        long started = clock.getAsLong();
        Exception last = null;
        while (true) {
            // Owner/foreground revocation is never treated as transient workload.
            authority.run();
            long now = clock.getAsLong();
            if (started < 0 || now < started) {
                if (last != null) throw last;
                throw new IllegalStateException("startup_settlement_clock_invalid");
            }
            if (last != null && now - started >= STARTUP_SETTLEMENT_MILLIS) throw last;
            try {
                quiescence.run();
            } catch (Exception failure) {
                last = failure;
                now = clock.getAsLong();
                if (now < started || now - started >= STARTUP_SETTLEMENT_MILLIS) throw last;
                pause.run();
                continue;
            }
            authority.run();
            return;
        }
    }

    static boolean fixturePathSafe(File filesDir, File fixture, String name) throws IOException {
        File expected = new File(filesDir.getCanonicalFile(), name);
        return fixture.getAbsoluteFile().equals(new File(filesDir.getAbsoluteFile(), name))
                && Files.isRegularFile(fixture.toPath(), LinkOption.NOFOLLOW_LINKS)
                && fixture.getCanonicalFile().equals(expected);
    }

    // PhoneAgentHost._restoreIdle: 4 x 8s awaits + one shared 30s probe.
    // Connection phone-agent idle resume: 3 x 8s receipt checks around that call.
    // 86s of explicit budgets + 14s bridge/scheduling allowance. This is a QA
    // observer budget, not a claim that downstream feed/transport work is bounded.
    static final long IDLE_OBSERVATION_MILLIS = 100_000L;

    static boolean idleObservationPending(long started, long now) {
        return started >= 0 && now >= started && now - started < IDLE_OBSERVATION_MILLIS;
    }

    static boolean idleTokenPending(long expectedGeneration, long actualGeneration,
            boolean stopped, boolean helperStopped) {
        return expectedGeneration > 0 && actualGeneration == expectedGeneration
                && (stopped || helperStopped);
    }

    static boolean retainedHomeSafe(File rootfs, String helper) throws IOException {
        if (helper == null || !helper.matches("[A-Za-z0-9_-]{1,80}")) return false;
        String relative = "home/oc/.oc-profiles/" + helper;
        File home = new File(rootfs, relative);
        return Files.isDirectory(home.toPath(), LinkOption.NOFOLLOW_LINKS)
                && home.getCanonicalFile().equals(new File(rootfs.getCanonicalFile(), relative));
    }

    static boolean fixtureBindingValid(String owner, String nativeOwner, String savedOwner,
            String helper, String mappedHelper) {
        return owner != null && owner.matches("[A-Za-z0-9_-]{1,80}")
                && owner.equals(nativeOwner) && owner.equals(savedOwner)
                && helper != null && helper.matches("[A-Za-z0-9_-]{1,80}")
                && helper.equals(mappedHelper);
    }

    static boolean helperStartAllowed(long expectedGeneration, long actualGeneration,
            boolean serverRunning, boolean stopped, boolean helperStopped,
            int otherLiveProcesses, boolean helperPresent) {
        return expectedGeneration >= 0 && actualGeneration == expectedGeneration
                && serverRunning && !stopped && !helperPresent && otherLiveProcesses == 0
                && helperStopped == (expectedGeneration > 0);
    }

    private RuntimeQaGuard() { }
}

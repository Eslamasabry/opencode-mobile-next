package io.github.eslamasabry.opencode_mobile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.function.Consumer;

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

    static boolean fixturePathSafe(File filesDir, File fixture, String name) throws IOException {
        File expected = new File(filesDir.getCanonicalFile(), name);
        return fixture.getAbsoluteFile().equals(new File(filesDir.getAbsoluteFile(), name))
                && Files.isRegularFile(fixture.toPath(), LinkOption.NOFOLLOW_LINKS)
                && fixture.getCanonicalFile().equals(expected);
    }

    private RuntimeQaGuard() { }
}

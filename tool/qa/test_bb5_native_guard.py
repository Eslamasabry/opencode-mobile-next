"""Execute the actual private Android-test Java guard on a small host JVM."""
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
GUARD = ROOT / 'android/app/src/androidTest/java/io/github/eslamasabry/opencode_mobile/RuntimeQaGuard.java'
HARNESS = '''package io.github.eslamasabry.opencode_mobile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
public class GuardHost {
  public static void main(String[] args) throws Throwable {
    Path root = Path.of(args[1]);
    if (args[0].equals("path")) {
      Path files = Files.createDirectory(root.resolve("files"));
      Path alias = Files.createSymbolicLink(root.resolve("app-alias"), files);
      Path fixture = Files.writeString(files.resolve("bb5-runtime-qa.json"), "{}");
      check(RuntimeQaGuard.fixturePathSafe(files.toFile(), fixture.toFile(), fixture.getFileName().toString()), "direct path");
      check(RuntimeQaGuard.fixturePathSafe(alias.toFile(), alias.resolve(fixture.getFileName()).toFile(), fixture.getFileName().toString()), "trusted app alias");
      Files.delete(fixture);
      Path outside = Files.writeString(root.resolve("other.json"), "{}");
      Files.createSymbolicLink(fixture, outside);
      check(!RuntimeQaGuard.fixturePathSafe(files.toFile(), fixture.toFile(), fixture.getFileName().toString()), "leaf symlink refused");
      check(!RuntimeQaGuard.fixturePathSafe(files.toFile(), outside.toFile(), fixture.getFileName().toString()), "outside refused");
      Files.delete(fixture); Files.createDirectory(fixture);
      check(!RuntimeQaGuard.fixturePathSafe(files.toFile(), fixture.toFile(), fixture.getFileName().toString()), "directory refused");
    } else {
      Exception owner = new Exception("owner_changed");
      Exception cleanup = new Exception("cleanup_invalid");
      AtomicBoolean timer = new AtomicBoolean(), cleaned = new AtomicBoolean(), reported = new AtomicBoolean();
      try {
        RuntimeQaGuard.runWithCleanup(() -> { fail(owner); timer.set(true); },
            () -> { cleaned.set(true); throw cleanup; }, failure -> { check(failure == cleanup, "cleanup diagnostic"); reported.set(true); });
        throw new AssertionError("primary missing");
      } catch (Throwable failure) { check(failure == owner, "primary replaced by cleanup"); }
      check(cleaned.get() && reported.get() && !timer.get(), "startup refusal before timer");
      try {
        RuntimeQaGuard.runWithCleanup(() -> {}, () -> { throw cleanup; }, failure -> { throw new AssertionError("unexpected secondary"); });
        throw new AssertionError("cleanup swallowed");
      } catch (Throwable failure) { check(failure == cleanup, "successful scenario cleanup failure"); }
      try {
        RuntimeQaGuard.runWithCleanup(() -> { throw owner; }, () -> { throw cleanup; }, failure -> { throw new Error("report unavailable"); });
      } catch (Throwable failure) { check(failure == owner, "report failure replaced primary"); }
    }
  }
  static void fail(Exception failure) throws Exception { throw failure; }
  static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
}'''


class NativeGuardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder = tempfile.TemporaryDirectory(prefix='bb5-guard-')
        cls.path = Path(cls.folder.name)
        source = cls.path / 'GuardHost.java'
        source.write_text(HARNESS)
        result = subprocess.run(['javac', '-J-Xmx96m', '-d', str(cls.path), str(GUARD), str(source)],
                                capture_output=True, text=True, timeout=20)
        if result.returncode:
            cls.folder.cleanup()
            raise AssertionError('host guard compilation failed: ' + result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.folder.cleanup()

    def run_case(self, case):
        with tempfile.TemporaryDirectory(prefix='bb5-case-') as folder:
            result = subprocess.run(['java', '-Xmx64m', '-cp', str(self.path),
                                     'io.github.eslamasabry.opencode_mobile.GuardHost', case, folder],
                                    capture_output=True, text=True, timeout=5)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_trusted_android_app_alias_works_without_admitting_fixture_symlinks(self):
        self.run_case('path')

    def test_startup_failure_is_preserved_even_when_cleanup_and_reporting_fail(self):
        self.run_case('failure')


if __name__ == '__main__':
    unittest.main()

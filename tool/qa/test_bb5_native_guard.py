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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
      Path home = Files.createDirectories(files.resolve("home/oc/.oc-profiles/canonical"));
      Path secret = Files.writeString(home.resolve("existing-data"), "retained");
      check(RuntimeQaGuard.retainedHomeSafe(files.toFile(), "canonical"), "existing helper home");
      check(RuntimeQaGuard.retainedHomeSafe(alias.toFile(), "canonical"), "trusted root alias");
      check(!RuntimeQaGuard.retainedHomeSafe(files.toFile(), "missing"), "missing home not created");
      check(Files.readString(secret).equals("retained"), "home data unchanged");
      Path linked = home.resolveSibling("linked"); Files.createSymbolicLink(linked, home);
      check(!RuntimeQaGuard.retainedHomeSafe(files.toFile(), "linked"), "helper home symlink refused");
      check(!RuntimeQaGuard.retainedHomeSafe(files.toFile(), "../canonical"), "unsafe home id refused");
      Files.delete(fixture); Files.createDirectory(fixture);
      check(!RuntimeQaGuard.fixturePathSafe(files.toFile(), fixture.toFile(), fixture.getFileName().toString()), "directory refused");
    } else if (args[0].equals("settle-transient")) {
      AtomicLong now = new AtomicLong();
      AtomicInteger checks = new AtomicInteger(), authority = new AtomicInteger();
      Exception busy = new Exception("other_process_refused");
      RuntimeQaGuard.awaitStartupSettlement(now::get, () -> authority.incrementAndGet(),
          () -> { if (checks.incrementAndGet() < 3) throw busy; }, () -> now.addAndGet(100));
      check(checks.get() == 3 && now.get() == 200 && authority.get() >= 4, "retry transient gates with authority checks");
    } else if (args[0].equals("settle-persistent")) {
      AtomicLong now = new AtomicLong();
      AtomicInteger checks = new AtomicInteger();
      Exception busy = new Exception("active_or_unknown_work_refused");
      try {
        RuntimeQaGuard.awaitStartupSettlement(now::get, () -> {},
            () -> { checks.incrementAndGet(); throw busy; }, () -> now.addAndGet(100));
        throw new AssertionError("persistent busy admitted");
      } catch (Throwable failure) { check(failure == busy, "last fixed refusal retained"); }
      check(now.get() == 15000 && checks.get() == 150, "persistent work bounded at fifteen seconds");
    } else if (args[0].equals("settle-revoked")) {
      for (String reason : new String[]{"owner_changed", "foreground_lost"}) {
        AtomicLong now = new AtomicLong();
        AtomicInteger checks = new AtomicInteger();
        Exception revoked = new Exception(reason), busy = new Exception("other_process_refused");
        try {
          RuntimeQaGuard.awaitStartupSettlement(now::get,
              () -> { if (now.get() > 0) throw revoked; },
              () -> { checks.incrementAndGet(); throw busy; }, () -> now.addAndGet(100));
          throw new AssertionError("revoked authority admitted");
        } catch (Throwable failure) { check(failure == revoked, "authority revocation never retried"); }
        check(checks.get() == 1 && now.get() == 100, "revocation stops retrying before another gate check");
      }
      AtomicInteger checks = new AtomicInteger();
      Exception revoked = new Exception("owner_changed_after_quiescence");
      AtomicInteger authority = new AtomicInteger();
      try {
        RuntimeQaGuard.awaitStartupSettlement(() -> 0L,
            () -> { if (authority.incrementAndGet() == 2) throw revoked; },
            () -> checks.incrementAndGet(), () -> { throw new AssertionError("unexpected retry"); });
        throw new AssertionError("revocation after quiescence admitted");
      } catch (Throwable failure) { check(failure == revoked, "final authority check retained"); }
      check(checks.get() == 1, "single quiescent observation");
    } else if (args[0].equals("observation")) {
      check(RuntimeQaGuard.idleObservationPending(1000, 1000), "observe from start");
      check(RuntimeQaGuard.idleObservationPending(1000, 29000), "old 28s deadline remains pending");
      check(RuntimeQaGuard.idleObservationPending(1000, 100999), "observe until last millisecond");
      check(!RuntimeQaGuard.idleObservationPending(1000, 101000), "bounded at deadline");
      check(!RuntimeQaGuard.idleObservationPending(1000, 999), "backwards clock refused");
      check(!RuntimeQaGuard.idleObservationPending(-1, 10), "invalid start refused");
      check(RuntimeQaGuard.IDLE_OBSERVATION_MILLIS == 100000L, "four inner 8s awaits plus 30s probe, three outer 8s awaits and scheduling allowance");
      check(RuntimeQaGuard.idleTokenPending(7, 7, true, true), "stopped same token pending");
      check(RuntimeQaGuard.idleTokenPending(7, 7, false, true), "server resumed helper still owed");
      check(!RuntimeQaGuard.idleTokenPending(7, 8, false, true), "other token not pending proof");
      check(!RuntimeQaGuard.idleTokenPending(7, 7, false, false), "completed token not pending");
      check(!RuntimeQaGuard.idleTokenPending(0, 0, true, true), "invalid token not pending proof");
    } else if (args[0].equals("binding")) {
      check(RuntimeQaGuard.fixtureBindingValid("saved", "saved", "saved", "canonical", "canonical"), "saved aliases admitted");
      check(!RuntimeQaGuard.fixtureBindingValid("qa_bb5_idle", "saved", "saved", "canonical", "canonical"), "synthetic rebind refused");
      check(!RuntimeQaGuard.fixtureBindingValid("saved", "changed", "saved", "canonical", "canonical"), "native owner drift refused");
      check(!RuntimeQaGuard.fixtureBindingValid("saved", "saved", "changed", "canonical", "canonical"), "Dart owner drift refused");
      check(!RuntimeQaGuard.fixtureBindingValid("saved", "saved", "saved", "canonical", "changed"), "helper mapping drift refused");
      check(!RuntimeQaGuard.fixtureBindingValid("saved", "saved", "saved", "../home", "../home"), "unsafe helper refused");
      check(!RuntimeQaGuard.fixtureBindingValid(null, null, null, "canonical", "canonical"), "missing identity refused");
    } else if (args[0].equals("helper")) {
      check(RuntimeQaGuard.helperStartAllowed(0, 0, true, false, false, 0, false), "initial server-only helper");
      check(RuntimeQaGuard.helperStartAllowed(8, 8, true, false, true, 0, false), "same token fallback");
      check(!RuntimeQaGuard.helperStartAllowed(8, 9, true, false, true, 0, false), "stale token refused");
      check(!RuntimeQaGuard.helperStartAllowed(8, 0, true, false, false, 0, false), "completed token refused");
      check(!RuntimeQaGuard.helperStartAllowed(8, 8, true, false, true, 0, true), "tracked helper refused");
      check(!RuntimeQaGuard.helperStartAllowed(8, 8, true, false, true, 1, false), "in-flight helper refused");
      check(!RuntimeQaGuard.helperStartAllowed(8, 8, false, true, true, 0, false), "stopped server refused");
      check(!RuntimeQaGuard.helperStartAllowed(0, 8, true, false, true, 0, false), "ordinary start cannot bypass idle token");
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

    def test_startup_settlement_retries_transient_quiescence_only(self):
        self.run_case('settle-transient')

    def test_startup_settlement_refuses_persistent_busy_after_fifteen_seconds(self):
        self.run_case('settle-persistent')

    def test_startup_settlement_stops_immediately_on_owner_or_foreground_revocation(self):
        self.run_case('settle-revoked')

    def test_observer_covers_real_callback_budget_and_reports_only_current_pending_token(self):
        self.run_case('observation')

    def test_fixture_retains_saved_owner_and_canonical_helper_mapping(self):
        self.run_case('binding')

    def test_fallback_refuses_stale_tokens_and_existing_or_in_flight_helpers(self):
        self.run_case('helper')

    def test_runner_cannot_delete_or_rebind_personal_recovery_and_home(self):
        source = (ROOT / 'android/app/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinIdleAcceptance.kt').read_text()
        for forbidden in ['stageServerRecovery(', 'bindServerRecovery(', 'deleteServerRecovery(',
                          'deleteAgentHome(', 'QA_POLICY', 'putString(marker', 'putString(policy']:
            with self.subTest(forbidden=forbidden):
                self.assertFalse(forbidden in source, 'unsafe fixture operation: ' + forbidden)
        self.assertIn('RuntimeQaGuard.fixtureBindingValid(', source)
        self.assertIn('RuntimeQaGuard.helperStartAllowed(', source)
        self.assertIn('synchronized(linux) { requireQuiescentServerOnly(linux) }', source)
        self.assertLess(source.index('RuntimeQaGuard.awaitStartupSettlement('), source.index('save(JSONObject()'))

    def test_startup_failure_is_preserved_even_when_cleanup_and_reporting_fail(self):
        self.run_case('failure')


if __name__ == '__main__':
    unittest.main()

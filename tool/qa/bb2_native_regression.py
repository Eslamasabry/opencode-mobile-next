#!/usr/bin/env python3
"""Run BB2 exact native red probes, restore source, then the focused green gate.

Invoke through tool/qa/machine_lock.sh build --; this script never acquires a
second build lock and never kills an unrelated process.
"""
from pathlib import Path
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "io.github.eslamasabry.opencode_mobile"
GRADLE = "/home/eslam/.gradle/wrapper/dists/gradle-9.5.0-all/aca6g93cdtcf0oapcfka748qh/gradle-9.5.0/bin/gradle"
OUT = ROOT / "docs/qa/BB2-2026-10-07"
SOURCES = ROOT / "android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile"
RED = {
    f"{PACKAGE}.RestartBackoffTest.repeatedShortExitsDoubleDelayUntilTheSixtySecondCap",
    f"{PACKAGE}.RestartBackoffTest.thirtySecondsOfUptimeResetsDelayButOneMillisecondLessDoesNot",
    f"{PACKAGE}.NativeRecoveryBudgetTest.threeReservationsAreTheLifetimeLimitEvenAfterReceiptsFinish",
    f"{PACKAGE}.NativeRecoveryBudgetTest.migrationRequiresAnExplicitVersionTwoNativeAuthorityMarker",
    f"{PACKAGE}.NativeRecoveryBudgetTest.admissionRequiresMatchingGenerationAndEveryCurrentPermission",
    f"{PACKAGE}.NativeRecoveryBudgetTest.manualResetRejectsUnboundStaleStoppedPausedOrNativeOwnedProof",
    f"{PACKAGE}.NativeRecoveryBudgetTest.stoppedServerKeepsForegroundOnlyForAnAdmittedScheduledUnspentRetry",
    f"{PACKAGE}.NativeRecoveryBudgetTest.nativeLaunchDoesNotNotifyAnAlreadyEmptySetAfterFinalReservation",
    f"{PACKAGE}.NativeRecoveryBudgetTest.repeatedManualCyclesRetainEveryConfirmedReceiptAndRefuseOverflow",
}


def replace_once(value, old, new):
    assert value.count(old) == 1, old
    return value.replace(old, new)


def cleanup():
    owned = ROOT / "build/app/intermediates"
    assert owned.resolve() == Path("/home/eslam/Storage/Code/oc_app-sol-bb/build/app/intermediates")
    if owned.exists():
        shutil.rmtree(owned)


def invoke(label, tests):
    args = [GRADLE, "-p", "android", "--no-daemon", "--max-workers=1",
            "-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g",
            "-Pkotlin.compiler.execution.strategy=in-process", ":app:testReleaseUnitTest"]
    for test in sorted(tests):
        args += ["--tests", test]
    env = os.environ.copy()
    env["JAVA_HOME"] = "/home/eslam/Storage/tmp/codex-audit3-temurin17/jdk-17.0.20.1+1"
    with (OUT / f"native-{label}.txt").open("w") as output:
        process = subprocess.Popen(args, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
        print(f"{label} owned_gradle_client_pid={process.pid}", flush=True)
        code = process.wait()
    results = ROOT / "build/app/test-results/testReleaseUnitTest"
    cases = {}
    if results.exists():
        for path in results.glob("TEST-*.xml"):
            for case in ET.parse(path).getroot().iter("testcase"):
                name = f"{case.attrib['classname']}.{case.attrib['name']}"
                cases[name] = case.find("failure") is not None
    print(f"{label} exit={code} cases={len(cases)} failures={sum(cases.values())}", flush=True)
    cleanup()
    return code, cases


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    paths = [SOURCES / "RestartBackoff.kt", SOURCES / "NativeRecoveryBudget.kt"]
    originals = {path: path.read_text() for path in paths}
    try:
        backoff = replace_once(originals[paths[0]], "uptimeMs >= 30000L", "uptimeMs >= 30001L")
        backoff = replace_once(backoff, "(delay * 2).coerceAtMost(60000L)", "(delay * 2)")
        paths[0].write_text(backoff)
        budget = originals[paths[1]]
        changes = [
            ("attempts < 3 && !pending", "attempts < 4 && !pending"),
            ('value?.get("version") == 2 && value["nativeAuthority"] == true', "true"),
            ("enabled && wanted && !userStopped &&", "enabled && wanted &&"),
            ("boundProfile == requestedProfile &&", "true &&"),
            ("running || (scheduled && admitted && attempts in 0..2)", "running"),
            ("!nativeOwned || tracked", "true"),
            ("existing.size < 16", "existing.size < 17"),
        ]
        for old, new in changes:
            budget = replace_once(budget, old, new)
        paths[1].write_text(budget)
        code, cases = invoke("red", RED)
        if code == 0 or not all(cases.get(test) is True for test in RED):
            raise RuntimeError("native_red_probes_not_all_observed")
    finally:
        for path, original in originals.items():
            path.write_text(original)
    code, cases = invoke("green", {
        f"{PACKAGE}.RestartBackoffTest", f"{PACKAGE}.NativeRecoveryBudgetTest",
        f"{PACKAGE}.ServiceDiagnosticsTest",
    })
    if code != 0 or not cases or any(cases.values()):
        raise RuntimeError("native_green_gate_failed")
    print("PASS BB2_native_red_and_green", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

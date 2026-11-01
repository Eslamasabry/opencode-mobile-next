"""FX-LINUXDL self-tests: full GET with SHA-256 match, Range resume, throttle, cut, corrupt, offline."""

from __future__ import annotations

import hashlib
import http.client
import json
import time

from tests.fxtest import FixtureCase, request

ROOTFS = "/cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-amd64.tar.gz"


class FxLinuxDlTest(FixtureCase):
    FIXTURE = "FX-LINUXDL"
    EXTRA_ENV = {"FX_LINUXDL_SYNTH": "1"}

    @classmethod
    def setUpClass(cls) -> None:
        super().setUpClass()
        manifest = json.loads((cls.run_dir / "FX-LINUXDL" / "mirror" / "manifest.json").read_text())
        cls.files = {"/" + f["path"]: f for f in manifest["files"]}

    def setUp(self) -> None:
        self.assertEqual(self.ctl("reset").status, 200)

    def get(self, path: str, headers: dict[str, str] | None = None, timeout: float = 15):
        return request("GET", self.port("http"), path, headers=headers, timeout=timeout)

    def test_ready_reports_manifest_and_pins(self) -> None:
        self.assertTrue(self.ready["synthetic"])
        self.assertEqual(self.ready["manifest"]["mismatch"], [])
        self.assertEqual(self.ready["manifest"]["missing"], [])
        self.assertGreaterEqual(len(self.ready["manifest"]["ok"]), 4)
        self.assertEqual(self.ready["base_url"]["emulator"], f"http://10.0.2.2:{self.port('http')}")
        pins = {p["url"]: p for p in self.ready["pins"]}
        real = "https://cdimage.ubuntu.com" + ROOTFS.removeprefix("/cdimage.ubuntu.com")
        self.assertEqual(pins[real]["sha256"], "e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86")
        self.assertFalse(pins[real]["mirrored"])  # synthetic bytes, not the real pinned file
        self.assertIn(ROOTFS, self.files)

    def test_full_get_matches_sha_with_length_and_etag(self) -> None:
        r = self.get(ROOTFS)
        self.assertEqual(r.status, 200)
        self.assertEqual(int(r.headers["content-length"]), len(r.body))
        self.assertEqual(hashlib.sha256(r.body).hexdigest(), self.files[ROOTFS]["sha256"])
        self.assertEqual(r.headers["accept-ranges"], "bytes")
        self.assertTrue(r.headers["etag"].startswith('"'))
        head = request("HEAD", self.port("http"), ROOTFS)
        self.assertEqual(head.headers["content-length"], r.headers["content-length"])
        self.assertEqual(self.get("/nope/missing.tar.gz").status, 404)
        self.assertEqual(self.get("/../../etc/passwd").status, 404)

    def test_range_resume(self) -> None:
        full = self.get(ROOTFS).body
        first = full[:1000]
        r = self.get(ROOTFS, {"Range": "bytes=1000-"})
        self.assertEqual(r.status, 206)
        self.assertEqual(r.headers["content-range"], f"bytes 1000-{len(full) - 1}/{len(full)}")
        self.assertEqual(hashlib.sha256(first + r.body).hexdigest(), self.files[ROOTFS]["sha256"])
        r = self.get(ROOTFS, {"Range": "bytes=10-19"})
        self.assertEqual((r.status, r.body), (206, full[10:20]))
        r = self.get(ROOTFS, {"Range": "bytes=-5"})
        self.assertEqual(r.body, full[-5:])
        self.assertEqual(self.get(ROOTFS, {"Range": f"bytes={len(full)}-"}).status, 416)
        # If-Range with a stale validator gets the whole file.
        r = self.get(ROOTFS, {"Range": "bytes=10-19", "If-Range": '"stale"'})
        self.assertEqual((r.status, len(r.body)), (200, len(full)))

    def test_throttle_measurably_slows(self) -> None:
        size = self.files[ROOTFS]["size"]
        started = time.monotonic()
        self.get(ROOTFS)
        fast = time.monotonic() - started
        rate = size  # the whole file should take about one second
        self.ctl("throttle", {"bytes_per_sec": rate})
        started = time.monotonic()
        r = self.get(ROOTFS)
        slow = time.monotonic() - started
        self.assertEqual(len(r.body), size)
        self.assertGreater(slow, 0.7)
        self.assertGreater(slow, fast * 2)
        self.ctl("throttle", {"bytes_per_sec": 0})
        self.assertEqual(self.ctl("state", method="GET").json()["throttle_bytes_per_sec"], 0)

    def test_cut_closes_mid_body(self) -> None:
        self.ctl("cut", {"path": "/cdimage.ubuntu.com/", "after_bytes": 4096, "count": 1})
        conn = http.client.HTTPConnection("127.0.0.1", self.port("http"), timeout=10)
        try:
            conn.request("GET", ROOTFS)
            resp = conn.getresponse()
            self.assertEqual(resp.status, 200)
            with self.assertRaises(http.client.IncompleteRead) as err:
                resp.read()
            self.assertEqual(len(err.exception.partial), 4096)
        finally:
            conn.close()
        # count = 1: the next download completes and resumes cleanly.
        r = self.get(ROOTFS)
        self.assertEqual(hashlib.sha256(r.body).hexdigest(), self.files[ROOTFS]["sha256"])
        cut = [e for e in self.requests_log() if e.get("cut")]
        self.assertTrue(cut)

    def test_corrupt_changes_the_sha(self) -> None:
        self.ctl("corrupt", {"path": ROOTFS, "on": True})
        r = self.get(ROOTFS)
        self.assertEqual(len(r.body), self.files[ROOTFS]["size"])
        self.assertNotEqual(hashlib.sha256(r.body).hexdigest(), self.files[ROOTFS]["sha256"])
        other = next(p for p in self.files if p != ROOTFS)
        self.assertEqual(hashlib.sha256(self.get(other).body).hexdigest(), self.files[other]["sha256"])
        self.ctl("corrupt", {"path": ROOTFS, "on": False})
        r = self.get(ROOTFS)
        self.assertEqual(hashlib.sha256(r.body).hexdigest(), self.files[ROOTFS]["sha256"])

    def test_offline_refuses_with_a_hard_close(self) -> None:
        self.ctl("offline", {"on": True})
        with self.assertRaises((ConnectionError, http.client.HTTPException, OSError)):
            self.get(ROOTFS, timeout=5)
        self.ctl("offline", {"on": False})
        self.assertEqual(self.get(ROOTFS).status, 200)

    def test_standard_fault_refuse(self) -> None:
        self.ctl("fault", {"path": "/registry.npmjs.org/", "action": "refuse", "status": 503, "count": 1})
        npm = next(p for p in self.files if p.startswith("/registry.npmjs.org/"))
        self.assertEqual(self.get(npm).status, 503)
        self.assertEqual(self.get(npm).status, 200)

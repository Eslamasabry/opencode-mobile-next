"""FX-GIT self-tests: clone over git:// and http://, push, reject-push, auth for HTTP push."""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

from tests.fxtest import FixtureCase


class FxGitTest(FixtureCase):
    FIXTURE = "FX-GIT"

    @classmethod
    def setUpClass(cls) -> None:
        super().setUpClass()
        cls.work = Path(tempfile.mkdtemp(prefix="fx-git-work-"))
        home = cls.work / "home"
        home.mkdir()
        cls.git_env = {
            "PATH": os.environ.get("PATH", "/usr/bin:/bin"),
            "HOME": str(home),
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_TERMINAL_PROMPT": "0",
            "GIT_ASKPASS": "/bin/false",
            "GIT_AUTHOR_NAME": "fx", "GIT_AUTHOR_EMAIL": "fx@example.invalid",
            "GIT_COMMITTER_NAME": "fx", "GIT_COMMITTER_EMAIL": "fx@example.invalid",
            "LC_ALL": "C",
        }

    @classmethod
    def tearDownClass(cls) -> None:
        shutil.rmtree(cls.work, ignore_errors=True)
        super().tearDownClass()

    def setUp(self) -> None:
        self.assertEqual(self.ctl("reset").status, 200)
        self.dir = Path(tempfile.mkdtemp(dir=self.work))

    def git(self, *args: str, cwd: Path | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(["git", *args], cwd=cwd, env=self.git_env, capture_output=True, text=True, timeout=30)
        if check and result.returncode != 0:
            self.fail(f"git {' '.join(args[:2])} failed: {result.stderr}")
        return result

    @property
    def password(self) -> str:
        return self.secrets["FX_GIT_PASSWORD"]

    def http_url(self, user: str | None = None, password: str | None = None) -> str:
        cred = f"{user}:{password}@" if user else ""
        return f"http://{cred}127.0.0.1:{self.port('http')}/app.git"

    def clone_and_commit(self, url: str, name: str = "c") -> Path:
        dest = self.dir / name
        self.git("clone", url, str(dest))
        self.assertEqual((dest / "README.md").exists(), True)
        (dest / "change.txt").write_text(f"change from {name}\n")
        self.git("add", "change.txt", cwd=dest)
        self.git("commit", "-m", f"change {name}", cwd=dest)
        return dest

    # -- tests -------------------------------------------------------------------

    def test_ready_lists_clone_urls(self) -> None:
        urls = self.ready["clone_urls"]
        self.assertEqual(urls["git"], f"git://127.0.0.1:{self.port('daemon')}/app.git")
        self.assertEqual(urls["http"], f"http://127.0.0.1:{self.port('http')}/app.git")
        self.assertIn("emulator", self.ready)
        self.assertEqual(self.ready["default_branch"], "main")

    def test_clone_over_git_and_push_accepted(self) -> None:
        dest = self.clone_and_commit(self.ready["clone_urls"]["git"])
        self.git("push", "origin", "HEAD:main", cwd=dest)
        refs = self.ctl("refs").json()
        head = self.git("rev-parse", "HEAD", cwd=dest).stdout.strip()
        self.assertEqual(refs["refs"]["refs/heads/main"], head)
        self.assertEqual(refs["head"], "refs/heads/main")
        self.assertEqual(refs["commits"][0], "change c")

    def test_clone_over_http_without_auth_and_push_with_any_user(self) -> None:
        dest = self.clone_and_commit(self.http_url())
        self.git("remote", "set-url", "origin", self.http_url("x-access-token", self.password), cwd=dest)
        self.git("push", "origin", "HEAD:main", cwd=dest)
        self.assertEqual(self.ctl("refs").json()["commits"][0], "change c")
        log = [e for e in self.requests_log() if e.get("service") == "git-receive-pack" and e.get("status") == 200]
        self.assertTrue(log)
        self.assertTrue(all(e["auth"] in ("basic", "none") for e in self.requests_log()))
        self.assert_no_secret_in_run_dir(self.password)

    def test_http_push_requires_the_password(self) -> None:
        dest = self.clone_and_commit(self.http_url())
        result = self.git("push", "origin", "HEAD:main", cwd=dest, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.git("remote", "set-url", "origin", self.http_url("fx", "wrong-password"), cwd=dest)
        result = self.git("push", "origin", "HEAD:main", cwd=dest, check=False)
        self.assertNotEqual(result.returncode, 0)
        statuses = [e["status"] for e in self.requests_log() if e.get("service") == "git-receive-pack"]
        self.assertIn(401, statuses)
        self.assertEqual(self.ctl("refs").json()["commits"], ["Initial commit"])

    def test_reject_push_fails_with_message_then_recovers(self) -> None:
        self.assertEqual(self.ctl("reject-push", {"on": True}).json()["reject_push"], True)
        dest = self.clone_and_commit(self.ready["clone_urls"]["git"])
        result = self.git("push", "origin", "HEAD:main", cwd=dest, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("push rejected by the fixture", result.stderr)
        self.git("remote", "set-url", "origin", self.http_url("fx", self.password), cwd=dest)
        result = self.git("push", "origin", "HEAD:main", cwd=dest, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("push rejected by the fixture", result.stderr)
        self.ctl("reject-push", {"on": False})
        self.git("push", "origin", "HEAD:main", cwd=dest)

    def test_reset_restores_the_seed(self) -> None:
        seed = self.ctl("refs").json()["refs"]["refs/heads/main"]
        dest = self.clone_and_commit(self.ready["clone_urls"]["git"])
        self.git("push", "origin", "HEAD:main", cwd=dest)
        self.assertNotEqual(self.ctl("refs").json()["refs"]["refs/heads/main"], seed)
        self.ctl("reject-push", {"on": True})
        self.ctl("reset")
        refs = self.ctl("refs").json()
        self.assertEqual(refs["refs"]["refs/heads/main"], seed)
        self.assertFalse(self.ctl("state", method="GET").json()["reject_push"])

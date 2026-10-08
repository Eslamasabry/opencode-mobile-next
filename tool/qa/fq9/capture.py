"""Durable pre-cleanup evidence assembled exclusively from closed projections."""

import json
import math
import os
from pathlib import Path

from .common import DriverFailure
from .terminal import FAIL_CODES as TERMINAL_CODES

FAIL_CODES = TERMINAL_CODES | frozenset(
    {
        "diagnostic_capture_failed",
        "diagnostic_clock_invalid",
        "evidence_unavailable",
        "evidence_already_exists",
    }
)


def durable_write(path, value):
    path = Path(path)
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("x") as stream:
            json.dump(value, stream, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    except FileExistsError:
        raise DriverFailure("evidence_already_exists") from None
    except OSError:
        raise DriverFailure("evidence_unavailable") from None


class BackgroundCapture:
    def __init__(self, ports, output):
        self.ports, self.output = ports, output
        self.began = None
        self.epoch = None
        self.last_bucket = None
        self.report = dict(
            schema=1, beforeResumeAndCleanup=True, terminal=[], logs=[], errors=[]
        )

    def begin(self):
        self.began = self.ports.monotonic()
        self.epoch = self.ports.diagnostic_epoch_ms()
        if type(self.epoch) is not int or not 0 <= self.epoch <= 2**53 - 1:
            raise DriverFailure("diagnostic_clock_invalid")

    def elapsed(self):
        value = 0 if self.began is None else self.ports.monotonic() - self.began
        if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
            raise DriverFailure("diagnostic_clock_invalid")
        return value

    def _error(self, error):
        code = (
            error.code
            if isinstance(error, DriverFailure) and error.code in FAIL_CODES
            else "diagnostic_capture_failed"
        )
        if code not in self.report["errors"]:
            self.report["errors"].append(code)

    def terminal(self):
        try:
            self.report["terminal"].append(
                dict(
                    elapsedSeconds=self.elapsed(),
                    snapshot=self.ports.terminal_snapshot(),
                )
            )
        except Exception as error:
            self._error(error)

    def logs(self):
        try:
            if self.epoch is None:
                raise DriverFailure("diagnostic_clock_invalid")
            self.report["logs"].append(
                dict(
                    elapsedSeconds=self.elapsed(),
                    snapshots=self.ports.diagnostic_logs(
                        self.epoch + 1140000, self.epoch + 1260000
                    ),
                )
            )
        except Exception as error:
            self._error(error)

    def observe(self):
        """Call after read-only polling sleeps; save the window before wraparound."""
        elapsed = self.elapsed()
        bucket = int(elapsed // 30)
        if 1140 <= elapsed <= 1290 and bucket != self.last_bucket:
            self.last_bucket = bucket
            self.logs()
            self.terminal()

    def finish(self):
        self.terminal()
        self.logs()
        self.report["windowStartMs"] = (
            None if self.epoch is None else self.epoch + 1140000
        )
        self.report["windowEndMs"] = (
            None if self.epoch is None else self.epoch + 1260000
        )
        self.report["capturedThroughElapsedSeconds"] = self.elapsed()
        self.report["windowReached"] = self.elapsed() >= 1140
        self.report["windowEndReached"] = self.elapsed() >= 1260
        try:
            durable_write(self.output, self.report)
        except Exception:
            return False
        return not self.report["errors"]

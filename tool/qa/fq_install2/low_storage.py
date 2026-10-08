"""Bounded app-install storage proof using ports supplied by the locked runner.

This module has no adb, filesystem cleanup, account or APK control. The runner
must verify the flagged artifact and hold the emulator lock before invoking it.
Native snapshots must be a closed projection: map only the exact native
SetupDiskSpace error to ``errorCode='low_storage'``; never return raw error text.
"""

import math
import time
from typing import Protocol


AGENT_IDS = frozenset({'codex', 'gemini', 'qwen', 'goose', 'omp-acp', 'fx'})
QA_MIN_FREE_BYTES = 8589934592
MIN_REAL_HEADROOM_BYTES = 800000000
TERMINAL_STATES = frozenset({'idle', 'done', 'cancelled', 'interrupted', 'failed'})


class LowStorageProofError(RuntimeError):
    """Closed failure codes; never reflect device output or account information."""


class DevicePort(Protocol):
    def available_storage_bytes(self) -> int: ...
    def target_inventory(self, agent_id: str) -> dict: ...
    def tap_install(self, agent_id: str) -> None: ...
    def storage_guidance_visible(self) -> bool: ...


class NativePort(Protocol):
    def setup_snapshot(self) -> dict: ...


def _require(condition, code):
    if not condition:
        raise LowStorageProofError(code)


def _absent(inventory):
    _require(type(inventory) is dict, 'invalid_inventory')
    _require(inventory.get('leftovers') is False and
             inventory.get('targetPids') == [] and
             type(inventory.get('allocatedBytes')) is int and
             inventory['allocatedBytes'] == 0 and
             inventory.get('staging') == [] and
             inventory.get('lockPresent') is False, 'target_not_absent')


def _snapshot(native):
    value = native.setup_snapshot()
    _require(type(value) is dict and type(value.get('components')) is dict and
             type(value.get('state')) is str, 'invalid_snapshot')
    identity = value.get('jobId')
    _require(identity is None or
             (type(identity) is str and 0 < len(identity) <= 128),
             'invalid_snapshot')
    return value


def _no_component_download(snapshot):
    # Include dependency progress even before the target component appears.
    # An omitted/invalid counter cannot certify that nothing downloaded.
    for component in snapshot['components'].values():
        _require(type(component) is dict, 'invalid_snapshot')
        _require('done' in component, 'invalid_download_counter')
        done = component['done']
        _require(done is None or (type(done) is int and done >= 0),
                 'invalid_download_counter')
        _require(done in (None, 0), 'download_started')


def run_low_storage(agent_id, device, native, *, qa_artifact_verified,
                    timeout_seconds=90, poll_seconds=.25,
                    clock=time.monotonic, sleep=time.sleep):
    """Tap the real Install action and qualify the injected native admission.

    ``declaredMinimumFreeBytes`` is the numeric projection of the matching
    ``params.agentInstallGuard`` declaration in this same fresh job snapshot.
    Native setup.json does not persist the dispatched component requirement;
    the host dispatch test, not this driver, verifies that exact spec value.
    ``done`` is the native component byte counter (null before any download).
    Every component, including dependencies, must be projected and checked.
    This function neither resets the guard nor removes payloads: runner finally
    blocks must restore the normal default-off APK even when this proof fails.
    """
    _require(type(agent_id) is str and agent_id in AGENT_IDS, 'unsupported_agent')
    _require(qa_artifact_verified is True, 'qa_artifact_not_verified')
    for value, maximum in ((timeout_seconds, 300), (poll_seconds, 5)):
        _require(type(value) in (int, float) and math.isfinite(value) and
                 0 < value <= maximum, 'invalid_deadline')

    # Real storage is measured before every installation action. High injected
    # requirements must cause the refusal on a healthy, unfilled device.
    available = device.available_storage_bytes()
    _require(type(available) is int and
             MIN_REAL_HEADROOM_BYTES <= available < QA_MIN_FREE_BYTES,
             'real_storage_not_suitable')
    _absent(device.target_inventory(agent_id))
    baseline = _snapshot(native)
    _require(baseline['state'] in TERMINAL_STATES, 'setup_not_terminal')

    started = clock()
    deadline = started + timeout_seconds
    device.tap_install(agent_id)
    target = 'agent-' + agent_id
    while clock() < deadline:
        snapshot = _snapshot(native)
        if not snapshot['jobId'] or snapshot['jobId'] == baseline['jobId']:
            sleep(poll_seconds)
            continue
        _no_component_download(snapshot)
        component = snapshot['components'].get(target)
        if component is None:
            _require(snapshot['state'] not in TERMINAL_STATES,
                     'fresh_job_target_missing')
            sleep(poll_seconds)
            continue
        _require(type(component) is dict, 'invalid_snapshot')
        required = component.get('declaredMinimumFreeBytes')
        _require(type(required) is int and required == QA_MIN_FREE_BYTES,
                 'qa_requirement_declaration_not_observed')
        if snapshot['state'] in TERMINAL_STATES:
            _require(snapshot['state'] == 'failed' and
                     component.get('state') == 'failed', 'guard_did_not_refuse')
            _require(component.get('errorCode') == 'low_storage',
                     'native_storage_code_not_observed')
            _absent(device.target_inventory(agent_id))
            # Native status can lead the app's next setup poll. Give that
            # presentation a short grace period inside the overall deadline.
            guidance_deadline = min(deadline, clock() + 2)
            while True:
                guidance = device.storage_guidance_visible()
                _require(type(guidance) is bool, 'invalid_guidance_projection')
                if guidance or clock() >= guidance_deadline:
                    break
                sleep(min(poll_seconds, guidance_deadline - clock()))
            elapsed = clock() - started
            _require(type(elapsed) in (int, float) and math.isfinite(elapsed) and
                     0 <= elapsed <= timeout_seconds, 'invalid_deadline')
            return {
                'state': 'pass' if guidance else 'partial',
                'code': 'verified' if guidance else 'storage_guidance_missing',
                'facts': {
                    'asserted': guidance,
                    'guardRefused': True,
                    'noDownload': True,
                    'freshJob': True,
                    'targetAbsent': True,
                    'visibleStorageWayForward': guidance,
                    'availableBytes': available,
                    'declaredRequiredBytes': required,
                    'guardRequirementDeclared': True,
                    'elapsedSeconds': elapsed,
                },
            }
        sleep(poll_seconds)
    raise LowStorageProofError('storage_guard_timeout')

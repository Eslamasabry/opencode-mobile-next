"""App-side payload removal proof; caller supplies the locked app/UI ports.

There is deliberately no fallback to shell deletion. Existing accounts, Claude,
conversations, shared helpers and qualification metadata are never touched here.
"""
import math
import time

IDS = frozenset({'codex', 'gemini', 'qwen', 'goose', 'omp-acp', 'fx'})


def run_uninstall(agent_id, name, device, *, timeout_seconds=60,
                  clock=time.monotonic, sleep=time.sleep):
    if agent_id not in IDS or not isinstance(name, str) or not name:
        raise ValueError('unsupported_agent')
    if type(timeout_seconds) not in (int, float) or not math.isfinite(timeout_seconds) or not 0 < timeout_seconds <= 180:
        raise ValueError('invalid_deadline')
    available = device.available_storage_bytes()  # Storage first, even removal.
    if type(available) is not int or available < 800000000:
        raise RuntimeError('insufficient_real_storage')
    before = device.target_inventory(agent_id)
    if before.get('targetPids') != []:
        raise RuntimeError('target_in_use')
    allocated = before.get('allocatedBytes')
    if type(allocated) is not int or allocated <= 0 or before.get('pinMatches') is not True or before.get('linkMatches') is not True:
        raise RuntimeError('installed_target_not_verified')
    if device.app_remove(agent_id, name) is not True:
        return {'state': 'partial', 'code': 'app_removal_action_missing',
                'facts': {'asserted': False, 'removedViaApp': False}}
    began = clock()
    deadline = began + timeout_seconds
    while clock() < deadline:
        after = device.target_inventory(agent_id)
        if (after.get('leftovers') is False and after.get('targetPids') == [] and
            after.get('allocatedBytes') == 0 and after.get('staging') == [] and
            after.get('lockPresent') is False):
            visible = device.target_not_installed_visible(agent_id)
            return {
                'state': 'pass' if visible else 'partial',
                'code': 'verified' if visible else 'removal_row_not_refreshed',
                'facts': {'asserted': visible, 'removedViaApp': True,
                          'leftoversRemoved': True, 'noOrphans': True,
                          'bytesFreed': allocated,
                          'freeSpaceDeltaBytes': device.available_storage_bytes() - available,
                          'notInstalledRow': visible,
                          'elapsedSeconds': round(clock() - began, 3)},
            }
        sleep(.25)
    return {'state': 'fail', 'code': 'app_removal_timeout',
            'facts': {'asserted': False, 'removedViaApp': True,
                      'leftoversRemoved': False, 'noOrphans': False}}

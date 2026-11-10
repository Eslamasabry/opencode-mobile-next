"""Offline-prepared BA10 proof orchestration; no ADB, subprocess, or device CLI.

The real-device adapter must hold the exact shared flock in locked_session(),
bound every call, verify APK hash/signature/source receipt at restore time, and
use the real app Install action in install_through_app(). No shell deletion,
sign-out, account setup, process killing, or private removal hook is permitted.
Version/auth/inventory/retention are read-only closed projections. Only the
public Remove <name> -> confirmation -> Remove path mutates the target.
"""
import math
import re
import time

SERIAL = 'emulator-5554'
LOCK = '/home/eslam/Storage/tmp/oc-emulator.lock'
SIGNER = '1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C'
TARGETS = {
    'codex': ('Codex', '0.160.0'), 'gemini': ('Gemini CLI', '0.62.0'),
    'qwen': ('Qwen Code', '0.24.7'), 'goose': ('Goose', '1.53.0'),
    'omp-acp': ('Oh My Pi', '18.5.1'), 'fx': ('fx', '0.0.12'),
}
COPY = ('This removes the installed agent. Your accounts and conversations stay. '
        'You can install it again.')
RETENTION_FIELDS = {
    'claudeReady', 'claudeSignedIn', 'claudeChatCount', 'claudeChatIdsDigest',
    'phoneGateDigest', 'sharedNodePresent', 'sharedPaseoPresent',
    'targetAccountHomePresent',
}


def require(condition, code):
    if not condition:
        raise RuntimeError(code)


def validate_receipt(receipt):
    require(type(receipt) is dict and set(receipt) == {
        'apk', 'build', 'sha256', 'sourceRevision', 'dartDefines'}, 'invalid_normal_receipt')
    require(type(receipt['build']) is int and receipt['build'] == 2197,
            'normal_build_not_2197')
    require(type(receipt['apk']) is str and receipt['apk'].startswith('/'), 'invalid_apk_path')
    for key, width in [('sha256', 64), ('sourceRevision', 40)]:
        require(type(receipt[key]) is str and re.fullmatch('[0-9a-f]{'+str(width)+'}', receipt[key]),
                'invalid_normal_identity')
    defines = receipt['dartDefines']
    require(type(defines) is dict and not set(defines)-{'OC_QA_AGENT_INSTALL_MIN_FREE_BYTES'} and
            type(defines.get('OC_QA_AGENT_INSTALL_MIN_FREE_BYTES', 0)) is int and
            defines.get('OC_QA_AGENT_INSTALL_MIN_FREE_BYTES', 0) == 0,
            'normal_guard_not_off')


def retention(ports):
    value = ports.retention_projection()
    require(type(value) is dict and set(value) == RETENTION_FIELDS, 'invalid_retention_projection')
    require(all(type(value[key]) is bool for key in RETENTION_FIELDS-{
        'claudeChatCount', 'claudeChatIdsDigest', 'phoneGateDigest'}), 'invalid_retention_projection')
    require(value['claudeReady'] and value['claudeSignedIn'] and value['sharedNodePresent'] and
            value['sharedPaseoPresent'], 'claude_or_shared_host_not_ready')
    require(type(value['claudeChatCount']) is int and value['claudeChatCount'] >= 0,
            'invalid_retention_projection')
    require(all(type(value[key]) is str and re.fullmatch('[0-9a-f]{64}', value[key])
                for key in ['claudeChatIdsDigest', 'phoneGateDigest']), 'invalid_retention_projection')
    return value


def absent(value):
    return (type(value) is dict and value.get('leftovers') is False and
            value.get('targetPids') == [] and type(value.get('ownedAllocatedBytes')) is int and
            value['ownedAllocatedBytes'] == 0 and value.get('staging') == [] and
            value.get('lockPresent') is False)


def certify_removal(agent_id, ports, normal, *, timeout_seconds=90,
                    clock=time.monotonic, sleep=time.sleep):
    """One fresh app install/removal, with final normal-app restoration.

    ``ownedAllocatedBytes`` measures exactly the authored payload/link/staging/
    lock set with lstat blocks, without following symlinks. It is independent
    of free-space delta and the product's rounded success copy; do not call it
    the app's exact AgentRemovalResult receipt. Adapter inventory must reject
    unsafe ancestors/foreign launchers and project exact target PIDs only.
    The account-home field is presence only, never credential content/digests.
    ``capture`` must produce reviewed small JPGs and returns no evidence text.
    """
    require(agent_id in TARGETS, 'unsupported_agent')
    require(type(timeout_seconds) in (int, float) and math.isfinite(timeout_seconds) and
            0 < timeout_seconds <= 180, 'invalid_deadline')
    validate_receipt(normal)
    require(ports.emulator_serial == SERIAL and ports.emulator_lock_path == LOCK,
            'wrong_device_or_lock')
    name, pin = TARGETS[agent_id]
    result = {'agentId': agent_id, 'state': 'partial', 'code': 'not_started',
              'normalRestored': False, 'deviceProof': False}
    restore_required = False
    operation_error = None
    with ports.locked_session():
        try:
            free = ports.available_storage_bytes()
            require(type(free) is int and free >= 800000000, 'insufficient_real_storage')
            ports.require_idle_setup()
            require(ports.verify_normal_artifact(normal, SIGNER) is True, 'normal_artifact_unverified')
            for target in TARGETS:
                require(absent(ports.target_inventory(target)), 'target_not_absent')
            # No rejected read-only preflight may restart/replace the app.
            restore_required = True
            require(ports.restore_normal(normal, SIGNER) is True, 'normal_restore_failed')
            require(ports.available_storage_bytes() >= 800000000, 'insufficient_real_storage')
            ports.install_through_app(agent_id, name)
            ports.require_idle_setup()
            installed = ports.agent_state_projection(agent_id)
            require(type(installed) is dict and set(installed) == {'version', 'authState'},
                    'invalid_agent_state')
            require(installed['version'] == pin and installed['authState'] in {
                'signedOut', 'probeUnsupported', 'probeError'}, 'unexpected_version_or_auth')
            before = ports.target_inventory(agent_id)
            require(before.get('pinMatches') is True and before.get('linkMatches') is True and
                    before.get('targetPids') == [] and type(before.get('ownedAllocatedBytes')) is int and
                    before['ownedAllocatedBytes'] > 0, 'installed_target_not_verified')
            kept = retention(ports)
            free_before = ports.available_storage_bytes()
            rows = ports.ui()
            buttons = [node for node in rows if ports.text(node) == 'Remove '+name]
            if not buttons:
                result['code'] = 'public_remove_action_missing'
            else:
                ports.tap_node(buttons[-1])
                rows = ports.ui()
                labels = {ports.text(node) for node in rows}
                require('Remove '+name+' from this phone?' in labels and COPY in labels,
                        'removal_confirmation_missing')
                confirm = [node for node in rows if ports.text(node) == 'Remove']
                require(len(confirm) == 1, 'removal_confirmation_ambiguous')
                began = clock()
                ports.tap_node(confirm[0])
                success = None
                while clock()-began < timeout_seconds:
                    current = ports.target_inventory(agent_id)
                    labels = [ports.text(node) for node in ports.ui()]
                    pattern = re.escape(name)+r' removed\. Freed [0-9]+(?:\.[0-9]+)? (?:B|KB|MB|GB|KiB|MiB|GiB)\.'
                    success = next((text for text in labels if re.fullmatch(pattern, text)), None)
                    row = any(name in text.splitlines() and any(
                        line.startswith('Not installed') for line in text.splitlines()) for text in labels)
                    if absent(current) and success is not None and row:
                        require(retention(ports) == kept, 'retained_state_changed')
                        ports.capture(agent_id+'-removed')
                        result.update(state='pass', code='verified', deviceProof=True,
                                      version=pin, authState=installed['authState'],
                                      removedViaApp=True, noOrphans=True, leftoversRemoved=True,
                                      removedAllocatedBytes=before['ownedAllocatedBytes'],
                                      freeSpaceDeltaBytes=ports.available_storage_bytes()-free_before,
                                      appFreedDisplay=success, retainedClaudeAndSharedHost=True)
                        break
                    sleep(.25)
                else:
                    raise RuntimeError('app_removal_timeout')
        except Exception as error:
            result.update(state='fail', code='proof_failed', errorType=type(error).__name__)
            operation_error = error
        finally:
            try:
                if restore_required:
                    require(ports.restore_normal(normal, SIGNER) is True, 'normal_restore_failed')
                    result['normalRestored'] = True
            except Exception:
                result.update(state='fail', code='normal_restore_failed')
                operation_error = RuntimeError('normal_restore_failed')
            ports.record_result(result)
    if operation_error is not None:
        # Closed errors after evidence and lock release; never adapter text.
        raise RuntimeError(result['code']) from None
    return result

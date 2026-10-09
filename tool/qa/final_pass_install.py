"""Adapters for the existing BA/BB5 device drivers; no device work on import.

Per-row private configuration:
* ba-install: {agent, manifest}; requires a reviewed normal-artifact manifest and a fresh install receipt.
* ba-removal / ba-storage-floor: {agent, manifest}; manifest is the existing
  fq_install2 artifact receipt. Its normal must exactly match the candidate.
  Any BA row may replace agent with agents: a unique list of 1..6 allowed
  targets. These run serially and stop at the first non-pass result.
* bb5: {qa_apk, runner_apk, target_sha, runner_sha, normal_apk, normal_sidecar,
  normal_sha, normal_version, apksigner, aapt}. Paths must be absolute files.
  The QA-enabled artifact must match the existing driver's VERSION (currently 2202),
  and the normal must match both its restore version and the outer context.

The context owns the whole-session emulator flock. adopt_lock(module) must
redirect ONLY that module's LOCK/fcntl references to the inherited descriptor,
retain the lock on LOCK_UN, and restore references on exit. capture(callable)
returns the callable's result while privately discarding its output. command()
must inherit lock_fd, capture output privately and enforce its timeout. Existing
BA generic drivers cannot prove a fresh install in their receipt, so that row
uses the parameterized fresh-install driver. A missing/new-build driver is a blocker, never a pass.
"""
import ast
from contextlib import contextmanager
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys


_AGENTS = {'codex', 'gemini', 'qwen', 'goose', 'omp-acp', 'fx'}
_IMPORTS = ('manifest', 'launch', 'low_storage', 'uninstall', 'device', 'probe', 'agent', 'run', 'device_install')
_LOCK = Path('/home/eslam/Storage/tmp/oc-emulator.lock')


def _result(status, reason, receipts=(), **data):
    result = {'status': status, 'reason': reason,
              'receipts': [str(p) for p in receipts if p.is_file()]}
    if data:
        result['data'] = data
    return result


def _file(value):
    if not isinstance(value, (str, Path)):
        raise ValueError()
    path = Path(value)
    if not path.is_absolute() or not path.is_file():
        raise ValueError()
    return path.resolve()


def _digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def _constants(path):
    values = {}
    for node in ast.parse(path.read_text()).body:
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name):
                    try:
                        values[target.id] = ast.literal_eval(node.value)
                    except (ValueError, TypeError):
                        pass
    return values


def _signature(path):
    try:
        info = path.stat()
        return info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns
    except FileNotFoundError:
        return None


def _fresh(path, before, maximum=262144):
    return (path.is_file() and not path.is_symlink() and
            path.stat().st_size <= maximum and _signature(path) != before)


def _json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError()
            result[key] = value
        return result
    value = json.loads(path.read_text(), object_pairs_hook=unique)
    if not isinstance(value, dict):
        raise ValueError()
    return value


@contextmanager
def _driver(root):
    """Isolate the legacy driver's unqualified sibling imports and argv."""
    saved_path, saved_argv = sys.path[:], sys.argv[:]
    saved = {key: sys.modules.pop(key, None) for key in _IMPORTS}
    try:
        path = root / 'tool/qa/fq_install2'
        sys.path.insert(0, str(path))
        spec = importlib.util.spec_from_file_location('_final_pass_ba', path / 'run.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        yield module
    finally:
        sys.path[:] = saved_path
        sys.argv[:] = saved_argv
        for key, value in saved.items():
            sys.modules.pop(key, None)
            if value is not None:
                sys.modules[key] = value


def _removed(value):
    if not isinstance(value, dict) or value.get('state') != 'pass':
        return False
    facts = value.get('facts', {})
    return isinstance(facts, dict) and all(facts.get(key) is True for key in
        ('asserted', 'removedViaApp', 'leftoversRemoved', 'noOrphans', 'notInstalledRow'))


def _generic_ba(row, config, context):
    if set(config) != {'agent', 'manifest'} or config.get('agent') not in _AGENTS:
        return _result('blocked', 'invalid_configuration')
    if not callable(getattr(context, 'adopt_lock', None)) or not callable(getattr(context, 'capture', None)):
        return _result('blocked', 'unsupported_lock_contract')
    case = 'uninstall' if row == 'ba-removal' else 'low-storage'
    output = context.output / row
    receipt = output / (config['agent'] + '-' + case + '-observations.json')
    with _driver(context.root) as module:
        manifest_path = _file(config['manifest'])
        artifacts = module.manifest.load(manifest_path, case)
        normal = artifacts['normal']
        if (normal['build'] != context.candidate_build or
                _file(normal['apk']) != _file(context.candidate) or
                normal['sha256'] != _digest(context.candidate)):
            return _result('blocked', 'candidate_incompatible')
        before = _signature(receipt)
        sys.argv = [str(context.root / 'tool/qa/fq_install2/run.py'), config['agent'],
                    '--case', case, '--manifest', str(manifest_path), '--execute',
                    '--output', str(output), '--private-observations']
        failed = False
        try:
            with context.adopt_lock(module):
                context.capture(module.main)
        except Exception:
            failed = True
        if not _fresh(receipt, before):
            return _result('fail', 'fresh_receipt_missing', safe_to_continue=False)
        value = _json(receipt)
        valid = (not failed and not value.get('error') and
                 value.get('agentId') == config['agent'] and value.get('case') == case and
                 value.get('appBuild') == context.candidate_build and
                 value.get('normalRestored') is True and _removed(value.get('uninstall')))
        if row == 'ba-storage-floor':
            guard = value.get('guard', {})
            facts = guard.get('facts', {}) if isinstance(guard, dict) else {}
            retry = value.get('normalRetry', {})
            valid = (valid and guard.get('state') == 'pass' and
                     all(facts.get(k) is True for k in ('asserted', 'guardRefused', 'noDownload',
                         'freshJob', 'targetAbsent', 'visibleStorageWayForward', 'guardRequirementDeclared')) and
                     facts.get('declaredRequiredBytes') == 8589934592 and
                     isinstance(retry, dict) and retry.get('pinMatches') is True and
                     retry.get('linkMatches') is True and retry.get('targetPids') == [])
        return _result('pass' if valid else 'fail',
                       'verified' if valid else 'row_not_qualified', [receipt],
                       **({'safe_to_continue': False} if value.get('normalRestored') is not True else {}))


def _install(config, context):
    if set(config) != {'agent', 'manifest'} or config.get('agent') not in _AGENTS:
        return _result('blocked', 'invalid_configuration')
    driver = context.root / 'tool/qa/fq_install2/device_install.py'
    if not driver.is_file():
        return _result('blocked', 'install_receipt_unavailable')
    with _driver(context.root) as module:
        manifest = _file(config['manifest'])
        artifact = module.manifest.load(manifest, 'install')['normal']
    if (artifact['build'] != context.candidate_build or
            _file(artifact['apk']) != _file(context.candidate) or
            artifact['sha256'] != _digest(context.candidate)):
        return _result('blocked', 'candidate_incompatible')
    output = context.output / 'ba-install'
    receipt = output / (config['agent'] + '-device.json')
    before = _signature(receipt)
    completed = context.command([sys.executable, str(driver), config['agent'],
        '--manifest', str(manifest), '--output', str(output),
        '--inherited-emulator-lock-fd', str(context.lock_fd)], timeout=900)
    if not _fresh(receipt, before):
        return _result('fail', 'fresh_receipt_missing', safe_to_continue=False)
    value = _json(receipt)
    proof, installed = value.get('installProof', {}), value.get('installed', {})
    valid = (completed.returncode == 0 and value.get('agentId') == config['agent'] and
             value.get('appBuild') == context.candidate_build and value.get('normalRestored') is True and
             not value.get('errorType') and isinstance(proof, dict) and
             all(proof.get(k) is True for k in ('installedViaApp', 'freshJob', 'checksumVerified')) and
             isinstance(installed, dict) and installed.get('pinMatches') is True and
             installed.get('linkMatches') is True and
             value.get('version', {}).get('exactVersion') is True and
             value.get('retainedStateMatches') is True and _removed(value.get('uninstall')))
    return _result('pass' if valid else 'fail', 'verified' if valid else 'row_not_qualified', [receipt],
                   safe_to_continue=value.get('normalRestored') is True)


def _bb5(config, context):
    keys = {'runner_apk', 'target_sha', 'runner_sha', 'normal_apk', 'normal_sidecar',
            'normal_sha', 'normal_version', 'apksigner', 'aapt'}
    if set(config) != keys | {'qa_apk'}:
        return _result('blocked', 'invalid_configuration')
    driver = context.root / 'tool/qa/bb5_runtime_acceptance.py'
    constants = _constants(driver)
    normal_version = constants.get('NORMAL_VERSION', constants.get('VERSION'))
    if (constants.get('VERSION') != context.candidate_build or
            type(config['normal_version']) is not int or config['normal_version'] != normal_version or
            getattr(context, 'normal_build', None) != normal_version or
            _file(config['normal_apk']) != _file(context.normal_apk)):
        return _result('blocked', 'candidate_incompatible')
    for key in ('target_sha', 'runner_sha', 'normal_sha'):
        if not isinstance(config[key], str) or not re.fullmatch('[a-f0-9]{64}', config[key]):
            return _result('blocked', 'invalid_configuration')
    paths = {key: _file(config[key]) for key in
             ('qa_apk', 'runner_apk', 'normal_apk', 'normal_sidecar', 'apksigner', 'aapt')}
    if (config['target_sha'] != _digest(paths['qa_apk']) or
            config['normal_sha'] != _digest(paths['normal_apk']) or
            config['runner_sha'] != _digest(paths['runner_apk'])):
        return _result('blocked', 'artifact_hash_mismatch')
    receipt = context.output / 'bb5.txt'
    before = _signature(receipt)
    argv = [sys.executable, str(driver), '--emulator-go', '--apk', str(paths['qa_apk']),
            '--version', str(context.candidate_build), '--out', str(receipt),
            '--inherited-emulator-lock-fd', str(context.lock_fd)]
    for key, value in config.items():
        if key == 'qa_apk':
            continue
        argv.extend(['--' + key.replace('_', '-'), str(value)])
    completed = context.command(argv, timeout=1200)
    if not _fresh(receipt, before):
        return _result('fail', 'fresh_receipt_missing', safe_to_continue=False)
    lines = receipt.read_text().splitlines()
    marker = 'PASS BB5_locked_actual_idle_stop_resume_and_normal_' + str(normal_version) + '_restoration'
    valid = completed.returncode == 0 and lines.count(marker) == 1 and not any(line.startswith('FAIL ') for line in lines)
    return _result('pass' if valid else 'fail', 'verified' if valid else 'row_not_qualified', [receipt])


def run(row, config, context):
    """Run one row, returning only closed status/facts and existing receipts."""
    if row not in {'ba-install', 'ba-removal', 'ba-storage-floor', 'bb5'} or type(config) is not dict:
        return _result('blocked', 'invalid_configuration')
    try:
        fd = context.lock_fd
        if type(fd) is not int or fd < 3:
            return _result('blocked', 'unsupported_lock_contract')
        owned, expected = os.fstat(fd), _LOCK.stat()
        if (owned.st_dev, owned.st_ino) != (expected.st_dev, expected.st_ino):
            return _result('blocked', 'unsupported_lock_contract')
        if row.startswith('ba-') and 'agents' in config:
            agents = config['agents']
            if ('agent' in config or type(agents) is not list or not 1 <= len(agents) <= 6 or
                    any(type(agent) is not str or agent not in _AGENTS for agent in agents) or
                    len(set(agents)) != len(agents)):
                return _result('blocked', 'invalid_configuration')
            receipts = []
            for index, agent in enumerate(agents):
                single = {key: value for key, value in config.items() if key != 'agents'}
                single['agent'] = agent
                result = run(row, single, context)
                receipts.extend(Path(path) for path in result['receipts'])
                if result['status'] != 'pass':
                    return _result(result['status'], 'stopped_on_agent_failure', receipts,
                                   completedAgents=index)
            return _result('pass', 'verified', receipts, completedAgents=len(agents))
        if row == 'bb5':
            return _bb5(config, context)
        if row == 'ba-install':
            return _install(config, context)
        return _generic_ba(row, config, context)
    except (ValueError, TypeError, KeyError, OSError, SyntaxError, AttributeError):
        return _result('blocked', 'invalid_configuration')
    except Exception:
        return _result('fail', 'driver_failed', safe_to_continue=False)

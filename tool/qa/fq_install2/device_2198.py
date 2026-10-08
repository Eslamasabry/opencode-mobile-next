#!/usr/bin/env python3
"""One normal2198 target under an outer flock; no account or shell removal actions."""
import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path
import re
import sys
import time
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw
import run

QA = run.REPO / 'docs/qa/FQ-install2-2026-10-09'
NORMAL = Path('/home/eslam/Storage/tmp/oc-apk-share/oc-2198.apk')
SOURCE = '064a43a3687773626dc5ce37853446c29be41e0c'


def account_mask_boxes(nodes):
    boxes = []
    for node in nodes:
        copy = (node.get('text') or '') + '\n' + (node.get('content-desc') or '')
        # Modal barriers hide background account semantics, not its pixels.
        # Mask the entire barrier; use fresh non-modal rows for image evidence.
        if 'Claude Code' not in copy and 'Signed in as ' not in copy and 'Scrim' not in copy.splitlines():
            continue
        bounds = node.get('bounds', '')
        if not re.fullmatch(r'\[\d+,\d+\]\[\d+,\d+\]', bounds):
            raise RuntimeError('account_mask_bounds_unavailable')
        boxes.append(tuple(map(int, re.findall(r'\d+', bounds))))
    return boxes


def closed_error_code(error):
    allowed = {'unsupported_agent', 'insufficient_real_storage', 'target_not_absent',
               'pin_not_verified', 'removal_not_qualified', 'app_install_timeout',
               'app_install_failed', 'target_install_action_missing', 'phone_check_did_not_drain',
               'target_partial_or_in_use', 'artifact_hash_mismatch', 'artifact_signer_mismatch',
               'artifact_changed', 'app_restore_failed', 'app_build_mismatch',
               'installed_app_hash_mismatch', 'account_mask_bounds_unavailable'}
    return str(error) if isinstance(error, RuntimeError) and str(error) in allowed else None


def configure_device(d, output):
    d.configure(output)
    original = d.adb
    def rooted_read(*args, **kwargs):
        if (args and args[0] == 'shell' and len(args) > 2 and
                args[1] in ('cat', 'stat') and
                any('/data/user/0/' + d.PKG + '/' in value for value in args[2:])):
            args = ('shell', 'su', '0') + args[1:]
        return original(*args, **kwargs)
    d.adb = rooted_read
    def bounded_ui():
        path = '/data/local/tmp/fq-install2.xml'
        d.adb('shell', 'uiautomator', 'dump', path, timeout=12)
        raw = d.adb('shell', 'cat', path, timeout=12)
        if len(raw) > 2 * 1024 * 1024:
            raise RuntimeError('ui_snapshot_too_large')
        nodes = ET.fromstring(raw).findall('.//node')
        d.adb('shell', 'rm', '-f', path)
        return nodes
    d.ui = bounded_ui
    def safe_shot(name):
        # Raw pixels and UI copy remain memory-only. Mask the whole Claude row
        # and every signed-in identity node before writing any image bytes.
        boxes = account_mask_boxes(d.ui())
        image = Image.open(BytesIO(d.adb('exec-out', 'screencap', '-p', raw=True))).convert('RGB')
        boxes.extend(account_mask_boxes(d.ui()))
        draw = ImageDraw.Draw(image)
        for box in boxes:
            draw.rectangle(box, fill='white')
        image.thumbnail((540, 1200))
        image.save(output / (name + '.jpg'), quality=73)
    d.shot = safe_shot


def retention(d, p):
    # Only public gate fingerprints and opaque chat IDs enter digests. Never
    # select account/token preferences or hash credential files.
    raw = d.adb('shell', 'cat', '/data/user/0/' + d.PKG + '/shared_prefs/FlutterSharedPreferences.xml')
    gates, ids = [], []
    for node in ET.fromstring(raw):
        key = node.get('name', '')
        if 'oc.agentPhoneGate.' in key:
            value = json.loads(node.text or '{}')
            for agent_id, gate in value.items():
                gates.append((key, agent_id, gate.get('fingerprint'), gate.get('architecture')))
        if 'oc.agentFeed.' in key:
            for row in json.loads(node.text or '[]'):
                if row.get('agentId') == 'claude':
                    ids.append((row.get('sourceId'), row.get('sessionID')))
    digest = lambda value: hashlib.sha256(json.dumps(sorted(value), sort_keys=True).encode()).hexdigest()
    shared = json.loads(p.probe("""import pathlib,json
home=pathlib.Path('/home/oc')
profiles=home/'.oc-profiles'
print(json.dumps({'nodePresent':(home/'.local/node/bin/node').exists(),'paseoPresent':(home/'.local/bin/paseo').exists(),'claudeHomes':sum((f/'claude').is_dir() for f in profiles.iterdir()),'targetHomes':{a:sum((f/a).is_dir() for f in profiles.iterdir()) for a in ['codex','gemini','qwen','goose','omp-acp','fx']}}))
"""))
    return {'claudeSignedInVisible': any('Claude Code' in d.text(n) and 'Signed in as ' in d.text(n) for n in d.ui()),
            'phoneGateDigest': digest(gates), 'claudeChatIdDigest': digest(ids),
            'claudeChatCount': len(ids), **shared}


def run_case(agent_id):
    if agent_id not in run.TARGETS:
        raise RuntimeError('unsupported_agent')
    d, p, a, metadata = run.legacy_ports()
    QA.mkdir(parents=True, exist_ok=True)
    configure_device(d, QA)
    class Ports(run.Ports):
        freed_display = None
        last_job = None
        def setup_snapshot(self):
            job = super().setup_snapshot()
            summary = {'state': job['state'], 'target': job['components'].get('agent-' + self.agent_id)}
            signature = json.dumps(summary, sort_keys=True)
            if signature != self.last_job:
                print(json.dumps({'stage': 'setup', **summary}), flush=True)
                self.last_job = signature
            return job
        def target_not_installed_visible(self, target):
            for node in self.ui():
                value = self.text(node)
                if re.fullmatch(re.escape(self.name) + r' removed\. Freed [0-9]+(?:\.[0-9]+)? (?:B|KB|MB|GB|KiB|MiB|GiB)\.', value):
                    self.freed_display = value
            return super().target_not_installed_visible(target)
    ports = Ports(d, p, a, metadata, agent_id)
    artifact = {'apk': str(NORMAL), 'build': 2198, 'sha256': '2069cc0ca62554e3fd798f8ba8bfa46f44ae84be4ff82142e87b495cdc8b89a3',
                'sourceRevision': SOURCE, 'dartDefines': {}}
    result = {'agentId': agent_id, 'appBuild': 2198, 'sourceRevision': SOURCE, 'normalRestored': False}
    restore = False
    try:
        result['availableBeforeBytes'] = ports.available_storage_bytes()
        if result['availableBeforeBytes'] < 800000000:
            raise RuntimeError('insufficient_real_storage')
        p.require_idle_setup()
        run.verify_artifact(artifact)
        for other in run.TARGETS:
            inv = ports.target_inventory(other)
            if inv['leftovers'] or inv['targetPids']:
                raise RuntimeError('target_not_absent')
        restore = True
        run.select_apk(ports, artifact)
        ports.install_if_absent()
        result['installed'] = ports.target_inventory(agent_id)
        version = json.loads(p.probe("""import subprocess,json,re
try:
 p=subprocess.run([EXE,'--version'],capture_output=True,timeout=10)
 out=p.stdout.decode(errors='replace')
 match=p.returncode==0 and re.search(r'(?<![0-9])'+re.escape(PIN)+r'(?![0-9])',out) is not None
except Exception: match=False
print(json.dumps({'exactVersion':match}))
""".replace('EXE', repr('/home/oc/.local/bin/' + metadata[agent_id]['executable'])).replace('PIN', repr(metadata[agent_id]['version']))))
        result['version'] = version
        if not version['exactVersion']:
            raise RuntimeError('pin_not_verified')
        d.launch_agents()
        result['retainedBeforeRemoval'] = retention(d, p)
        d.shot(agent_id + '-installed')
        result['launch'] = run.run_launch(ports, agent_id, metadata[agent_id]['name'])
        d.launch_agents()
        result['uninstall'] = run.run_uninstall(agent_id, metadata[agent_id]['name'], ports)
        result['appFreedDisplay'] = ports.freed_display
        d.launch_agents()
        result['retainedAfterRemoval'] = retention(d, p)
        result['retainedStateMatches'] = result['retainedBeforeRemoval'] == result['retainedAfterRemoval']
        result['finalInventory'] = ports.target_inventory(agent_id)
        result['availableAfterBytes'] = ports.available_storage_bytes()
        d.shot(agent_id + '-removed')
        if result['uninstall']['state'] != 'pass' or not result['retainedStateMatches'] or not ports.freed_display:
            raise RuntimeError('removal_not_qualified')
    except Exception as error:
        result['errorType'] = type(error).__name__
        code = closed_error_code(error)
        if code is not None:
            result['errorCode'] = code
        try:
            result['terminalSetup'] = ports.setup_snapshot()
            result['terminalInventory'] = ports.target_inventory(agent_id)
        except Exception:
            result['terminalObservationUnavailable'] = True
    finally:
        try:
            if restore:
                run.select_apk(ports, artifact)
                result['normalRestored'] = True
        except Exception:
            result['restorationBlocked'] = True
        (QA / (agent_id + '-device.json')).write_text(json.dumps(result, indent=2) + '\n')
        d.adb('shell', 'rm', '-f', '/data/local/tmp/fq-install.xml', '/data/local/tmp/fq-install2.xml')
        d.end_session()
    print(json.dumps(result))
    return 1 if result.get('errorType') or not result['normalRestored'] else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('agent', choices=tuple(run.TARGETS))
    parser.add_argument('--lock-held', action='store_true', required=True)
    args = parser.parse_args()
    sys.exit(run_case(args.agent))

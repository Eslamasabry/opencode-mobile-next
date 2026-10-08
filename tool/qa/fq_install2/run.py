#!/usr/bin/env python3
"""Offline plans by default; explicit execution uses one locked target session."""
import argparse
import fcntl
import hashlib
import importlib.util
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import stat
import sys
import time
import xml.etree.ElementTree as ET

import manifest
from launch import run_launch, TARGETS
from low_storage import run_low_storage
from uninstall import run_uninstall

REPO = Path(__file__).resolve().parents[3]
LEGACY = REPO / 'tool/qa/fq_install'
LOCK = Path('/home/eslam/Storage/tmp/oc-emulator.lock')
SIGNER = '1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c'
STORAGE_COPY = 'There is not enough free space on this phone. Free some storage and try again.'


def legacy_ports():
    # Load only after --execute; a plan does not call adb or import device code.
    sys.path.insert(1, str(LEGACY))
    import device, probe, agent
    spec = importlib.util.spec_from_file_location('fq_install_catalog_runner', LEGACY / 'run.py')
    old = importlib.util.module_from_spec(spec); spec.loader.exec_module(old)
    return device, probe, agent, old.catalog()


def artifact_identity(apk):
    try:
        info=apk.lstat()
    except OSError:
        raise RuntimeError('artifact_unavailable') from None
    if not stat.S_ISREG(info.st_mode): raise RuntimeError('artifact_unavailable')
    return (info.st_dev,info.st_ino,info.st_size,info.st_mtime_ns,info.st_ctime_ns)


def require_artifact_unchanged(artifact, identity):
    if artifact_identity(Path(artifact['apk'])) != identity:
        raise RuntimeError('artifact_changed')


def verify_artifact(artifact):
    apk = Path(artifact['apk'])
    identity=artifact_identity(apk)
    # Keep the verified inode open through signer verification; reject races
    # involving the final path, replacement, writes, or changed timestamps.
    descriptor=os.open(apk,os.O_RDONLY|os.O_NOFOLLOW)
    with os.fdopen(descriptor,'rb') as stream:
        opened=os.fstat(stream.fileno())
        if (opened.st_dev,opened.st_ino,opened.st_size,opened.st_mtime_ns,opened.st_ctime_ns)!=identity:
            raise RuntimeError('artifact_changed')
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if digest != artifact['sha256']: raise RuntimeError('artifact_hash_mismatch')
        require_artifact_unchanged(artifact,identity)
        sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or str(Path.home()/'Android/Sdk'))
        candidates = sorted((sdk/'build-tools').glob('*/apksigner'))
        signer = shutil.which('apksigner') or (str(candidates[-1]) if candidates else None)
        if signer is None: raise RuntimeError('signer_tool_unavailable')
        result = subprocess.run([signer,'verify','--print-certs',str(apk)],capture_output=True,timeout=30)
        require_artifact_unchanged(artifact,identity)
        if result.returncode or SIGNER not in result.stdout.decode(errors='replace').lower():
            raise RuntimeError('artifact_signer_mismatch')
    return identity


class Ports:
    def __init__(self, d, p, a, metadata, agent_id):
        self.d,self.p,self.a = d,p,a
        self.agent_id,self.name = agent_id,metadata[agent_id]['name']
        self.metadata = metadata
    def ui(self):
        # One bounded dump, never legacy's three 50-second retries.
        path='/data/local/tmp/fq-install2.xml'
        self.d.adb('shell','uiautomator','dump',path,timeout=12)
        raw=self.d.adb('shell','cat',path,timeout=12)
        if len(raw)>2*1024*1024: raise RuntimeError('ui_snapshot_too_large')
        return ET.fromstring(raw).findall('.//node')
    def text(self, node): return self.d.text(node)
    def tap_node(self, node): return self.d.tap_node(node)
    def back(self): self.d.adb('shell','input','keyevent','4'); time.sleep(.4)
    def capture_launch_rejection(self):
        self.d.shot(self.agent_id+'-launch-rejection')
    def available_storage_bytes(self): return self.a.space()
    def target_inventory(self, agent_id):
        c=self.metadata[agent_id]
        self.a.AGENT,self.a.C,self.a.NAME,self.a.EXE,self.a.PIN = agent_id,c,c['name'],c['executable'],c['version']
        self.a.BASE='/home/oc/.local/share/oc-agents/'+agent_id
        return self.a.inventory()  # Read-only; never invoke manual cleanup.
    def setup_snapshot(self):
        # Raw errors/scripts/params are private; export exact closed facts only.
        raw=self.d.adb('shell','cat',self.p.FILES+'/linux/setup.json')
        if len(raw)>2*1024*1024: raise RuntimeError('snapshot_too_large')
        job=json.loads(raw)
        if not isinstance(job,dict) or not isinstance(job.get('components'),dict):
            raise RuntimeError('snapshot_invalid')
        guard=(job.get('params') or {}).get('agentInstallGuard',{})
        comps={}
        for key,value in job['components'].items():
            if type(key) is not str or not re.fullmatch(r'[a-z0-9_-]{1,80}',key) or not isinstance(value,dict): continue
            declared=None
            if key in ['agent-'+id for id in TARGETS] and guard.get('agentId')==key.removeprefix('agent-'):
                try: declared=int(guard.get('minimumFreeBytes',''))
                except (TypeError,ValueError): pass
            comps[key]={'state':value.get('state'),'done':value.get('done'),
                        'declaredMinimumFreeBytes':declared,
                        'errorCode':'low_storage' if value.get('error')==STORAGE_COPY else None}
        return {'jobId':job.get('jobId'),'state':job.get('state'),'components':comps}
    def tap_install(self, agent_id):
        if agent_id != self.agent_id: raise RuntimeError('target_mismatch')
        self.d.launch_agents()
        self.d.tap('Install '+self.name)
        self.d.tap('Install '+self.name)
    def storage_guidance_visible(self):
        return any(STORAGE_COPY in self.text(node).splitlines() for node in self.ui())
    def app_remove(self, agent_id, name):
        if agent_id != self.agent_id or name != self.name: raise RuntimeError('target_mismatch')
        self.d.launch_agents()
        exact='Remove '+name
        found=[node for node in self.ui() if self.text(node)==exact]
        if not found: return False
        self.tap_node(found[-1])
        confirmation='Remove '+name+' from this phone? Your account and conversations stay saved.'
        nodes=self.ui()
        if not any(self.text(node)==confirmation for node in nodes): return False
        buttons=[node for node in nodes if self.text(node)==exact]
        if not buttons: return False
        self.tap_node(buttons[-1]); return True
    def target_not_installed_visible(self, agent_id):
        if agent_id != self.agent_id: return False
        return any(self.name in self.text(node).splitlines() and
                   any(line.startswith('Not installed') for line in self.text(node).splitlines())
                   for node in self.ui())
    def install_if_absent(self):
        # Real storage check precedes every install dispatch.
        if self.available_storage_bytes()<800000000: raise RuntimeError('insufficient_real_storage')
        current=self.target_inventory(self.agent_id)
        if current['pinMatches'] and current['linkMatches']: return
        if current['leftovers'] or current['targetPids']: raise RuntimeError('target_partial_or_in_use')
        baseline=self.setup_snapshot()['jobId']
        self.tap_install(self.agent_id)
        deadline=time.monotonic()+240
        while time.monotonic()<deadline:
            job=self.setup_snapshot()
            if job['jobId']!=baseline and job['state'] in ('failed','cancelled','interrupted'):
                raise RuntimeError('app_install_failed')
            if job['jobId']!=baseline and job['state']=='done':
                current=self.target_inventory(self.agent_id)
                if current['pinMatches'] and current['linkMatches']:
                    # Wait for automatic phone checks to drain before navigation.
                    for _ in range(90):
                        if not any('Checking ' in self.text(n) and '…' in self.text(n) for n in self.ui()): return
                        time.sleep(1)
                    raise RuntimeError('phone_check_did_not_drain')
            time.sleep(.5)
        raise RuntimeError('app_install_timeout')


def select_apk(ports, artifact):
    # Reverify under the lock, including restoration. --wait may have queued
    # after the initial offline verification while a build pathname changed.
    identity=verify_artifact(artifact)
    ports.p.require_idle_setup()
    package=ports.d.adb('shell','cmd','package','path',ports.d.PKG).strip().removeprefix('package:')
    current=ports.d.adb('shell','sha256sum',package).split()[0]
    if current != artifact['sha256']:
        require_artifact_unchanged(artifact,identity)
        if 'Success' not in ports.d.adb('install','-r','-d',artifact['apk'],timeout=90):
            raise RuntimeError('app_restore_failed')
        require_artifact_unchanged(artifact,identity)
    if 'versionCode='+str(artifact['build'])+' ' not in ports.d.adb('shell','dumpsys','package',ports.d.PKG):
        raise RuntimeError('app_build_mismatch')
    package=ports.d.adb('shell','cmd','package','path',ports.d.PKG).strip().removeprefix('package:')
    if ports.d.adb('shell','sha256sum',package).split()[0] != artifact['sha256']:
        raise RuntimeError('installed_app_hash_mismatch')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('agent',choices=tuple(TARGETS))
    parser.add_argument('--case',choices=('launch','uninstall','low-storage'),required=True)
    parser.add_argument('--manifest',type=Path,required=True)
    parser.add_argument('--execute',action='store_true',help='Operate only after coordinator artifact delivery')
    parser.add_argument('--wait',action='store_true')
    parser.add_argument('--output',type=Path,default=REPO/'docs/qa/FQ-install2-2026-10-08')
    args=parser.parse_args()
    artifacts=manifest.load(args.manifest,args.case)
    if not args.execute:
        print(json.dumps({'agentId':args.agent,'case':args.case,'offlinePlan':True,
                          'normalBuild':artifacts['normal']['build'],
                          'deviceTouched':False,'requiresGuardCandidate':args.case=='low-storage'}))
        return
    # Verify before device access; wrong hashes/signers cause no mutations.
    for artifact in artifacts.values(): verify_artifact(artifact)
    d,p,a,metadata=legacy_ports()
    result={'agentId':args.agent,'case':args.case,'appBuild':artifacts['normal']['build'],
            'sourceRevision':artifacts['normal']['sourceRevision'],'normalRestored':False}
    with LOCK.open('a') as lock:
        try: fcntl.flock(lock,fcntl.LOCK_EX | (0 if args.wait else fcntl.LOCK_NB))
        except BlockingIOError: raise RuntimeError('emulator_in_use')
        d.configure(args.output.resolve())
        ports=Ports(d,p,a,metadata,args.agent)
        restore_required=False
        operation_error=None
        restoration_failed=False
        try:
            if d.adb('get-state').strip()!='device' or d.adb('shell','getprop','ro.product.cpu.abi').strip()!='x86_64':
                raise RuntimeError('dev_emulator_unavailable')
            # Storage first, before APK replacement or installation.
            result['availableBytes']=ports.available_storage_bytes()
            if result['availableBytes']<800000000: raise RuntimeError('insufficient_real_storage')
            p.require_idle_setup()
            for other in TARGETS:
                if other==args.agent: continue
                inv=ports.target_inventory(other)
                if inv['leftovers'] or inv['targetPids']: raise RuntimeError('another_target_installed')
            target=ports.target_inventory(args.agent)
            if target['targetPids']: raise RuntimeError('target_in_use')
            if args.case=='low-storage' and target['leftovers']:
                raise RuntimeError('target_not_absent')
            if args.case!='low-storage' and target['leftovers'] and not (
                    target.get('pinMatches') is True and target.get('linkMatches') is True):
                raise RuntimeError('target_partial_or_in_use')
            # A rejected read-only preflight must not install/restore an APK.
            # Obligation starts before the first attempt, so partial installs
            # and post-install validation failures still restore normal.
            restore_required=True
            if args.case=='low-storage':
                select_apk(ports,artifacts['guard'])
                result['guardBuild']=artifacts['guard']['build']
                result['guardSourceRevision']=artifacts['guard']['sourceRevision']
                result['guard']=run_low_storage(args.agent,ports,ports,qa_artifact_verified=True)
                d.shot(args.agent+'-storage-refusal')
                ports.back()
                select_apk(ports,artifacts['normal'])
                ports.install_if_absent()
                result['normalRetry']=ports.target_inventory(args.agent)
                # Product removal after retry is required before the next agent.
                result['uninstall']=run_uninstall(args.agent,metadata[args.agent]['name'],ports)
            else:
                select_apk(ports,artifacts['normal']); ports.install_if_absent()
                if args.case=='launch':
                    result['launch']=run_launch(ports,args.agent,metadata[args.agent]['name'])
                result['uninstall']=run_uninstall(args.agent,metadata[args.agent]['name'],ports)
            d.shot(args.agent+'-final')
        except Exception as error:
            result['error']=type(error).__name__  # Never reflect raw bridge text.
            operation_error=error
        finally:
            try:
                if restore_required:
                    select_apk(ports,artifacts['normal'])
                    result['normalRestored']=True
            except Exception:
                result['restorationBlocked']=True
                restoration_failed=True
            try:
                args.output.mkdir(parents=True,exist_ok=True)
                (args.output/(args.agent+'-'+args.case+'-observations.json')).write_text(json.dumps(result,indent=2)+'\n')
            finally:
                d.end_session(); fcntl.flock(lock,fcntl.LOCK_UN)
    if restoration_failed: raise RuntimeError('normal_app_restoration_failed') from None
    if operation_error is not None: raise operation_error
    print(json.dumps({'agentId':args.agent,'case':args.case,'normalRestored':result['normalRestored']}))

if __name__=='__main__':
    try: main()
    except Exception as error: raise SystemExit('Certification stopped: '+type(error).__name__)

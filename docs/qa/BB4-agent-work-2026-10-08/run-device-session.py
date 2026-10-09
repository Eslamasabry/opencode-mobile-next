import subprocess,json,hashlib
from pathlib import Path
root=Path('/home/eslam/Storage/Code/oc_app-sol-bb');out=root/'docs/qa/BB4-agent-work-2026-10-08'
a=json.loads((out/'target-work-artifact.json').read_text());b=json.loads((out/'runner-work-artifact.json').read_text())
for line in (out/'build-candidate.sha256').read_text().splitlines():
 digest,name=line.split('  ',1);assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest
for name in ['target-work','runner-work']:
 state=json.loads((out/(name+'-owned-processes.json')).read_text());assert state['remaining']==[] and state['intermediatesAbsent']
cmd=['flock','/home/eslam/Storage/tmp/oc-emulator.lock','python3','/tmp/oc-bb4-agent-boot-and-device.py','--emulator-go','--scenario','queue','--apk',str(root/'build/app/outputs/flutter-apk/app-release.apk'),'--runner-apk',str(root/'build/app/outputs/apk/androidTest/release/app-release-androidTest.apk'),'--apksigner','/home/eslam/Android/Sdk/build-tools/36.0.0/apksigner','--aapt','/home/eslam/Android/Sdk/build-tools/36.0.0/aapt','--out',str(out/'host-device-work-leases-restore.txt'),'--target-sha',a['sha256'],'--runner-sha',b['sha256'],'--version','2214']
raise SystemExit(subprocess.run(cmd,cwd=root).returncode)

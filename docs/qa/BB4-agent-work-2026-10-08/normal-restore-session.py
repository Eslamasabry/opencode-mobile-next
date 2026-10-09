import sys,time,json,hashlib,re,types,subprocess,io
from pathlib import Path
from PIL import Image
sys.path.insert(0,'tool/qa')
import bb9_component_update_acceptance as H
import bb4_runtime_acceptance as L
L.configure(H)
import bb9_external_installer as E
H.inherited_lock()
normal=Path('/home/eslam/Storage/tmp/oc-apk-share/oc-2195.apk')
sha=hashlib.sha256(normal.read_bytes()).hexdigest()
sdk='/home/eslam/Android/Sdk/build-tools/36.0.0/'
cert=subprocess.run([sdk+'apksigner','verify','--print-certs',str(normal)],capture_output=True,text=True,check=True)
assert re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)',cert.stdout)==[H.Q.CERT.lower()]
meta=subprocess.run([sdk+'aapt','dump','badging',str(normal)],capture_output=True,text=True,check=True)
assert "name='"+H.PACKAGE+"'" in meta.stdout and "versionCode='2195'" in meta.stdout
from bb9_service_installer import ServiceInstaller
E.ExternalInstaller=ServiceInstaller
d=H.Device();lines=[];code=1
import xml.etree.ElementTree as ET
original_tree=ET.fromstring(d.cat(H.FLUTTER))
original_active=H.Q.preference_values(original_tree).get('flutter.oc.activeProfile')
original_active_text=original_active.text if original_active is not None else None
original_owner=H.selected_profile(ET.tostring(original_tree,encoding='unicode'))
original_policy_keys=['flutter.oc.automation.'+original_owner,'flutter.oc.builtinRecovery.'+original_owner]
import copy
original_policy={key:copy.deepcopy(H.Q.preference_values(original_tree).get(key)) for key in original_policy_keys}
def restore_original_metadata(device):
 # This deliberately restores metadata before the unchanged legacy comparator.
 # The current preference tree preserves profile/auth and unrelated QA additions.
 H.require(device.adb('shell','am','force-stop',H.PACKAGE,timeout=5).returncode==0,
           'final_metadata_force_stop_failed')
 until=time.monotonic()+10
 def bounded_adb(*args,timeout=3):
  remaining=until-time.monotonic()
  H.require(remaining>0,'final_metadata_uid_still_live')
  return device.adb(*args,timeout=min(timeout,remaining))
 package=bounded_adb('shell','cmd','package','list','packages','-U','--user','0',H.PACKAGE)
 H.require(package.returncode==0 and isinstance(package.stdout,str) and len(package.stdout)<=4096,
           'final_metadata_uid_unavailable')
 matches=re.findall(r'^package:'+re.escape(H.PACKAGE)+r' uid:(\d+)$',package.stdout,re.M)
 H.require(len(matches)==1 and int(matches[0])>=10000,'final_metadata_uid_unavailable')
 uid=int(matches[0])
 bounded_device=types.SimpleNamespace(adb=bounded_adb)
 while H.uid_inventory(bounded_device,uid):
  remaining=until-time.monotonic()
  H.require(remaining>0,'final_metadata_uid_still_live')
  time.sleep(min(.1,remaining))
 current_tree=ET.fromstring(device.cat(H.FLUTTER))
 H.require(current_tree.tag=='map','final_metadata_preferences_invalid')
 desired={'flutter.oc.activeProfile':original_active,**original_policy}
 H.require(set(original_policy)==set(original_policy_keys) and len(original_policy_keys)==2,
           'final_metadata_original_keys_invalid')
 for key,node in desired.items():
  current=[child for child in current_tree if child.attrib.get('name')==key]
  H.require(len(current)<=1,'final_metadata_duplicate_key')
  if current:current_tree.remove(current[0])
  if node is not None:current_tree.append(copy.deepcopy(node))
 H.require(not H.uid_inventory(device,uid),'final_metadata_uid_still_live')
 device.write_dead(H.FLUTTER,ET.tostring(current_tree,encoding='unicode'))
 H.require(not H.uid_inventory(device,uid),'final_metadata_uid_still_live')
 restored_values=H.Q.preference_values(ET.fromstring(device.cat(H.FLUTTER)))
 H.require(all(H.preference_equal(node,restored_values.get(key)) for key,node in desired.items()),
           'final_metadata_typed_restoration_failed')
 for line in ['PASS original_owner_typed_policy_and_marker_metadata_restored_while_entire_uid_dead',
              'PASS original_active_profile_pointer_restored_while_app_dead']:
  lines.append(line)
  print(line,flush=True)
H.Device.restore_original_metadata_before_comparison=restore_original_metadata
# Keep the qualified bootstrap stopped. H.execute selects the existing OC2 owner
# while dead, starts through MainActivity, and captures the freshly armed baseline.
# A pre-bootstrap restore_person cannot compare a legacy/missing ownership receipt.
out=Path(sys.argv[sys.argv.index('--out')+1])
try:
 code=L.main()
finally:
 try:
  restore_original_metadata(d)
  result=d.adb('install','-r','-d',str(normal),timeout=120)
  assert result.returncode==0
  assert d.installed_hash(H.PACKAGE)==sha
  package=d.adb('shell','dumpsys','package',H.PACKAGE,timeout=5)
  assert re.search(r'versionCode=2195\b',package.stdout)
  assert d.adb('shell','am','start','-n',H.PACKAGE+'/.MainActivity',timeout=5).returncode==0
  lines.append('PASS normal_2195_same_signer_in_place_restore_inside_same_emulator_lock')
 except Exception:
  lines.append('FAIL normal_2195_restore_unproven');code=1
 else:
  try:
   time.sleep(3)
   result=d.run(['adb','-s',H.SERIAL,'exec-out','screencap','-p'],text=False,timeout=15)
   assert result.returncode==0 and len(result.stdout)<4000000
   im=Image.open(io.BytesIO(result.stdout));im.thumbnail((480,960));im.convert('RGB').save('docs/qa/BB4-agent-work-2026-10-08/restored-normal-2195.jpg',quality=65)
   lines.append('PASS normal_2195_small_restoration_screenshot_captured')
  except Exception as error:
   category=type(error).__name__ if type(error) in (subprocess.TimeoutExpired,AssertionError,OSError,ValueError) else 'unavailable'
   lines.append('OBSERVED normal_2195_screenshot_unavailable_'+category)
 with out.open('a') as f:
  for line in lines:f.write(line+'\n');print(line,flush=True)
raise SystemExit(code)

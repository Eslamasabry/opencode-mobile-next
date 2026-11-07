#!/usr/bin/env bash
# Creates the emulator for the upgrade journeys (role U, plan 14 §4.6): the AVD native/tool/qa/upgrade_e2e.sh boots
# on emulator-5584.
#
#   native/e2e/avd/create.sh [NAME]          NAME defaults to OCN_U
#
#   OC_AVD_ROOT   where the .avd folder goes (default: the avd home, $ANDROID_AVD_HOME or ~/.android/avd); the
#                 <NAME>.ini always lands in the avd home, so `emulator -avd NAME` and the gate find it
#   OC_AVD_MIN_FREE_GB  free space required where the AVD goes (default 20, plan 14 §4.6)
#   ANDROID_HOME  the SDK (default ~/Android/Sdk); needs cmdline-tools/latest and
#                 system-images;android-35;google_apis;x86_64
#
# Image: android-35 google_apis x86_64 (rootable, adb root works for the gate's state hooks). Not android-37.0: on
# that image every screenshot aborts SurfaceFlinger (hasReadColorBufferDma) and takes the package service down
# mid `adb install`, so Maestro flows cannot run (found by E00-S10, 2026-11-07).
# Size: 4 GB RAM, 8 GB data (Flutter 2.1.4 + built-in Linux + the 375 MB voice pack fit; 16 GB needed more free
# space than the PC had). The emulator wants 1.2x the data size free to create the partition, and the gate wants
# 10 GB more for its seeded snapshot.
# Never touches an AVD that exists: delete it yourself (`avdmanager delete avd -n NAME`) to remake it.
set -euo pipefail
name="${1:-OCN_U}"
[[ "$name" =~ ^OCN_[A-Z0-9_]+$ ]] || { echo "create.sh: AVD names start with OCN_ (flows may clear their data)" >&2; exit 2; }
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
avdmanager="$sdk/cmdline-tools/latest/bin/avdmanager"
image="system-images;android-35;google_apis;x86_64"
avd_home="${ANDROID_AVD_HOME:-${ANDROID_USER_HOME:-$HOME/.android}/avd}"
root="${OC_AVD_ROOT:-$avd_home}"
dir="$root/$name.avd"
need_gb="${OC_AVD_MIN_FREE_GB:-20}"  # plan 14 §4.6 floor; lower it only knowingly

[ -x "$avdmanager" ] || { echo "create.sh: no avdmanager at $avdmanager" >&2; exit 2; }
[ -d "$sdk/system-images/android-35/google_apis/x86_64" ] ||
  { echo "create.sh: install $image first (sdkmanager \"$image\")" >&2; exit 2; }
if [ -e "$avd_home/$name.ini" ] || [ -e "$dir" ]; then
  echo "create.sh: $name exists ($avd_home/$name.ini or $dir); not touching it" >&2; exit 2
fi
mkdir -p "$root"
free_gb=$(( $(df --output=avail -k "$root" | tail -1) / 1024 / 1024 ))
if [ "$free_gb" -lt "$need_gb" ]; then
  echo "create.sh: $root has ${free_gb} GB free; the AVD (about 10 GB) and the gate's snapshot need ${need_gb} GB" >&2
  exit 2
fi

echo no | "$avdmanager" create avd -n "$name" -k "$image" -d pixel_7 -p "$dir" >/dev/null
cfg="$dir/config.ini"
set_key() { # <key> <value>: replace or append one config.ini line
  if grep -q "^$1=" "$cfg"; then sed -i "s|^$1=.*|$1=$2|" "$cfg"; else echo "$1=$2" >> "$cfg"; fi
}
# avdmanager may write a temporary data partition path: the partition must persist for snapshots.
sed -i '/^disk.dataPartition.path=/d' "$cfg"
set_key hw.ramSize 4096
set_key disk.dataPartition.size 8G
set_key hw.gpu.enabled yes
set_key hw.gpu.mode swiftshader_indirect
set_key hw.keyboard no
set_key PlayStore.enabled no
echo "created $name at $dir (android-35 google_apis, 4 GB RAM, 8 GB data)"

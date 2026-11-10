#!/usr/bin/env bash
# Build source only. Does not sign, install, publish, or invoke CI.
set -euo pipefail
stage_android=false
case "${1:-}" in
  '') ;;
  --stage-android) stage_android=true ;;
  --help|-h) echo "Usage: $0 [--stage-android] (builds arm64-v8a and x86_64)"; exit 0 ;;
  *) echo "Usage: $0 [--stage-android]" >&2; exit 64 ;;
esac
[[ $# -le 1 ]] || { echo 'Unexpected build arguments' >&2; exit 64; }
repo_dir="$(cd "$(dirname "$0")/../../.." && pwd)"
ndk_dir="${ANDROID_NDK_HOME:-/home/eslam/Android/Sdk/ndk/28.2.13676358}"
artifact_dir="$(realpath -m "${OC_ENGINE_BUILD_DIR:-/home/eslam/Storage/tmp/aiteam-phone-engine-build}")"
case "$artifact_dir" in /tmp|/tmp/*) echo 'Build artifacts must use Storage, not /tmp' >&2; exit 64;; esac
mkdir -p "$artifact_dir/tmp" "$artifact_dir/target" "$artifact_dir/android"
export TMPDIR="$artifact_dir/tmp"
export CARGO_TARGET_DIR="$artifact_dir/target"
ndk_bin="$ndk_dir/toolchains/llvm/prebuilt/linux-x86_64/bin"
abis=(arm64-v8a x86_64)
targets=(aarch64-linux-android x86_64-linux-android)
for target in "${targets[@]}"; do
  [[ -x "$ndk_bin/${target}26-clang" ]] || { echo "Android NDK compiler unavailable for $target" >&2; exit 1; }
  rustup target list --installed | rg -q "^${target}$" || {
    echo "Install the Rust Android standard library: rustup target add $target" >&2; exit 1;
  }
done
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ndk_bin/aarch64-linux-android26-clang"
export CC_aarch64_linux_android="$ndk_bin/aarch64-linux-android26-clang"
export AR_aarch64_linux_android="$ndk_bin/llvm-ar"
export CXX_aarch64_linux_android="$ndk_bin/aarch64-linux-android26-clang++"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$ndk_bin/x86_64-linux-android26-clang"
export CC_x86_64_linux_android="$ndk_bin/x86_64-linux-android26-clang"
export AR_x86_64_linux_android="$ndk_bin/llvm-ar"
export CXX_x86_64_linux_android="$ndk_bin/x86_64-linux-android26-clang++"
source_digest() {
  python3 - "$repo_dir" <<'PY'
import hashlib,pathlib,sys
repo=pathlib.Path(sys.argv[1])
inputs=[repo/'engine/phone/Cargo.toml',repo/'engine/phone/Cargo.lock',*sorted((repo/'engine/phone/src').rglob('*.rs'))]
source=hashlib.sha256()
for path in inputs:
    source.update(str(path.relative_to(repo)).encode()+b'\0'+path.read_bytes()+b'\0')
print(source.hexdigest())
PY
}
source_before="$(source_digest)"
for index in "${!targets[@]}"; do
  target="${targets[$index]}"
  abi="${abis[$index]}"
  cargo build --manifest-path "$repo_dir/engine/phone/Cargo.toml" --locked --release --target "$target" \
    --bin oc-phone-engine --bin oc-engine-sandbox --bin oc-engine-boundary-probe
  mkdir -p "$artifact_dir/android/$abi"
  cp "$CARGO_TARGET_DIR/$target/release/oc-phone-engine" "$artifact_dir/android/$abi/libaiteam_engine.so"
  cp "$CARGO_TARGET_DIR/$target/release/oc-engine-sandbox" "$artifact_dir/android/$abi/libaiteam_sandbox.so"
  cp "$CARGO_TARGET_DIR/$target/release/oc-engine-boundary-probe" "$artifact_dir/android/$abi/libaiteam_boundary_probe.so"
done
[[ "$(source_digest)" == "$source_before" ]] || {
  echo 'Rust sources changed during the dual-ABI build; rebuild before staging.' >&2; exit 1;
}
python3 - "$repo_dir" "$artifact_dir" "$ndk_dir" "$source_before" <<'PY'
import hashlib,json,pathlib,struct,subprocess,sys
repo,artifacts,ndk=map(pathlib.Path,sys.argv[1:4])
abis={}
for abi,target,machine in [('arm64-v8a','aarch64-linux-android',183),('x86_64','x86_64-linux-android',62)]:
    files={}
    for name in ['libaiteam_engine.so','libaiteam_sandbox.so','libaiteam_boundary_probe.so']:
        data=(artifacts/'android'/abi/name).read_bytes()
        if data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H',data,18)[0] != machine:
            raise SystemExit(f'Unexpected ELF architecture: {abi}/{name}')
        files[name]=hashlib.sha256(data).hexdigest()
    abis[abi]={'target':target,'api':26,'sha256':files}
revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip()
dirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=repo,text=True).strip())
manifest={'schemaVersion':2,'sourceSha256':sys.argv[4],'sourceRevision':revision,'sourceDirty':dirty,'rustcVersion':subprocess.check_output(['rustc','--version'],text=True).strip(),'ndkVersion':(ndk/'source.properties').read_text().split('Pkg.Revision = ')[1].splitlines()[0],'abis':abis}
(artifacts/'android/manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Android native artifacts: '+str(artifacts/'android'))
PY
python3 "$repo_dir/engine/phone/tool/native-notices.py" "$artifact_dir/android/Rust-Phone-Engine.txt" "${targets[@]}"
if $stage_android; then
  for abi in "${abis[@]}"; do
    mkdir -p "$repo_dir/android/app/src/main/jniLibs/$abi"
    for name in libaiteam_engine.so libaiteam_sandbox.so libaiteam_boundary_probe.so; do
      cp "$artifact_dir/android/$abi/$name" "$repo_dir/android/app/src/main/jniLibs/$abi/$name"
    done
  done
  mkdir -p "$repo_dir/android/app/src/main/assets"
  cp "$artifact_dir/android/manifest.json" "$repo_dir/android/app/src/main/assets/aiteam-engine-manifest.json"
  cp "$artifact_dir/android/Rust-Phone-Engine.txt" "$repo_dir/LICENSES/Rust-Phone-Engine.txt"
  echo 'Staged both ABI bundles, manifest and notices locally; APK signing and delivery remain separate.'
fi

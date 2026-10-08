"""Compile an isolated native status driver without Gradle or an APK build.

Run from the repository root. No device access or account reads happen here.
All compilers use a shared machine slot, sequentially, with a 256 MiB JVM cap.
"""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--build-dir', required=True)
parser.add_argument('--profile-id', required=True)
args = parser.parse_args()
out = Path(args.build_dir).resolve()
out.mkdir(parents=True, exist_ok=True)
driver = Path(__file__).resolve().parent
sdk = Path(os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'Android/Sdk')))
android = sdk / 'platforms/android-37.0/android.jar'
dart = Path.home() / '.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart'
compiler = os.environ.get('KOTLINC', str(Path.home() / '.sdkman/candidates/kotlin/current/bin/kotlinc'))
env = dict(os.environ, JAVA_OPTS='-Xmx256m')


def run(command, name):
    with (out / (name + '.log')).open('wb') as log:
        subprocess.run(['tool/qa/machine_lock.sh', 'test', '--'] + list(map(str, command)),
                       env=env, stdout=log, stderr=log, check=True)


# Production auth and launch classes are loaded from the installed APK, not
# shadowed by freshly compiled source. Only this diagnostic entry point is built.
run([dart, 'run', driver / 'auth_script.dart', args.profile_id, out / 'probe.txt'], 'script')
run([compiler, driver / 'ProbeRunner.kt', '-cp', android,
     '-include-runtime', '-d', out / 'probe.jar'], 'kotlin')
run([sorted(sdk.glob('build-tools/*/d8'))[-1], '--min-api', '26', '--lib', android,
     '--output', out, out / 'probe.jar'], 'dex')
run([sdk / 'ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android26-clang',
     driver / 'context_runner.c', '-ldl', '-o', out / 'context_runner'], 'context')

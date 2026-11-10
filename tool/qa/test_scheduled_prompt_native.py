#!/usr/bin/env python3
"""Small JVM policy gate. Run via machine_lock.sh; no Gradle/emulator/network."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
KOTLIN = Path('/home/eslam/.sdkman/candidates/kotlin/2.3.20')
JDK = Path('/home/eslam/Storage/tmp/codex-audit3-temurin17/jdk-17.0.20.1+1')
CACHE = Path('/home/eslam/.gradle/caches/modules-2/files-2.1')
MAIN = ROOT / 'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile'
TEST = ROOT / 'android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile'


def artifact(relative):
    files = list((CACHE / relative).glob('*/*.jar'))
    if len(files) != 1:
        raise RuntimeError('Pinned JVM test dependency unavailable')
    return files[0]


def main():
    junit = artifact('junit/junit/4.13.2')
    hamcrest = artifact('org.hamcrest/hamcrest-core/1.3')
    json = artifact("org.json/json/20180813")
    android = Path("/home/eslam/Android/Sdk/platforms/android-37.0/android.jar")
    classpath = os.pathsep.join(map(str, (junit, hamcrest, json, android)))
    with tempfile.TemporaryDirectory(prefix='scheduled-prompt-jvm-') as directory:
        output = Path(directory) / 'tests.jar'
        subprocess.run([
            str(KOTLIN / 'bin/kotlinc'), '-J-Xmx256m', '-J-XX:MaxMetaspaceSize=192m',
            '-jvm-target', '17', '-cp', classpath, '-d', str(output),
            str(MAIN / 'ScheduledPromptPolicy.kt'), str(TEST / 'ScheduledPromptPolicyTest.kt'),
            str(MAIN / 'ScheduledPromptRecovery.kt'), str(TEST / 'ScheduledPromptRecoveryTest.kt'),
            str(MAIN / 'ScheduledPromptStore.kt'), str(TEST / 'ScheduledPromptStoreTest.kt'),
            str(MAIN / 'ScheduledPromptPhoneLease.kt'),
        ], cwd=ROOT, check=True)
        subprocess.run([
            str(JDK / 'bin/java'), '-Xmx128m', '-cp',
            os.pathsep.join((str(output), classpath, str(KOTLIN / 'lib/kotlin-stdlib.jar'))),
            'org.junit.runner.JUnitCore',
            'io.github.eslamasabry.opencode_mobile.ScheduledPromptPolicyTest',
            'io.github.eslamasabry.opencode_mobile.ScheduledPromptRecoveryTest',
            'io.github.eslamasabry.opencode_mobile.ScheduledPromptStoreTest',
        ], cwd=ROOT, check=True)


if __name__ == '__main__':
    main()

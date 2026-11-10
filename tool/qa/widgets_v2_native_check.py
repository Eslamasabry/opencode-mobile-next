#!/usr/bin/env python3
"""Offline widget resource/API compilation and pure JVM tests, without Gradle/Flutter.

Run under tool/qa/machine_lock.sh build. Requires cached Android API37, Kotlin,
JDK17, Flutter embedding, JSON and JUnit. MainActivity and unrelated notification
ownership are boundary stubs; the full service and headless engine compile. This does
NOT qualify service lifecycle, full-app compilation or launcher behavior.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
HOME = Path.home()
SDK = Path(os.environ.get('ANDROID_HOME', HOME / 'Android/Sdk'))
CACHE = HOME / '.gradle/caches/modules-2/files-2.1'
JDK = Path(os.environ.get('WIDGET_CHECK_JDK', '/home/eslam/Storage/tmp/codex-audit3-temurin17/jdk-17.0.20.1+1'))
KOTLIN = Path(os.environ.get('WIDGET_CHECK_KOTLIN', HOME / '.sdkman/candidates/kotlin/2.3.20'))
MAIN = ROOT / 'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile'
TEST = ROOT / 'android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile'
PACKAGE = 'io.github.eslamasabry.opencode_mobile'

def jar(pattern):
    found = sorted(CACHE.glob(pattern))
    if not found:
        raise SystemExit(f'Missing cached dependency: {pattern}')
    return found[-1]

def run(args):
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT,
                   env={**os.environ, 'JAVA_HOME': str(JDK)})

android = SDK / 'platforms/android-37.0/android.jar'
aapt = SDK / 'build-tools/36.1.0/aapt2'
json_jar = jar('org.json/json/20180813/*/*.jar')
junit = jar('junit/junit/4.13.2/*/*.jar')
hamcrest = jar('org.hamcrest/hamcrest-core/1.3/*/*.jar')
embedding = jar('io.flutter/flutter_embedding_release/1.0.0-03e67977a7ff5893d96ac97f22c6a795530c0040/*/*.jar')
lifecycle = jar('androidx.lifecycle/lifecycle-common-jvm/2.10.0/*/*.jar')
with tempfile.TemporaryDirectory(prefix='widgets-v2-') as temporary:
    work = Path(temporary)
    compiled = work / 'resources.zip'
    run([aapt, 'compile', '--dir', ROOT / 'android/app/src/main/res', '-o', compiled])
    # AppCompat is an unrelated app theme dependency; stub only its base style.
    styles = work / 'style_boundary/values'
    styles.mkdir(parents=True)
    (styles / 'styles.xml').write_text('<resources><style name="Theme.AppCompat.DayNight.NoActionBar" parent="android:style/Theme.Material.NoActionBar"/></resources>')
    run([aapt, 'compile', '--dir', styles.parent, '-o', work / 'style_boundary.zip'])
    manifest = work / 'AndroidManifest.xml'
    manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{PACKAGE}"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="37"/><application/></manifest>')
    run([aapt, 'link', '-I', android, '--manifest', manifest, '--java', work / 'java', '-o', work / 'resources.apk', compiled, work / 'style_boundary.zip'])
    classes = work / 'classes'
    classes.mkdir()
    run([JDK / 'bin/javac', '-d', classes, *sorted((work / 'java').rglob('*.java'))])
    stubs = work / 'Boundaries.kt'
    stubs.write_text('''package io.github.eslamasabry.opencode_mobile
import android.app.Activity
import android.app.Notification
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import io.flutter.plugin.common.MethodChannel
class MainActivity : Activity() { companion object {
 const val EXTRA_LAUNCH_ACTION = "launch"
 var backgroundChannel: MethodChannel? = null
} }
internal object StatusNotification {
 const val OWNER_LIVE = "live"
 const val ID = 1
 const val CHANNEL_ID = "status"
 fun ensureChannel(context: Context) {}
 fun begin(context: Context, owner: String): Notification = Notification()
 fun end(context: Context, owner: String): Boolean = true
 fun pausedByAndroid(context: Context) {}
 fun push(context: Context, content: StatusContent): Boolean = true
 fun refresh(context: Context): Boolean = true
}
class LivePauseReceiver { companion object {
 const val FLUTTER_PREFERENCES = "FlutterSharedPreferences"
 const val FLUTTER_PREFERENCE_KEEP_LIVE = "flutter.keepLive"
 fun setPausedByUser(context: Context, value: Boolean) {}
} }
class CodingActionReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {}
}
''')
    cp = os.pathsep.join(str(x) for x in [classes, json_jar, android, embedding, lifecycle, junit, hamcrest])
    names = ['SessionsWidgetProvider', 'UsageWidgetProvider', 'UsageWidgetFit', 'NativeStrings',
             'WidgetV2Policy', 'WidgetV2Style', 'WidgetV2Bridge', 'WidgetActionPolicy',
             'WidgetOverlayActivity', 'WidgetRouteActivity', 'BackgroundConnectionService',
             'WidgetActionRuntime', 'WidgetActionLease', 'StatusNotificationModel',
             'BackgroundPauseStore', 'BackgroundPauseReceipt']
    tests = ['WidgetV2PolicyTest', 'WidgetActionPolicyTest', 'UsageWidgetFitTest', 'WidgetSessionCardLayoutTest', 'WidgetActionLeaseTest']
    sources = [MAIN / f'{name}.kt' for name in names] + [TEST / f'{name}.kt' for name in tests]
    out = work / 'widgets.jar'
    run([KOTLIN / 'bin/kotlinc', '-jvm-target', '17', '-classpath', cp,
         *sources, stubs, '-include-runtime', '-d', out])
    run([JDK / 'bin/java', '-cp', os.pathsep.join([str(out), cp]), 'org.junit.runner.JUnitCore',
         *[f'{PACKAGE}.{name}' for name in tests]])
print('PASS: resource compile/link, focused widget Kotlin API compile, and pure JVM tests.')
print('Boundary stubs used for MainActivity/notifications; no Android runtime or APK qualification.')

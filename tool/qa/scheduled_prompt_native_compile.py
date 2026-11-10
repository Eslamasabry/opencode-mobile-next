#!/usr/bin/env python3
"""Bounded source compile smoke; app boundary stubs mean this is not APK proof.

Run via OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- python3 this_file.
No Gradle, network, signing, emulator, or host connection is used.
"""
from pathlib import Path
import os,subprocess,tempfile,zipfile
root=Path(__file__).resolve().parents[2]
cache=Path('/home/eslam/.gradle/caches/modules-2/files-2.1')
with tempfile.TemporaryDirectory(prefix='schedule-android-') as d:
 d=Path(d)
 aar=next((cache/'androidx.work/work-runtime/2.10.5').glob('*/*.aar'))
 with zipfile.ZipFile(aar) as z:(d/'work.jar').write_bytes(z.read('classes.jar'))
 flutter=Path('/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/cache/artifacts/engine/android-arm64-release/flutter.jar')
 cp=[Path('/home/eslam/Android/Sdk/platforms/android-37.0/android.jar'),flutter,d/'work.jar',next((cache/'com.google.guava/listenablefuture/1.0').glob('*/*.jar'))]
 stub=d/'Boundaries.kt'
 stub.write_text('''package io.github.eslamasabry.opencode_mobile
import android.content.Context
class MainActivity : android.app.Activity()
class BuiltinLinux {
 fun activeScheduledPhoneLease(): Boolean = false
 fun acquireScheduledPhoneServer(p:String,s:String,d:Long): Map<String,Any> = TODO()
 fun releaseScheduledPhoneServer(t:String) {}
 companion object { fun get(c:Context):BuiltinLinux = TODO() }
}
object NativeStrings { fun get(c:Context,id:Int,vararg args:Any):String = "" }
object R { object mipmap {const val ic_launcher=1}; object string {
const val native_schedule_channel=1; const val native_schedule_digest_title=2; const val native_schedule_title=3;
const val native_schedule_digest_body=4; const val native_schedule_ran=5; const val native_schedule_skipped=6; const val native_schedule_failed=7
}}
''')
 files=list((root/'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile').glob('ScheduledPrompt*.kt'))
 subprocess.run(['/home/eslam/.sdkman/candidates/kotlin/2.3.20/bin/kotlinc','-J-Xmx256m','-J-XX:MaxMetaspaceSize=192m','-jvm-target','17','-cp',os.pathsep.join(map(str,cp)),'-d',str(d/'out.jar'),str(stub),*map(str,files)],check=True)
 print('Scheduled Android source compile passed (existing app boundaries stubbed).')

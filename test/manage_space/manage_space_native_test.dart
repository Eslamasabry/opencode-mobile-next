import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final sdkman =
      '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
  final compiler =
      Platform.environment['KOTLINC'] ??
      (File(sdkman).existsSync() ? sdkman : 'kotlinc');
  bool available(String executable) => executable.contains('/')
      ? File(executable).existsSync()
      : (Platform.environment['PATH'] ?? '')
            .split(Platform.isWindows ? ';' : ':')
            .any((path) => File('$path/$executable').existsSync());
  final unavailable = !available(compiler) || !available('java');
  test(
    'exported manage-space activity discards caller Flutter engine and route',
    () async {
      final temp = await Directory.systemTemp.createTemp(
        'manage-space-native-',
      );
      addTearDown(() => temp.delete(recursive: true));
      final sources = <String, String>{
        'Intent.kt': '''
package android.content
class Intent(val owner: Any? = null, val target: Class<*>? = null) {
  val extras = mutableMapOf<String, Any>()
  var data: String? = null
}
''',
        'Bundle.kt': 'package android.os\nclass Bundle',
        'Engine.kt': '''
package io.flutter.embedding.engine
class FlutterEngine { val dartExecutor = Executor() }
class Executor { val binaryMessenger = Any() }
''',
        'Activity.kt': '''
package io.flutter.embedding.android
import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.engine.FlutterEngine
open class FlutterActivity {
  var intent = Intent()
  var delivered: Intent? = null
  open fun onCreate(savedInstanceState: Bundle?) { delivered = intent }
  open fun onNewIntent(intent: Intent) { delivered = intent }
  open fun getDartEntrypointFunctionName(): String = "main"
  open fun configureFlutterEngine(engine: FlutterEngine) {}
  open fun cleanUpFlutterEngine(engine: FlutterEngine) {}
  open fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {}
}
''',
        'Bridge.kt': '''
package io.github.eslamasabry.opencode_mobile
import android.content.Intent
class ProjectExportBridge(activity: Any, messenger: Any) {
  fun dispose() {}
  fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean = false
}
''',
        'Harness.kt': '''
import android.content.Intent
import io.github.eslamasabry.opencode_mobile.ManageSpaceActivity
fun hostile(): Intent = Intent().apply {
  extras["cached_engine_id"] = "main-engine"
  extras["route"] = "/untrusted"
  extras["dart_entrypoint_args"] = listOf("untrusted")
  data = "opencode://untrusted"
}
fun main() {
  val activity = ManageSpaceActivity()
  activity.intent = hostile()
  activity.onCreate(null)
  check(activity.delivered!!.extras.isEmpty()) { "caller extras reached Flutter" }
  check(activity.delivered!!.data == null) { "caller URL reached Flutter" }
  check(activity.getDartEntrypointFunctionName() == "manageSpaceMain")
  activity.onNewIntent(hostile())
  check(activity.delivered!!.extras.isEmpty()) { "new-intent extras reached Flutter" }
  check(activity.delivered!!.data == null) { "new-intent URL reached Flutter" }
  println("PASS manage-space intent isolation")
}
''',
      };
      for (final source in sources.entries) {
        await File('${temp.path}/${source.key}').writeAsString(source.value);
      }
      final jar = '${temp.path}/native.jar';
      final compiled = await Process.run(compiler, [
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ManageSpaceActivity.kt',
        ...sources.keys.map((name) => '${temp.path}/$name'),
        '-include-runtime',
        '-d',
        jar,
      ]);
      expect(compiled.exitCode, 0, reason: '${compiled.stderr}');
      if (compiled.exitCode != 0) return;
      final run = await Process.run('java', ['-jar', jar]);
      expect(run.exitCode, 0, reason: '${run.stderr}');
      expect(run.stdout, contains('PASS manage-space intent isolation'));
    },
    skip: unavailable
        ? 'Kotlin compiler or java unavailable; intent harness not run'
        : false,
  );
}

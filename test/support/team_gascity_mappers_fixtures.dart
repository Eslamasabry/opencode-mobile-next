import 'dart:convert';
import 'dart:io';

import 'package:opencode_mobile/orchestration/adapters/gascity/dto/dto.dart';

/// Repo-relative fixture root; `flutter test` runs from the package root.
final Directory fixtureRoot = findFixtureRoot();

Directory findFixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError(
    'tool/qa/gascity_fixture not found from ${Directory.current}',
  );
}

Map<String, Object?> readJson(File file) =>
    readMap(jsonDecode(file.readAsStringSync()));

Map<String, Object?> loadRecording(String name) =>
    readJson(File('${fixtureRoot.path}/recordings/$name.json'));

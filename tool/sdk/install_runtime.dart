import 'dart:io';

const _deprecatedMethods = <String, Map<String, String>>{
  'global_api.dart': {
    'globalEvent': 'Use OpencodeSdk.globalEventStream() for this SSE endpoint.',
  },
  'event_api.dart': {
    'eventSubscribe':
        'Use OpencodeSdk.eventSubscribeStream() for this SSE endpoint.',
  },
  'events_api.dart': {
    'v2EventSubscribe':
        'Use OpencodeSdk.v2EventSubscribeStream() for this SSE endpoint.',
  },
  'sessions_api.dart': {
    'v2SessionEvents':
        'Use OpencodeSdk.v2SessionEventsStream() for this SSE endpoint.',
  },
  'filesystem_api.dart': {
    'v2FsRead':
        'The generated wildcard route sends a literal *. Use OpencodeSdk.v2FsReadPath() instead.',
  },
};

Future<void> main(List<String> arguments) async {
  if (arguments.length != 2) {
    stderr.writeln(
      'Usage: dart run tool/sdk/install_runtime.dart <runtime> <package>',
    );
    exitCode = 64;
    return;
  }

  final runtime = Directory(arguments[0]);
  final package = Directory(arguments[1]);
  await for (final entity in runtime.list(
    recursive: true,
    followLinks: false,
  )) {
    if (entity is! File) continue;
    final relative = entity.path.substring(runtime.path.length + 1);
    final target = File('${package.path}/$relative');
    await target.parent.create(recursive: true);
    await target.writeAsBytes(await entity.readAsBytes(), flush: true);
  }

  final library = File('${package.path}/lib/opencode_sdk.dart');
  var librarySource = await library.readAsString();
  const exports = [
    "export 'package:opencode_sdk/src/http/errors.dart';",
    "export 'package:opencode_sdk/src/http/filesystem.dart';",
    "export 'package:opencode_sdk/src/sse/event_streams.dart';",
  ];
  for (final export in exports) {
    if (!librarySource.contains(export)) {
      librarySource = '$librarySource\n$export\n';
    }
  }
  if (librarySource != await library.readAsString()) {
    await library.writeAsString(librarySource, flush: true);
  }

  for (final fileEntry in _deprecatedMethods.entries) {
    final file = File('${package.path}/lib/src/api/${fileEntry.key}');
    var source = await file.readAsString();
    for (final methodEntry in fileEntry.value.entries) {
      final existingAnnotation = RegExp(
        r"@Deprecated\('[^']+'\)\s+Future<Response<[^>]+>>\s+" +
            RegExp.escape(methodEntry.key) +
            r'\(',
      );
      if (existingAnnotation.hasMatch(source)) continue;
      final declaration = RegExp(
        r'(^  Future<Response<[^>]+>>\s+' +
            RegExp.escape(methodEntry.key) +
            r'\()',
        multiLine: true,
      );
      if (!declaration.hasMatch(source)) {
        throw StateError('Could not find ${methodEntry.key} in ${file.path}.');
      }
      source = source.replaceFirstMapped(
        declaration,
        (match) => "  @Deprecated('${methodEntry.value}')\n${match[1]}",
      );
    }
    await file.writeAsString(source, flush: true);
  }
}

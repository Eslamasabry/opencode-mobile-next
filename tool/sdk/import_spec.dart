import 'dart:io';

Future<void> main(List<String> arguments) async {
  if (arguments.length != 2) {
    stderr.writeln(
      'Usage: dart run tool/sdk/import_spec.dart <source> <target>',
    );
    exitCode = 64;
    return;
  }

  final source = File(arguments[0]);
  if (!await source.exists()) {
    stderr.writeln('OpenAPI source does not exist: ${source.path}');
    exitCode = 66;
    return;
  }

  final target = File(arguments[1]);
  await target.parent.create(recursive: true);
  await target.writeAsBytes(await source.readAsBytes(), flush: true);
}

import 'dart:io';

Future<void> main(List<String> arguments) async {
  if (arguments.length != 1) {
    stderr.writeln('Usage: dart run clean_generated_markdown.dart <file>');
    exitCode = 64;
    return;
  }

  final file = File(arguments.single);
  final lines = (await file.readAsString())
      .replaceAll(RegExp(r'[ \t]+$', multiLine: true), '')
      .split('\n');
  while (lines.isNotEmpty && lines.last.isEmpty) {
    lines.removeLast();
  }
  await file.writeAsString('${lines.join('\n')}\n');
}

// End-to-end prompt test: sends a real prompt and waits for streamed parts.
// Requires the target opencode server to have a working model configured.
import 'dart:async';
import 'dart:io';

import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';

Future<void> main(List<String> args) async {
  final baseUrl = args.isNotEmpty ? args[0] : 'http://127.0.0.1:4123';
  final api = OpenCodeApi(baseUrl: baseUrl);

  final health = await api.health();
  stdout.writeln('server v${health.version}');

  final s = await api.createSession();
  var sawUserMsg = false;
  var sawAssistantPart = false;
  final assistantMsgIds = <String>{};
  var textReceived = StringBuffer();
  Object? errorSeen;

  final done = Completer<void>();
  final stream = EventStream(
    api: api,
    onStatus: (st) => stdout.writeln('[stream] $st'),
    onEvent: (env) {
      switch (env.type) {
        case 'message.updated':
          final info = env.properties['info'];
          if (info is Map<String, dynamic>) {
            final m = MessageInfo.fromJson(info);
            if (m.role == 'user') sawUserMsg = true;
            if (m.role == 'assistant') assistantMsgIds.add(m.id);
            if (m.errorText != null) {
              errorSeen = m.errorText;
              if (!done.isCompleted) done.complete();
            }
          }
          break;
        case 'message.part.updated':
          final pj = env.properties['part'];
          if (pj is Map<String, dynamic>) {
            final p = Part.fromJson(pj);
            if (p.messageID != null && !assistantMsgIds.contains(p.messageID)) {
              // Part for a message we haven't seen yet - still may be assistant.
              // Only count once confirmed, but buffer nothing.
            }
            if (p.type == 'text' &&
                p.text.isNotEmpty &&
                (p.messageID == null || assistantMsgIds.contains(p.messageID))) {
              sawAssistantPart = true;
              textReceived.write(p.text);
              if (!done.isCompleted && textReceived.length > 5) done.complete();
            }
          }
          break;
        case 'session.error':
          errorSeen = env.properties['error']?.toString();
          if (!done.isCompleted) done.complete();
          break;
      }
    },
    onError: (e) {},
  )..start();

  await Future<void>.delayed(const Duration(seconds: 2));
  await api.promptAsync(s.id, text: 'Reply with exactly: hello from opencode');
  stdout.writeln('prompt sent, waiting for stream…');

  await done.future.timeout(const Duration(minutes: 3), onTimeout: () {});

  await stream.dispose();
  await api.abort(s.id).catchError((_) => false as dynamic);
  await api.deleteSession(s.id).catchError((_) => false as dynamic);

  stdout.writeln('user message seen:      $sawUserMsg');
  stdout.writeln('assistant parts seen:    $sawAssistantPart');
  stdout.writeln('text received:           "${textReceived.toString().trim()}"');
  if (errorSeen != null) stdout.writeln('error from server:       $errorSeen');

  final pass = sawUserMsg && sawAssistantPart;
  stdout.writeln(pass ? '\nPROMPT FLOW PASSED' : '\nPROMPT FLOW INCOMPLETE');
  exit(pass ? 0 : 1);
}

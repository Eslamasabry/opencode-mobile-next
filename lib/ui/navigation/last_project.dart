import 'dart:async';

import '../../domain/chat_feed.dart';
import '../../state/connection.dart';

/// The shell's door to the chat-feed contract on the connection.
///
/// `ConnectionController` implements [ChatFeedSource]; until a build carries
/// that, every call here quietly does nothing, so the shell never depends on
/// the state work having landed.
ChatFeedSource? _source(ConnectionController controller) {
  final Object candidate = controller;
  return candidate is ChatFeedSource ? candidate : null;
}

/// The project the person last started or opened a chat in, or null on first
/// use. Never a temporary folder.
String? lastUsedProjectOf(ConnectionController controller) {
  final directory = _source(controller)?.lastUsedProjectDirectory;
  return isTemporaryProjectDirectory(directory) ? null : directory;
}

/// Records [directory] as the last-used project (ignored when temporary).
void rememberLastUsedProject(
  ConnectionController controller,
  String? directory,
) {
  if (directory == null || isTemporaryProjectDirectory(directory)) return;
  final source = _source(controller);
  if (source == null) return;
  unawaited(source.rememberLastUsedProject(directory));
}

/// Whether [directory] may be listed as a project anywhere in the shell.
bool isListableProject(String? directory) =>
    !isTemporaryProjectDirectory(directory);

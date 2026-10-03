import 'dart:async';

import '../../domain/chat_feed.dart';
import '../../state/connection.dart';

/// The shell's small doors to the chat-feed contract on the connection.

/// The project the person last started or opened a chat in, or null on first
/// use. Never a temporary folder.
String? lastUsedProjectOf(ConnectionController controller) =>
    chatFeedSourceOf(controller).lastUsedProjectDirectory;

/// Records [directory] as the last-used project (ignored when temporary).
void rememberLastUsedProject(
  ConnectionController controller,
  String? directory,
) {
  if (directory == null || isTemporaryProjectDirectory(directory)) return;
  unawaited(chatFeedSourceOf(controller).rememberLastUsedProject(directory));
}

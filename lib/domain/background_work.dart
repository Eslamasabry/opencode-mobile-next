import '../api/models.dart';
import 'server_gateway.dart';

/// Promotion belongs to the active, blocking tool call, not every busy
/// child session. In particular, v1 Bash, shell and PTY work is ineligible.
List<Part> foregroundBackgroundableParts(
  Iterable<MessageWithParts> messages,
  BackgroundWorkSupport support,
) {
  if (support == BackgroundWorkSupport.unavailable) return const [];
  return [
    for (final message in messages)
      if (message.info.role == 'assistant' &&
          message.info.time?.completed == null)
        for (final part in message.parts)
          if (part.type == 'tool' &&
              part.toolState.status == 'running' &&
              part.toolState.executed &&
              part.toolState.metadata?['background'] != true &&
              part.toolState.input['background'] != true &&
              (part.toolName == 'task' ||
                  (support == BackgroundWorkSupport.subagentsAndShells &&
                      part.toolName == 'shell')))
            part,
  ];
}

import '../domain/models.dart';

/// Live state that has outlived what it described, cleared before the
/// transcript draws it. Servers do not always send the closing event: Claude
/// Code via Paseo reports "compacting" as its own timeline item and the
/// finished compaction as another, and a tool call can be left reported as
/// running while the agent has long moved on.
///
/// - A running compaction line is hidden once anything follows it or the
///   conversation is no longer busy: the notice that it finished is the
///   record. Its message stays in place with the kind [compactionPassed],
///   so the answer has one message per message given: the transcript reads
///   rows, parts and turn owners by the same index (a dropped one shifted
///   every later index, and the newest row read past the end: "This part of
///   OpenCode hit an error" under a compaction mid-turn, 2026-10-28).
/// - A tool call still running is shown finished when the same turn wrote
///   words after it, or the conversation is no longer busy, unless the agent
///   marks it as a background process (`background: true`).
///
/// Returns [messages] itself when nothing needed settling, so a caller may
/// keep the answer and get the same objects on every build.
///
/// [moreFollows]: [messages] is the start of a longer conversation (the
/// rest begins with a prompt), so its last message is not the newest.
/// Settling the two stretches apart gives the same answer as the whole.
List<MessageWithParts> settleStaleLive(
  List<MessageWithParts> messages, {
  required bool busy,
  bool moreFollows = false,
}) {
  final replace = <int, MessageWithParts>{};

  bool isPrompt(MessageWithParts m) =>
      m.info.role == 'user' && !m.parts.any((p) => p.type.startsWith('v2:'));

  // Words written later in the same turn, walking from the end.
  var wordsLater = false;
  for (var i = messages.length - 1; i >= 0; i--) {
    final message = messages[i];
    if (isPrompt(message)) {
      wordsLater = false;
      continue;
    }
    final compaction = message.parts.any(
      (p) => p.type == 'v2:compaction' && p.toolName == 'running',
    );
    if (compaction && (!busy || moreFollows || i < messages.length - 1)) {
      replace[i] = MessageWithParts(
        info: message.info,
        parts: [
          for (final p in message.parts)
            p.type == 'v2:compaction' && p.toolName == 'running'
                ? Part(
                    id: p.id,
                    type: p.type,
                    text: p.text,
                    messageID: p.messageID,
                    toolName: compactionPassed,
                    synthetic: p.synthetic,
                    noticeMetadata: p.noticeMetadata,
                  )
                : p,
        ],
      );
      continue;
    }
    if (message.info.role != 'assistant') continue;
    List<Part>? parts;
    for (var p = message.parts.length - 1; p >= 0; p--) {
      final part = message.parts[p];
      if (part.type == 'text' && part.text.trim().isNotEmpty) {
        wordsLater = true;
      } else if (part.type == 'tool' &&
          (part.toolState.status == 'running' ||
              part.toolState.status == 'pending') &&
          part.toolState.executed &&
          (wordsLater || !busy) &&
          !_background(part)) {
        parts ??= [...message.parts];
        parts[p] = _finished(part);
      }
    }
    if (parts != null) {
      replace[i] = MessageWithParts(info: message.info, parts: parts);
    }
  }
  if (replace.isEmpty) return messages;
  return [for (var i = 0; i < messages.length; i++) replace[i] ?? messages[i]];
}

/// The kind a running compaction line takes once its moment has passed: it
/// draws nothing.
const compactionPassed = 'passed';

bool _background(Part part) =>
    part.toolState.metadata?['background'] == true ||
    part.toolState.input['background'] == true;

Part _finished(Part part) {
  final old = part.toolState;
  return Part(
    id: part.id,
    type: part.type,
    text: part.text,
    messageID: part.messageID,
    callID: part.callID,
    toolName: part.toolName,
    toolState: ToolState(
      status: 'completed',
      title: old.title,
      input: old.input,
      inputJson: old.inputJson,
      output: old.output,
      outputValue: old.outputValue,
      metadata: old.metadata,
      outputFiles: old.outputFiles,
      segments: old.segments,
      startedAt: old.startedAt,
      completedAt: old.completedAt,
      pruned: old.pruned,
      executed: old.executed,
    ),
    mime: part.mime,
    filename: part.filename,
    url: part.url,
    synthetic: part.synthetic,
    noticeMetadata: part.noticeMetadata,
  );
}

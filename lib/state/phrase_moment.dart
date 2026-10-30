import '../domain/models.dart';
import 'phrase_chips.dart';

/// What the chat's state says about the moment, for the phrase chips.
class PhraseMomentState {
  const PhraseMomentState(
    this.moment, {
    this.continueShown = false,
    this.errorShown = false,
  });

  /// Null: no chips (an empty new chat).
  final PhraseMoment? moment;

  /// The transcript already shows its own Continue button for a reply cut off
  /// by length, so the chip row does not repeat "Continue".
  final bool continueShown;

  /// The turn ended in an error notice with its own way on (another model,
  /// sign in, Send again): no row of phrases stacked under it.
  final bool errorShown;
}

const _editTools = {'edit', 'write', 'multiedit', 'patch', 'apply_patch'};

/// Works out the moment of a chat from what it already holds; no server
/// calls. [messages] is the loaded transcript, oldest first.
///
/// * [busy]: the agent is working (busy by the server's word, or this phone's
///   own send in flight).
/// * [stoppedByPerson]: the person stopped the newest turn from this phone.
/// * [offline]: the transcript's Continue button is not offered offline.
PhraseMomentState derivePhraseMoment({
  required List<MessageWithParts> messages,
  required bool busy,
  bool stoppedByPerson = false,
  bool offline = false,
  DateTime? now,
  Duration interruptedAfter = const Duration(seconds: 30),
}) {
  if (busy) return const PhraseMomentState(PhraseMoment.working);
  final real = [
    for (final message in messages)
      if (!message.info.id.startsWith('local-')) message,
  ];
  if (real.isEmpty) return const PhraseMomentState(null);
  var lastUser = real.lastIndexWhere((message) => message.info.role == 'user');
  final turn = real.sublist(lastUser < 0 ? 0 : lastUser + 1);
  final assistants = [
    for (final message in turn)
      if (message.info.role == 'assistant') message,
  ];
  if (assistants.isEmpty) {
    // Prompt sent, nothing came back, nothing is running.
    return PhraseMomentState(
      stoppedByPerson ? PhraseMoment.stoppedPartway : PhraseMoment.idle,
    );
  }
  final last = assistants.last.info;
  final kind = last.errorText == null
      ? null
      : MessageErrorKind.refineFromText(
          last.errorKind ?? MessageErrorKind.unknown,
          last.errorText!,
        );
  final cutByLength =
      kind == MessageErrorKind.outputLength ||
      (last.finish == 'length' && last.errorText == null);
  final nowMs = (now ?? DateTime.now()).millisecondsSinceEpoch;
  final created = last.time?.created;
  final interrupted =
      last.errorText == null &&
      last.time != null &&
      !last.time!.isDone &&
      created != null &&
      nowMs - created > interruptedAfter.inMilliseconds;
  if (cutByLength || kind != null || stoppedByPerson || interrupted) {
    return PhraseMomentState(
      PhraseMoment.stoppedPartway,
      continueShown: kind == MessageErrorKind.outputLength && !offline,
      // A stop is not an error, and a reply cut by its length keeps the
      // row's Continue: every other error shows its own notice.
      errorShown:
          kind != null &&
          kind != MessageErrorKind.outputLength &&
          kind != MessageErrorKind.aborted &&
          !stoppedByPerson,
    );
  }
  final edited = [for (final message in assistants) ...message.parts].any(
    (part) =>
        part.type == 'tool' &&
        _editTools.contains(part.toolName) &&
        part.toolState.status == 'completed',
  );
  return PhraseMomentState(
    edited ? PhraseMoment.finishedWithChanges : PhraseMoment.idle,
  );
}

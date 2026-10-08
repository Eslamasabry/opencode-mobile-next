part of '../chat_screen.dart';

// The transcript's turn model (STATE-16, KIT-41): what a server message is
// (a prompt, a step, a notice), which turn it belongs to, how a turn's
// parts fold under one work line, and each message's meta line.

/// Finds the mapper's v2-only variant tag on a message, if any: a part whose
/// `type` starts with `v2:` (see `mapApi2Message`). v1 servers never emit
/// these, and `Part.isRenderable` is false for them, so the v1 rendering
/// path is untouched.
@visibleForTesting
Part? v2VariantPart(MessageWithParts message) {
  for (final part in message.parts) {
    if (part.type.startsWith('v2:')) return part;
  }
  return null;
}

// The transcript's vocabulary (STANDARDS STATE-16 is its frozen form). The
// server stores a conversation as a flat list of messages, and that list is
// not how a person reads it:
//
//  * A **prompt** is what you sent: your words and attachments.
//  * A **turn** is one prompt and everything the agent did about it, until it
//    stopped and handed back to you. It is the unit a reader thinks in, and
//    the unit that gets one footer (model, usage, the "more" control).
//  * A **step** is one model call inside a turn, which the server stores as
//    one assistant message: a thought, perhaps a sentence, some tool calls.
//    A long turn is dozens of them. The boundary between two steps is
//    plumbing and is never drawn.
//  * A **notice** is something that happened during a turn and that nobody
//    typed: the project moved, instructions changed, context was added. The
//    server files it under the `user` role; it does not end the turn.
//  * The **reply** is the prose of a whole turn, which is what "copy" copies.
//
// The list is virtualised, one server message per row, so one turn spans
// several rows: each row draws its part of the turn as a KitTurn segment
// (first: the prompt; middle: a step; last: the step that ends the turn and
// carries the footer).

/// Whether [message] is something a person wrote, as opposed to a notice the
/// server filed under the same role.
bool _isPrompt(MessageWithParts message) =>
    message.info.role == 'user' && v2VariantPart(message) == null;

/// Whether the assistant message at [index] is the last step of its turn:
/// nothing but notices stands between it and the next prompt, or the end.
bool _endsTurn(List<MessageWithParts> messages, int index) {
  if (messages[index].info.role != 'assistant') return false;
  for (var next = index + 1; next < messages.length; next += 1) {
    if (_isPrompt(messages[next])) return true;
    if (messages[next].info.role == 'assistant') return false;
  }
  return true;
}

/// The newest turn when it got no answer, as (prompt index, index of the
/// step that ends it, or null when no step came, and whether it ended
/// silently). Not silent: it ended on an error the agent did not get past
/// and no step wrote any words, or [refused] (the server refused the prompt
/// before any step, a session error). Silent: it ended with no words, no
/// steps and no error at all, so the person is told "No reply came back"
/// rather than left with a bare prompt. Null when the newest turn was
/// answered, stopped (an abort, or [stoppedPromptID]), or is still running
/// ([running]).
(int, int?, bool)? _unansweredTurn(
  List<MessageWithParts> messages, {
  required bool refused,
  required bool running,
  String? stoppedPromptID,
}) {
  if (running) return null;
  final prompt = messages.lastIndexWhere(_isPrompt);
  if (prompt < 0) return null;
  int? last;
  var stepped = false;
  for (var index = prompt + 1; index < messages.length; index += 1) {
    final message = messages[index];
    // A command that answers with the conversation compacted did answer.
    if (v2VariantPart(message)?.type == 'v2:compaction') return null;
    if (message.info.role != 'assistant') continue;
    last = index;
    final said = message.parts.any(
      (part) => part.type == 'text' && part.text.trim().isNotEmpty,
    );
    if (said) return null;
    stepped = stepped || message.parts.any((part) => part.type == 'tool');
  }
  final stopped = messages[prompt].info.id == stoppedPromptID;
  // A command (`/compact`) may do its work without a word back.
  final command = messages[prompt].parts.any(
    (part) => part.type == 'text' && part.text.trimLeft().startsWith('/'),
  );
  if (last == null) {
    if (refused) return (prompt, null, false);
    return stopped || command ? null : (prompt, null, true);
  }
  final info = messages[last].info;
  final raw = info.errorText;
  if (raw == null) {
    // Steps that ran and ended quietly did answer; so did a turn stopped
    // on purpose. A step still unfinished is not over.
    if (stepped || stopped || info.time?.isDone != true) return null;
    return (prompt, last, true);
  }
  final kind = MessageErrorKind.refineFromText(
    info.errorKind ?? MessageErrorKind.unknown,
    raw,
  );
  if (kind == MessageErrorKind.aborted) return null;
  return (prompt, last, false);
}

/// The row that ends a turn the person stopped from this phone
/// ([stoppedPromptID]), which then carries the turn's "You stopped this
/// reply." line: the row that carries the turn's footer ([owners], see
/// [_turnActionOwners]), or its prompt when nothing of the reply drew.
/// Servers differ in what a stop leaves behind: OpenCode 1 marks the last
/// step "aborted" (that row says it on its own), while Claude Code via Paseo
/// and OpenCode 2's interrupt just end the turn, and a stop before the first
/// word leaves no step at all. Null while the turn still runs ([running]) or
/// when nothing was stopped here.
int? _stoppedTurnRow(
  List<MessageWithParts> messages,
  Set<int> owners, {
  required String? stoppedPromptID,
  required bool running,
}) {
  if (running || stoppedPromptID == null) return null;
  var prompt = messages.indexWhere(
    (message) => message.info.id == stoppedPromptID,
  );
  // Stopped while the prompt was still this phone's own copy, which the
  // server's has since replaced: every send from here clears the stop, so
  // the newest prompt is that copy.
  if (prompt < 0 && stoppedPromptID.startsWith('local-')) {
    prompt = messages.lastIndexWhere(_isPrompt);
  }
  if (prompt < 0 || !_isPrompt(messages[prompt])) return null;
  var row = prompt;
  for (var index = prompt + 1; index < messages.length; index += 1) {
    if (_isPrompt(messages[index])) break;
    if (owners.contains(index)) row = index;
  }
  return row;
}

/// The words of a prompt, when that is all it is: null when it carried
/// files, which a resend from here could not bring along.
String? _promptWordsOnly(MessageWithParts prompt) {
  if (prompt.parts.any((part) => part.type == 'file')) return null;
  final text = prompt.parts
      .where((part) => part.type == 'text')
      .map((part) => part.text)
      .where((value) => value.trim().isNotEmpty)
      .join('\n');
  return text.trim().isEmpty ? null : text;
}

/// For each turn, the one message that carries its "more" control: the step
/// that ends the turn when that step draws anything, otherwise the last step
/// that does. A turn often ends on pure bookkeeping (a `step-finish`), and
/// the control must not vanish with it.
Set<int> _turnActionOwners(
  List<MessageWithParts> messages,
  List<List<Part>> display, {
  required bool metaAlways,
  bool running = false,
  Set<String> ignore = const {},
}) {
  final owners = <int>{};
  int? lastStep;
  int? lastWithBody;
  bool hasBody(int index) =>
      display[index].any((part) => part.isRenderable) ||
      messages[index].info.errorText != null ||
      messages[index].info.finish == 'length';
  void closeTurn({bool last = false}) {
    final end = lastStep;
    // The turn in progress has no footer yet: its control would sit under
    // whatever step happens to be newest and jump down with every new one.
    // It arrives when the turn ends; long-press works meanwhile.
    if (end != null && !(last && running)) {
      final endDraws =
          hasBody(end) ||
          metaAlways ||
          _messageMeta(messages, end).modelLabel != null;
      final owner = endDraws ? end : lastWithBody;
      if (owner != null) owners.add(owner);
    }
    lastStep = null;
    lastWithBody = null;
  }

  for (var index = 0; index < messages.length; index += 1) {
    // Not drawn, so neither a prompt that ends a turn nor a step of one.
    if (ignore.contains(messages[index].info.id)) continue;
    if (_isPrompt(messages[index])) {
      closeTurn();
    } else if (messages[index].info.role == 'assistant') {
      lastStep = index;
      if (hasBody(index)) lastWithBody = index;
    }
  }
  closeTurn(last: true);
  return owners;
}

/// The assistant messages of the turn that holds [index], oldest first.
/// Notices inside the turn are skipped, not treated as its edge.
List<MessageWithParts> _turnSteps(List<MessageWithParts> messages, int index) {
  var start = index;
  while (start > 0 && !_isPrompt(messages[start - 1])) {
    start -= 1;
  }
  var end = index;
  while (end + 1 < messages.length && !_isPrompt(messages[end + 1])) {
    end += 1;
  }
  return [
    for (var i = start; i <= end; i += 1)
      if (messages[i].info.role == 'assistant') messages[i],
  ];
}

/// A notice's own first line, when it is short enough to be a title. "Server
/// message" says nothing; the message usually says what it is about.
String? _noticeTitle(String text) {
  final first = text.trim().split('\n').first.trim();
  final plain = first.replaceAll(RegExp(r'[*_`#]+'), '').trim();
  return plain.isEmpty || plain.length > 60 ? null : plain;
}

String _noticeRest(String text) {
  final trimmed = text.trim();
  final breakAt = trimmed.indexOf('\n');
  return breakAt < 0 ? '' : trimmed.substring(breakAt).trim();
}

/// Agent or person Markdown that no one selects or acts on inside it: a
/// prompt bubble (its long-press is the prompt's menu), a thought, a notice.
/// A reply's prose goes through [MarkdownText], which adds the agent blocks
/// (```choices```), file links and the code reader.
KitMarkdown _proseMarkdown(
  BuildContext context,
  String text, {
  KitTextRole role = KitTextRole.body,
}) => KitMarkdown(
  text,
  role: role,
  tone: role == KitTextRole.secondary ? KitTextTone.secondary : null,
  selectable: false,
  interactive: MarkdownInteractionScope.enabledOf(context),
  highlighter: TranscriptHighlight.decorate,
);

/// What the work of [tools] did, counted for the work line's words.
KitWorkCounts _workCounts(Iterable<Part> tools, {required int steps}) {
  var read = 0;
  var searched = 0;
  var listed = 0;
  var edited = 0;
  var ran = 0;
  var fetched = 0;
  var delegated = 0;
  var other = 0;
  var notRun = 0;
  for (final part in tools) {
    if (!part.toolState.executed) {
      notRun++;
      continue;
    }
    switch (part.toolName?.trim().toLowerCase() ?? '') {
      case 'read':
        read++;
      case 'glob' || 'grep':
        searched++;
      case 'list':
        listed++;
      case 'edit' || 'write' || 'patch' || 'apply_patch' || 'multiedit':
        edited++;
      case 'bash' || 'shell':
        ran++;
      case 'webfetch' || 'websearch':
        fetched++;
      case 'task' || 'subagent':
        delegated++;
      default:
        other++;
    }
  }
  return KitWorkCounts(
    read: read,
    searched: searched,
    listed: listed,
    edited: edited,
    ran: ran,
    fetched: fetched,
    delegated: delegated,
    other: other,
    notRun: notRun,
    steps: steps,
  );
}

bool _isToolPart(Part part) => part.type == 'tool';

/// What a finished turn did on its way to the answer: the passing words the
/// agent said between steps ("Now let me test each command:") and the
/// notices the server filed mid-turn (a background command finishing). Once
/// the turn is over they belong with the work, folded under its line.
///
/// Only passing words fold (decision 2026-09-28, review board "chat", gap
/// 17): an explanation the agent wrote before its last step ("The
/// flakiness comes from CheckoutBloc…") is part of the answer and stays in
/// view, below the turn's one work line. The work folds; the prose never
/// hides.
final _foldedIntoWork = Expando<bool>('folded into work');

/// Whether [text], said between steps, is passing words ("Looking into
/// it.", "Now let me run the tests:") rather than an explanation: one short
/// line, or one line that leads into the next step with a colon. A second
/// line, a paragraph break or a code block makes it an explanation.
bool _isPassingWords(String text) {
  final words = text.trim();
  if (words.isEmpty) return true;
  if (words.contains('\n') || words.contains('```')) return false;
  return words.length <= 80 || (words.endsWith(':') && words.length <= 200);
}

bool _isFoldedIntoWork(Object item) => _foldedIntoWork[item] ?? false;

/// A notice [_isFoldedIntoWork] took into the work line. It draws nothing of
/// its own in the transcript.
bool _isFoldedNotice(MessageWithParts message) => _isFoldedIntoWork(message);

bool _foldableNotice(MessageWithParts message) {
  final part = v2VariantPart(message);
  return part != null &&
      part.type == 'v2:notice' &&
      BackgroundAgentResult.fromPart(part) == null;
}

/// Marks, for each finished turn, what folds into its work. The turn still
/// running ([liveTail]) keeps everything in view: what the agent says while it
/// works is how the reader follows along. Every mark is written each time,
/// so a turn that is continued is unfolded again.
void _markFoldedWork(List<MessageWithParts> messages, {bool liveTail = false}) {
  var start = 0;
  void closeTurn(int end, {required bool live}) {
    // The turn in reading order: each entry is a part, or a notice message.
    final entries = <Object>[];
    var seenAssistant = false;
    for (var i = start; i < end; i += 1) {
      final message = messages[i];
      _foldedIntoWork[message] = false;
      if (message.info.role == 'assistant') {
        seenAssistant = true;
        for (final part in message.parts.where((part) => part.isRenderable)) {
          _foldedIntoWork[part] = false;
          entries.add(part);
        }
      } else if (seenAssistant && _foldableNotice(message)) {
        entries.add(message);
      }
    }
    if (live) return;
    bool isWork(Object entry) =>
        entry is Part && (entry.type == 'tool' || entry.type == 'reasoning');
    bool isText(Object entry) => entry is Part && entry.type == 'text';
    final lastWork = entries.lastIndexWhere(isWork);
    if (lastWork < 0) return;
    // Text before the last work is on the way; but a turn that ended on a
    // tool call still said something last, and that stays the answer.
    var cutoff = lastWork;
    if (!entries.skip(lastWork + 1).any(isText)) {
      cutoff = entries.lastIndexWhere(isText);
      while (cutoff > 0 && isText(entries[cutoff - 1])) {
        cutoff -= 1;
      }
    }
    for (var i = 0; i < entries.length; i += 1) {
      // Text folds only on the way to the answer, and only passing words;
      // a notice (a background command finishing) is part of the work
      // wherever it landed.
      final entry = entries[i];
      if ((entry is Part &&
              isText(entry) &&
              i < cutoff &&
              _isPassingWords(entry.text)) ||
          entry is MessageWithParts) {
        _foldedIntoWork[entry] = true;
      }
    }
  }

  for (var index = 0; index < messages.length; index += 1) {
    if (!_isPrompt(messages[index])) continue;
    closeTurn(index, live: false);
    start = index + 1;
  }
  closeTurn(messages.length, live: liveTail);
}

List<List<Part>> _timelineDisplayParts(
  List<MessageWithParts> messages, {
  bool liveTail = false,
}) {
  _markFoldedWork(messages, liveTail: liveTail);
  final display = List.generate(messages.length, (_) => <Part>[]);
  final pendingParts = <Part>[];
  String? pendingType;
  int? pendingOwner;
  int? lastAssistant;
  // The turn still running keeps its work where it happened, between the
  // words, so the reader can follow along; a finished turn gathers its
  // work into one line (the first stretch's place) with the explanation it
  // kept in view after it (see [_foldedIntoWork]).
  final liveFrom = liveTail ? messages.lastIndexWhere(_isPrompt) + 1 : null;
  int? poolOwner;
  var poolEnd = 0;

  void flushPending() {
    if (pendingOwner case final owner?) {
      if (pendingType == 'text') {
        display[owner].add(_mergeTextParts(pendingParts));
      } else if (poolOwner case final pool?) {
        display[pool].insertAll(poolEnd, pendingParts);
        poolEnd += pendingParts.length;
      } else {
        display[owner].addAll(pendingParts);
        if (liveFrom == null || owner < liveFrom) {
          poolOwner = owner;
          poolEnd = display[owner].length;
        }
      }
    }
    pendingParts.clear();
    pendingType = null;
    pendingOwner = null;
  }

  void appendPart(int owner, Part part) {
    final mergeable =
        part.type == 'tool' ||
        part.type == 'text' ||
        part.type == 'reasoning' ||
        _isFoldedIntoWork(part);
    if (!mergeable) {
      flushPending();
      poolOwner = null;
      display[owner].add(part);
      return;
    }
    // Two kinds of stretch: what the agent says (text) and what it does
    // (thoughts and tool calls). A stretch of work runs across as many steps
    // as it takes and belongs to the step that began it, so it can be drawn
    // as one thing.
    final kind = part.type == 'text' && !_isFoldedIntoWork(part)
        ? 'text'
        : 'work';
    if (pendingType != null && pendingType != kind) flushPending();
    pendingType ??= kind;
    pendingOwner ??= owner;
    pendingParts.add(part);
  }

  for (var index = 0; index < messages.length; index += 1) {
    final message = messages[index];
    final parts = message.parts.where((part) => part.isRenderable);
    if (_isFoldedNotice(message)) {
      // Drawn inside the work line of the step that began the stretch.
      for (final part in message.parts.where(
        (part) => part.type == 'v2:notice',
      )) {
        _foldedIntoWork[part] = true;
        appendPart(pendingOwner ?? lastAssistant!, part);
      }
      continue;
    }
    if (message.info.role != 'assistant') {
      flushPending();
      poolOwner = null;
      display[index].addAll(parts);
      continue;
    }
    lastAssistant = index;

    if (message.info.errorText != null && parts.isEmpty) flushPending();
    for (final part in parts) {
      appendPart(index, part);
    }
    if (message.info.errorText != null) {
      flushPending();
      // An error is drawn where it happened: work after it starts anew.
      poolOwner = null;
    }

    final nextIsAssistant =
        index + 1 < messages.length &&
        (messages[index + 1].info.role == 'assistant' ||
            _isFoldedNotice(messages[index + 1]));
    if (!nextIsAssistant) flushPending();
  }
  flushPending();
  return display;
}

/// [_mergeTextParts] for its parity test (a streamed reply's fragments
/// joined the way the transcript draws them).
@visibleForTesting
Part debugMergeTextParts(List<Part> parts) => _mergeTextParts(parts);

Part _mergeTextParts(List<Part> parts) {
  assert(parts.isNotEmpty);
  if (parts.length == 1) return parts.single;
  final first = parts.first;
  final buffer = StringBuffer();
  // What the buffer ends with, tracked from the last fragment written:
  // reading it back from the buffer would copy the whole prefix per
  // fragment, quadratic in a long streamed reply (codex-perf chat.md #1).
  var endsWithNewline = false;
  for (final part in parts) {
    if (part.text.trim().isEmpty) continue;
    if (buffer.isNotEmpty && !endsWithNewline && !part.text.startsWith('\n')) {
      buffer.write('\n\n');
    }
    buffer.write(part.text);
    endsWithNewline = part.text.endsWith('\n');
  }
  final merged = Part(
    id: first.id,
    messageID: first.messageID,
    type: first.type,
    text: buffer.toString(),
  );
  if (parts.any(_isFoldedIntoWork)) _foldedIntoWork[merged] = true;
  return merged;
}

class _AssistantPartRun {
  const _AssistantPartRun(
    this.parts, {
    this.grouped = false,
    this.heading,
    this.note,
    this.card,
  });

  final List<Part> parts;
  final bool grouped;

  /// Set when this run is one tool call the domain reads as an agent card
  /// (docs/design/genui-plan-2026-10-07.md): it is drawn as the card, in the
  /// reply and not folded into the work line.
  final GenUiParse? card;

  /// What the agent said it was about to do, when it said so in a line
  /// ("Patching home shell") right before this run of tool calls. The run
  /// carries it as its title, so a step is one line instead of two.
  final String? heading;

  /// The rest of the thought [heading] opened, shown inside the opened step.
  final String? note;
}

/// Splits a thought into a title for the work that follows it and the rest.
/// Models open a thought with a short line naming what they are about to do
/// ("**Rebuilding latest source**"), then explain. That line titles the step;
/// the explanation waits inside it. A thought with no such opening line keeps
/// its own block.
({String heading, String? note})? _stepHeading(Part part) {
  if (part.type != 'reasoning') return null;
  final text = part.text.trim();
  if (text.isEmpty) return null;
  final breakAt = text.indexOf('\n');
  final first = (breakAt < 0 ? text : text.substring(0, breakAt)).trim();
  if (first.length > 72) return null;
  // Models differ. Some open every thought with a title of their own
  // ("**Rebuilding latest source**"); others think in long prose whose first
  // line is just its first sentence ("Okay, let me look at the router.").
  // Only a line the model marked as a heading may title a step when more
  // follows it; prose stays a thinking block of its own.
  final markedHeading =
      first.startsWith('#') ||
      (first.startsWith('**') && first.endsWith('**') && first.length > 4);
  if (breakAt >= 0 && !markedHeading) return null;
  final plain = first.replaceAll(RegExp(r'[*_`#]+'), '').trim();
  if (plain.isEmpty) return null;
  final rest = breakAt < 0 ? '' : text.substring(breakAt).trim();
  return (heading: plain, note: rest.isEmpty ? null : rest);
}

List<_AssistantPartRun> _groupAssistantParts(List<Part> parts) {
  final runs = <_AssistantPartRun>[];
  var index = 0;
  while (index < parts.length) {
    final current = parts[index];
    // A thought that is only markup ("**", a lone "#") draws an empty rule.
    if (current.type == 'reasoning' &&
        current.text.replaceAll(RegExp(r'[\s*_`#>-]+'), '').isEmpty) {
      index += 1;
      continue;
    }
    if (!_isToolPart(current)) {
      if (current.type != 'text' && current.type != 'reasoning') {
        runs.add(_AssistantPartRun([current]));
        index += 1;
        continue;
      }
      final textParts = <Part>[current];
      var next = index + 1;
      while (next < parts.length && parts[next].type == current.type) {
        textParts.add(parts[next]);
        next += 1;
      }
      runs.add(_AssistantPartRun([_mergeTextParts(textParts)]));
      index = next;
      continue;
    }

    final toolParts = <Part>[current];
    var next = index + 1;
    while (next < parts.length && _isToolPart(parts[next])) {
      toolParts.add(parts[next]);
      next += 1;
    }
    // A one-line thought right before the call or the run is its title, not
    // a row of its own.
    ({String heading, String? note})? title;
    if (runs.isNotEmpty && !runs.last.grouped) {
      title = _stepHeading(runs.last.parts.single);
      if (title != null) runs.removeLast();
    }
    runs.add(
      _AssistantPartRun(
        toolParts,
        grouped: toolParts.length > 1,
        heading: title?.heading,
        note: title?.note,
      ),
    );
    index = next;
  }
  return runs;
}

class _MessageMeta {
  const _MessageMeta({this.modelLabel, this.turnTokens, this.turnCost});

  _MessageMeta withModelLabel(String? label) => _MessageMeta(
    modelLabel: label,
    turnTokens: turnTokens,
    turnCost: turnCost,
  );

  final String? modelLabel;
  final int? turnTokens;
  final double? turnCost;

  bool get isEmpty =>
      modelLabel == null && turnTokens == null && turnCost == null;
}

String? _modelLabel(MessageInfo info) {
  final provider = info.providerID?.trim();
  final model = info.modelID?.trim();
  if (model?.isNotEmpty != true) return null;
  return provider?.isNotEmpty == true
      ? presentedModelLabel(provider!, model!)
      : model;
}

_MessageMeta _messageMeta(List<MessageWithParts> messages, int index) {
  final current = messages[index];
  if (current.info.role != 'assistant') return const _MessageMeta();
  // Everything about a turn is said once, in its footer: a label between
  // two steps would cut the turn in half.
  if (!_endsTurn(messages, index)) return const _MessageMeta();

  final steps = _turnSteps(messages, index);
  final models = <String>[];
  for (final step in steps) {
    final label = _modelLabel(step.info);
    if (label != null && (models.isEmpty || models.last != label)) {
      models.add(label);
    }
  }
  String? previousModel;
  for (
    var previous = messages.indexOf(steps.first) - 1;
    previous >= 0;
    previous -= 1
  ) {
    final info = messages[previous].info;
    if (info.role != 'assistant') continue;
    previousModel = _modelLabel(info);
    break;
  }
  // Named when the turn ran on a different model than the one before it, or
  // changed model part-way.
  final modelChanged =
      models.isNotEmpty && (models.length > 1 || models.first != previousModel);
  final currentModel = models.join(' → ');

  var turnTokens = 0;
  var turnCost = 0.0;
  for (final step in steps) {
    turnTokens += step.info.tokens.total;
    turnCost += step.info.cost;
  }
  return _MessageMeta(
    modelLabel: modelChanged ? currentModel : null,
    turnTokens: turnTokens > 0 ? turnTokens : null,
    turnCost: turnCost > 0 ? turnCost : null,
  );
}

/// Work an agent left running in the background after its turn ended: a
/// command it started with "run in the background" whose ending notice has
/// not arrived yet. Protocol-neutral: a protocol's mapper marks the step
/// that started such work ([backgroundTaskMetadataKey] on the step's
/// metadata) and words the notice that ends it as a `task_notification`
/// step whose input names the starting step (`toolUseId`) or the task
/// (`taskId`). The UI asks [runningBackgroundTasks] (an open conversation's
/// messages) or a [BackgroundTaskGateway] (a conversations list row), and
/// never reads the wire.
///
/// The rule for wrong guesses. Work counts as still running only while all
/// of these hold:
///
/// 1. its starting step said it went on in the background;
/// 2. no ending notice names it;
/// 3. it started less than [backgroundTaskMaxAge] (6 hours) ago;
/// 4. the agent's process has not been seen down or starting again since it
///    started ([BackgroundTaskGateway.agentRuntimeGapAt]): background work
///    dies with the process that ran it, and no notice follows.
///
/// A conversation whose start is not in what the app has read (history
/// trimmed, never opened) shows nothing: the app never guesses work it
/// cannot see.
library;

import 'dart:async';

import 'models.dart';
import 'tool_label.dart' show isBackgroundTaskNotice;

/// The step metadata key a protocol's mapper sets on a step whose work went
/// on in the background: a map with the task's own `id` when the agent gave
/// one, and an optional `label` (the agent's description of the work).
const backgroundTaskMetadataKey = 'backgroundTask';

/// The longest the app believes background work is still running without
/// its notice.
const backgroundTaskMaxAge = Duration(hours: 6);

/// One piece of background work.
final class BackgroundTask {
  const BackgroundTask({
    required this.callID,
    required this.messageID,
    required this.since,
    required this.name,
    this.command = '',
    this.taskID,
    this.waiting = false,
  });

  /// The starting step's call id: what the ending notice names.
  final String callID;

  /// The message that holds the starting step (to scroll to it).
  final String messageID;

  /// When the starting step was reported.
  final DateTime since;

  /// What the work is, in short plain words ("Build the owner APK",
  /// "build_tv_release.sh"). Never a path.
  final String name;

  /// The full command, for a details view only; never copy.
  final String command;

  /// The agent's own id for the task, when it gave one.
  final String? taskID;

  /// The command is a shell loop that waits for something (`until grep -q
  /// ready log; do sleep 30; done`): [name] is then the program it waits on
  /// ("grep"), and the UI words it ("Waiting for grep").
  final bool waiting;
}

/// The background work [part] (a step of [message]) started, or null.
BackgroundTask? backgroundTaskStarted(MessageWithParts message, Part part) {
  if (part.type != 'tool' || part.toolState.status != 'completed') {
    return null;
  }
  final marker = part.toolState.metadata?[backgroundTaskMetadataKey];
  if (marker is! Map) return null;
  final created = message.info.time?.created;
  final callID = part.callID ?? part.id;
  if (created == null || callID == null || callID.isEmpty) return null;
  final command = part.toolState.input['command'];
  final commandText = command is String ? command : '';
  final label = marker['label'];
  final id = marker['id'];
  final labelled = label is String && label.trim().isNotEmpty;
  final waitsOn = labelled ? null : backgroundTaskWaitsOn(commandText);
  return BackgroundTask(
    callID: callID,
    messageID: message.info.id,
    since: DateTime.fromMillisecondsSinceEpoch(created),
    name: labelled
        ? _oneLine(label)
        : waitsOn ?? backgroundTaskName(commandText),
    command: commandText,
    taskID: id is String && id.isNotEmpty ? id : null,
    waiting: waitsOn != null,
  );
}

/// What an ending notice names: the starting step's call id and the task
/// id, either of which may be missing. Null when [part] is not a notice.
({String? callID, String? taskID})? backgroundTaskEnded(Part part) {
  if (part.type != 'tool' || !isBackgroundTaskNotice(part.toolName ?? '')) {
    return null;
  }
  String? text(Object? value) =>
      value is String && value.trim().isNotEmpty ? value.trim() : null;
  final input = part.toolState.input;
  return (callID: text(input['toolUseId']), taskID: text(input['taskId']));
}

/// Whether [task] may still be running at [now] (rules 3 and 4 above).
bool backgroundTaskBelieved(
  BackgroundTask task, {
  required DateTime now,
  DateTime? runtimeGapAt,
}) {
  if (now.difference(task.since) >= backgroundTaskMaxAge) return false;
  if (runtimeGapAt != null && !task.since.isAfter(runtimeGapAt)) return false;
  return true;
}

/// The background work still running in [messages] (oldest first), by the
/// rule in this file's header.
List<BackgroundTask> runningBackgroundTasks(
  Iterable<MessageWithParts> messages, {
  required DateTime now,
  DateTime? runtimeGapAt,
}) {
  final started = <String, BackgroundTask>{};
  final endedCalls = <String>{};
  final endedTasks = <String>{};
  for (final message in messages) {
    for (final part in message.parts) {
      final start = backgroundTaskStarted(message, part);
      if (start != null) {
        started[start.callID] = start;
        continue;
      }
      final end = backgroundTaskEnded(part);
      if (end == null) continue;
      if (end.callID case final id?) endedCalls.add(id);
      if (end.taskID case final id?) endedTasks.add(id);
    }
  }
  return [
    for (final task in started.values)
      if (!endedCalls.contains(task.callID) &&
          !(task.taskID != null && endedTasks.contains(task.taskID)) &&
          backgroundTaskBelieved(task, now: now, runtimeGapAt: runtimeGapAt))
        task,
  ];
}

/// A command as a short plain name: the program or script it runs, without
/// its folder, its environment or the wrappers around it, and at most two
/// plain words after it. `cd app && FOO=1 bash tool/build_tv_release.sh
/// --owner` reads "build_tv_release.sh"; `flutter build apk --release` reads
/// "flutter build apk". Empty text reads "a command".
String backgroundTaskName(String command) {
  // The last command of a chain is the one the work is about.
  final segments = command
      .split(RegExp(r'&&|\|\||;|\n'))
      .map((segment) => segment.trim())
      .where((segment) => segment.isNotEmpty)
      .toList();
  var words = <String>[];
  for (final segment in segments.reversed) {
    final candidate = _commandWords(segment.split('|').first);
    if (candidate.isNotEmpty && candidate.first != 'cd') {
      words = candidate;
      break;
    }
  }
  if (words.isEmpty) return 'a command';
  final program = words.first.split('/').last;
  final rest = <String>[];
  for (final word in words.skip(1)) {
    if (rest.length == 2 || !RegExp(r'^[A-Za-z][\w:.-]*$').hasMatch(word)) {
      break;
    }
    if (word.contains('/') || word.contains('.')) break;
    rest.add(word);
  }
  final name = [program, ...rest].join(' ');
  return name.length > 48 ? '${name.substring(0, 47)}…' : name;
}

/// The program a command waits on when it ends in a shell loop, or null.
/// `until grep -q ready log; do sleep 30; done` waits on "grep"; `while !
/// curl -sf localhost:8080; do sleep 5; done` on "curl". A loop whose test
/// is a bracket (`while [ ! -f out.apk ]`) names what it tests ("out.apk"),
/// else the first command of its body that is not a `sleep`.
String? backgroundTaskWaitsOn(String command) {
  final segments = command
      .split(RegExp(r'&&|\|\||;|\n'))
      .map((segment) => segment.trim())
      .where((segment) => segment.isNotEmpty)
      .toList();
  final last = segments.lastOrNull?.replaceAll(RegExp(r'''["')]+'''), '');
  if (last == null || !RegExp(r'^done\b').hasMatch(last)) return null;
  final head = RegExp(r'''(?:^|[\s'"(])(?:until|while)\s+(.+)$''');
  for (var i = segments.length - 2; i >= 0; i--) {
    final test = head.firstMatch(segments[i])?.group(1);
    if (test == null) continue;
    final words = _commandWords(
      test.replaceFirst(RegExp(r'^!\s*'), '').split('|').first,
    );
    if (words.isEmpty) return null;
    var program = words.first.split('/').last;
    if (const {'[', '[[', 'test', 'true', ':'}.contains(program)) {
      // What the test looks at (`out.apk` in `[ ! -f out/app.apk ]`), else
      // the first command of the body that is not a sleep.
      final operands = words
          .skip(1)
          .where(
            (word) =>
                !word.startsWith('-') && !const {']', ']]', '!'}.contains(word),
          );
      if (operands.isNotEmpty) program = operands.last.split('/').last;
      for (final segment
          in operands.isNotEmpty
              ? const <String>[]
              : segments.sublist(i + 1, segments.length - 1)) {
        final body = _commandWords(
          segment.replaceFirst(RegExp(r'^do\s+'), '').split('|').first,
        );
        if (body.isNotEmpty && body.first != 'sleep' && body.first != 'do') {
          program = body.first.split('/').last;
          break;
        }
      }
    }
    return program.length > 48 ? '${program.substring(0, 47)}…' : program;
  }
  return null;
}

const _wrappers = {
  'sudo',
  'nohup',
  'env',
  'time',
  'exec',
  'command',
  'nice',
  'setsid',
  'stdbuf',
};

const _interpreters = {
  'bash',
  'sh',
  'zsh',
  'dash',
  'python',
  'python3',
  'node',
  'deno',
  'ruby',
  'perl',
  'npx',
  'bunx',
};

/// [segment]'s words from the program on: environment assignments,
/// wrappers (`nohup`, `timeout 600`) and an interpreter that runs a script
/// (`bash x.sh`) left out.
List<String> _commandWords(String segment) {
  final words = segment
      .trim()
      .split(RegExp(r'\s+'))
      .map((word) => word.replaceAll(RegExp(r'''^["']|["']$'''), ''))
      .where((word) => word.isNotEmpty)
      .toList();
  var i = 0;
  while (i < words.length) {
    final word = words[i];
    if (RegExp(r'^[A-Za-z_][A-Za-z0-9_]*=').hasMatch(word) ||
        _wrappers.contains(word)) {
      i++;
    } else if (word == 'timeout') {
      i++;
      while (i < words.length &&
          RegExp(r'^(-\S+|\d+\w?)$').hasMatch(words[i])) {
        i++;
      }
    } else {
      break;
    }
  }
  var rest = words.sublist(i);
  if (rest.length > 1 && _interpreters.contains(rest.first.split('/').last)) {
    final script = rest.skip(1).where((word) => !word.startsWith('-'));
    if (script.isNotEmpty) rest = [script.first];
  }
  return rest;
}

String _oneLine(String text) {
  final line = text.trim().split('\n').first.trim();
  return line.length > 64 ? '${line.substring(0, 63)}…' : line;
}

/// What [message] says about whether its conversation's turn failed: true
/// for an agent message with an error, false for a prompt (a new turn) or
/// the agent's words after it (it went on), null for anything else (a
/// step, a background task's notice).
bool? turnFailedAfter(MessageWithParts message) {
  if (message.info.role == 'user') return false;
  if (message.info.errorText != null) return true;
  return message.parts.any(
        (part) => part.type == 'text' && part.text.trim().isNotEmpty,
      )
      ? false
      : null;
}

/// Whether the last turn in [messages] ended on the agent's failure
/// ([turnFailedAfter]).
bool lastTurnFailedIn(Iterable<MessageWithParts> messages) {
  var failed = false;
  for (final message in messages) {
    failed = turnFailedAfter(message) ?? failed;
  }
  return failed;
}

/// A gateway whose conversations can leave work running in the background.
abstract interface class BackgroundTaskGateway {
  /// The last time this app saw [sessionID]'s agent with no process behind
  /// it (not loaded, closed, or starting again); work started before then
  /// cannot still be running. Null when never seen down.
  DateTime? agentRuntimeGapAt(String sessionID);

  /// Whether [sessionID] has background work still running, from what the
  /// app has read of it (rules in this file's header).
  bool backgroundTasksRunning(String sessionID);

  /// Whether [sessionID]'s last turn, as the app read it, ended on the
  /// agent's own failure (Claude Code's "API Error: …" line). Work it left in
  /// the background may still run, but the conversation reads as failed, not
  /// as working.
  bool lastTurnFailed(String sessionID);

  /// Fires when what [backgroundTasksRunning] or [lastTurnFailed] answers
  /// may have changed.
  Stream<void> get backgroundTaskChanges;
}

/// What the app has seen of each agent's background work, shared by every
/// connection to the same helper (a conversations list and an open chat
/// read the same agent through different connections). Keyed by the
/// agent's own id on its helper. Bounded; nothing is saved.
final class BackgroundTaskBook {
  BackgroundTaskBook({DateTime Function()? clock})
    : _clock = clock ?? DateTime.now;

  /// The process-wide book.
  static final shared = BackgroundTaskBook();

  static const _maxAgents = 256;
  static const _maxTasks = 64;

  final DateTime Function() _clock;
  final _agents = <String, _AgentTasks>{};
  final _changes = StreamController<void>.broadcast();

  Stream<void> get changes => _changes.stream;

  /// Counts the changes [changes] announced, for a memo's signature.
  int get revision => _revision;
  int _revision = 0;

  _AgentTasks _agent(String key) {
    final agent = _agents.remove(key) ?? _AgentTasks();
    _agents[key] = agent;
    while (_agents.length > _maxAgents) {
      _agents.remove(_agents.keys.first);
    }
    return agent;
  }

  /// [messages] is everything the app read of [key]'s conversation: what it
  /// says replaces what was known (a full read is the truth).
  void replace(String key, Iterable<MessageWithParts> messages) {
    final agent = _agent(key);
    final before = agent.signature;
    agent.started.clear();
    agent.endedCalls.clear();
    agent.endedTasks.clear();
    agent.failed = false;
    for (final message in messages) {
      agent.observe(message);
    }
    agent.trim(_maxTasks);
    if (agent.signature != before) _changed();
  }

  /// One more message of [key]'s conversation, as it streams.
  void observe(String key, MessageWithParts message) {
    final agent = _agent(key);
    final before = agent.signature;
    agent.observe(message);
    agent.trim(_maxTasks);
    if (agent.signature != before) _changed();
  }

  /// [key]'s agent was seen with no process behind it at [at].
  void runtimeGap(String key, DateTime at) {
    final agent = _agent(key);
    final had = running(key, now: at);
    final gap = agent.gap;
    if (gap == null || at.isAfter(gap)) agent.gap = at;
    if (had && !running(key, now: at)) _changed();
  }

  DateTime? gapAt(String key) => _agents[key]?.gap;

  /// Whether [key]'s last turn, as read, ended on a failure: an assistant
  /// message with an error and no words after it, until the next prompt.
  bool turnFailed(String key) => _agents[key]?.failed ?? false;

  /// Whether [key] has background work still running at [now].
  bool running(String key, {DateTime? now}) {
    final agent = _agents[key];
    if (agent == null) return false;
    final at = now ?? _clock();
    return agent.started.values.any(
      (task) =>
          !agent.endedCalls.contains(task.callID) &&
          !(task.taskID != null && agent.endedTasks.contains(task.taskID)) &&
          backgroundTaskBelieved(task, now: at, runtimeGapAt: agent.gap),
    );
  }

  void forget(String key) {
    if (_agents.remove(key) != null) _changed();
  }

  void _changed() {
    _revision++;
    if (!_changes.isClosed) _changes.add(null);
  }

  /// Empties the book (tests).
  void clear() => _agents.clear();
}

final class _AgentTasks {
  final started = <String, BackgroundTask>{};
  final endedCalls = <String>{};
  final endedTasks = <String>{};
  DateTime? gap;
  bool failed = false;

  String get signature =>
      '${started.keys.join(',')}|${endedCalls.join(',')}|'
      '${endedTasks.join(',')}|$failed';

  void observe(MessageWithParts message) {
    failed = turnFailedAfter(message) ?? failed;
    for (final part in message.parts) {
      final start = backgroundTaskStarted(message, part);
      if (start != null) {
        started[start.callID] = start;
        continue;
      }
      final end = backgroundTaskEnded(part);
      if (end == null) continue;
      if (end.callID case final id?) endedCalls.add(id);
      if (end.taskID case final id?) endedTasks.add(id);
    }
  }

  void trim(int max) {
    while (started.length > max) {
      started.remove(started.keys.first);
    }
    while (endedCalls.length > max * 4) {
      endedCalls.remove(endedCalls.first);
    }
    while (endedTasks.length > max * 4) {
      endedTasks.remove(endedTasks.first);
    }
  }
}

part of '../chat_screen.dart';

// A command that runs far longer than an agent lets one run. Agents cap a
// foreground command at about ten minutes (Claude Code's Bash, OpenCode's
// bash), so a step still "running" after much longer means the turn is hung
// or this phone lost track of it. The live line then says so in plain words
// with Stop, and a message typed meanwhile is offered as "Stop and send"
// instead of waiting behind a reply that may never come. Background
// commands are not this: they return at once and may run for hours.

/// How long a foreground command may run before the turn looks stuck: the
/// agents' ten-minute cap, with room for the `timeout 900` runs they set
/// themselves.
const stuckCommandAfter = Duration(minutes: 15);

mixin _ChatStuckFields {
  /// How long the newest running foreground command has run, once past
  /// [stuckCommandAfter]; worked out once per build ([_liveTurn]).
  Duration? _stuckFor;

  /// The stuck command, cut to one short line.
  String? _stuckName;

  /// Wakes the screen when a running command reaches the threshold.
  Timer? _stuckTimer;
}

/// Whether [tool] is a command the agent waits on (not one it sent to the
/// background).
bool _isForegroundCommand(Part tool) {
  if (!isShellTool(tool.toolName ?? 'tool', tool.toolState)) return false;
  final input = tool.toolState.input;
  for (final key in const ['run_in_background', 'runInBackground']) {
    if (input[key] == true) return false;
  }
  return true;
}

extension _ChatStuck on _ChatScreenState {
  /// Notes the newest running step: past [stuckCommandAfter] the turn looks
  /// stuck; before it, the screen wakes when it gets there.
  void _noteRunningCommand(Part tool) {
    final started = tool.toolState.startedAt;
    if (started == null || !_isForegroundCommand(tool)) return;
    final age = DateTime.now().difference(started);
    if (age >= stuckCommandAfter) {
      _stuckFor = age;
      _stuckName = _commandName(tool);
      return;
    }
    if (_stuckTimer?.isActive == true) return;
    _stuckTimer = Timer(
      stuckCommandAfter - age + const Duration(seconds: 1),
      () {
        if (mounted) _setChatState(() {});
      },
    );
  }

  /// The words after the command: "2 h 29 min · may be stuck". Null while
  /// the newest command is within the limit.
  String? _stuckWords(BuildContext context) {
    final age = _stuckFor;
    if (age == null) return null;
    final strings = _chatL10n(context);
    final time = age.inHours < 1
        ? strings.chatStuckMinutes(age.inMinutes)
        : strings.chatStuckHours(age.inHours, age.inMinutes % 60);
    return _stuckName == null
        ? strings.chatStuckUnnamed(time)
        : strings.chatStuckCommand(time);
  }

  /// The command as the line shows it: a leading `cd <path>;` or
  /// `cd <path> &&` dropped, the first line, cut to 40 characters.
  String? _commandName(Part tool) {
    final raw = tool.toolState.input['command'];
    if (raw is! String) return null;
    final bare = raw.trim().replaceFirst(
      RegExp(r'^cd\s+("[^"]*"|\S+)\s*(;|&&)\s*'),
      '',
    );
    final first = bare.split('\n').first.trim();
    if (first.isEmpty) return null;
    return first.characters.length > 40
        ? '${first.characters.take(39)}\u2026'
        : first;
  }

  /// Send is "Stop and send" for an agent that runs a message after the
  /// reply (OpenCode 1) when that reply looks hung (a command past the limit,
  /// or a send that just failed): the turn is stopped first, then the words
  /// go. An agent that steers or restarts keeps its own Send.
  bool _stopsBeforeSend(bool busy) =>
      busy &&
      !_conn.capabilities.promptSteering &&
      _midTurnPrompt == null &&
      (_stuckFor != null || _sendError != null);

  /// "Stop and send": a turn that looks hung is stopped first, and the words
  /// go out once it has stopped. False when the stop failed: nothing is
  /// sent.
  Future<bool> _stopBeforeSend() async {
    if (!_stopsBeforeSend(
      _conn.busySessions.contains(widget.sessionID) || _live != null,
    )) {
      return true;
    }
    await _abort();
    return mounted && _abortSucceeded;
  }

  /// The failed request says nothing of the turn that was already running:
  /// read it again (history and the busy list), so a step the stream lost
  /// the end of stops showing as running.
  void _readTurnAfterFailedSend() {
    unawaited(_load());
    unawaited(_conn.reconcileBusySessions());
  }
}

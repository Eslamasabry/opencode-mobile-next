import '../api/models.dart';

/// What the server said about how a run ended. Only assistant-message facts
/// (`finish`, `error`, `time.completed`) decide this; session idleness and
/// message prose never do.
enum RunOutcomeKind {
  /// The provider reported a normal stop.
  completed,

  /// The provider stopped for output length.
  cutOff,

  /// The server attached an error to a message of the run.
  failed,

  /// The run was aborted (server error kind).
  aborted,

  /// The last step has no completion time and no error yet.
  running,

  /// The step completed but the provider gave no recognised finish reason.
  notReported,
}

class RunOutcome {
  const RunOutcome(this.kind, {this.finish, this.errorHeadline});
  final RunOutcomeKind kind;

  /// The raw finish reason exactly as the server reported it, if any.
  final String? finish;

  /// First meaningful line of the server error, if any.
  final String? errorHeadline;
}

enum RunFileChange { edited, written, patched }

/// A file a COMPLETED edit/write/patch tool reported touching. This is tool
/// evidence, not a verified diff of the working tree.
class RunChangedFile {
  const RunChangedFile({
    required this.path,
    required this.change,
    required this.toolName,
    required this.state,
    this.partID,
  });
  final String path;
  final RunFileChange change;
  final String toolName;
  final ToolState state;
  final String? partID;
}

/// A bash/shell tool call of the run with whatever the server recorded.
class RunCommand {
  const RunCommand({
    required this.command,
    required this.toolName,
    required this.state,
    required this.failed,
    required this.looksLikeTest,
    this.exitCode,
    this.partID,
  });

  /// The command text, bounded to [RunResult.maxCommandLength].
  final String command;
  final String toolName;
  final ToolState state;

  /// True when the tool itself ended in the error state.
  final bool failed;

  /// Null when the server recorded no exit code (many v1 payloads).
  final int? exitCode;

  /// Derived from the command TEXT only, by [RunResult.looksLikeTestCommand].
  /// It says nothing about whether tests ran or passed.
  final bool looksLikeTest;
  final String? partID;

  /// True when the server pruned the output, so nothing can be opened.
  bool get outputPruned => state.pruned;
}

/// Deterministic summary of the most recent run in a session, built only
/// from the server's message and tool-part records. Every field is either
/// copied from those records or explicitly marked unknown; no model call,
/// no prose parsing, no guessing from idleness.
class RunResult {
  const RunResult._({
    required this.sessionID,
    required this.runID,
    required this.userMessageID,
    required this.startedAt,
    required this.finishedAt,
    required this.stepCount,
    required this.agent,
    required this.model,
    required this.outcome,
    required this.changedFiles,
    required this.commands,
    required this.toolCount,
    required this.prunedToolCount,
    required this.erroredToolCount,
    required this.truncated,
    required this.boundaryKnown,
    required this.earlierStepErrors,
    required this.lastStepID,
  });

  static const maxChangedFiles = 50;
  static const maxCommands = 50;
  static const maxCommandLength = 240;

  final String sessionID;

  /// Id of the first assistant message of the run: the stable handle a user
  /// can match against the transcript.
  final String runID;
  final String? userMessageID;

  /// Creation time of the user message that started the run.
  final DateTime? startedAt;

  /// Completion time of the terminal newest step only. An earlier completed
  /// step cannot finish a turn whose newest step is still running.
  final DateTime? finishedAt;

  /// Assistant messages that make up the run.
  final int stepCount;
  final String? agent;
  final String? model;
  final RunOutcome outcome;
  final List<RunChangedFile> changedFiles;
  final List<RunCommand> commands;

  /// Tool parts seen in the run, of any kind.
  final int toolCount;
  final int prunedToolCount;
  final int erroredToolCount;

  /// True when [changedFiles] or [commands] hit their bound.
  final bool truncated;

  /// False when the loaded history never reached the user message that
  /// started this run (older pages not fetched). Step and evidence counts
  /// are then lower bounds, and the run id is merely the oldest loaded step.
  final bool boundaryKnown;

  /// Steps before the newest one that carried a server error. The outcome
  /// itself is taken from the newest step only, so later work always wins.
  final int earlierStepErrors;

  /// Id of the newest assistant message of the run: the exact record whose
  /// completion a live event would have announced.
  final String lastStepID;

  /// False when the run carried no tool parts at all: there is nothing to
  /// show as evidence, and the UI must say so rather than imply "no changes".
  bool get hasToolEvidence => toolCount > 0;

  /// The grouping is a conversation turn inferred from server messages
  /// (assistant messages after the latest user message), not a server-side
  /// run id. Messages may arrive in any order. [historyComplete] tells the
  /// builder the caller reached the start of the session, so a missing user
  /// message still means the boundary is known. Returns null when no
  /// assistant message exists after the boundary.
  static RunResult? fromMessages(
    String sessionID,
    List<MessageWithParts> messages, {
    bool historyComplete = false,
  }) {
    // List.sort is not stable. Codex turn items share a created timestamp,
    // so retain authoritative input order for ties rather than sorting ids.
    final indexed = messages.indexed.toList()
      ..sort((a, b) {
        final time = _byCreation(a.$2, b.$2);
        return time != 0 ? time : a.$1.compareTo(b.$1);
      });
    final ordered = indexed.map((item) => item.$2).toList();
    var lastUser = -1;
    for (var i = 0; i < ordered.length; i++) {
      if (ordered[i].info.role == 'user') lastUser = i;
    }
    final run = ordered
        .sublist(lastUser + 1)
        .where((m) => m.info.role == 'assistant')
        .toList();
    if (run.isEmpty) return null;
    final user = lastUser >= 0 ? ordered[lastUser] : null;
    var earlierErrors = 0;
    for (final message in run.take(run.length - 1)) {
      if (message.info.errorText != null) earlierErrors++;
    }

    final changed = <String, RunChangedFile>{};
    final commands = <RunCommand>[];
    var toolCount = 0;
    var pruned = 0;
    var errored = 0;
    var truncated = false;
    for (final message in run) {
      for (final part in message.parts) {
        if (part.type != 'tool') continue;
        toolCount++;
        final state = part.toolState;
        if (state.pruned) pruned++;
        if (state.status == 'error') errored++;
        final tool = (part.toolName ?? '').toLowerCase();
        final change = _changeOf(tool);
        if (change != null) {
          if (state.status != 'completed' || !state.executed) continue;
          for (final path in _pathsOf(tool, state)) {
            if (changed.length >= maxChangedFiles &&
                !changed.containsKey(path)) {
              truncated = true;
              continue;
            }
            changed[path] = RunChangedFile(
              path: path,
              change: change,
              toolName: tool,
              state: state,
              partID: part.id ?? part.callID,
            );
          }
        } else if (tool == 'bash' || tool == 'shell') {
          if (state.status != 'completed' && state.status != 'error') continue;
          if (commands.length >= maxCommands) {
            truncated = true;
            continue;
          }
          final raw = state.input['command']?.toString().trim() ?? '';
          final command = raw.length > maxCommandLength
              ? '${raw.substring(0, maxCommandLength)}…'
              : raw;
          commands.add(
            RunCommand(
              command: command,
              toolName: tool,
              state: state,
              failed: state.status == 'error',
              exitCode: _exitOf(state.metadata),
              looksLikeTest: looksLikeTestCommand(raw),
              partID: part.id ?? part.callID,
            ),
          );
        }
      }
    }

    final last = run.last.info;
    final outcome = _outcomeOf(last);
    return RunResult._(
      sessionID: sessionID,
      runID: run.first.info.id,
      userMessageID: user?.info.id,
      startedAt: _time(
        user?.info.time?.created ?? run.first.info.time?.created,
      ),
      finishedAt: outcome.kind == RunOutcomeKind.running
          ? null
          : _time(last.time?.completed),
      stepCount: run.length,
      agent: last.agent ?? run.first.info.agent,
      model: last.modelID ?? run.first.info.modelID,
      outcome: outcome,
      changedFiles: List.unmodifiable(changed.values),
      commands: List.unmodifiable(commands),
      toolCount: toolCount,
      prunedToolCount: pruned,
      erroredToolCount: errored,
      truncated: truncated,
      boundaryKnown: lastUser >= 0 || historyComplete,
      earlierStepErrors: earlierErrors,
      lastStepID: last.id,
    );
  }

  /// True only when the command TEXT names a common test runner or a
  /// `<tool> test` invocation. A label derived from this must say it came
  /// from the command text.
  static bool looksLikeTestCommand(String command) {
    final text = command.toLowerCase();
    if (_testRunner.hasMatch(text)) return true;
    return _toolTest.hasMatch(text);
  }

  static final _testRunner = RegExp(
    r'(^|[\s;&|(])(pytest|jest|vitest|mocha|rspec|phpunit|ctest|tox|nose2?)\b',
  );
  static final _toolTest = RegExp(
    r'(^|[\s;&|(])(flutter|dart|npm|pnpm|yarn|bun|deno|cargo|go|mvn|gradlew?|'
    r'dotnet|make|poetry|uv|swift|mix|sbt|lein|stack|cabal)'
    r'(\s+run)?\s+tests?\b',
  );

  static int _byCreation(MessageWithParts a, MessageWithParts b) {
    final byTime = (a.info.time?.created ?? 0).compareTo(
      b.info.time?.created ?? 0,
    );
    return byTime;
  }

  static DateTime? _time(int? raw) =>
      raw == null || raw <= 0 ? null : DateTime.fromMillisecondsSinceEpoch(raw);

  static RunFileChange? _changeOf(String tool) => switch (tool) {
    'edit' || 'multiedit' => RunFileChange.edited,
    'write' => RunFileChange.written,
    'patch' || 'apply_patch' => RunFileChange.patched,
    _ => null,
  };

  static List<String> _pathsOf(String tool, ToolState state) {
    final paths = <String>[];
    void add(Object? raw) {
      final path = raw?.toString().trim();
      if (path != null && path.isNotEmpty && !paths.contains(path)) {
        paths.add(path);
      }
    }

    final files = state.metadata?['files'];
    if (files is List) {
      for (final file in files) {
        if (file is Map) add(file['path'] ?? file['filePath'] ?? file['file']);
      }
    }
    if (paths.isEmpty) {
      add(
        state.input['filePath'] ?? state.input['path'] ?? state.input['file'],
      );
    }
    return paths;
  }

  static int? _exitOf(Map<String, dynamic>? metadata) {
    final raw = metadata?['exit'] ?? metadata?['exitCode'];
    if (raw is int) return raw;
    if (raw is num) return raw.toInt();
    if (raw is String) return int.tryParse(raw);
    return null;
  }

  /// The newest step alone decides: an error on it fails the run, a stop on
  /// it completes the run even if an earlier step had failed and was retried.
  static RunOutcome _outcomeOf(MessageInfo last) {
    final errorText = last.errorText;
    if (errorText != null) {
      final kind = MessageErrorKind.refineFromText(
        last.errorKind ?? MessageErrorKind.unknown,
        errorText,
      );
      return RunOutcome(
        kind == MessageErrorKind.aborted
            ? RunOutcomeKind.aborted
            : kind == MessageErrorKind.outputLength
            ? RunOutcomeKind.cutOff
            : RunOutcomeKind.failed,
        finish: last.finish,
        errorHeadline: errorHeadline(errorText),
      );
    }
    if (last.time?.isDone != true) {
      return RunOutcome(RunOutcomeKind.running, finish: last.finish);
    }
    final finish = last.finish?.trim().toLowerCase();
    return switch (finish) {
      'stop' ||
      'end_turn' ||
      'end-turn' => RunOutcome(RunOutcomeKind.completed, finish: last.finish),
      'length' ||
      'max_tokens' ||
      'max-tokens' => RunOutcome(RunOutcomeKind.cutOff, finish: last.finish),
      _ => RunOutcome(RunOutcomeKind.notReported, finish: last.finish),
    };
  }
}

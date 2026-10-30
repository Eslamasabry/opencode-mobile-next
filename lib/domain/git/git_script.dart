/// One Git action as one `sh` script, and the envelope its answer comes back
/// in.
///
/// Every bridge returns plain text, some merge stdout with stderr, and the
/// in-app bridge keeps only the last 64 KB. So each step's stdout and stderr
/// go to files and come back base64-encoded on marker lines:
///
/// ```text
/// @@OCG begin
/// @@OCG out status <base64 stdout>
/// @@OCG err status <base64 stderr>
/// @@OCG exit status 0
/// @@OCG end
/// ```
///
/// Anything else (a proot warning, a login banner) is ignored. A missing
/// `begin` or an `exit` without its `out` means the answer was cut off.
library;

import 'dart:convert';

import 'git_shell.dart';

/// One command of a [GitScript]: an argv list, every word quoted when the
/// script is written.
class GitStep {
  const GitStep(
    this.name,
    this.argv, {
    this.stopOnFailure = false,
    this.optional = false,
  });

  /// Letters, digits and `-` only; the marker key.
  final String name;
  final List<String> argv;

  /// A failure ends the script here (later steps do not run).
  final bool stopOnFailure;

  /// Extra information: an answer without this step's result is still a
  /// whole answer.
  final bool optional;
}

/// The environment every Git command runs with: no prompts, no pager, no
/// colour, English messages, no optional index locks (agents may be working
/// in the same repository).
const gitBaseEnvironment = <String, String>{
  'GIT_TERMINAL_PROMPT': '0',
  'GIT_PAGER': 'cat',
  'PAGER': 'cat',
  'LC_ALL': 'C',
  'LANG': 'C',
  'GIT_OPTIONAL_LOCKS': '0',
};

class GitScript {
  const GitScript({
    required this.directory,
    required this.steps,
    this.environment = const {},
    this.removeOnExit = const [],
  });

  /// Where the project lives, as the shell sees it.
  final String directory;
  final List<GitStep> steps;

  /// Extra variables exported for every step (values are quoted).
  final Map<String, String> environment;

  /// Files the script deletes when it exits, however it exits.
  final List<String> removeOnExit;

  static final _stepName = RegExp(r'^[A-Za-z0-9-]+$');
  static final _envName = RegExp(r'^[A-Z_][A-Z0-9_]*$');

  /// The script text. Never contains a secret: callers pass only paths.
  String render() {
    final b = StringBuffer()
      ..writeln("printf '@@OCG begin\\n'")
      ..writeln(
        'cd -- ${shQuote(directory)} 2>/dev/null || '
        "{ printf '@@OCG nodir\\n'; exit 0; }",
      )
      ..writeln(
        'command -v git >/dev/null 2>&1 || '
        "{ printf '@@OCG nogit\\n'; exit 0; }",
      )
      ..writeln(
        'ocg_t=\$(mktemp -d 2>/dev/null) || '
        "{ printf '@@OCG notemp\\n'; exit 0; }",
      );
    final cleanup = [
      r'rm -rf "$ocg_t"',
      for (final file in removeOnExit) 'rm -f -- ${shQuote(file)}',
    ].join('; ');
    b.writeln('trap ${shQuote(cleanup)} EXIT');
    final env = {...gitBaseEnvironment, ...environment};
    for (final entry in env.entries) {
      if (!_envName.hasMatch(entry.key)) {
        throw ArgumentError.value(entry.key, 'environment');
      }
      b.writeln('${entry.key}=${shQuote(entry.value)}; export ${entry.key}');
    }
    // ocg_s NAME CMD...: run CMD, then print its stdout, stderr and status.
    b
      ..writeln('ocg_s() {')
      ..writeln('  ocg_n=\$1; shift')
      ..writeln(r'  "$@" >"$ocg_t/o" 2>"$ocg_t/e"; ocg_c=$?')
      ..writeln(
        "  printf '@@OCG out %s ' \"\$ocg_n\"; "
        "base64 <\"\$ocg_t/o\" | tr -d '\\n'; printf '\\n'",
      )
      ..writeln(
        "  printf '@@OCG err %s ' \"\$ocg_n\"; "
        "base64 <\"\$ocg_t/e\" | tr -d '\\n'; printf '\\n'",
      )
      ..writeln("  printf '@@OCG exit %s %s\\n' \"\$ocg_n\" \"\$ocg_c\"")
      ..writeln(r'  return $ocg_c')
      ..writeln('}');
    for (final step in steps) {
      if (!_stepName.hasMatch(step.name)) {
        throw ArgumentError.value(step.name, 'step.name');
      }
      final line = 'ocg_s ${step.name} ${shCommand(step.argv)}';
      b.writeln(
        step.stopOnFailure
            ? "$line || { printf '@@OCG end\\n'; exit 0; }"
            : line,
      );
    }
    b.writeln("printf '@@OCG end\\n'");
    return b.toString();
  }
}

/// What one step printed and how it ended.
class GitStepResult {
  const GitStepResult({
    required this.exitCode,
    required this.stdout,
    required this.stderr,
  });

  final int exitCode;
  final String stdout;
  final String stderr;

  bool get ok => exitCode == 0;
}

/// Why the envelope itself could not be read.
enum GitEnvelopeProblem { folderMissing, gitMissing, noTemp, truncated }

/// A decoded answer.
class GitEnvelope {
  const GitEnvelope({this.steps = const {}, this.problem});

  final Map<String, GitStepResult> steps;
  final GitEnvelopeProblem? problem;

  GitStepResult? operator [](String name) => steps[name];

  static final _marker = RegExp(r'^@@OCG (\S+)(?: (\S+))?(?: (\S*))?\s*$');

  static GitEnvelope parse(String output) {
    var begun = false;
    var ended = false;
    final outs = <String, String>{};
    final errs = <String, String>{};
    final exits = <String, int>{};
    for (final raw in const LineSplitter().convert(output)) {
      final line = raw.trimRight();
      final at = line.indexOf('@@OCG ');
      if (at < 0) continue;
      final match = _marker.firstMatch(line.substring(at));
      if (match == null) continue;
      final kind = match.group(1)!;
      final name = match.group(2);
      final value = match.group(3) ?? '';
      switch (kind) {
        case 'begin':
          begun = true;
        case 'end':
          ended = true;
        case 'nodir':
          return const GitEnvelope(problem: GitEnvelopeProblem.folderMissing);
        case 'nogit':
          return const GitEnvelope(problem: GitEnvelopeProblem.gitMissing);
        case 'notemp':
          return const GitEnvelope(problem: GitEnvelopeProblem.noTemp);
        case 'out' when name != null:
          outs[name] = value;
        case 'err' when name != null:
          errs[name] = value;
        case 'exit' when name != null:
          exits[name] = int.tryParse(value) ?? -1;
      }
    }
    if (!begun || !ended) {
      return const GitEnvelope(problem: GitEnvelopeProblem.truncated);
    }
    final steps = <String, GitStepResult>{};
    for (final entry in exits.entries) {
      final out = outs[entry.key];
      final err = errs[entry.key];
      if (out == null || err == null) {
        return const GitEnvelope(problem: GitEnvelopeProblem.truncated);
      }
      steps[entry.key] = GitStepResult(
        exitCode: entry.value,
        stdout: _decode(out),
        stderr: _decode(err),
      );
    }
    return GitEnvelope(steps: steps);
  }

  static String _decode(String value) {
    if (value.isEmpty) return '';
    try {
      return utf8.decode(base64.decode(value), allowMalformed: true);
    } on FormatException {
      return '';
    }
  }
}

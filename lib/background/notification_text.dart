import '../domain/models.dart';
import '../domain/server_gateway.dart';

/// Words that may reach the notification shade, kept safe.
///
/// The shade (not the lock screen, which gets a count-only public version)
/// shows a command or the first line of a reply so the person can decide
/// without opening the app. Nothing secret may ride along: keys, tokens and
/// passwords are masked, and raw errors never become copy.

const _mask = '•••';

typedef _Mask = String Function(Match match);

final List<(RegExp, _Mask)> _secretPatterns = [
  // Authorization headers and bearer tokens.
  (
    RegExp(r'\b(bearer|basic)\s+[A-Za-z0-9._~+/=-]{6,}', caseSensitive: false),
    (m) => '${m[1]} $_mask',
  ),
  // key=value and key: value pairs that name a secret: the name stays.
  (
    RegExp(
      r"""\b((?:api[_-]?key|access[_-]?key|secret|token|passwd|password|pwd|authorization|auth)[A-Za-z0-9_-]*)\s*[=:]\s*("[^"]*"|'[^']*'|\S+)""",
      caseSensitive: false,
    ),
    (m) => '${m[1]}=$_mask',
  ),
  // Flags that carry a secret.
  (
    RegExp(
      r'(--?(?:password|passwd|token|secret|api-key|apikey|key|auth))[ =]+("[^"]*"|\S+)',
      caseSensitive: false,
    ),
    (m) => '${m[1]} $_mask',
  ),
  // user:password@host
  (RegExp(r'(?<=://)[^/\s:@]+:[^/\s@]+(?=@)'), (_) => _mask),
  // Well-known token shapes.
  (RegExp(r'\b(?:sk|pk|rk)-[A-Za-z0-9_-]{8,}'), (_) => _mask),
  (RegExp(r'\bgh[pousr]_[A-Za-z0-9]{16,}'), (_) => _mask),
  (RegExp(r'\bxox[abprs]-[A-Za-z0-9-]{10,}'), (_) => _mask),
  (RegExp(r'\bAKIA[0-9A-Z]{16}\b'), (_) => _mask),
  (RegExp(r'\bAIza[0-9A-Za-z_-]{20,}'), (_) => _mask),
  // Any long unbroken token-looking run.
  (RegExp(r'[A-Za-z0-9+/_=-]{32,}'), (_) => _mask),
];

/// One short line of [input] with secrets masked, at most [maxLength] long.
String redactForNotification(String input, {int maxLength = 140}) {
  var text = input.replaceAll(RegExp(r'\s+'), ' ').trim();
  for (final (pattern, replace) in _secretPatterns) {
    text = text.replaceAllMapped(pattern, replace);
  }
  if (text.length > maxLength) {
    text = '${text.substring(0, maxLength - 1).trimRight()}…';
  }
  return text;
}

/// The first line of the newest assistant reply, redacted; empty when none.
String firstReplyLine(List<MessageWithParts> messages, {int maxLength = 140}) {
  for (final message in messages.reversed) {
    if (message.info.role != 'assistant') continue;
    for (final part in message.parts) {
      if (part.type != 'text' || part.synthetic) continue;
      final lines = [
        for (final line in part.text.split('\n'))
          line.replaceFirst(RegExp(r'^[#>*\-\s`]+'), '').trim(),
      ].where((line) => line.isNotEmpty).toList();
      if (lines.isEmpty) continue;
      var chosen = lines.first;
      if (chosen.endsWith(':')) {
        // A line that introduces a list is not a summary by itself: read it
        // with the first item, or drop the colon when nothing follows.
        chosen = lines.length > 1
            ? '$chosen ${lines[1]}'
            : chosen.substring(0, chosen.length - 1).trimRight();
      }
      return redactForNotification(chosen, maxLength: maxLength);
    }
  }
  return '';
}

/// A plain reason token for a stopped chat; native turns it into a sentence.
/// Never the server's own text. Must match `alertReasonText` in
/// BackgroundConnectionService.kt.
String stoppedReasonToken(String? errorName) =>
    switch (MessageErrorKind.fromName(errorName)) {
      MessageErrorKind.providerAuth => 'auth',
      MessageErrorKind.contextOverflow => 'context',
      MessageErrorKind.outputLength => 'length',
      MessageErrorKind.contentFilter => 'filter',
      MessageErrorKind.modelNotFound => 'model',
      _ => 'other',
    };

/// A question's choices as notification buttons: one single-choice prompt
/// with three or fewer short choices, else none (the alert only opens the
/// app). Must agree with `AlertButtons.shortOptions` in
/// StatusNotificationModel.kt.
List<String> notificationOptionsOf(PendingQuestion question) {
  if (question.prompts.length != 1) return const [];
  final prompt = question.prompts.single;
  if (prompt.multiple) return const [];
  final labels = [for (final choice in prompt.choices) choice.label.trim()];
  if (labels.isEmpty || labels.length > 3) return const [];
  if (labels.any((label) => label.isEmpty || label.length > 24)) {
    return const [];
  }
  if (labels.toSet().length != labels.length) return const [];
  return labels;
}

/// What a permission asks, for a notification title: an action token
/// (`run`, `edit`, `read`, `folder`) and its short, masked target ("npm
/// install", "settings.dart"). Native puts them into "Run npm install?".
/// Both are empty when the ask names neither a command nor a file, and then
/// the alert cannot be allowed from the shade. Tokens must match
/// `AlertCopy.of` in StatusNotificationModel.kt.
({String action, String target}) permissionAskOf(PermissionRequest request) {
  final command = request.commandPreview;
  if (command != null && command.trim().isNotEmpty) {
    return (action: 'run', target: commandHead(command));
  }
  final path = request.filePath;
  if (path != null && path.trim().isNotEmpty) {
    final action = switch (request.permission.toLowerCase()) {
      'read' => 'read',
      'external_directory' => 'folder',
      _ => 'edit',
    };
    return (action: action, target: _lastSegment(path));
  }
  return (action: '', target: '');
}

/// A command's program and, when it has one, its sub-command: "npm install"
/// for `npm install --save left-pad`, "git push" for `git push origin main`,
/// "pytest" for `/usr/bin/pytest -q`. Masked; at most 40 characters.
String commandHead(String command) {
  final words = redactForNotification(
    command,
    maxLength: 400,
  ).split(' ').where((word) => word.isNotEmpty).toList();
  // Leading `NAME=value` assignments only set the environment.
  while (words.isNotEmpty &&
      RegExp(r'^[A-Za-z_][A-Za-z0-9_]*=').hasMatch(words.first)) {
    words.removeAt(0);
  }
  if (words.isEmpty) return '';
  final program = _lastSegment(words.first);
  final head =
      words.length > 1 && RegExp(r'^[a-z][a-z0-9:_-]*$').hasMatch(words[1])
      ? '$program ${words[1]}'
      : program;
  return redactForNotification(head, maxLength: 40);
}

/// The notification subtitle: "MacBook · api-server". Either part may be
/// missing; empty when both are.
String notificationSubtitle(String? server, String? project) => [
  for (final part in [server, project])
    if (part != null && part.trim().isNotEmpty) part.trim(),
].join(' · ');

/// The last folder or file name of [path]; [path] itself when it has none.
String _lastSegment(String path) {
  final parts = path.trim().split(RegExp(r'[\\/]+'))
    ..removeWhere((part) => part.isEmpty);
  return redactForNotification(
    parts.isEmpty ? path.trim() : parts.last,
    maxLength: 40,
  );
}

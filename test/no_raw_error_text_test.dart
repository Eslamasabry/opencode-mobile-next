// Guard (owner bug report 2026-09-27: a list said "ApiException: upstream
// answered 502 while reading page 2" as its words): lib/ui never shows raw
// exception, native or server text as primary copy. An error says what
// failed in words, offers a way forward, and keeps the technical text under
// Details (productErrorText / productErrorDetails in
// lib/ui/widgets/product_states.dart).
//
// A source scan: it flags the ways raw text reached the screen before —
// `'$error'`, `error.toString()`, and the `.message` of an exception whose
// message is transport or native text — unless the line feeds a Details
// fold. The allowlist names each remaining line and why it is safe (or
// which owner fixes it); an entry that no longer matches fails too, so the
// list only shrinks.

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart' show ApiException;
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/domain/product_failure.dart';

/// Names a caught failure usually has in lib/ui.
const _errorNames = r'(?:e|err|error|failure|caught|exception|cause)';

/// Exceptions whose `.message` is transport, server or native text, never
/// words for people: an HTTP status and body, a Java message, a script's
/// output.
const _rawMessageTypes = <String>{
  'ApiException',
  'Api2Error',
  'Api2AuthRequired',
  'Api2NetworkError',
  'Api2Unavailable',
  'Api2RequestError',
  'PlatformException',
  'MissingPluginException',
  'DioException',
  'TermuxBridgeException',
  'BuiltinLinuxException',
  'LocalAgentFailure',
  'LocalServerControlFailure',
  'FileSystemException',
  'SocketException',
  'HttpException',
};

/// file (relative to lib/ui) → substrings of lines that may stay, and why.
const _allowed = <String, Map<String, String>>{
  // Details only: the raw text goes to a fold or a technical value.
  'screens/agents/agents_text.dart': {
    'ProductFailure.from(error).technicalDetails ?? error.toString();':
        'AgentFailure.technical: shown only in the Details fold '
        '(AgentErrorNotice); the words are agentHostFailureText & co.',
  },
  'widgets/team_host_form.dart': {
    'var text = error.toString();':
        'builds the verdict Details fold text (KitDetailsFold)',
  },
  'widgets/local_agent_server_entry.dart': {
    'localAgentFailureText(l10n, failure.kind, failure.message),':
        'says the failure kind in words; the message is classified inside',
  },
  'kit/kit_scanner.dart': {
    "failure = KitScannerFailure('\$error');":
        'deviceMessage is shown only as details (pairing_scanner_screen)',
  },
  'kit/kit_field.dart': {
    "label: '\${l10n.kitFieldErrorLabel}: \$error',":
        'error here is the field\'s own words (a String), spoken',
  },
  'kit/kit_date_time_picker.dart': {
    "label: '\${l10n.kitFieldErrorLabel}: \$error',":
        'error here is the field\'s own words (a String), spoken',
  },
  'widgets/phone_server_card.dart': {
    'log = error.message;': 'shown in the server log panel, not as words',
  },
  // Transcript export, separately redacted by its local put helper.
  'screens/chat/chat_session_menu.dart': {
    "put('> \${l10n.chatUiError}: \$error\\n');":
        'transcript export text, masked by put through KitRedact; not UI copy',
  },
};

class _Hit {
  _Hit(this.file, this.line, this.text, this.rule);
  final String file;
  final int line;
  final String text;
  final String rule;

  @override
  String toString() => 'lib/ui/$file:$line [$rule] $text';
}

bool _feedsDetails(String line) =>
    RegExp(r'\bdetails?\s*[:=]|productErrorDetails|KitRedact').hasMatch(line);

final _interpolation = RegExp(
  '\\\$(?:$_errorNames\\b(?![.(\\w])|\\{$_errorNames\\})',
);
final _toStringCall = RegExp(
  '\\b(?:$_errorNames|snapshot\\.error\\??)\\.toString\\(\\)',
);
final _typedCatch = RegExp(r'on\s+(\w+)\s+catch\s*\(\s*(\w+)');
final _messageInSwitch = RegExp(
  r'\b(\w+)\(\)\s*=>\s*\w+\.message\b|\b(\w+)\(\s*:final message\s*\)',
);

/// The leaks in one file's [lines].
List<_Hit> _scanLines(String file, List<String> lines) {
  final hits = <_Hit>[];
  // Variables bound by `on RawType catch (v)`, with the depth they end at.
  final rawVars = <String, int>{};
  var depth = 0;
  for (var i = 0; i < lines.length; i++) {
    final line = lines[i];
    if (line.trimLeft().startsWith('//')) continue;
    final caught = [
      for (final match in _typedCatch.allMatches(line))
        if (_rawMessageTypes.contains(match.group(1))) match.group(2)!,
    ];
    void hit(String rule) => hits.add(_Hit(file, i + 1, line.trim(), rule));
    if (!_feedsDetails(line)) {
      if (RegExp(r'\.technicalDetails\b').hasMatch(line)) {
        hit('domain technical details as prose');
      }
      if (_interpolation.hasMatch(line)) hit('interpolated error');
      if (_toStringCall.hasMatch(line)) hit('error.toString()');
      for (final name in rawVars.keys) {
        // An emptiness test reads the message without showing it.
        final shown = RegExp(
          '\\b$name\\.message\\b(?!(\\.trim\\(\\))?\\.is(Not)?Empty)',
        );
        if (shown.hasMatch(line)) {
          hit('raw exception message');
        }
      }
      for (final match in _messageInSwitch.allMatches(line)) {
        final type = match.group(1) ?? match.group(2);
        if (_rawMessageTypes.contains(type)) hit('raw exception message');
      }
    }
    depth += '{'.allMatches(line).length - '}'.allMatches(line).length;
    rawVars.removeWhere((_, opened) => depth <= opened);
    // The catch block opened on this line ends when depth falls back.
    for (final name in caught) {
      rawVars[name] = depth - 1;
    }
  }
  return hits;
}

List<_Hit> _scan() {
  final hits = <_Hit>[];
  for (final entity in Directory('lib/ui').listSync(recursive: true)) {
    if (entity is! File || !entity.path.endsWith('.dart')) continue;
    final file = entity.path.substring('lib/ui/'.length);
    hits.addAll(_scanLines(file, entity.readAsLinesSync()));
  }
  return hits;
}

bool _isAllowed(_Hit hit) =>
    _allowed[hit.file]?.keys.any((line) => hit.text.contains(line)) ?? false;

void main() {
  test('the scan finds the leaks it guards against', () {
    final hits = _scanLines('sample.dart', [
      "KitText('\$error'),",
      'body: error.toString(),',
      "message: 'Failed: \${error}',",
      '} on ApiException catch (e) {',
      '  setState(() => _error = e.message);',
      '}',
      'setState(() => _error = e.message);',
      'Api2Error() => error.message,',
      '} on PlatformException catch (failure) {',
      '  details: failure.message,',
      '}',
      'details: error.toString(),',
      '// KitText(error.toString())',
      'body: failure.technicalDetails,',
      'final details = failure.technicalDetails;',
    ]);
    expect(hits.map((hit) => hit.line), [1, 2, 3, 5, 8, 14]);
  });

  test('the domain mapper never promotes protocol text to authored copy', () {
    for (final status in [400, 401, 404, 409, 422, 429, 500, 503]) {
      final failures = <Object>[
        ApiException('Untrusted server prose', statusCode: status),
        ApiException(
          'Untrusted server prose',
          statusCode: status,
          errorTag: 'SessionRevertPending',
        ),
        Api2RequestError('Untrusted server prose', statusCode: status),
      ];
      for (final error in failures) {
        final failure = ProductFailure.from(error);
        expect(failure.authoredMessage, isNull);
        expect(failure.category, isNot(ProductFailureCategory.words));
        expect(failure.technicalDetails, contains('Untrusted server prose'));
      }
    }
  });

  test('the product error mapper depends on domain categories only', () {
    final source = File(
      'lib/ui/widgets/product_states.dart',
    ).readAsStringSync();
    expect(RegExp(r"/api2?/").hasMatch(source), isFalse);
    expect(source, contains("import '../../domain/product_failure.dart'"));
  });

  test('lib/ui never shows raw exception text as the words', () {
    final leaks = _scan().where((hit) => !_isAllowed(hit)).toList();
    expect(
      leaks,
      isEmpty,
      reason:
          'Show productErrorText(error) as the words and put '
          'productErrorDetails(error) under Details:\n${leaks.join('\n')}',
    );
  });

  test('every allowlist entry still names a line (the list only shrinks)', () {
    final hits = _scan();
    final stale = <String>[
      for (final MapEntry(key: file, value: lines) in _allowed.entries)
        for (final line in lines.keys)
          if (!hits.any((hit) => hit.file == file && hit.text.contains(line)))
            'lib/ui/$file: $line',
    ];
    expect(
      stale,
      isEmpty,
      reason: 'Remove fixed entries:\n${stale.join('\n')}',
    );
  });
}

/// What an agent's own sign-in printed for the person to act on: the page
/// to sign in on and, for a device sign-in, the one-time code to enter
/// there.
///
/// Read from what the terminal shows, never sent anywhere: the screen only
/// turns these into "Open sign-in page" (through the app's link policy) and
/// "Copy code" buttons.
///
/// Real shapes:
/// - fx: `https://vercel.com/oauth/device?user_code=ABCD-EFGH` and the code.
/// - Codex `login --device-auth`: `https://auth.openai.com/codex/device`,
///   then "Enter this one-time code" and `ABCD-12345` on the next line.
/// - Claude: an `https://claude.ai/oauth/authorize?…` page and a prompt to
///   paste a code (nothing to copy).
class AgentSignInOutput {
  const AgentSignInOutput({this.page, this.code});

  /// The newest https page that is not this phone's own (no localhost or
  /// loopback callback).
  final Uri? page;

  /// The one-time code the agent asked the person to enter on [page].
  final String? code;

  bool get isEmpty => page == null && code == null;

  /// [next] read over this one: a page or code that scrolled out of what
  /// was read is kept, and a new page brings its own code (or none).
  AgentSignInOutput merge(AgentSignInOutput next) {
    final newPage = next.page != null && next.page != page;
    return AgentSignInOutput(
      page: next.page ?? page,
      code: newPage ? next.code : (next.code ?? code),
    );
  }

  @override
  bool operator ==(Object other) =>
      other is AgentSignInOutput && other.page == page && other.code == code;

  @override
  int get hashCode => Object.hash(page, code);

  @override
  String toString() => 'AgentSignInOutput(page: $page, code: $code)';
}

/// One terminal row: its text, and whether it continues the row above
/// (the terminal wrapped it there).
typedef AgentSignInRow = ({String text, bool wrapped});

/// Reads [rows] (oldest first) for a sign-in page and code. [width] is the
/// terminal's width in columns; with it, a page a program broke across rows
/// itself (it filled a row to the edge) is put back together.
AgentSignInOutput readAgentSignInOutput(
  Iterable<AgentSignInRow> rows, {
  int? width,
}) {
  final lines = _logicalLines(rows, width);
  Uri? page;
  for (final line in lines) {
    for (final match in _url.allMatches(line.text)) {
      final uri = _signInPage(match.group(0)!);
      if (uri != null) page = uri;
    }
  }
  String? code;
  final fromPage = page?.queryParameters['user_code'];
  if (fromPage != null && _isCode(fromPage)) {
    code = fromPage;
  } else {
    final plain = [for (final line in lines) line.text.replaceAll(_url, ' ')];
    for (var i = 0; i < plain.length; i++) {
      // A code is only one the output calls a code: on that line or the
      // two after it ("Enter this one-time code" then the code).
      final near =
          _mentionsCode(plain, i) ||
          (i > 0 && _mentionsCode(plain, i - 1)) ||
          (i > 1 && _mentionsCode(plain, i - 2));
      if (!near) continue;
      for (final match in _code.allMatches(plain[i])) {
        final token = match.group(0)!;
        if (_isCode(token)) code = token;
      }
    }
  }
  return AgentSignInOutput(page: page, code: code);
}

/// Removes terminal control sequences: colours and cursor moves (CSI),
/// titles and hyperlinks (OSC), and other escapes and control characters.
String stripTerminalControls(String text) => text
    .replaceAll(_osc, '')
    .replaceAll(_csi, '')
    .replaceAll(_escape, '')
    .replaceAll(_control, '');

final _osc = RegExp(r'\x1B\][^\x07\x1B]*(?:\x07|\x1B\\)?');
final _csi = RegExp(r'\x1B\[[0-?]*[ -/]*[@-~]');
final _escape = RegExp(r'\x1B[@-Z\\-_]?');
final _control = RegExp(r'[\x00-\x08\x0B-\x1F\x7F]');

final _url = RegExp(r'''https?://[^\s"'<>`]+''', caseSensitive: false);
final _code = RegExp(
  r'(?<![A-Za-z0-9-])[A-Z0-9]{4,}(?:-[A-Z0-9]{4,})+(?![A-Za-z0-9-])',
);
final _letter = RegExp('[A-Z]');
final _urlChars = RegExp(r'''^[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+$''');
final _urlStructure = RegExp(r'[/?=&%.#]');
const _trailing = '.,;:!?)]}>\'"';

bool _isCode(String token) =>
    token.length <= 24 && _code.hasMatch(token) && _letter.hasMatch(token);

bool _mentionsCode(List<String> lines, int i) =>
    lines[i].toLowerCase().contains('code');

Uri? _signInPage(String raw) {
  var value = raw;
  while (value.isNotEmpty && _trailing.contains(value[value.length - 1])) {
    value = value.substring(0, value.length - 1);
  }
  final uri = Uri.tryParse(value);
  if (uri == null || uri.scheme.toLowerCase() != 'https') return null;
  if (uri.host.isEmpty || uri.userInfo.isNotEmpty) return null;
  if (_isThisPhone(uri.host.toLowerCase())) return null;
  return uri;
}

bool _isThisPhone(String host) {
  if (host == 'localhost' || host.endsWith('.localhost')) return true;
  if (host == '0.0.0.0' || host == '::1' || host == '[::1]') return true;
  return host.startsWith('127.');
}

class _Line {
  _Line(this.text, this.lastRow);
  String text;

  /// The length of the last terminal row this line ends on.
  int lastRow;
}

List<_Line> _logicalLines(Iterable<AgentSignInRow> rows, int? width) {
  final lines = <_Line>[];
  for (final row in rows) {
    final text = stripTerminalControls(row.text).trimRight();
    if (row.wrapped && lines.isNotEmpty) {
      lines.last
        ..text += text
        ..lastRow = text.length;
    } else {
      lines.add(_Line(text, text.length));
    }
  }
  if (width == null || width < 20) return lines;
  // A program that breaks a long page across rows itself fills each row to
  // the edge; the rest follows at the start of the next row.
  final joined = <_Line>[];
  for (final line in lines) {
    final last = joined.isEmpty ? null : joined.last;
    if (last != null && _continuesUrl(last, line.text, width)) {
      last
        ..text += line.text.trimLeft()
        ..lastRow = line.lastRow;
    } else {
      joined.add(line);
    }
  }
  return joined;
}

bool _continuesUrl(_Line above, String next, int width) {
  if (above.lastRow < width - 1) return false;
  final urls = _url.allMatches(above.text);
  if (urls.isEmpty || urls.last.end != above.text.length) return false;
  final rest = next.trim();
  if (rest.isEmpty) return false;
  final first = rest.split(RegExp(r'\s+')).first;
  if (!_urlChars.hasMatch(first)) return false;
  // A whole row of address characters, or a piece with an address's own
  // marks, not the next sentence ("Paste code here").
  return first == rest || _urlStructure.hasMatch(first);
}

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_sign_in_output.dart';

/// Rows as a program printed them: one per line, none wrapped by the
/// terminal.
List<AgentSignInRow> _rows(String text) => [
  for (final line in text.split('\n')) (text: line, wrapped: false),
];

AgentSignInOutput _read(String text, {int? width}) =>
    readAgentSignInOutput(_rows(text), width: width);

void main() {
  group('readAgentSignInOutput', () {
    test('fx: the Vercel device page and its code, through colours', () {
      final found = _read(
        '\x1B[1m> \x1B[22mVisit \x1B[36mhttps://vercel.com/oauth/device'
        '?user_code=ABCD-EFGH\x1B[39m to sign in.\n'
        '  Your code: \x1B[1mABCD-EFGH\x1B[22m',
      );
      expect(
        found.page.toString(),
        'https://vercel.com/oauth/device?user_code=ABCD-EFGH',
      );
      expect(found.code, 'ABCD-EFGH');
    });

    test('Codex device sign-in: the code on the line after "code"', () {
      final found = _read(
        'Follow these steps to sign in with ChatGPT using device code '
        'authorization:\n'
        '\n'
        '1. Open this link in your browser and sign in to your account\n'
        '   \x1B[94mhttps://auth.openai.com/codex/device\x1B[0m\n'
        '\n'
        '2. Enter this one-time code \x1B[90m(expires in 15 minutes)\x1B[0m\n'
        '   \x1B[94mABCD-12345\x1B[0m\n'
        '\n'
        'Device codes are a common phishing target. Never share this code.',
      );
      expect(found.page.toString(), 'https://auth.openai.com/codex/device');
      expect(found.code, 'ABCD-12345');
    });

    test('Claude: its page, and no code to copy', () {
      final found = _read(
        "Browser didn't open? Use the url below to sign in:\n"
        '\n'
        'https://claude.ai/oauth/authorize?code=true&client_id=9d1c250a'
        '&response_type=code&code_challenge=AbCd-EfGh123&state=XyZ\n'
        '\n'
        'Paste code here if prompted >',
      );
      expect(found.page?.host, 'claude.ai');
      expect(found.page?.queryParameters['state'], 'XyZ');
      expect(found.code, isNull);
    });

    test('a page a program broke at the edge is put back together', () {
      const url =
          'https://claude.ai/oauth/authorize?code=true&client_id=9d1c250a'
          '&response_type=code&redirect_uri=https%3A%2F%2Fconsole.anthropic'
          '.com%2Foauth%2Fcode%2Fcallback&state=last';
      const width = 40;
      final pieces = [
        for (var i = 0; i < url.length; i += width)
          url.substring(i, (i + width).clamp(0, url.length)),
      ];
      final found = _read(
        '${pieces.join('\n')}\n\nPaste code here if prompted >',
        width: width,
      );
      expect(found.page.toString(), url);
    });

    test('rows the terminal wrapped are one line', () {
      final found = readAgentSignInOutput([
        (text: 'Visit https://vercel.com/oauth/devi', wrapped: false),
        (text: 'ce?user_code=WXYZ-QRST now', wrapped: true),
      ], width: 35);
      expect(
        found.page.toString(),
        'https://vercel.com/oauth/device?user_code=WXYZ-QRST',
      );
      expect(found.code, 'WXYZ-QRST');
    });

    test('the next sentence is not read as the rest of a page', () {
      const width = 30;
      const line = 'https://auth.example.com/abcde'; // exactly 30 columns
      final found = _read('$line\nPaste code here', width: width);
      expect(found.page.toString(), line);
    });

    test('takes the newest page', () {
      final found = _read(
        'https://vercel.com/oauth/device?user_code=AAAA-BBBB\n'
        'That code expired. Visit\n'
        'https://vercel.com/oauth/device?user_code=CCCC-DDDD',
      );
      expect(found.page?.queryParameters['user_code'], 'CCCC-DDDD');
      expect(found.code, 'CCCC-DDDD');
    });

    test('never a plain http or this phone\'s own page', () {
      expect(
        _read(
          'Listening on http://localhost:1455/auth/callback\n'
          'Open https://localhost:8080/start or https://127.0.0.1/x\n'
          'or https://app.localhost/x or http://example.com/login',
        ).page,
        isNull,
      );
      final found = _read(
        'https://auth.openai.com/codex/device\n'
        'Waiting on http://localhost:1455/auth/callback',
      );
      expect(found.page.toString(), 'https://auth.openai.com/codex/device');
    });

    test('drops sentence punctuation after a page and reads OSC links', () {
      final found = _read(
        'Open (\x1B]8;;https://auth.openai.com/codex/device\x07'
        'https://auth.openai.com/codex/device\x1B]8;;\x07).',
      );
      expect(found.page.toString(), 'https://auth.openai.com/codex/device');
    });

    test('a code is only one the output calls a code', () {
      expect(_read('Mode: READ-ONLY\nBUILD-2026 ok').code, isNull);
      expect(_read('Enter the code:\n  ABCD-EFGH').code, 'ABCD-EFGH');
      expect(_read('code 2026-1007').code, isNull, reason: 'digits only');
    });

    test('merge keeps what scrolled away; a new page brings its code', () {
      const first = AgentSignInOutput(page: null, code: 'ABCD-EFGH');
      final withPage = first.merge(
        AgentSignInOutput(page: Uri.parse('https://a.example/device')),
      );
      expect(withPage.code, isNull);
      final kept = withPage.merge(const AgentSignInOutput());
      expect(kept, withPage);
    });
  });
}

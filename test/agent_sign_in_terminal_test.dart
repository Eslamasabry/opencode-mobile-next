import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/ui/screens/agents/agent_sign_in_terminal.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/agents_fakes.dart';
import 'support/chats_fakes.dart';
import 'support/fake_local_terminal.dart';

const _open = ValueKey('agents-sign-in-terminal-open');
const _copy = ValueKey('agents-sign-in-terminal-copy-code');
const _actions = ValueKey('agents-sign-in-terminal-actions');

class _Run {
  _Run(this.backend, this.opened);
  final FakeLocalTerminalBackend backend;
  final List<String> opened;

  /// The terminal's width once the screen laid it out.
  int get columns {
    final resize = backend.calls.lastWhere((c) => c.startsWith('resize 1 '));
    return int.parse(resize.split(' x ').last);
  }
}

Future<_Run> _pump(WidgetTester tester, String agentId) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final backend = FakeLocalTerminalBackend();
  final opened = <String>[];
  final host = FakeChatsHost(
    FakeChatFeedSource(items: const [], projects: [project('alpha')]),
  );
  await tester.pumpWidget(
    chatsApp(
      host,
      AgentSignInTerminalScreen(
        agents: FakePhoneAgentsSource(
          rows: [agentRowFor(agentId, FakeAgentStage.signedOut)],
        ),
        agentId: agentId,
        agentName: agentId,
        sessions: LocalTerminalSessions(backend: backend),
        openPage: (context, url) async => opened.add(url),
      ),
    ),
  );
  await tester.pumpAndSettle();
  // The screen's size reaches the shell after a short settle.
  await tester.pump(const Duration(milliseconds: 200));
  return _Run(backend, opened);
}

Future<void> _print(WidgetTester tester, _Run run, String text) async {
  run.backend.output(1, text);
  await tester.pump();
  // The shell's output is acknowledged on the next turn.
  await tester.pump(const Duration(milliseconds: 1));
}

/// Everything "Copy code" puts on the clipboard.
List<String> _clipboard(WidgetTester tester) {
  final copied = <String>[];
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    SystemChannels.platform,
    (call) async {
      if (call.method == 'Clipboard.setData') {
        copied.add((call.arguments as Map)['text'] as String);
      }
      return null;
    },
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      null,
    ),
  );
  return copied;
}

void main() {
  setUpAll(loadCaptureFonts);

  testWidgets('fx: its Vercel device page opens and its code copies', (
    tester,
  ) async {
    final run = await _pump(tester, 'fx');
    expect(find.byKey(_actions), findsNothing);
    final copied = _clipboard(tester);
    // Coloured, and longer than the phone's width: the terminal wraps it.
    await _print(
      tester,
      run,
      '\x1B[1m> \x1B[22mVisit \x1B[36mhttps://vercel.com/oauth/device'
      '?user_code=ABCD-EFGH\x1B[39m to sign in\r\n'
      '  Your code: \x1B[1mABCD-EFGH\x1B[22m\r\n',
    );
    expect(find.text('Open sign-in page'), findsOneWidget);
    expect(find.textContaining('Copy code'), findsOneWidget);
    expect(find.textContaining('ABCD-EFGH'), findsOneWidget);

    await tester.tap(find.byKey(_open));
    await tester.pump();
    expect(run.opened, ['https://vercel.com/oauth/device?user_code=ABCD-EFGH']);

    await tester.tap(find.byKey(_copy));
    await tester.pump();
    expect(copied, ['ABCD-EFGH']);
    expect(find.text('Copied'), findsOneWidget);
    await tester.pump(const Duration(seconds: 3));
    expect(find.text('Copied'), findsNothing);
  });

  testWidgets('Codex: its device page and the code on the next line', (
    tester,
  ) async {
    final run = await _pump(tester, 'codex');
    final copied = _clipboard(tester);
    await _print(
      tester,
      run,
      'Welcome to Codex\r\n\r\n'
      'Follow these steps to sign in with ChatGPT using device code '
      'authorization:\r\n\r\n'
      '1. Open this link in your browser and sign in to your account\r\n'
      '   \x1B[94mhttps://auth.openai.com/codex/device\x1B[0m\r\n\r\n'
      '2. Enter this one-time code \x1B[90m(expires in 15 minutes)\x1B[0m\r\n'
      '   \x1B[94mABCD-12345\x1B[0m\r\n\r\n'
      'Device codes are a common phishing target. Never share this code.\r\n',
    );
    await tester.tap(find.byKey(_open));
    await tester.pump();
    expect(run.opened, ['https://auth.openai.com/codex/device']);
    expect(find.textContaining('ABCD-12345'), findsWidgets);
    await tester.tap(find.byKey(_copy));
    await tester.pump();
    expect(copied, ['ABCD-12345']);
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('Claude: a page it broke across rows opens whole, and there '
      'is no code to copy', (tester) async {
    final run = await _pump(tester, 'claude');
    const url =
        'https://claude.ai/oauth/authorize?code=true&client_id=9d1c250a-e61b'
        '&response_type=code&redirect_uri=https%3A%2F%2Fconsole.anthropic.com'
        '%2Foauth%2Fcode%2Fcallback&scope=user%3Ainference'
        '&code_challenge=AbCdEf123&code_challenge_method=S256&state=XyZ789';
    final width = run.columns;
    final pieces = [
      for (var i = 0; i < url.length; i += width)
        url.substring(i, (i + width).clamp(0, url.length)),
    ];
    await _print(
      tester,
      run,
      "Browser didn't open? Use the url below to sign in:\r\n\r\n"
      '${pieces.join('\r\n')}\r\n\r\n'
      'Paste code here if prompted > ',
    );
    expect(find.byKey(_copy), findsNothing);
    await tester.tap(find.byKey(_open));
    await tester.pump();
    expect(run.opened, [url]);
  });

  testWidgets('the newest page wins', (tester) async {
    final run = await _pump(tester, 'fx');
    await _print(
      tester,
      run,
      'https://vercel.com/oauth/device?user_code=AAAA-BBBB\r\n'
      'That code expired.\r\n',
    );
    await _print(
      tester,
      run,
      'https://vercel.com/oauth/device?user_code=CCCC-DDDD\r\n',
    );
    expect(find.textContaining('CCCC-DDDD'), findsOneWidget);
    expect(find.textContaining('AAAA-BBBB'), findsNothing);
    await tester.tap(find.byKey(_open));
    await tester.pump();
    expect(run.opened, ['https://vercel.com/oauth/device?user_code=CCCC-DDDD']);
  });

  testWidgets('plain http and this phone\'s own pages are never offered', (
    tester,
  ) async {
    final run = await _pump(tester, 'codex');
    await _print(
      tester,
      run,
      'Starting local login server on http://localhost:1455.\r\n'
      'If your browser did not open, navigate to this URL:\r\n'
      'https://localhost:1455/auth/start\r\n'
      'https://127.0.0.1:1455/auth/start\r\n'
      'http://auth.example.com/login\r\n',
    );
    expect(find.byKey(_actions), findsNothing);
    expect(find.byKey(_open), findsNothing);
  });

  testWidgets('the buttons leave once the sign-in ends', (tester) async {
    final run = await _pump(tester, 'codex');
    await _print(tester, run, 'https://auth.openai.com/codex/device\r\n');
    expect(find.byKey(_open), findsOneWidget);
    run.backend.exit(1, 1);
    await tester.pumpAndSettle();
    expect(find.byKey(_open), findsNothing);
  });
}

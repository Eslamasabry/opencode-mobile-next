// Gate G12 (docs/ux-system/revamp/STANDARDS.md §18.2 G12; SEC-2, SEC-4,
// SEC-13; kit-v2.md §7): redaction through every technical part.
//
// Fake provider keys and bearer tokens are fed through KitLogPanel,
// KitDetailsFold (and showKitTechnicalDetails), KitCodeBlock,
// KitIconButton.copy / KitAction.copy, the diagnostics list and the Report
// a problem preview (its Copy, Share and GitHub paths). None of the secret
// parts may appear in rendered text (Text, RichText, EditableText,
// tooltips), in semantics labels, on the mocked clipboard, in what is
// handed to the share sheet or the link launcher, or in captured
// `debugPrint` output. The public prefix of a key (`sk-ant-`, `AIza`) may
// stay as a hint (KitRedact).
//
// The keys are fakes built from adjacent literals, so the test source holds
// no whole key, and failure messages name the key's kind, never its value.
//
// The last group is a source scan: `redact: false` (a verbatim copy, SEC-13)
// is only for the person's own content (code, diffs, files, messages, what
// they typed). Every call site in lib/ is in [_verbatimOwnContent] with a
// reason, or in [_verbatimFindings] (a reviewed call site whose text is not
// the person's own, left for its owner). Both lists only shrink: a new call
// site fails, and so does an entry whose count dropped (stale).
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A fake secret: [line] is how it reaches a part; [tail] is the part that
/// must never show (the public prefix may).
class _Fake {
  const _Fake(this.kind, this.line, this.tail);

  /// What it is, for failure messages ("Anthropic key"); never the value.
  final String kind;
  final String line;
  final String tail;

  /// Slices of [tail]: a partial mask still fails.
  Iterable<String> get fragments sync* {
    const width = 10;
    yield tail.substring(0, width);
    final mid = (tail.length - width) ~/ 2;
    yield tail.substring(mid, mid + width);
    yield tail.substring(tail.length - width);
  }
}

// Tails are built from adjacent literals so no whole key is in the source.
const _anthropicTail =
    'api03-'
    'G12Qx7AnthSecret'
    '0a1b2c3d4e';
const _projectTail =
    'G12Wm4ProjSecret'
    '5f6g7h8i9j';
const _legacyTail =
    'G12Zr2LegacySecret'
    '0k1l2m3n4o';
const _routerTail =
    'or-v1-'
    'G12Yt5RouterSecret'
    '5p6q7r8s';
const _googleTail =
    'SyG12Hv8GoogleSecret'
    '9t0u1v2w3x4y5z6A';
const _githubTail =
    'G12Nc3GithubSecret'
    '7B8C9D0E1F2G';
const _bearerTail =
    'G12Pk6BearerSecret'
    '3H4I5J6K7L';
const _inlineBearerTail =
    'G12Lf1InlineSecret'
    '8M9N0O1P2Q';
const _headerTail =
    'G12Jd9HeaderSecret'
    '3R4S5T6U7V';
const _envTail =
    'G12Bs0EnvSecret'
    '8W9X0Y1Z2a';
const _opaqueTail =
    'G12Opaque-horse'
    '-battery-staple';

const _fakes = <_Fake>[
  _Fake('Anthropic key', 'key sk-ant-$_anthropicTail', _anthropicTail),
  _Fake('OpenAI project key', 'using sk-proj-$_projectTail', _projectTail),
  _Fake('OpenAI key', 'OPENAI sk-$_legacyTail', _legacyTail),
  _Fake('OpenRouter key', 'router sk-$_routerTail', _routerTail),
  _Fake('Google key', 'google AIza$_googleTail', _googleTail),
  _Fake('GitHub token', 'gh ghp_$_githubTail', _githubTail),
  _Fake(
    'Authorization header',
    'Authorization: Bearer $_bearerTail',
    _bearerTail,
  ),
  _Fake(
    'bearer token',
    'request failed: Bearer $_inlineBearerTail',
    _inlineBearerTail,
  ),
  _Fake('x-api-key header', 'x-api-key: $_headerTail', _headerTail),
  _Fake('env key', 'ANTHROPIC_API_KEY=$_envTail', _envTail),
  // A server password registered as it loads (no pattern would find it).
  _Fake('registered server password', 'password was $_opaqueTail', _opaqueTail),
];

/// Every fake, one per line.
final String _allLines = _fakes.map((f) => f.line).join('\n');

/// A visible marker next to the secrets, so a check never passes on an
/// empty screen.
const _marker = 'G12 visible context';

/// The kinds whose secret shows in [text].
List<String> _leaksIn(String text) => [
  for (final fake in _fakes)
    if (fake.fragments.any(text.contains)) fake.kind,
];

/// Every string the tree draws or exposes as text.
List<String> _renderedText(WidgetTester tester) => [
  for (final widget in tester.allWidgets)
    ...switch (widget) {
      RichText() => [widget.text.toPlainText()],
      Text() => [widget.data ?? widget.textSpan?.toPlainText() ?? ''],
      EditableText() => [widget.controller.text],
      Tooltip() => [widget.message ?? widget.richMessage?.toPlainText() ?? ''],
      Semantics() => [
        widget.properties.label ?? '',
        widget.properties.value ?? '',
        widget.properties.hint ?? '',
      ],
      _ => const <String>[],
    },
];

/// Fails naming each place a secret reached, never the secret.
void _expectNoLeak(
  WidgetTester tester, {
  required String part,
  required List<String> clipboard,
  required List<String> printed,
  List<String> extra = const [],
}) {
  final problems = <String>[];
  for (final text in _renderedText(tester)) {
    for (final kind in _leaksIn(text)) {
      problems.add('$part renders a $kind');
    }
  }
  for (final fake in _fakes) {
    for (final fragment in fake.fragments) {
      if (find
          .bySemanticsLabel(RegExp(RegExp.escape(fragment)))
          .evaluate()
          .isNotEmpty) {
        problems.add('$part has a ${fake.kind} in a semantics label');
        break;
      }
    }
  }
  for (final text in clipboard) {
    for (final kind in _leaksIn(text)) {
      problems.add('$part copies a $kind to the clipboard');
    }
  }
  for (final text in printed) {
    for (final kind in _leaksIn(text)) {
      problems.add('$part prints a $kind through debugPrint');
    }
  }
  for (final text in extra) {
    for (final kind in _leaksIn(text)) {
      problems.add('$part hands a $kind to the share sheet or link');
    }
  }
  expect(
    problems.toSet().toList(),
    isEmpty,
    reason:
        'G12 (SEC-2, SEC-4): a secret passed through a technical part. '
        'Mask it with KitRedact before it is drawn, copied or printed.',
  );
}

/// Captures `debugPrint` for the length of [body], restoring it after, so
/// the framework's own check that it was restored still passes.
Future<List<String>> _capturePrints(Future<void> Function() body) async {
  final printed = <String>[];
  final saved = debugPrint;
  debugPrint = (String? message, {int? wrapWidth}) {
    if (message != null) printed.add(message);
  };
  try {
    await body();
  } finally {
    debugPrint = saved;
  }
  return printed;
}

/// Records what reaches the clipboard; answers nothing else.
List<String> _clipboard(WidgetTester tester) {
  final copied = <String>[];
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
    if (call.method == 'Clipboard.setData') {
      copied.add((call.arguments as Map)['text'] as String? ?? '');
    }
    return null;
  });
  addTearDown(
    () => messenger.setMockMethodCallHandler(SystemChannels.platform, null),
  );
  return copied;
}

Future<void> _host(WidgetTester tester, Widget child) async {
  tester.view.physicalSize = const Size(1280, 2400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: child,
        ),
      ),
    ),
  );
  await tester.pump();
}

/// Lets a copy's "copied" tick and any hold timers finish.
Future<void> _settleTimers(WidgetTester tester) =>
    tester.pump(const Duration(seconds: 5));

// ---------------------------------------------------------------------------
// The `redact: false` source scan.

/// Verbatim copies of the person's own content (SEC-13): path -> count and
/// why the copied text is theirs.
const _verbatimOwnContent = <String, (int, String)>{
  'lib/ui/kit/kit_viewer.dart': (
    1,
    'Copy contents copies the person\'s own file as it is (SEC-13)',
  ),
  'lib/ui/kit/kit_diff_view.dart': (
    1,
    'copies the selected lines of the person\'s own diff (SEC-13)',
  ),
  'lib/ui/screens/review_workspace.dart': (
    1,
    'Copy patch or Copy file (Review and the read-only diff page) copies the '
        'person\'s own diff or file (SEC-13)',
  ),
  'lib/ui/widgets/file_preview.dart': (
    1,
    'Copy original copies the person\'s own file as it is (SEC-13)',
  ),
  'lib/ui/screens/files_screen.dart': (
    1,
    'copies the review comment the person wrote themselves',
  ),
  'lib/ui/kit/chat/kit_turn.dart': (
    1,
    'the reply footer copies message text, which SEC-13 counts as the '
        'person\'s own content',
  ),
  'lib/main.dart': (
    1,
    'owner coord-main: when sharing into the app fails, Copy text copies the '
        'words the person shared from another app, their own text (SEC-13)',
  ),
  'lib/ui/widgets/agent_blocks.dart': (
    1,
    'an answer option in the conversation, message text the person picks '
        'to paste into their own prompt (SEC-13)',
  ),
  // The chat chain (P3.5), split across the chat library's part files
  // (G31 split rule: the one entry of 4 became these four of 1).
  'lib/ui/screens/chat/chat_message_actions.dart': (
    1,
    'chat chain (P3.5): Copy on a message is the person\'s own text',
  ),
  'lib/ui/screens/chat/chat_composer_region.dart': (
    1,
    'chat chain (P3.5): the composer draft is the person\'s own text',
  ),
  'lib/ui/screens/chat/chat_session_menu.dart': (
    1,
    'chat chain (P3.5): Copy transcript keeps only the person\'s prompts '
        'verbatim and masks everything else (replies, tool output, errors, '
        'the title) through KitRedact before the copy (_transcriptMarkdown). '
        'The share link copy is redacted.',
  ),
  'lib/ui/screens/chat/chat_drafts.dart': (
    1,
    'slice-close-chat: "Copy draft and leave" copies the unsaved draft, '
        'the person\'s own words',
  ),
};

/// Reviewed `redact: false` call sites whose text is NOT the person's own
/// content (G12 findings, 2026-09-27): path -> count, owner and what is
/// copied. Each is for its owner to fix (drop `redact: false`, or split the
/// person's text from the rest); the count only goes down.
const _verbatimFindings = <String, (int, String)>{
  'lib/ui/screens/workspace_screen.dart': (
    1,
    'owner: coordinator (workspace page): the share link is a '
        'server-issued URL, not the person\'s own text',
  ),

  'lib/ui/screens/phone_setup/phone_setup_termux_screen.dart': (
    1,
    'owner: phone setup agent: copies the app-authored Termux setup '
        'command (no secret; not the person\'s own text, so the default '
        'redacted copy would do)',
  ),
  'lib/ui/screens/phone_setup/phone_setup_termux_job_screen.dart': (
    1,
    'owner: phone setup agent: copies TermuxBridge.unlockCommand, an '
        'app-authored constant (no secret; the default redacted copy '
        'would do)',
  ),
};

final _redactFalse = RegExp(r'\bredact\s*:\s*false\b');

/// [source] without comments, string contents kept (a string never holds
/// `redact: false` as code).
String _stripComments(String source) => source
    .replaceAll(RegExp(r'/\*[\s\S]*?\*/'), '')
    .split('\n')
    .map((line) {
      final at = line.indexOf('//');
      return at < 0 ? line : line.substring(0, at);
    })
    .join('\n');

Map<String, int> _verbatimCallSites() {
  final counts = <String, int>{};
  final files =
      Directory('lib')
          .listSync(recursive: true)
          .whereType<File>()
          .where((f) => f.path.endsWith('.dart'))
          .toList()
        ..sort((a, b) => a.path.compareTo(b.path));
  for (final file in files) {
    final path = file.path.replaceAll(r'\', '/');
    final count = _redactFalse
        .allMatches(_stripComments(file.readAsStringSync()))
        .length;
    if (count > 0) counts[path] = count;
  }
  return counts;
}

void main() {
  setUp(() {
    KitRedact.clearKnownSecrets();
    KitRedact.registerKnownSecret(_opaqueTail);
    SharedPreferences.setMockInitialValues({});
  });

  tearDown(KitRedact.clearKnownSecrets);

  test('the fakes are ones the redactor must mask (the gate is not '
      'vacuous)', () {
    for (final fake in _fakes) {
      expect(
        KitRedact.containsSecret(fake.line),
        isTrue,
        reason: '${fake.kind}: KitRedact must recognise this fake',
      );
      expect(
        _leaksIn(KitRedact.text(fake.line)),
        isEmpty,
        reason: '${fake.kind}: KitRedact.text leaves part of it',
      );
    }
    expect(_leaksIn(_allLines), hasLength(_fakes.length));
  });

  testWidgets('the checker sees an unmasked secret in text, semantics, the '
      'clipboard and prints (negative control)', (tester) async {
    final raw = _fakes.first.line;
    await _host(tester, Semantics(label: raw, child: Text(raw)));
    expect(_renderedText(tester).expand(_leaksIn), contains(_fakes.first.kind));
    expect(
      find.bySemanticsLabel(
        RegExp(RegExp.escape(_fakes.first.fragments.first)),
      ),
      findsWidgets,
    );
    final printed = await _capturePrints(() async => debugPrint(raw));
    expect(printed.expand(_leaksIn), contains(_fakes.first.kind));
    expect(
      () => _expectNoLeak(
        tester,
        part: 'control',
        clipboard: [raw],
        printed: printed,
      ),
      throwsA(isA<TestFailure>()),
    );
  });

  testWidgets('KitLogPanel: lines on screen and Copy all are masked', (
    tester,
  ) async {
    final clipboard = _clipboard(tester);
    final printed = await _capturePrints(() async {
      final buffer = ValueNotifier<List<KitLogLine>>([
        const KitLogLine(_marker),
        for (final fake in _fakes) KitLogLine(fake.line),
        for (final fake in _fakes)
          KitLogLine(fake.line, level: KitLogLevel.error),
      ]);
      addTearDown(buffer.dispose);
      await _host(
        tester,
        SizedBox(
          height: 1800,
          child: KitLogPanel(
            lines: buffer,
            size: KitLogSize.fill,
            ended: const KitLogEnd(failed: true, exitCode: 1),
          ),
        ),
      );
      expect(find.textContaining(_marker), findsWidgets);
      await tester.tap(find.byKey(const ValueKey('kit-log-copy-all')));
      await tester.pump();
      await _settleTimers(tester);
    });
    expect(clipboard, isNotEmpty);
    expect(clipboard.last, contains(_marker));
    _expectNoLeak(
      tester,
      part: 'KitLogPanel',
      clipboard: clipboard,
      printed: printed,
    );
  });

  testWidgets('KitDetailsFold: notes, raw text and Copy all are masked', (
    tester,
  ) async {
    final clipboard = _clipboard(tester);
    final printed = await _capturePrints(() async {
      await _host(
        tester,
        KitDetailsFold(
          initiallyExpanded: true,
          notes: [for (final fake in _fakes) '$_marker: ${fake.line}'],
          values: const [KitTechnicalValue('Address', '100.64.0.3:4096')],
          text: '$_marker\n$_allLines',
        ),
      );
      expect(find.textContaining(_marker), findsWidgets);
      await tester.tap(find.byKey(const ValueKey('kit-details-copy-all')));
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('kit-details-copy-0')));
      await tester.pump();
      await _settleTimers(tester);
    });
    expect(clipboard, hasLength(2));
    expect(clipboard.first, contains(_marker));
    _expectNoLeak(
      tester,
      part: 'KitDetailsFold',
      clipboard: clipboard,
      printed: printed,
    );
  });

  testWidgets('KitDetailsFold refuses a secret value without echoing it', (
    tester,
  ) async {
    for (final fake in _fakes.where((f) => f.kind != 'x-api-key header')) {
      await _host(
        tester,
        KitDetailsFold(
          key: ValueKey(fake.kind),
          initiallyExpanded: true,
          values: [KitTechnicalValue('Value', fake.line)],
        ),
      );
      final error = tester.takeException();
      expect(error, isNotNull, reason: '${fake.kind} must trip SEC-4');
      expect(_leaksIn('$error'), isEmpty, reason: 'the assert echoes it');
    }
  });

  testWidgets('showKitTechnicalDetails: the sheet and Copy all are masked', (
    tester,
  ) async {
    final clipboard = _clipboard(tester);
    final printed = await _capturePrints(() async {
      await _host(tester, const SizedBox.shrink());
      final context = tester.element(find.byType(SizedBox).last);
      unawaited(
        showKitTechnicalDetails(
          context,
          title: 'Couldn\'t send',
          text: '$_marker\n$_allLines',
          notes: [for (final fake in _fakes) fake.line],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining(_marker), findsWidgets);
      await tester.tap(find.byKey(const ValueKey('kit-details-copy-all')));
      await tester.pump();
      await _settleTimers(tester);
    });
    expect(clipboard, isNotEmpty);
    _expectNoLeak(
      tester,
      part: 'showKitTechnicalDetails',
      clipboard: clipboard,
      printed: printed,
    );
  });

  for (final kind in [KitCodeKind.output, KitCodeKind.code]) {
    testWidgets('KitCodeBlock (${kind.name}): lines and Copy are masked', (
      tester,
    ) async {
      final clipboard = _clipboard(tester);
      final printed = await _capturePrints(() async {
        await _host(
          tester,
          KitCodeBlock(
            text: '$_marker\n$_allLines',
            kind: kind,
            maxLines: 40,
            highlight: false,
          ),
        );
        expect(find.textContaining(_marker), findsWidgets);
        await tester.tap(find.byKey(const ValueKey('kit-code-copy')));
        await tester.pump();
        await _settleTimers(tester);
      });
      expect(clipboard, hasLength(1));
      expect(clipboard.single, contains(_marker));
      _expectNoLeak(
        tester,
        part: 'KitCodeBlock(${kind.name})',
        clipboard: clipboard,
        printed: printed,
      );
    });
  }

  testWidgets('KitCodeBlock.fill: every drawn line is masked', (tester) async {
    final printed = await _capturePrints(() async {
      tester.view.physicalSize = const Size(1280, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: SizedBox(
              height: 1400,
              child: KitCodeBlock.fill(
                text: '$_marker\n$_allLines',
                highlight: false,
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining(_marker), findsWidgets);
    });
    _expectNoLeak(
      tester,
      part: 'KitCodeBlock.fill',
      clipboard: const [],
      printed: printed,
    );
  });

  testWidgets('KitCodeBlock(command) refuses a real key without echoing it', (
    tester,
  ) async {
    await _host(
      tester,
      KitCodeBlock(
        text: 'curl -H "${_fakes[6].line}"',
        kind: KitCodeKind.command,
      ),
    );
    final error = tester.takeException();
    expect(error, isAssertionError);
    expect(_leaksIn('$error'), isEmpty);
  });

  testWidgets('KitIconButton.copy and KitAction.copy mask by default', (
    tester,
  ) async {
    final clipboard = _clipboard(tester);
    final printed = await _capturePrints(() async {
      await _host(
        tester,
        Column(
          children: [
            KitIconButton.copy(
              key: const ValueKey('g12-icon-copy'),
              text: () => '$_marker\n$_allLines',
              tooltip: 'Copy output',
            ),
            KitButton.fromAction(
              KitAction.copy(
                key: const ValueKey('g12-action-copy'),
                label: 'Copy details',
                text: () => '$_marker\n$_allLines',
              ),
              role: KitButtonRole.secondary,
              expand: false,
            ),
          ],
        ),
      );
      await tester.tap(find.byKey(const ValueKey('g12-icon-copy')));
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('g12-action-copy')));
      await tester.pump();
      await _settleTimers(tester);
    });
    expect(clipboard, hasLength(2));
    for (final copied in clipboard) {
      expect(copied, contains(_marker));
    }
    _expectNoLeak(
      tester,
      part: 'KitIconButton.copy / KitAction.copy',
      clipboard: clipboard,
      printed: printed,
    );
  });

  testWidgets('diagnostics: the kept errors list and an opened entry are '
      'masked', (tester) async {
    final clipboard = _clipboard(tester);
    final diagnostics = AppDiagnosticsController();
    for (final fake in _fakes) {
      diagnostics.record(
        StateError('$_marker ${fake.line}'),
        StackTrace.fromString('#0 send (${fake.line})'),
        source: 'gateway',
      );
    }
    final printed = await _capturePrints(() async {
      tester.view.physicalSize = const Size(1280, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: AppDiagnosticsScreen(
            diagnostics: diagnostics,
            version: () async => '9.9.9+1',
            linkLauncher: (_) async => true,
            share: (_, _) async => true,
          ),
        ),
      );
      await tester.pumpAndSettle();
      final entries = find.byWidgetPredicate(
        (w) =>
            w.key is ValueKey<String> &&
            (w.key! as ValueKey<String>).value.startsWith('diagnostic-entry-'),
      );
      expect(entries, findsWidgets);
      for (final entry in entries.evaluate().toList()) {
        final finder = find.byWidget(entry.widget);
        await tester.ensureVisible(finder);
        await tester.tap(finder);
        await tester.pumpAndSettle();
      }
      expect(find.textContaining(_marker), findsWidgets);
    });
    _expectNoLeak(
      tester,
      part: 'diagnostics list',
      clipboard: clipboard,
      printed: printed,
    );
  });

  testWidgets('Report a problem: the preview, Copy, Share and the GitHub '
      'link carry no secret', (tester) async {
    final clipboard = _clipboard(tester);
    final handedOut = <String>[];
    final diagnostics = AppDiagnosticsController();
    for (final fake in _fakes) {
      diagnostics.record(StateError(fake.line), null, source: 'flutter');
    }
    // A caller that forgot to redact its report: the page must not trust it.
    final report = KitReport(
      title: 'Couldn\'t load files',
      details: '$_marker\n$_allLines',
      source: 'files-viewer',
      errorType: 'HttpException',
      log: _allLines,
    );
    final printed = await _capturePrints(() async {
      tester.view.physicalSize = const Size(1280, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: AppDiagnosticsScreen(
            diagnostics: diagnostics,
            error: report,
            version: () async => '9.9.9+1',
            linkLauncher: (uri) async {
              handedOut
                ..add(uri.toString())
                ..add(Uri.decodeFull(uri.toString()));
              return true;
            },
            share: (text, subject) async {
              handedOut
                ..add(text)
                ..add(subject);
              return true;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      // The person pastes a key into their own description: the field shows
      // what they typed, but nothing that leaves the phone may carry it.
      await tester.enterText(
        find.descendant(
          of: find.byKey(const ValueKey('report-problem-description')),
          matching: find.byType(EditableText),
        ),
        '$_marker ${_fakes.first.line}',
      );
      await tester.pumpAndSettle();

      Future<void> review() async {
        final button = find.byKey(const ValueKey('report-problem-review'));
        await tester.ensureVisible(button);
        await tester.pumpAndSettle();
        await tester.tap(button);
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey('report-problem-preview-sheet')),
          findsOneWidget,
        );
      }

      Future<void> tapInSheet(String key) async {
        final button = find.byKey(ValueKey(key));
        await tester.ensureVisible(button);
        await tester.pumpAndSettle();
        await tester.tap(button);
        await tester.pumpAndSettle();
      }

      await review();
      final preview = tester
          .widget<KitText>(
            find.byKey(const ValueKey('report-problem-preview-text')),
          )
          .text;
      expect(preview, contains(_marker));
      expect(
        _leaksIn(preview),
        isEmpty,
        reason: 'the preview is exactly what is sent',
      );
      // The sheet's own rendered text, before the field is on screen again.
      final sheetText = [
        for (final widget in tester.allWidgets)
          if (widget is RichText) widget.text.toPlainText(),
      ].where((t) => !t.startsWith('$_marker ${_fakes.first.line}'));
      for (final text in sheetText) {
        expect(_leaksIn(text), isEmpty, reason: 'the preview sheet renders');
      }

      await tapInSheet('report-problem-copy');
      await _settleTimers(tester);

      await tapInSheet('report-problem-share');
      await _settleTimers(tester);

      await tester.enterText(
        find.descendant(
          of: find.byKey(const ValueKey('report-problem-description')),
          matching: find.byType(EditableText),
        ),
        '$_marker ${_fakes.first.line}',
      );
      await tester.pumpAndSettle();
      await review();
      await tapInSheet('report-problem-open-github');
      await tester.tap(find.text('Open link'));
      await tester.pumpAndSettle();
      await _settleTimers(tester);
    });
    expect(clipboard, isNotEmpty, reason: 'Copy reached the clipboard');
    expect(handedOut, isNotEmpty, reason: 'Share and GitHub were used');
    // The description field itself holds what the person typed; every
    // other drawn string, and everything handed out, is checked.
    final field = find.descendant(
      of: find.byKey(const ValueKey('report-problem-description')),
      matching: find.byType(EditableText),
    );
    if (field.evaluate().isNotEmpty) {
      tester.widget<EditableText>(field).controller.clear();
      await tester.pumpAndSettle();
    }
    _expectNoLeak(
      tester,
      part: 'Report a problem',
      clipboard: clipboard,
      printed: printed,
      extra: handedOut,
    );
  });

  group('redact: false is only for the person\'s own content (SEC-13)', () {
    test('every verbatim copy in lib/ is reviewed; the lists only shrink', () {
      final sites = _verbatimCallSites();
      final problems = <String>[];
      final reviewed = {..._verbatimOwnContent, ..._verbatimFindings};
      for (final MapEntry(key: path, value: count) in sites.entries) {
        final entry = reviewed[path];
        if (entry == null) {
          problems.add(
            '$path: $count new `redact: false` — only the person\'s own '
            'content (code, diffs, files, messages, what they typed) copies '
            'verbatim; add it to _verbatimOwnContent with why, or drop '
            'redact: false',
          );
        } else if (count > entry.$1) {
          problems.add(
            '$path: $count `redact: false`, reviewed ${entry.$1} — review '
            'the new call site',
          );
        }
      }
      for (final MapEntry(key: path, value: entry) in reviewed.entries) {
        final count = sites[path] ?? 0;
        if (count < entry.$1) {
          problems.add(
            '$path: stale — now $count `redact: false`, listed ${entry.$1}; '
            'shrink the entry (remove it at 0)',
          );
        }
      }
      expect(problems, isEmpty, reason: problems.join('\n'));
    });

    test('every reviewed entry gives a real reason', () {
      for (final MapEntry(key: path, value: entry) in {
        ..._verbatimOwnContent,
        ..._verbatimFindings,
      }.entries) {
        expect(entry.$1, greaterThan(0), reason: path);
        expect(entry.$2.trim().length, greaterThan(10), reason: path);
      }
      for (final MapEntry(key: path, value: entry)
          in _verbatimFindings.entries) {
        expect(
          entry.$2,
          contains('owner'),
          reason: '$path: a finding names its owner',
        );
      }
      expect(
        _verbatimOwnContent.keys.toSet().intersection(
          _verbatimFindings.keys.toSet(),
        ),
        isEmpty,
      );
    });

    test('the scan finds redact: false in code, not in comments', () {
      expect(
        _redactFalse
            .allMatches(
              _stripComments(
                '// redact: false in a comment\n'
                '/* redact: false */\n'
                'KitCopy.copy(context, text, redact: false);\n'
                'KitIconButton.copy(text: t, redact:false)',
              ),
            )
            .length,
        2,
      );
    });
  });
}

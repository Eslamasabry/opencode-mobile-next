import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/completion_digest.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/widgets/completion_digest.dart';

const _digest = CompletionDigest(
  sessionID: 'session-1',
  idleAt: 100,
  changedFiles: 0,
  pendingDecisions: 0,
);

Widget _app(
  CompletionDigest digest, {
  TextScaler textScaler = const TextScaler.linear(1),
  TextDirection textDirection = TextDirection.ltr,
}) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(textScaler: textScaler),
    child: Directionality(textDirection: textDirection, child: child!),
  ),
  home: Scaffold(
    body: SingleChildScrollView(
      child: CompletionDigestCard(
        digest: digest,
        onOpenConversation: _noop,
        onReview: _noop,
        onDismiss: _noop,
      ),
    ),
  ),
);

void _noop() {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null);
  });

  testWidgets('keeps reported zero distinct from unavailable metadata', (
    tester,
  ) async {
    await tester.pumpWidget(_app(_digest));
    await tester.pump();

    expect(
      find.text('No changed files in the session total; this run is unknown.'),
      findsOneWidget,
    );
    expect(
      find.text('No pending decisions in the current cache.'),
      findsOneWidget,
    );

    await tester.pumpWidget(
      _app(
        const CompletionDigest(
          sessionID: 'session-2',
          idleAt: 200,
          changedFiles: null,
          pendingDecisions: null,
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Changed files: unknown.'), findsOneWidget);
    expect(find.text('Pending decisions: unknown.'), findsOneWidget);
    expect(
      find.text('No changed files in the session total; this run is unknown.'),
      findsNothing,
    );
  });

  testWidgets('copy reports success and keeps the digest metadata-only', (
    tester,
  ) async {
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copied = (call.arguments as Map<Object?, Object?>)['text'] as String;
        }
        return null;
      },
    );

    await tester.pumpWidget(_app(_digest));
    await tester.pump();
    await tester.tap(find.byKey(const Key('completion-digest-copy')));
    await tester.pump();

    expect(find.text('Digest copied'), findsOneWidget);
    expect(copied, contains('No changed files in the session total'));
    expect(copied, contains('this run is unknown'));
    expect(copied, contains('Tool outcomes and remaining tasks: unknown.'));
    expect(copied, isNot(contains('successful')));
  });

  testWidgets('copy reports a failure when the platform clipboard rejects it', (
    tester,
  ) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          throw PlatformException(code: 'clipboard-unavailable');
        }
        return null;
      },
    );

    await tester.pumpWidget(_app(_digest));
    await tester.pump();
    await tester.tap(find.byKey(const Key('completion-digest-copy')));
    await tester.pump();

    expect(find.text('Could not copy digest'), findsOneWidget);
  });

  testWidgets('wraps actions at large text on a narrow RTL surface', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      _app(
        _digest,
        textScaler: const TextScaler.linear(2.5),
        textDirection: TextDirection.rtl,
      ),
    );
    await tester.pump();

    expect(tester.takeException(), isNull);
    expect(find.byKey(const Key('completion-digest-card')), findsOneWidget);
    final cardContext = tester.element(
      find.byKey(const Key('completion-digest-card')),
    );
    expect(Directionality.of(cardContext), TextDirection.rtl);
    expect(MediaQuery.textScalerOf(cardContext).scale(10), 25);
  });
}

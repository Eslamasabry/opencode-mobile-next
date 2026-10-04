import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/external_link.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('safeExternalLinkUri', () {
    test('accepts https and normalizes the scheme case', () {
      expect(
        safeExternalLinkUri('https://example.com/a?b=c#d').toString(),
        'https://example.com/a?b=c#d',
      );
      expect(safeExternalLinkUri('HTTPS://example.com/')?.scheme, 'https');
      expect(
        safeExternalLinkUri('  https://example.com/ ')?.host,
        'example.com',
      );
    });

    test('accepts http so the caller can warn, not so it opens silently', () {
      expect(safeExternalLinkUri('http://example.com/')?.scheme, 'http');
    });

    test('refuses every scheme a link label cannot describe', () {
      for (final value in const [
        'javascript:alert(1)',
        'JavaScript:alert(1)',
        'data:text/html,<script>alert(1)</script>',
        'file:///etc/passwd',
        'content://com.android.providers.downloads/all_downloads/1',
        'intent://scan/#Intent;scheme=zxing;end',
        'opencode://settings',
        'tel:+15550100',
        'mailto:someone@example.com',
        '//example.com/protocol-relative',
        '/just/a/path',
      ]) {
        expect(safeExternalLinkUri(value), isNull, reason: value);
      }
    });

    test('refuses embedded credentials in any form', () {
      expect(safeExternalLinkUri('https://user:pass@example.com/'), isNull);
      expect(safeExternalLinkUri('https://user@example.com/'), isNull);
      // Percent-encoded userinfo is still userinfo once parsed.
      expect(safeExternalLinkUri('https://a%40b:c@example.com/'), isNull);
    });

    test('refuses hostless, empty, and unparseable values', () {
      expect(safeExternalLinkUri(null), isNull);
      expect(safeExternalLinkUri(''), isNull);
      expect(safeExternalLinkUri('   '), isNull);
      expect(safeExternalLinkUri('https://'), isNull);
      expect(safeExternalLinkUri('https:///path'), isNull);
      expect(safeExternalLinkUri('not a url at all'), isNull);
      expect(safeExternalLinkUri('http://[oops'), isNull);
    });

    test('refuses an overlong URL rather than passing it to the platform', () {
      final long = 'https://example.com/${'a' * 2048}';
      expect(safeExternalLinkUri(long), isNull);
      expect(
        safeExternalLinkUri('https://example.com/${'a' * 2000}'),
        isNotNull,
      );
    });
  });

  group('externalLinkHost', () {
    test('names the port only when the URL does', () {
      expect(
        externalLinkHost(Uri.parse('https://example.com/a')),
        'example.com',
      );
      expect(
        externalLinkHost(Uri.parse('https://example.com:8443/a')),
        'example.com:8443',
      );
    });
  });

  group('openAgentSignInPage', () {
    Future<Uri?> open(WidgetTester tester, String value) async {
      Uri? launched;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () => openAgentSignInPage(
                  context,
                  value,
                  launcher: (uri) async {
                    launched = uri;
                    return true;
                  },
                ),
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      return launched;
    }

    testWidgets(
      'the verified Claude sign-in page opens without a second sheet',
      (tester) async {
        const page = 'https://claude.com/cai/oauth/authorize?state=s&code=true';
        expect(await open(tester, page), Uri.parse(page));
        expect(find.text('Open external link?'), findsNothing);
      },
    );

    testWidgets('any other address still asks first', (tester) async {
      expect(await open(tester, 'https://claude.com.evil.example/cai'), isNull);
      expect(find.text('Open external link?'), findsOneWidget);
    });
  });

  group('openExternalLink', () {
    Future<ExternalLinkOutcome?> tap(
      WidgetTester tester,
      String? value, {
      Future<bool> Function(Uri uri)? launcher,
    }) async {
      ExternalLinkOutcome? outcome;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () async {
                  outcome = await openExternalLink(
                    context,
                    value,
                    launcher: launcher,
                  );
                },
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      return outcome;
    }

    testWidgets('blocks a hostile scheme without asking the platform', (
      tester,
    ) async {
      var launched = false;
      final outcome = await tap(
        tester,
        'javascript:alert(1)',
        launcher: (_) async {
          launched = true;
          return true;
        },
      );
      expect(outcome, ExternalLinkOutcome.blocked);
      expect(launched, isFalse);
      expect(find.textContaining('Link blocked'), findsOneWidget);
    });

    testWidgets('https still asks before leaving the app', (tester) async {
      Uri? launched;
      await tap(
        tester,
        'https://example.com/a',
        launcher: (uri) async {
          launched = uri;
          return true;
        },
      );
      expect(find.text('Open external link?'), findsOneWidget);
      expect(find.text('Opens example.com outside this app.'), findsOneWidget);
      expect(launched, isNull);

      await tester.tap(find.text('Open link'));
      await tester.pumpAndSettle();
      expect(launched, Uri.parse('https://example.com/a'));
    });

    testWidgets('declining the confirmation opens nothing', (tester) async {
      var launched = false;
      await tap(
        tester,
        'https://example.com/a',
        launcher: (_) async {
          launched = true;
          return true;
        },
      );
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(launched, isFalse);
    });

    testWidgets('reports when no app can handle an allowed link', (
      tester,
    ) async {
      await tap(tester, 'https://example.com/a', launcher: (_) async => false);
      await tester.tap(find.text('Open link'));
      await tester.pumpAndSettle();
      expect(find.text('No app could open this link.'), findsOneWidget);
    });

    testWidgets('reports a launcher failure instead of throwing', (
      tester,
    ) async {
      await tap(
        tester,
        'https://example.com/a',
        launcher: (_) async => throw StateError('no browser'),
      );
      await tester.tap(find.text('Open link'));
      await tester.pumpAndSettle();
      expect(find.text("Couldn't open link"), findsOneWidget);
    });

    testWidgets('never answers with a snackbar (KIT-34)', (tester) async {
      await tap(tester, 'javascript:alert(1)');
      expect(find.byType(SnackBar), findsNothing);
      expect(
        find.text(
          'This app opens only https:// links, and http:// links after you '
          'confirm.',
        ),
        findsOneWidget,
      );
    });

    testWidgets('insecure http: "Don\'t open" is the way back', (tester) async {
      var launched = false;
      final outcome = tap(
        tester,
        'http://docs.example/path',
        launcher: (_) async {
          launched = true;
          return true;
        },
      );
      await outcome;
      expect(find.text('Open insecure HTTP link?'), findsOneWidget);
      expect(find.textContaining('HTTP is not encrypted'), findsOneWidget);
      await tester.tap(find.text("Don't open"));
      await tester.pumpAndSettle();
      expect(launched, isFalse);
    });

    testWidgets('copy the link instead copies the full address and opens '
        'nothing', (tester) async {
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            copied = (call.arguments as Map)['text'] as String?;
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
      var launched = false;
      ExternalLinkOutcome? outcome;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () async {
                  outcome = await openExternalLink(
                    context,
                    'https://example.com/a?b=c',
                    launcher: (_) async {
                      launched = true;
                      return true;
                    },
                  );
                },
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('external-link-copy')));
      await tester.pumpAndSettle();
      expect(copied, 'https://example.com/a?b=c');
      expect(launched, isFalse);
      expect(outcome, ExternalLinkOutcome.cancelled);
    });

    testWidgets('credential link copy masks its secret and opens '
        'nothing', (tester) async {
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            copied = (call.arguments as Map)['text'] as String?;
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
      var launched = false;
      ExternalLinkOutcome? outcome;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () async {
                  outcome = await openExternalLink(
                    context,
                    'https://example.com/a?api_key=AUDIT_FAKE_KEY',
                    launcher: (_) async {
                      launched = true;
                      return true;
                    },
                  );
                },
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('external-link-copy')));
      await tester.pumpAndSettle();
      expect(copied, isNotNull);
      expect(
        copied!.contains('AUDIT_FAKE_KEY'),
        isFalse,
        reason: 'credential reached clipboard',
      );
      expect(launched, isFalse);
      expect(outcome, ExternalLinkOutcome.cancelled);
    });

    for (final suffix in const [
      '?%74oken=AUDIT_ENCODED_CREDENTIAL',
      '?code=AUDIT_OAUTH_CREDENTIAL',
      '#state=AUDIT_OAUTH_STATE',
      '#/callback?code=AUDIT_FRAGMENT_CODE',
    ]) {
      testWidgets('credential URI component stays out of Details and copy: '
          '${suffix.split('=').first}', (tester) async {
        final secret = suffix.split('=').last;
        final address = 'https://example.com/callback$suffix';
        String? copied;
        tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          (call) async {
            if (call.method == 'Clipboard.setData') {
              copied = (call.arguments as Map)['text'] as String?;
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
        var launched = false;
        await tap(
          tester,
          address,
          launcher: (_) async {
            launched = true;
            return true;
          },
        );
        await tester.tap(find.text('Details'));
        await tester.pumpAndSettle();
        expect(find.textContaining(secret), findsNothing);
        await tester.tap(find.byKey(const ValueKey('external-link-copy')));
        await tester.pumpAndSettle();
        expect(copied, isNotNull);
        expect(copied!.contains(secret), isFalse);
        expect(launched, isFalse);

        Uri? opened;
        await tap(
          tester,
          address,
          launcher: (uri) async {
            opened = uri;
            return true;
          },
        );
        await tester.tap(find.text('Open link'));
        await tester.pumpAndSettle();
        expect(opened, Uri.parse(address));
      });
    }

    testWidgets('the full address is one tap away under Details', (
      tester,
    ) async {
      await tap(tester, 'https://example.com/deep/path?x=1');
      expect(find.text('https://example.com/deep/path?x=1'), findsNothing);
      await tester.tap(find.text('Details'));
      await tester.pumpAndSettle();
      expect(find.text('Full address'), findsOneWidget);
      expect(
        find.textContaining('https://example.com/deep/path?x=1'),
        findsOneWidget,
      );
    });
  });
}

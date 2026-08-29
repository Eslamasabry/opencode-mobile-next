import 'package:flutter/material.dart';
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
      expect(safeExternalLinkUri('  https://example.com/ ')?.host,
          'example.com');
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
      expect(safeExternalLinkUri('https://example.com/${'a' * 2000}'),
          isNotNull);
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
      expect(find.text('example.com'), findsOneWidget);
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
      expect(find.textContaining('Could not open link'), findsOneWidget);
    });
  });
}

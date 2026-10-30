import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/dev_preview.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/dev_preview_screen.dart';
import 'package:opencode_mobile/ui/widgets/session_menu.dart';
import 'package:webview_flutter_platform_interface/webview_flutter_platform_interface.dart';

import 'support/fake_webview_platform.dart';
import 'support/fake_server_capabilities.dart';

Widget _app(Widget home) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

const _link = DevPreviewLink(port: 5173);

DevPreviewTarget _phone() =>
    DevPreviewTarget.resolve(_link, serverBaseUrl: 'http://127.0.0.1:4096');

DevPreviewTarget _remote() =>
    DevPreviewTarget.resolve(_link, serverBaseUrl: 'http://100.64.1.2:4096');

void main() {
  late FakeWebViewPlatform platform;
  setUp(() => platform = FakeWebViewPlatform.install());

  Future<void> pumpScreen(
    WidgetTester tester,
    DevPreviewTarget target, {
    Future<bool> Function(Uri uri)? launcher,
    bool supported = true,
  }) async {
    await tester.pumpWidget(
      _app(
        DevPreviewScreen(
          target: target,
          supported: supported,
          launcher: launcher,
        ),
      ),
    );
    // The page finishes loading, as a dev server's would; until then the
    // loading bar animates and the tree never settles.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    platform.delegate?.onFinished?.call(target.uri.toString());
    await tester.pumpAndSettle();
  }

  group('the preview loads the phone\'s own server', () {
    testWidgets('loads the loopback address, no bridge, JavaScript on', (
      tester,
    ) async {
      await pumpScreen(tester, _phone());
      final web = platform.controller!;
      expect(web.loaded.single.toString(), 'http://127.0.0.1:5173/');
      expect(web.javaScript, JavaScriptMode.unrestricted);
      expect(web.channels, isEmpty, reason: 'no JavaScript bridge to the app');
      // Cookies, cache and storage start clean.
      expect(web.cacheClears, greaterThanOrEqualTo(1));
      expect(web.storageClears, greaterThanOrEqualTo(1));
      expect(platform.cookieClears, greaterThanOrEqualTo(1));
      // The bar says "Preview" alone; the address is under Details.
      expect(
        tester
            .widget<Text>(
              find.descendant(
                of: find.byKey(const ValueKey('dev-preview-title')),
                matching: find.byType(Text),
              ),
            )
            .data,
        'Preview',
      );
      expect(find.textContaining('localhost:5173'), findsNothing);
      expect(find.textContaining('127.0.0.1'), findsNothing);
      expect(
        find.byKey(const ValueKey('dev-preview-remote-note')),
        findsNothing,
      );
    });

    testWidgets('closing clears cookies and storage again', (tester) async {
      await pumpScreen(tester, _phone());
      final before = platform.cookieClears;
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
      expect(platform.cookieClears, greaterThan(before));
    });

    testWidgets('Reload asks the page to reload', (tester) async {
      await pumpScreen(tester, _phone());
      await tester.tap(find.byKey(const ValueKey('dev-preview-reload')));
      await tester.pump();
      expect(platform.controller!.reloads, 1);
    });
  });

  group('a remote server', () {
    testWidgets(
      'loads the server host; the 0.0.0.0 note is under Details and on failure',
      (tester) async {
        await pumpScreen(tester, _remote());
        expect(
          platform.controller!.loaded.single.toString(),
          'http://100.64.1.2:5173/',
        );
        // A page that answers needs no note.
        expect(
          find.byKey(const ValueKey('dev-preview-remote-note')),
          findsNothing,
        );
        await tester.tap(find.byKey(const ValueKey('dev-preview-title')));
        await tester.pumpAndSettle();
        expect(find.byKey(const ValueKey('dev-preview-details')), findsOne);
        expect(find.textContaining('0.0.0.0'), findsOneWidget);
        expect(find.textContaining('http://100.64.1.2:5173/'), findsOneWidget);
        await tester.tapAt(const Offset(10, 10));
        await tester.pumpAndSettle();
        platform.delegate!.onError!(
          const WebResourceError(
            errorCode: -6,
            description: 'refused',
            isForMainFrame: true,
          ),
        );
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey('dev-preview-remote-note')),
          findsOneWidget,
        );
      },
    );
  });

  group('ask for a change', () {
    Future<List<String>> pumpWithConversation(
      WidgetTester tester, {
      Object? fail,
      DevPreviewDelivery delivery = DevPreviewDelivery.now,
    }) async {
      final sent = <String>[];
      await tester.pumpWidget(
        _app(
          DevPreviewScreen(
            target: _phone(),
            supported: true,
            voiceSupported: false,
            conversation: DevPreviewConversation(
              delivery: () => delivery,
              send: (text) async {
                if (fail != null) throw fail;
                sent.add(text);
              },
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      platform.delegate?.onFinished?.call(_phone().uri.toString());
      await tester.pumpAndSettle();
      return sent;
    }

    final field = find.byKey(const ValueKey('dev-preview-ask-field'));
    final send = find.byKey(const ValueKey('dev-preview-ask-send'));

    testWidgets('no conversation, no field', (tester) async {
      await pumpScreen(tester, _phone());
      expect(field, findsNothing);
    });

    testWidgets('sends the words into the conversation and says so', (
      tester,
    ) async {
      final sent = await pumpWithConversation(tester);
      expect(find.text('Ask for a change…'), findsOneWidget);
      await tester.enterText(field, '  Make the ball faster  ');
      await tester.pump();
      await tester.tap(send);
      await tester.pumpAndSettle();
      expect(sent, ['Make the ball faster']);
      expect(find.text('Sent to the conversation'), findsOneWidget);
      expect(find.text('Make the ball faster'), findsNothing);
      // The line leaves by itself.
      await tester.pump(const Duration(seconds: 6));
      await tester.pumpAndSettle();
      expect(find.text('Sent to the conversation'), findsNothing);
    });

    testWidgets('while the agent works it says the words joined the work', (
      tester,
    ) async {
      await pumpWithConversation(
        tester,
        delivery: DevPreviewDelivery.joinsTurn,
      );
      await tester.enterText(field, 'Bigger paddle');
      await tester.pump();
      await tester.tap(send);
      await tester.pumpAndSettle();
      expect(find.text('Added to what the agent is doing'), findsOneWidget);
      await tester.pump(const Duration(seconds: 6));
    });

    testWidgets('a failed send keeps the words and never shows the error', (
      tester,
    ) async {
      final sent = await pumpWithConversation(
        tester,
        fail: StateError('HTTP 502 upstream'),
      );
      await tester.enterText(field, 'Bigger paddle');
      await tester.pump();
      await tester.tap(send);
      await tester.pumpAndSettle();
      expect(sent, isEmpty);
      expect(find.text("Didn't send. Your words are still here"), findsOne);
      expect(find.text('Bigger paddle'), findsOneWidget);
      expect(find.textContaining('502'), findsNothing);
      await tester.pump(const Duration(seconds: 6));
    });

    testWidgets('Send is off until there are words', (tester) async {
      final sent = await pumpWithConversation(tester);
      await tester.tap(send);
      await tester.pumpAndSettle();
      expect(sent, isEmpty);
    });
  });

  group('staying on one origin', () {
    testWidgets('the same origin loads in the preview', (tester) async {
      await pumpScreen(tester, _phone());
      expect(
        await platform.delegate!.navigate('http://127.0.0.1:5173/about'),
        NavigationDecision.navigate,
      );
    });

    testWidgets(
      'another address does not load here; it goes to the link gate',
      (tester) async {
        Uri? launched;
        await pumpScreen(
          tester,
          _phone(),
          launcher: (uri) async {
            launched = uri;
            return true;
          },
        );
        final decision = await platform.delegate!.navigate(
          'https://example.com/docs',
        );
        expect(decision, NavigationDecision.prevent);
        await tester.pumpAndSettle();
        // The same confirmation every outside link gets.
        expect(find.text('Open external link?'), findsOneWidget);
        expect(launched, isNull);
        await tester.tap(find.text('Open link'));
        await tester.pumpAndSettle();
        expect(launched.toString(), 'https://example.com/docs');
      },
    );

    testWidgets('a hostile scheme is refused and shows the blocked answer', (
      tester,
    ) async {
      await pumpScreen(tester, _phone());
      expect(
        await platform.delegate!.navigate('intent://x#Intent;scheme=a;end'),
        NavigationDecision.prevent,
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('external-link-blocked')),
        findsOneWidget,
      );
    });

    testWidgets('a sub-frame to another address is dropped quietly', (
      tester,
    ) async {
      await pumpScreen(tester, _phone());
      expect(
        await platform.delegate!.navigate(
          'https://ads.example/x',
          mainFrame: false,
        ),
        NavigationDecision.prevent,
      );
      await tester.pumpAndSettle();
      expect(find.text('Open external link?'), findsNothing);
    });
  });

  group('bar actions', () {
    testWidgets(
      'Open in browser goes through the external-link gate with the current page',
      (tester) async {
        Uri? launched;
        await pumpScreen(
          tester,
          _phone(),
          launcher: (uri) async {
            launched = uri;
            return true;
          },
        );
        platform.delegate!.onUrl!(
          const UrlChange(url: 'http://127.0.0.1:5173/deep'),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(const ValueKey('dev-preview-browser')));
        await tester.pumpAndSettle();
        expect(find.text('Open insecure HTTP link?'), findsOneWidget);
        expect(launched, isNull, reason: 'nothing opens without the gate');
        await tester.tap(find.text('Open HTTP link'));
        await tester.pumpAndSettle();
        expect(launched.toString(), 'http://127.0.0.1:5173/deep');
      },
    );
  });

  group('a page that does not answer', () {
    testWidgets('says so in plain words and Try again reloads', (tester) async {
      await pumpScreen(tester, _phone());
      platform.delegate!.onError!(
        const WebResourceError(
          errorCode: -6,
          description: 'net::ERR_CONNECTION_REFUSED',
          isForMainFrame: true,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Nothing is answering yet'), findsOneWidget);
      expect(find.textContaining('ERR_CONNECTION_REFUSED'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('dev-preview-retry')));
      await tester.pump();
      expect(platform.controller!.reloads, 1);
    });

    testWidgets('a failed sub-resource does not hide the page', (tester) async {
      await pumpScreen(tester, _phone());
      platform.delegate!.onError!(
        const WebResourceError(
          errorCode: -2,
          description: 'x',
          isForMainFrame: false,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Nothing is answering yet'), findsNothing);
    });
  });

  group('where there is no web view', () {
    testWidgets('explains and offers the browser instead', (tester) async {
      await pumpScreen(tester, _phone(), supported: false);
      expect(find.text("Preview isn't available here"), findsOneWidget);
      expect(platform.controller, isNull);
      expect(find.byKey(const ValueKey('dev-preview-browser')), findsWidgets);
    });
  });

  group('openDevPreview', () {
    Future<void> tapOpen(
      WidgetTester tester, {
      required String? baseUrl,
      bool leased = false,
    }) async {
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () => openDevPreview(
                  context,
                  _link,
                  serverBaseUrl: baseUrl,
                  leased: leased,
                  supported: true,
                ),
                child: const Text('Go'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Go'));
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pump(const Duration(milliseconds: 50));
    }

    testWidgets('opens the screen for a reachable server', (tester) async {
      await tapOpen(tester, baseUrl: 'http://127.0.0.1:4096');
      expect(find.byType(DevPreviewScreen), findsOneWidget);
    });

    testWidgets('with no server it explains instead of opening', (
      tester,
    ) async {
      await tapOpen(tester, baseUrl: null);
      expect(find.byType(DevPreviewScreen), findsNothing);
      expect(find.byKey(const ValueKey('dev-preview-blocked')), findsOneWidget);
      expect(find.text('Connect a server first'), findsOneWidget);
    });
  });

  group('the conversation menu entry', () {
    Future<void> openMenu(WidgetTester tester, {required bool preview}) async {
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () => showKitMenu(
                  context,
                  items: sessionMenuItems(
                    AppLocalizations.of(context),
                    SessionMenuOffer.of(
                      testServerCapabilities,
                      shared: false,
                      savedServer: true,
                      preview: preview,
                    ),
                    onSelected: (_) {},
                  ),
                ),
                child: const Text('Menu'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Menu'));
      await tester.pumpAndSettle();
    }

    testWidgets('appears when the conversation mentioned a port', (
      tester,
    ) async {
      await openMenu(tester, preview: true);
      expect(
        find.byKey(const ValueKey('session-menu-preview')),
        findsOneWidget,
      );
      expect(find.text('Preview a running app'), findsOneWidget);
    });

    testWidgets('is absent otherwise', (tester) async {
      await openMenu(tester, preview: false);
      expect(find.byKey(const ValueKey('session-menu-preview')), findsNothing);
    });
  });
}

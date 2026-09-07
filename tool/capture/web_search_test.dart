// Synthetic source capture. No server, provider account, or external URL is used.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/web_sources_screen.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

class _Api extends CaptureApi implements WebSearchGateway {
  int searches = 0;
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(webSearch: true);
  @override
  Future<List<WebSearchProvider>> webSearchProviders() async => const [
    WebSearchProvider(id: 'example', name: 'Configured search provider'),
  ];
  @override
  Future<WebSearchResponse> searchWeb(
    String query, {
    required String providerID,
  }) async {
    searches++;
    return const WebSearchResponse(
      providerID: 'example',
      results: [
        WebSearchResult(
          url: 'https://flutter.dev/docs',
          title: 'Flutter documentation',
          content:
              'Build accessible interfaces with clear navigation and readable text.',
        ),
        WebSearchResult(
          url: 'https://dart.dev/guides',
          title: 'Dart guides',
          content:
              'Practical guidance for asynchronous programming and application development.',
        ),
      ],
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [true, false]) {
    testWidgets('reviewed web search ${light ? 'light' : 'dark'}', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = captureDevicePixelRatio;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final api = _Api();
      final controller = await captureController(
        prefs: await setupCapturePreferences(),
        api: api,
      );
      List<WebSourceSelection>? confirmed;
      try {
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: Builder(
              builder: (context) => Scaffold(
                body: Center(
                  child: TextButton(
                    onPressed: () async {
                      confirmed = await Navigator.of(context)
                          .push<List<WebSourceSelection>>(
                            MaterialPageRoute(
                              builder: (_) =>
                                  WebSourcesScreen(controller: controller),
                            ),
                          );
                    },
                    child: const Text('Add web source'),
                  ),
                ),
              ),
            ),
            boundaryKey: key,
            controller: controller,
            store: controller.store,
            light: light,
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.widgetWithText(TextButton, 'Add web source'));
        await tester.pumpAndSettle();
        expect(api.searches, 0);
        await tester.enterText(
          find.byKey(const ValueKey('web-search-query')),
          'Accessible Flutter interfaces',
        );
        // Text entry schedules the query-dependent button rebuild. A real
        // frame renders it enabled before the user can press Search.
        await tester.pump();
        expect(
          tester
              .widget<FilledButton>(find.widgetWithText(FilledButton, 'Search'))
              .onPressed,
          isNotNull,
        );
        await tester.tap(find.widgetWithText(FilledButton, 'Search'));
        await tester.pumpAndSettle();
        expect(api.searches, 1);
        expect(find.text('Flutter documentation'), findsOneWidget);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/web-search/results-${light ? 'light' : 'dark'}.png',
          await capturePng(tester, key),
        );
        await tester.tap(
          find.widgetWithText(TextButton, 'Add to review').first,
        );
        await tester.pumpAndSettle();
        await tester.scrollUntilVisible(
          find.byKey(const ValueKey('web-sources-confirm')),
          400,
          scrollable: find
              .descendant(
                of: find.byType(WebSourcesScreen),
                matching: find.byType(Scrollable),
              )
              .first,
        );
        // Paint the scroll update before capturing and pressing continuation.
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey('web-sources-confirm')).hitTestable(),
          findsOneWidget,
        );
        expect(find.text('Use selected sources (1)'), findsOneWidget);
        expect(find.byKey(const ValueKey('web-source-url')), findsNothing);
        expect(api.searches, 1);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/web-search/review-${light ? 'light' : 'dark'}.png',
          await capturePng(tester, key),
        );
        await tester.tap(
          find.byKey(const ValueKey('web-sources-confirm')).hitTestable(),
        );
        await tester.pumpAndSettle();
        expect(confirmed, hasLength(1));
        expect(confirmed!.single.url, 'https://flutter.dev/docs');
        expect(find.byType(WebSourcesScreen), findsNothing);
        await tester.pumpWidget(const SizedBox());
      } finally {
        controller.dispose();
      }
    });
  }
}

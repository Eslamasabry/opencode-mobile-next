// The home-screen widgets' floating host: the style window, the steer box
// and the voice box, driven by a fake of the `oc/widgets` channel.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/widget_v2_contract.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/widgets_overlay/widget_overlay_api.dart';
import 'package:opencode_mobile/widgets_overlay/widget_overlay_app.dart';
import 'package:opencode_mobile/widgets_overlay/widget_overlay_launch.dart';

class _Api implements WidgetOverlayApi {
  final saved = <(String, String)>[];
  final submitted = <String>[];
  var closed = 0;
  var dictations = 0;
  Object? saveError;
  Object? dictateError;
  String dictated = 'from the microphone';
  Completer<Map<String, dynamic>>? submitGate;
  Map<String, dynamic> submitResult = {'status': 'accepted'};
  Object? submitError;

  @override
  Future<Map<String, dynamic>> getLaunchArguments() async => {};
  @override
  Future<void> saveStyle({
    required String background,
    required String accent,
  }) async {
    if (saveError != null) throw saveError!;
    saved.add((background, accent));
  }

  final projects = <String>[];
  @override
  Future<void> saveProject(String project) async => projects.add(project);
  @override
  Future<String> dictate({String? languageTag}) async {
    dictations++;
    if (dictateError != null) throw dictateError!;
    return dictated;
  }

  @override
  Future<Map<String, dynamic>> submitAction(String text) async {
    submitted.add(text);
    if (submitError != null) throw submitError!;
    return submitGate != null ? submitGate!.future : submitResult;
  }

  @override
  Future<void> close() async => closed++;
}

Future<void> _open(
  WidgetTester tester,
  _Api api,
  String route, {
  String steerMode = 'steer',
  String background = 'solid',
  String accent = 'wallpaper',
}) async {
  tester.view.physicalSize = const Size(412, 915) * 3;
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    WidgetOverlayApp(
      api: api,
      launch: WidgetOverlayLaunch(
        route: route,
        steerMode: steerMode,
        styleBackground: background,
        styleAccent: accent,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  group('launch arguments', () {
    test('unknown values fall back, routing data is never read', () {
      final launch = WidgetOverlayLaunch.fromPlatform({
        'route': '/widgets/steer',
        'steerMode': 'weird',
        'profileID': 'secret-profile',
        'style': {'background': 'neon', 'accent': 'blue'},
      });
      expect(launch.steerMode, 'unknown');
      expect(launch.styleBackground, 'solid');
      expect(launch.styleAccent, 'blue');
      expect(launch.route, '/widgets/steer');
    });
  });

  group('project window (the Conversations widget\'s Edit)', () {
    test('reads the projects and the current one', () {
      final launch = WidgetOverlayLaunch.fromPlatform({
        'route': '/widgets/project',
        'projects': ['IPTV_King', '', 3, 'oc_app'],
        'project': 'oc_app',
      });
      expect(launch.projects, ['IPTV_King', 'oc_app']);
      expect(launch.project, 'oc_app');
    });

    testWidgets('offers All projects and each project; a tap saves it', (
      tester,
    ) async {
      final api = _Api();
      tester.view.physicalSize = const Size(412, 915) * 3;
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        WidgetOverlayApp(
          api: api,
          launch: const WidgetOverlayLaunch(
            route: '/widgets/project',
            projects: ['IPTV_King', 'oc_app'],
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Show conversations from'), findsOneWidget);
      expect(find.text('All projects'), findsOneWidget);
      expect(find.text('IPTV_King'), findsOneWidget);
      // No style form here.
      expect(find.text('Widget style'), findsNothing);
      await tester.tap(find.text('oc_app'));
      await tester.pumpAndSettle();
      expect(api.projects, ['oc_app']);
    });
  });

  group('style window', () {
    testWidgets('opens on the saved style and saves the changed one', (
      tester,
    ) async {
      final api = _Api();
      await _open(
        tester,
        api,
        '/widgets/configure',
        background: 'tinted',
        accent: 'green',
      );
      expect(find.text('Widget style'), findsOneWidget);
      await tester.tap(find.text('Transparent'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('widget-accent-blue')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Save style'));
      await tester.pumpAndSettle();
      expect(api.saved, [('transparent', 'blue')]);
    });

    testWidgets('a refused save keeps the choice and says so in words', (
      tester,
    ) async {
      final api = _Api()..saveError = PlatformException(code: 'staleSnapshot');
      await _open(tester, api, '/widgets/configure');
      await tester.tap(find.text('Tinted'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Save style'));
      await tester.pumpAndSettle();
      expect(
        find.text('This widget changed. Close this and try again.'),
        findsOneWidget,
      );
      api.saveError = null;
      await tester.tap(find.text('Save style'));
      await tester.pumpAndSettle();
      expect(api.saved, [('tinted', 'wallpaper')]);
    });

    testWidgets('the preview follows the layout choice', (tester) async {
      await _open(tester, _Api(), '/widgets/configure');
      expect(find.text('Editing files'), findsOneWidget);
      await tester.tap(find.text('Small'));
      await tester.pumpAndSettle();
      expect(find.text('Working'), findsWidgets);
      expect(find.text('Editing files'), findsNothing);
      await tester.tap(find.text('Wide'));
      await tester.pumpAndSettle();
      expect(find.text('Allow'), findsOneWidget);
    });

    testWidgets('closing leaves without saving', (tester) async {
      final api = _Api();
      await _open(tester, api, '/widgets/configure');
      await tester.tap(find.byTooltip('Cancel'));
      expect(api.closed, 1);
      expect(api.saved, isEmpty);
    });
  });

  group('steer box', () {
    testWidgets('native steering: "Add to this turn", sent and closed', (
      tester,
    ) async {
      final api = _Api();
      await _open(tester, api, '/widgets/steer');
      expect(find.text('Add to this turn'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'also add tests');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Send'));
      await tester.pump();
      await tester.pump();
      expect(api.submitted, ['also add tests']);
      expect(find.text('Sent'), findsWidgets);
      expect(api.closed, 0);
      await tester.pump(const Duration(seconds: 1));
      expect(api.closed, 1);
    });

    testWidgets('OpenCode 1 says "Send after this reply"', (tester) async {
      await _open(tester, _Api(), '/widgets/steer', steerMode: 'afterReply');
      expect(find.text('Send after this reply'), findsOneWidget);
      expect(find.text('Add to this turn'), findsNothing);
    });

    for (final mode in ['restart', 'unknown']) {
      testWidgets('$mode mode cannot send and says why', (tester) async {
        final api = _Api();
        await _open(tester, api, '/widgets/steer', steerMode: mode);
        expect(
          find.text(
            mode == 'restart'
                ? 'This conversation can only be changed from inside the app.'
                : 'Waiting for conversation details…',
          ),
          findsOneWidget,
        );
        await tester.tap(find.text('Send'));
        final mic = tester.widget<KitIconButton>(
          find.byKey(const ValueKey('widget-steer-mic')),
        );
        expect(mic.onPressed, isNull);
        await tester.pump();
        expect(api.submitted, isEmpty);
        expect(api.dictations, 0);
      });
    }

    testWidgets('a failed send keeps the draft and allows another try', (
      tester,
    ) async {
      final api = _Api()
        ..submitResult = {'status': 'rejected', 'error': 'staleRequest'};
      await _open(tester, api, '/widgets/steer');
      await tester.enterText(find.byType(TextField), 'keep me');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Send'));
      await tester.pumpAndSettle();
      expect(
        find.text('The conversation changed. Your message is still here.'),
        findsOneWidget,
      );
      expect(find.text('keep me'), findsOneWidget);
      api.submitResult = {'status': 'accepted'};
      await tester.tap(find.text('Send'));
      await tester.pump();
      await tester.pump();
      expect(api.submitted, ['keep me', 'keep me']);
      await tester.pump(const Duration(seconds: 1));
    });

    testWidgets('only one send at a time', (tester) async {
      final api = _Api()..submitGate = Completer();
      await _open(tester, api, '/widgets/steer');
      await tester.enterText(find.byType(TextField), 'once');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Send'));
      await tester.pump();
      expect(find.text('Sending…'), findsWidgets);
      await tester.tap(find.text('Sending…').last, warnIfMissed: false);
      await tester.pump();
      expect(api.submitted, ['once']);
      api.submitGate!.complete({'status': 'accepted'});
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
    });

    testWidgets('an uncertain send stays shut until the person checks', (
      tester,
    ) async {
      final api = _Api()..submitResult = {'status': 'uncertain'};
      await _open(tester, api, '/widgets/steer');
      await tester.enterText(find.byType(TextField), 'maybe sent');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Send'));
      await tester.pumpAndSettle();
      expect(
        find.text(
          'We couldn’t confirm whether this was sent. Check the conversation before sending again.',
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('Send'));
      await tester.pump();
      expect(api.submitted, ['maybe sent']);
      expect(find.text('maybe sent'), findsOneWidget);
    });

    testWidgets('a channel error after dispatch is uncertain, not retried', (
      tester,
    ) async {
      final api = _Api()..submitError = PlatformException(code: 'x');
      await _open(tester, api, '/widgets/steer');
      await tester.enterText(find.byType(TextField), 'lost');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Send'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Send'));
      await tester.pump();
      expect(api.submitted, ['lost']);
    });

    testWidgets('dictation fills the field for editing, never sends', (
      tester,
    ) async {
      final api = _Api();
      await _open(tester, api, '/widgets/steer');
      await tester.enterText(find.byType(TextField), 'first');
      await tester.tap(find.byTooltip('Dictate a message'));
      await tester.pumpAndSettle();
      expect(find.text('first from the microphone'), findsOneWidget);
      expect(api.submitted, isEmpty);
    });

    testWidgets('a cancelled dictation is quiet; unavailable is explained', (
      tester,
    ) async {
      final api = _Api()..dictateError = PlatformException(code: 'cancelled');
      await _open(tester, api, '/widgets/steer');
      await tester.tap(find.byTooltip('Dictate a message'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Dictation isn’t available'), findsNothing);
      api.dictateError = PlatformException(code: 'unavailable');
      await tester.tap(find.byTooltip('Dictate a message'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Dictation isn’t available'), findsOneWidget);
    });

    testWidgets('tapping outside the box closes it', (tester) async {
      final api = _Api();
      await _open(tester, api, '/widgets/steer');
      await tester.tapAt(const Offset(20, 20));
      expect(api.closed, 1);
    });
  });

  group('voice box', () {
    testWidgets('opens dictation at once and waits for an explicit Send', (
      tester,
    ) async {
      final api = _Api();
      await _open(tester, api, '/widgets/voice', steerMode: 'unknown');
      expect(find.text('New message'), findsOneWidget);
      expect(api.dictations, 1);
      expect(find.text('from the microphone'), findsOneWidget);
      expect(api.submitted, isEmpty);
      await tester.tap(find.text('Send'));
      await tester.pump();
      await tester.pump();
      expect(api.submitted, ['from the microphone']);
      await tester.pump(const Duration(seconds: 1));
      expect(api.closed, 1);
    });
  });

  test('WidgetOverlayPort refuses a colour that is not a choice', () async {
    expect(
      () => WidgetOverlayPort.saveStyle(background: 'solid', accent: '#ff0000'),
      throwsArgumentError,
    );
  });

  testWidgets('KitSteerBox is what the steer window shows', (tester) async {
    await _open(tester, _Api(), '/widgets/steer');
    expect(find.byType(KitSteerBox), findsOneWidget);
    expect(find.byType(KitFloatingPanel), findsOneWidget);
  });
}

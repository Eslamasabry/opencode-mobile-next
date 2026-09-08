import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/run_result_screen.dart';
import 'package:opencode_mobile/ui/widgets/return_brief_card.dart';
import 'package:opencode_mobile/ui/widgets/return_brief_panel.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'support/return_brief_fixture.dart';
import 'return_brief_state_test.dart' show BriefTestPreferences;

Future<void> frames(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Finder inBrief(String text) => find.descendant(
  of: find.byType(ReturnBriefCard),
  matching: find.text(text),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets(
    'Workspace render and dismissal never view a session or answer a request',
    (tester) async {
      final c = await briefController(requests: true);
      addTearDown(c.dispose);
      await tester.pumpWidget(briefApp(c));
      await frames(tester);
      expect(inBrief('Unreviewed work'), findsOneWidget);
      expect((c.repository as BriefRepository).views, isEmpty);
      await tester.ensureVisible(inBrief('Dismiss shown items'));
      await tester.tap(inBrief('Dismiss shown items'));
      await frames(tester);
      expect(find.text('Unreviewed work'), findsNothing);
      expect(c.isSessionUnread(c.sessionsById['results']!), isTrue);
      expect(c.permissions.keys, ['p1']);
      expect((c.repository as BriefRepository).views, isEmpty);
      final restarted = await briefController(requests: true);
      addTearDown(restarted.dispose);
      await tester.pumpWidget(briefApp(restarted));
      await frames(tester);
      expect(find.text('Unreviewed work'), findsNothing);
      restarted.sessionsById['results'] = briefSession('results', idle: 21);
      restarted.publish();
      await frames(tester);
      expect(inBrief('Review results'), findsOneWidget);
    },
  );

  testWidgets(
    'refused dismissal stays visible and a successful retry hides it',
    (tester) async {
      SharedPreferences.resetStatic();
      final backend = BriefTestPreferences()..refuse = true;
      SharedPreferencesStorePlatform.instance = backend;
      final c = await briefController();
      addTearDown(c.dispose);
      await tester.pumpWidget(briefApp(c));
      await frames(tester);
      await tester.tap(inBrief('Dismiss shown items'));
      await frames(tester);
      expect(find.textContaining('Dismissal was not saved'), findsOneWidget);
      expect(inBrief('Review results'), findsOneWidget);
      backend.refuse = false;
      await tester.tap(inBrief('Dismiss shown items'));
      await frames(tester);
      expect(find.text('Unreviewed work'), findsNothing);
    },
  );

  testWidgets(
    'an item arriving during dismissal remains; scope switch does not hide new project',
    (tester) async {
      SharedPreferences.resetStatic();
      final backend = BriefTestPreferences()..barrier = Completer<void>();
      SharedPreferencesStorePlatform.instance = backend;
      final c = await briefController();
      addTearDown(c.dispose);
      await tester.pumpWidget(briefApp(c));
      await frames(tester);
      await tester.tap(inBrief('Dismiss shown items'));
      await tester.pump();
      c.sessionsById['later'] = briefSession('later', idle: 10);
      c.publish();
      backend.barrier!.complete();
      await frames(tester);
      expect(inBrief('Review the database migration'), findsOneWidget);
      expect(inBrief('Polish the mobile checkout'), findsNothing);
      c.changeProject();
      c.sessionsById['results'] = briefSession('results');
      c.publish();
      await frames(tester);
      expect(inBrief('Polish the mobile checkout'), findsOneWidget);
    },
  );

  testWidgets(
    'Review results and Continue return to the brief without implicit acknowledgement',
    (tester) async {
      final c = await briefController();
      addTearDown(c.dispose);
      await tester.pumpWidget(briefApp(c));
      await frames(tester);
      await tester.tap(inBrief('Review results'));
      await frames(tester);
      expect(find.byType(RunResultScreen), findsOneWidget);
      await tester.pageBack();
      await frames(tester);
      expect(inBrief('Review results'), findsOneWidget);
      await tester.tap(inBrief('Continue'));
      await frames(tester);
      expect(find.text('/chat/results'), findsOneWidget);
      await tester.pageBack();
      await frames(tester);
      expect(inBrief('Review results'), findsOneWidget);
      expect(c.returnBriefAcknowledgement.runs, isEmpty);
    },
  );

  testWidgets('scope change retires result route while history is loading', (
    tester,
  ) async {
    final c = await briefController();
    addTearDown(c.dispose);
    final wake = Completer<void>();
    c.wake = wake.future;
    await tester.pumpWidget(briefApp(c));
    await frames(tester);
    await tester.tap(inBrief('Review results'));
    await frames(tester);
    c.changeProject();
    await frames(tester);
    expect(find.byType(RunResultScreen), findsNothing);
    wake.complete();
    await frames(tester);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'offline requests are labelled last observed and use the guarded permission sheet',
    (tester) async {
      final c = await briefController(requests: true);
      addTearDown(c.dispose);
      c.status = StreamStatus.disconnected;
      await tester.pumpWidget(briefApp(c));
      await frames(tester);
      expect(find.textContaining('Last observed state.'), findsOneWidget);
      await tester.ensureVisible(inBrief('Answer'));
      await tester.tap(inBrief('Answer'));
      await frames(tester);
      expect(find.text('git status'), findsWidgets);
      expect(c.permissions, hasLength(1));
      c.changeProject();
      await frames(tester);
      expect(find.text('git status'), findsNothing);
    },
  );

  testWidgets(
    'form Answer closes on project switch and cannot send the old form',
    (tester) async {
      final c = await briefController();
      addTearDown(c.dispose);
      c.forms['f1'] = Api2FormInfo(
        id: 'f1',
        sessionID: 'results',
        title: 'Choose deployment target',
      );
      await tester.pumpWidget(briefApp(c));
      await frames(tester);
      await tester.tap(inBrief('Answer'));
      await frames(tester);
      expect(find.text('Choose deployment target'), findsOneWidget);
      c.changeProject();
      await frames(tester);
      expect(find.text('Choose deployment target'), findsNothing);
      expect((c.api as BriefApi).formReplies, isEmpty);
    },
  );

  for (final fullScreen in [false, true]) {
    for (final changeProject in [false, true]) {
      testWidgets(
        '${fullScreen ? 'full-screen' : 'sheet'} form dismiss retires on '
        '${changeProject ? 'project change' : 'request settlement'} without '
        'closing an unrelated route',
        (tester) async {
          final c = await briefController();
          addTearDown(c.dispose);
          c.forms['f1'] = Api2FormInfo(
            id: 'f1',
            sessionID: 'results',
            title: 'Choose deployment target',
            fields: [
              for (var i = 0; i < (fullScreen ? 5 : 1); i++)
                Api2FormField(key: 'field-$i', type: Api2FormFieldType.string),
            ],
          );
          await tester.pumpWidget(briefApp(c));
          await frames(tester);
          await tester.ensureVisible(inBrief('Answer'));
          await tester.tap(inBrief('Answer').hitTestable());
          await frames(tester);
          final dismiss = find.byKey(const Key('form-cancel'));
          expect(dismiss.hitTestable(), findsOneWidget);
          await tester.tap(dismiss.hitTestable());
          await frames(tester);
          final confirmation = find.byKey(const Key('form-dismiss-confirm'));
          expect(confirmation, findsOneWidget);
          final navigator = Navigator.of(tester.element(confirmation));
          unawaited(
            navigator.push<void>(
              MaterialPageRoute<void>(
                builder: (_) => const Scaffold(body: Text('Unrelated details')),
              ),
            ),
          );
          await frames(tester);
          expect(find.text('Unrelated details'), findsOneWidget);
          if (changeProject) {
            c.changeProject();
          } else {
            c.forms.remove('f1');
            c.publish();
          }
          await frames(tester);
          expect(
            find.byKey(const Key('form-sheet'), skipOffstage: false),
            findsNothing,
          );
          expect(
            find.byKey(const Key('form-dismiss-confirm'), skipOffstage: false),
            findsNothing,
          );
          expect(find.text('Unrelated details'), findsOneWidget);
          expect((c.api as BriefApi).formReplies, isEmpty);
          navigator.pop();
          await frames(tester);
          expect(find.text('Unrelated details'), findsNothing);
          expect(tester.takeException(), isNull);
        },
      );
    }
  }

  testWidgets(
    'known empty, unsupported and partial inventories have distinct presentation',
    (tester) async {
      final c = await briefController();
      addTearDown(c.dispose);
      c.sessionsById.clear();
      await tester.pumpWidget(briefApp(c));
      await frames(tester);
      expect(find.text('Unreviewed work'), findsNothing);
      c.known = false;
      c.publish();
      await frames(tester);
      expect(find.text('Review status unknown'), findsOneWidget);
      await tester.tap(find.text('Review status unknown'));
      await frames(tester);
      expect(
        find.textContaining('Unreviewed results are unknown.'),
        findsOneWidget,
      );
      await tester.tap(find.text('Close'));
      await frames(tester);
      c.known = true;
      c.partial = true;
      c.publish();
      await frames(tester);
      expect(find.textContaining('Loaded sessions only.'), findsOneWidget);
    },
  );

  testWidgets('project switch during a save never dismisses the new project', (
    tester,
  ) async {
    SharedPreferences.resetStatic();
    final backend = BriefTestPreferences()..barrier = Completer<void>();
    SharedPreferencesStorePlatform.instance = backend;
    final c = await briefController();
    addTearDown(c.dispose);
    final oldScope = c.returnBriefScope;
    await tester.pumpWidget(briefApp(c));
    await frames(tester);
    await tester.tap(inBrief('Dismiss shown items'));
    await tester.pump();
    c.changeProject();
    c.sessionsById['results'] = briefSession('results');
    c.publish();
    await frames(tester);
    backend.barrier!.complete();
    await frames(tester);
    expect(inBrief('Review results'), findsOneWidget);
    expect(c.returnBriefAcknowledgement.runs, isEmpty);
    expect(c.returnBriefScope, isNot(oldScope));
    expect(find.textContaining('Dismissal was not saved'), findsNothing);
  });

  for (final cancel in [false, true]) {
    test(
      'form ${cancel ? 'cancel' : 'reply'} rejects project switch during transport wake',
      () async {
        final c = await briefController();
        addTearDown(c.dispose);
        final form = Api2FormInfo(id: 'same', sessionID: 'results');
        c.forms['same'] = form;
        final wake = Completer<void>();
        c.wake = wake.future;
        final send = cancel ? c.cancelForm('same') : c.replyForm('same', {});
        final check = expectLater(send, throwsStateError);
        c.changeProject();
        final replacement = Api2FormInfo(id: 'same', sessionID: 'another');
        c.forms['same'] = replacement;
        wake.complete();
        await check;
        expect((c.api as BriefApi).formReplies, isEmpty);
        expect(c.forms['same'], same(replacement));
      },
    );
  }

  for (final dark in [false, true]) {
    for (final rtl in [false, true]) {
      testWidgets(
        '320px 2x text wraps all brief controls, dark=$dark rtl=$rtl',
        (tester) async {
          tester.view.physicalSize = const Size(320, 1800);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.reset);
          final c = await briefController(requests: true);
          addTearDown(c.dispose);
          await tester.pumpWidget(
            briefApp(
              c,
              scale: 2,
              dark: dark,
              rtl: rtl,
              home: SingleChildScrollView(
                child: ReturnBriefPanel(controller: c),
              ),
            ),
          );
          await frames(tester);
          for (final label in [
            'Review results',
            'Answer',
            'Continue',
            'Dismiss shown items',
          ]) {
            final paragraph = tester.renderObject<RenderParagraph>(
              inBrief(label),
            );
            expect(paragraph.didExceedMaxLines, isFalse, reason: label);
            expect(paragraph.debugHasOverflowShader, isFalse, reason: label);
            expect(paragraph.size.width, lessThanOrEqualTo(256));
          }
          expect(tester.takeException(), isNull);
        },
      );
    }
  }
}

// screen-phone-1 (wave 2b): the phone pages rebuilt from kit parts, and the
// map items they now handle. Each test asserts what the person sees or what
// the screen sends.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_field.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_customize_sheet.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_ready_screen.dart';

import 'screen_phone_1_fixtures.dart';

Future<void> _openOtherWays(WidgetTester tester) async {
  final toggle = find.byKey(const ValueKey('phone-setup-start-other-ways'));
  await tester.ensureVisible(toggle);
  await tester.pumpAndSettle();
  await tester.tap(toggle);
  await tester.pumpAndSettle();
}

/// Opens the customize sheet the way the phone's card does ("Add tools").
Widget _customizeHost({required bool addMode}) => Scaffold(
  body: Builder(
    builder: (context) => Center(
      child: KitButton.primary(
        label: 'open',
        onPressed: () => showSetupCustomizeSheet(
          context,
          engine: PhoneSetup.engine,
          addMode: addMode,
        ),
      ),
    ),
  ),
);

void main() {
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('oc/voice'), (
          call,
        ) async {
          if (call.method == 'getDeviceInfo') return <String, Object?>{};
          return null;
        });
  });

  group('phone-setup-start', () {
    testWidgets('a fresh phone keeps one primary and folds the other ways', (
      tester,
    ) async {
      await pumpPhone(tester, home: startScreen());
      await tester.pumpAndSettle();
      expect(find.text('Run a coding agent right here'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('phone-setup-start-by-address')),
        findsNothing,
      );
      await _openOtherWays(tester);
      expect(
        find.byKey(const ValueKey('phone-setup-start-by-address')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
      await unmountPhone(tester);
    });
  });

  group('phone-setup-customize-sheet', () {
    testWidgets('required tools are locked on with a word, not a dead switch', (
      tester,
    ) async {
      await pumpPhone(tester, home: _customizeHost(addMode: false));
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('Choose what to install'), findsOneWidget);
      // linux, essentials, node, opencode are required in the fake registry.
      expect(find.text('Required'), findsNWidgets(4));
      expect(find.text('About 4 minutes · ~165 MB'), findsOneWidget);
      await tester.tap(
        find.byKey(const ValueKey('phone-setup-customize-python')),
      );
      await tester.pumpAndSettle();
      expect(find.text('About 3 minutes · ~135 MB'), findsOneWidget);
      await unmountPhone(tester);
    });

    testWidgets('every optional tool installed: nothing to add, and why', (
      tester,
    ) async {
      await pumpPhone(
        tester,
        home: _customizeHost(addMode: true),
        optionalInstalled: {'python'},
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('Add tools'), findsOneWidget);
      expect(find.text('Installed'), findsOneWidget);
      // The reason is the line right above the disabled Add (STATE-8),
      // said once.
      expect(
        find.text('Every optional tool is already on this phone.'),
        findsOneWidget,
      );
      final add = tester.widget<KitButton>(
        find.byKey(const ValueKey('phone-setup-customize-done')),
      );
      expect(add.onPressed, isNull);
      await unmountPhone(tester);
    });
  });

  group('phone-setup-ready', () {
    testWidgets('the name is a labelled kit field that says what is wrong', (
      tester,
    ) async {
      await pumpPhone(
        tester,
        home: PhoneSetupReadyScreen(linux: PhoneLinux(running: true)),
      );
      await tester.pumpAndSettle();
      expect(find.byType(KitField), findsOneWidget);
      expect(find.text('Project name'), findsOneWidget);
      await tester.enterText(
        find.byKey(const ValueKey('phone-setup-ready-name')),
        'a/b',
      );
      await tester.tap(find.byKey(const ValueKey('phone-setup-ready-create')));
      await tester.pumpAndSettle();
      expect(find.text('Use one name, without slashes.'), findsOneWidget);
      await unmountPhone(tester);
    });

    testWidgets('Close leaves for the app root', (tester) async {
      await pumpPhone(
        tester,
        home: const Scaffold(body: Text('root')),
        routes: {'/ready': (_) => PhoneSetupReadyScreen(linux: PhoneLinux())},
      );
      final navigator = tester.state<NavigatorState>(find.byType(Navigator));
      navigator.pushNamed('/ready');
      await tester.pumpAndSettle();
      expect(find.text('OpenCode is ready'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('phone-setup-ready-close')));
      await tester.pumpAndSettle();
      expect(find.text('root'), findsOneWidget);
      expect(find.text('OpenCode is ready'), findsNothing);
      await unmountPhone(tester);
    });
  });
}

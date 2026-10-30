// KitField behaviour (docs/ux-system/kit-api/KitField.md, Tests required).
// Arabic is dropped (owner decision 2026-09-27): test 6 checks the forced
// left-to-right kinds under an RTL Directionality only.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_field.dart';
import 'package:opencode_mobile/ui/kit/kit_sheet.dart' show KitDraft;
import 'package:shared_preferences/shared_preferences.dart';

import 'kit_motion_still.dart';

// One theme instance, so a rebuilt host never animates a theme change.
final _dark = AppTheme.dark();

Widget _host(Widget child, {bool reduced = false, TextDirection? direction}) {
  Widget body = Padding(padding: const EdgeInsets.all(16), child: child);
  if (direction != null) {
    body = Directionality(textDirection: direction, child: body);
  }
  return MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: _dark,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, app) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: reduced),
      child: app!,
    ),
    home: Scaffold(body: SingleChildScrollView(child: body)),
  );
}

TextField _textField(WidgetTester tester) =>
    tester.widget<TextField>(find.byType(TextField));

void main() {
  final stillCounter = TextEditingController();
  tearDownAll(stillCounter.dispose);
  // G8x (MOT-7): still under both kinds of reduced motion, at mount and
  // after a change.
  kitMotionStillTests(
    'KitField',
    builds: {
      'default': () => const KitField(label: 'Name', helper: 'A short name.'),
      'error': () => const KitField(label: 'Port', error: 'Enter a port.'),
      'checking': () => KitField(
        label: 'Address',
        kind: KitFieldKind.url,
        checkingSince: DateTime.now(),
      ),
      'secret saved': () => const KitField.secret(label: 'Key', saved: true),
    },
    changes: {
      // Set in code: a focused field's caret blinks on the framework's own
      // ticker, which is not this part's motion.
      'the counter unfolds': KitMotionChange(
        build: () => KitField(
          label: 'Title',
          maxLength: 5,
          controller: stillCounter..text = '',
        ),
        act: (tester, stage) async => stillCounter.text = 'abcd',
        shows: '4 of 5',
      ),
      'Replace opens the field': KitMotionChange(
        build: () => const KitField.secret(
          label: 'Key',
          saved: true,
          replaceKey: Key('replace'),
        ),
        act: (tester, stage) async {
          await tester.tap(find.byKey(const Key('replace')));
        },
        hides: 'Saved',
      ),
    },
  );

  late List<String> announcements;
  late List<MethodCall> platform;
  String? clipboard;

  setUp(() {
    announcements = [];
    platform = [];
    clipboard = null;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockDecodedMessageHandler<dynamic>(
      SystemChannels.accessibility,
      (message) async {
        final map = message as Map<Object?, Object?>;
        if (map['type'] == 'announce') {
          announcements.add(
            (map['data'] as Map<Object?, Object?>)['message']! as String,
          );
        }
        return null;
      },
    );
    messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      platform.add(call);
      if (call.method == 'Clipboard.getData') {
        return clipboard == null ? null : {'text': clipboard};
      }
      return null;
    });
  });

  tearDown(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockDecodedMessageHandler<dynamic>(
      SystemChannels.accessibility,
      null,
    );
    messenger.setMockMethodCallHandler(SystemChannels.platform, null);
  });

  testWidgets('1. the label shows above, names the field, and focuses it', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      _host(const KitField(label: 'Project name', hint: 'my-app')),
    );
    final label = find.text('Project name');
    expect(label, findsOneWidget);
    expect(
      tester.getBottomLeft(label).dy,
      lessThanOrEqualTo(tester.getTopLeft(find.byType(TextField)).dy),
    );
    final node = tester.getSemantics(find.byType(EditableText));
    expect(node.label, contains('Project name'));
    expect(node.flagsCollection.isTextField, isTrue);

    await tester.tap(label);
    await tester.pump();
    expect(_textField(tester).focusNode!.hasFocus, isTrue);
    semantics.dispose();
  });

  testWidgets('2. the counter appears at 80 %, announces once, and stops at '
      'the limit', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _host(KitField(label: 'Name', controller: controller, maxLength: 10)),
    );
    await tester.enterText(find.byType(TextField), 'abcdefg');
    await tester.pump();
    expect(find.text('7 of 10'), findsNothing);

    await tester.enterText(find.byType(TextField), 'abcdefgh');
    await tester.pump();
    expect(find.text('8 of 10'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'abcdefghi');
    await tester.pump();
    expect(find.text('9 of 10'), findsOneWidget);
    expect(announcements, ['8 of 10']);

    await tester.enterText(find.byType(TextField), 'abcdefghij');
    await tester.pump();
    expect(find.text('Limit reached'), findsOneWidget);
    expect(announcements, ['8 of 10', 'Limit reached']);

    await tester.enterText(find.byType(TextField), 'abcdefghijk');
    await tester.pump();
    expect(controller.text, 'abcdefghij');
    expect(announcements, ['8 of 10', 'Limit reached']);
  });

  testWidgets('3. the error replaces the helper as a live region; a Form '
      'validator message renders in the same slot', (tester) async {
    final semantics = tester.ensureSemantics();
    Widget build(String? error) => _host(
      KitField(label: 'Port', helper: 'Between 1 and 65535', error: error),
    );
    await tester.pumpWidget(build(null));
    expect(find.text('Between 1 and 65535'), findsOneWidget);

    await tester.pumpWidget(build('Enter a port number'));
    await tester.pumpAndSettle();
    expect(find.text('Between 1 and 65535'), findsNothing);
    expect(find.text('Enter a port number'), findsOneWidget);
    final live = find.bySemanticsLabel('Error: Enter a port number');
    expect(live, findsOneWidget);
    expect(tester.getSemantics(live).flagsCollection.isLiveRegion, isTrue);

    final form = GlobalKey<FormState>();
    await tester.pumpWidget(
      _host(
        Form(
          key: form,
          child: KitField(
            label: 'Host',
            validator: (value) =>
                (value ?? '').isEmpty ? 'Enter the host' : null,
          ),
        ),
      ),
    );
    final before = tester.getSize(find.byType(TextField));
    expect(form.currentState!.validate(), isFalse);
    await tester.pumpAndSettle();
    expect(find.bySemanticsLabel('Error: Enter the host'), findsOneWidget);
    expect(find.text('Enter the host'), findsOneWidget);
    // The inner editable draws no error of its own and does not grow.
    expect(tester.getSize(find.byType(TextField)), before);
    await tester.enterText(find.byType(TextField), 'example.com');
    expect(form.currentState!.validate(), isTrue);
    await tester.pumpAndSettle();
    expect(find.text('Enter the host'), findsNothing);
    semantics.dispose();
  });

  testWidgets('4. disabled without a reason asserts', (tester) async {
    await tester.pumpWidget(
      _host(Builder(builder: (_) => KitField(label: 'Name', enabled: false))),
    );
    expect(tester.takeException(), isA<AssertionError>());
  });

  testWidgets('4. disabled shows its reason, ignores input and disables its '
      'action', (tester) async {
    var browsed = 0;
    await tester.pumpWidget(
      _host(
        KitField(
          label: 'Folder',
          kind: KitFieldKind.path,
          enabled: false,
          disabledReason: 'Connect to a server first.',
          action: KitAction(
            label: 'Browse',
            icon: Icons.folder,
            onPressed: () => browsed++,
          ),
        ),
      ),
    );
    expect(find.text('Connect to a server first.'), findsOneWidget);
    expect(_textField(tester).enabled, isFalse);
    await tester.tap(find.byIcon(Icons.folder), warnIfMissed: false);
    await tester.pump();
    expect(browsed, 0);
  });

  testWidgets('5. checking says so without a spinner, escalates after 8 s '
      'once, and offers the ways out', (tester) async {
    var skipped = 0;
    await tester.pumpWidget(
      _host(
        KitField(
          label: 'Address',
          kind: KitFieldKind.url,
          checkingSince: DateTime.now(),
          onSlow: [
            KitAction(label: 'Skip the check', onPressed: () => skipped++),
          ],
        ),
      ),
    );
    expect(find.text('Checking…'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.byType(LinearProgressIndicator), findsNothing);
    expect(find.text('Skip the check'), findsNothing);

    await tester.pump(const Duration(seconds: 9));
    await tester.pumpAndSettle();
    expect(find.text('Still checking after 8 s'), findsOneWidget);
    expect(announcements, ['Still checking after 8 s']);
    await tester.tap(find.text('Skip the check'));
    expect(skipped, 1);
    await tester.pump(const Duration(seconds: 20));
    expect(announcements, ['Still checking after 8 s']);
  });

  testWidgets('5. three onSlow actions assert', (tester) async {
    await tester.pumpWidget(
      _host(
        KitField(
          label: 'Address',
          onSlow: [
            for (final label in ['a', 'b', 'c'])
              KitAction(label: label, onPressed: () {}),
          ],
        ),
      ),
    );
    expect(tester.takeException(), isA<AssertionError>());
  });

  testWidgets('5a. the composer draws no label, names itself, and has no '
      'helper, error or counter line', (tester) async {
    final semantics = tester.ensureSemantics();
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _host(
        KitField.composer(
          label: 'Message',
          controller: controller,
          hint: 'Ask anything',
        ),
      ),
    );
    expect(find.text('Message'), findsNothing);
    expect(
      tester.getSemantics(find.byType(EditableText)).label,
      contains('Message'),
    );
    expect(
      find.byType(DecoratedBox).evaluate().where((e) {
        final box = (e.widget as DecoratedBox).decoration;
        return box is BoxDecoration && box.border != null;
      }),
      isEmpty,
    );
    semantics.dispose();
  });

  testWidgets('6. technical kinds are LTR inside RTL; text follows it', (
    tester,
  ) async {
    for (final kind in [
      KitFieldKind.mono,
      KitFieldKind.path,
      KitFieldKind.url,
    ]) {
      await tester.pumpWidget(
        _host(
          KitField(label: 'Value', kind: kind),
          direction: TextDirection.rtl,
        ),
      );
      expect(
        _textField(tester).textDirection,
        TextDirection.ltr,
        reason: '$kind',
      );
    }
    await tester.pumpWidget(
      _host(const KitField.secret(label: 'Key'), direction: TextDirection.rtl),
    );
    expect(_textField(tester).textDirection, TextDirection.ltr);
    await tester.pumpWidget(
      _host(const KitField(label: 'Name'), direction: TextDirection.rtl),
    );
    expect(_textField(tester).textDirection, isNull);
    expect(
      tester.widget<EditableText>(find.byType(EditableText)).textDirection,
      isNull,
    );
  });

  testWidgets('7. number rejects letters, normalises Arabic-Indic digits, '
      'and decimal allows one separator', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _host(
        KitField(
          label: 'Port',
          kind: KitFieldKind.number,
          controller: controller,
        ),
      ),
    );
    await tester.enterText(find.byType(TextField), '12ab');
    expect(controller.text, '12');
    await tester.enterText(find.byType(TextField), '٣٤');
    expect(controller.text, '34');
    await tester.enterText(find.byType(TextField), '1.5');
    expect(controller.text, '15');

    await tester.pumpWidget(
      _host(
        KitField(
          key: const Key('decimal'),
          label: 'Ratio',
          kind: KitFieldKind.number,
          decimal: true,
          controller: controller,
        ),
      ),
    );
    await tester.enterText(find.byType(TextField), '1.5.2');
    expect(controller.text, '1.52');
  });

  testWidgets('8. multiline with a draft restores, saves under its key and '
      'clears', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    expect(KitDraft.keyFor('note.s1', 'p1'), 'oc.draft.note.s1.p1');

    Future<TextEditingController> mount() async {
      final controller = TextEditingController();
      await tester.pumpWidget(
        _host(
          KitField(
            key: UniqueKey(),
            label: 'Note',
            kind: KitFieldKind.multiline,
            draft: KitDraft(
              target: 'note.s1',
              profileId: 'p1',
              controller: controller,
              prefs: prefs,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return controller;
    }

    final first = await mount();
    await tester.enterText(find.byType(TextField), 'half a thought');
    await tester.pumpAndSettle();
    expect(prefs.getString('oc.draft.note.s1.p1'), 'half a thought');
    await tester.pumpWidget(const SizedBox.shrink());
    first.dispose();

    final second = await mount();
    expect(second.text, 'half a thought');
    await KitDraft(
      target: 'note.s1',
      profileId: 'p1',
      controller: second,
      prefs: prefs,
    ).clear();
    expect(prefs.containsKey('oc.draft.note.s1.p1'), isFalse);
    await tester.pumpWidget(const SizedBox.shrink());
    second.dispose();
  });

  testWidgets('9. multiline: Enter is a new line, Ctrl+Enter submits; '
      'single-line Enter submits once', (tester) async {
    final submitted = <String>[];
    await tester.pumpWidget(
      _host(
        KitField(
          label: 'Objective',
          kind: KitFieldKind.multiline,
          onSubmitted: submitted.add,
        ),
      ),
    );
    await tester.tap(find.byType(TextField));
    await tester.enterText(find.byType(TextField), 'line one\nline two');
    await tester.testTextInput.receiveAction(TextInputAction.newline);
    await tester.pump();
    expect(submitted, isEmpty);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pump();
    expect(submitted, ['line one\nline two']);

    submitted.clear();
    await tester.pumpWidget(
      _host(
        KitField(
          key: const Key('single'),
          label: 'Name',
          onSubmitted: submitted.add,
        ),
      ),
    );
    await tester.tap(find.byType(TextField));
    await tester.enterText(find.byType(TextField), 'app');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pump();
    expect(submitted, ['app']);
  });

  testWidgets('10. secret asserts an empty controller at mount', (
    tester,
  ) async {
    final controller = TextEditingController(text: 'sk-live');
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _host(KitField.secret(label: 'API key', controller: controller)),
    );
    expect(tester.takeException(), isA<AssertionError>());
  });

  testWidgets('10. secret is obscured, never learned, reveals, pastes, and '
      'keeps its value out of semantics', (tester) async {
    final semantics = tester.ensureSemantics();
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _host(KitField.secret(label: 'API key', controller: controller)),
    );
    final field = _textField(tester);
    expect(field.obscureText, isTrue);
    expect(field.enableSuggestions, isFalse);
    expect(field.autocorrect, isFalse);
    expect(field.enableIMEPersonalizedLearning, isFalse);
    expect(field.autofillHints, anyOf(isNull, isEmpty));

    clipboard = 'sk-ant-secret-123';
    await tester.tap(find.byTooltip('Paste'));
    await tester.pumpAndSettle();
    expect(controller.text, 'sk-ant-secret-123');
    expect(
      platform.where((call) => call.method == 'Clipboard.getData'),
      hasLength(1),
    );

    final node = tester.getSemantics(find.byType(EditableText));
    expect(node.flagsCollection.isObscured, isTrue);
    expect(node.value, isNot(contains('sk-ant')));
    expect(node.label, contains('API key'));
    final everything = StringBuffer();
    void walk(SemanticsNode n) {
      everything
        ..write(n.label)
        ..write(n.value)
        ..write(n.hint);
      n.visitChildren((child) {
        walk(child);
        return true;
      });
    }

    walk(
      tester
          .binding
          .renderViews
          .first
          .owner!
          .semanticsOwner!
          .rootSemanticsNode!,
    );
    expect(everything.toString(), isNot(contains('sk-ant')));
    expect(
      tester.widget(find.byType(KitField)).toStringDeep(),
      isNot(contains('sk-ant')),
    );
    // The whole element tree too: the inner editable's own diagnostics
    // list its controller, which must not print the text (SEC-2).
    expect(
      tester.binding.rootElement!.toStringDeep(),
      isNot(contains('sk-ant')),
    );

    await tester.tap(find.byTooltip('Show API key'));
    await tester.pump();
    expect(_textField(tester).obscureText, isFalse);
    expect(
      tester.binding.rootElement!.toStringDeep(),
      isNot(contains('sk-ant')),
    );
    await tester.tap(find.byTooltip('Hide API key'));
    await tester.pump();
    expect(_textField(tester).obscureText, isTrue);
    semantics.dispose();
  });

  testWidgets('10. Paste runs through the input formatters, like a keyboard '
      'paste', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final changes = <String>[];
    await tester.pumpWidget(
      _host(
        KitField.secret(
          label: 'API key',
          controller: controller,
          inputFormatters: [FilteringTextInputFormatter.deny(RegExp(r'\s'))],
          onChanged: changes.add,
        ),
      ),
    );
    clipboard = '  sk-abc\n';
    await tester.tap(find.byTooltip('Paste'));
    await tester.pumpAndSettle();
    expect(controller.text, 'sk-abc');
    expect(_textField(tester).controller!.text, 'sk-abc');
    expect(changes, ['sk-abc']);

    // Typing still reaches the host's controller.
    await tester.enterText(find.byType(TextField), 'sk-typed');
    await tester.pump();
    expect(controller.text, 'sk-typed');
    // And the host's own edits reach the editable.
    controller.clear();
    await tester.pump();
    expect(_textField(tester).controller!.text, isEmpty);
  });

  testWidgets('10. a disabled secret cannot be revealed', (tester) async {
    await tester.pumpWidget(
      _host(
        const KitField.secret(
          label: 'API key',
          enabled: false,
          disabledReason: 'Saving…',
          revealKey: Key('reveal'),
        ),
      ),
    );
    await tester.tap(find.byKey(const Key('reveal')), warnIfMissed: false);
    await tester.pump();
    expect(_textField(tester).obscureText, isTrue);
    expect(find.text('Saving…'), findsOneWidget);
  });

  testWidgets('10. saved shows Saved · Replace and no field; Replace calls '
      'onReplace and shows an empty field', (tester) async {
    final semantics = tester.ensureSemantics();
    var replaced = 0;
    await tester.pumpWidget(
      _host(
        KitField.secret(
          label: 'API key',
          saved: true,
          onReplace: () => replaced++,
        ),
      ),
    );
    expect(find.text('Saved'), findsOneWidget);
    expect(find.text('Replace'), findsOneWidget);
    expect(find.byType(TextField), findsNothing);
    // With no editable to carry it, the row names the field: one node that
    // reads the label, Saved and Replace, and whose tap is Replace.
    final row = tester.getSemantics(find.text('Saved')).getSemanticsData();
    expect(row.label, contains('API key'));
    expect(row.label, contains('Saved'));
    expect(row.label, contains('Replace'));
    expect(row.flagsCollection.isButton, isTrue);
    expect(row.hasAction(SemanticsAction.tap), isTrue);
    semantics.dispose();

    await tester.tap(find.text('Replace'));
    await tester.pumpAndSettle();
    expect(replaced, 1);
    expect(find.byType(TextField), findsOneWidget);
    expect(_textField(tester).controller!.text, isEmpty);
  });

  testWidgets('10. a disabled saved secret says why Replace is off', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    var replaced = 0;
    await tester.pumpWidget(
      _host(
        KitField.secret(
          label: 'API key',
          saved: true,
          enabled: false,
          disabledReason: 'Saving…',
          helper: 'Stored on this phone only.',
          onReplace: () => replaced++,
        ),
      ),
    );
    final row = tester.getSemantics(find.text('Saved')).getSemanticsData();
    expect(row.label, contains('API key'));
    expect(row.hint, contains('Saving…'));
    expect(row.hint, isNot(contains('Saving…\nSaving…')));
    expect(row.hasAction(SemanticsAction.tap), isFalse);
    await tester.tap(find.text('Replace'), warnIfMissed: false);
    await tester.pump();
    expect(replaced, 0);
    expect(find.byType(TextField), findsNothing);
    semantics.dispose();
  });

  testWidgets('12. selectAllOnFocus selects the initial text', (tester) async {
    final controller = TextEditingController(text: 'feature-branch');
    final focus = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focus.dispose);
    await tester.pumpWidget(
      _host(
        KitField(
          label: 'Branch',
          controller: controller,
          focusNode: focus,
          selectAllOnFocus: true,
        ),
      ),
    );
    focus.requestFocus();
    await tester.pump();
    await tester.pump();
    expect(controller.selection.start, 0);
    expect(controller.selection.end, 'feature-branch'.length);
  });

  testWidgets('13. Esc is not consumed by the field', (tester) async {
    var escapes = 0;
    await tester.pumpWidget(
      _host(
        CallbackShortcuts(
          bindings: {
            const SingleActivator(LogicalKeyboardKey.escape): () => escapes++,
          },
          child: const KitField(label: 'Name', autofocus: true),
        ),
      ),
    );
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pump();
    expect(escapes, 1);
  });

  testWidgets('14. under reduced motion one pump settles the error fold', (
    tester,
  ) async {
    Widget build(String? error) =>
        _host(KitField(label: 'Port', error: error), reduced: true);
    await tester.pumpWidget(build(null));
    await tester.pumpWidget(build('Enter a port number'));
    await tester.pump();
    expect(tester.hasRunningAnimations, isFalse);
    expect(find.text('Enter a port number'), findsOneWidget);

    // A Form validator's message folds in the same way.
    final form = GlobalKey<FormState>();
    await tester.pumpWidget(
      _host(
        Form(
          key: form,
          child: KitField(label: 'Host', validator: (_) => 'Enter the host'),
        ),
        reduced: true,
      ),
    );
    form.currentState!.validate();
    await tester.pump();
    expect(tester.hasRunningAnimations, isFalse);
    expect(find.text('Enter the host'), findsOneWidget);
  });

  testWidgets('15. the trailing action is a labelled 48 dp target', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      _host(
        KitField(
          label: 'Folder',
          kind: KitFieldKind.path,
          actionKey: const Key('browse'),
          action: KitAction(
            label: 'Browse',
            icon: Icons.folder,
            onPressed: () {},
          ),
        ),
      ),
    );
    final size = tester.getSize(find.byKey(const Key('browse')));
    expect(size.width, greaterThanOrEqualTo(48));
    expect(size.height, greaterThanOrEqualTo(48));
    expect(find.byTooltip('Browse'), findsOneWidget);
    expect(find.bySemanticsLabel('Browse'), findsWidgets);
    semantics.dispose();
  });

  testWidgets('16. no overflow from 320 to 1600 dp at text 1.0-2.0, and the '
      'helper is not clipped at 2.0', (tester) async {
    const helper =
        'Paste the code the sign-in page showed you. It expires in ten '
        'minutes, so ask for a new one if it stopped working.';
    for (final size in const [
      Size(320, 640),
      Size(412, 915),
      Size(915, 412),
      Size(1600, 1000),
    ]) {
      for (final scale in const [1.0, 1.3, 2.0]) {
        for (final direction in TextDirection.values) {
          tester.view.physicalSize = size;
          tester.view.devicePixelRatio = 1;
          await tester.pumpWidget(
            MaterialApp(
              theme: AppTheme.light(),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              builder: (context, app) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(scale)),
                child: Directionality(textDirection: direction, child: app!),
              ),
              home: Scaffold(
                body: SingleChildScrollView(
                  child: Column(
                    children: [
                      const KitField(
                        label: 'Sign-in code',
                        kind: KitFieldKind.mono,
                        helper: helper,
                        maxLength: 10,
                      ),
                      KitField.secret(
                        label: 'API key',
                        error: 'That key was refused by the provider.',
                      ),
                      KitField(
                        label: 'Folder',
                        kind: KitFieldKind.path,
                        action: KitAction(
                          label: 'Browse',
                          icon: Icons.folder,
                          onPressed: () {},
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
          expect(tester.takeException(), isNull, reason: '$size x$scale');
          final text = tester.renderObject<RenderParagraph>(find.text(helper));
          expect(text.didExceedMaxLines, isFalse);
        }
      }
    }
    tester.view.reset();
  });

  testWidgets('diagnostics never carry the controller text', (tester) async {
    final controller = TextEditingController(text: 'private words');
    addTearDown(controller.dispose);
    final field = KitField(label: 'Name', controller: controller);
    expect(field.toStringDeep(), isNot(contains('private words')));
  });

  testWidgets('optional says so at the end of the label line; plain fields '
      'do not', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _host(
        KitField(
          label: 'Anything to add?',
          controller: controller,
          optional: true,
        ),
      ),
    );
    expect(find.text('Optional'), findsOneWidget);
    // On the label's line, after the label.
    final label = tester.getRect(find.text('Anything to add?'));
    final optional = tester.getRect(find.text('Optional'));
    expect(optional.left, greaterThan(label.right));
    expect((optional.center.dy - label.center.dy).abs(), lessThan(4));

    await tester.pumpWidget(
      _host(KitField(label: 'Name', controller: controller)),
    );
    expect(find.text('Optional'), findsNothing);
  });
}

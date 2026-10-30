// KitStepSlider (docs/ux-system/kit-api/KitStepSlider.md): one choice among a
// few ordered steps; tap, drag, keys and a screen reader's increase and
// decrease all move it; it is one slider node; disabled does not respond.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

const _steps = ['a', 'b', 'c', 'd'];
const _labels = ['Smallest', 'Small', 'Default', 'Large'];

Widget _slider(
  String selected, {
  ValueChanged<String>? onChanged,
  bool enabled = true,
}) => Padding(
  padding: const EdgeInsets.all(16),
  child: KitStepSlider<String>(
    semanticsLabel: 'Text size',
    steps: [
      for (var i = 0; i < _steps.length; i++)
        KitSliderStep(
          key: ValueKey('step-${_steps[i]}'),
          value: _steps[i],
          label: _labels[i],
        ),
    ],
    selected: selected,
    onChanged: enabled ? (onChanged ?? (_) {}) : null,
  ),
);

Widget _app(Widget child, {TextDirection? direction, double scale = 1}) =>
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, app) => Directionality(
        textDirection: direction ?? TextDirection.ltr,
        child: MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(scale)),
          child: app!,
        ),
      ),
      home: Scaffold(body: child),
    );

void main() {
  kitMotionStillTests(
    'KitStepSlider',
    builds: {
      'default': () => _slider('b'),
      'disabled': () => _slider('b', enabled: false),
    },
    changes: {
      'moves a step': KitMotionChange(
        build: () => _slider('a'),
        act: (tester, stage) => stage.rebuild(_slider('c')),
        shows: 'Default',
      ),
    },
  );

  testWidgets('shows the chosen step and chooses by tap', (tester) async {
    String? chosen;
    await tester.pumpWidget(_app(_slider('b', onChanged: (v) => chosen = v)));
    expect(find.text('Small'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('step-d')));
    expect(chosen, 'd');
  });

  testWidgets('each new step clicks once; the same step does not (MOT-11)', (
    tester,
  ) async {
    final haptics = <Object?>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'HapticFeedback.vibrate') {
          haptics.add(call.arguments);
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
    var chosen = 'b';
    await tester.pumpWidget(
      _app(
        StatefulBuilder(
          builder: (context, setState) => _slider(
            chosen,
            onChanged: (value) => setState(() => chosen = value),
          ),
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('step-d')));
    await tester.pumpAndSettle();
    expect(chosen, 'd');
    expect(haptics, ['HapticFeedbackType.selectionClick']);
    await tester.tap(find.byKey(const ValueKey('step-d')));
    await tester.pumpAndSettle();
    expect(haptics, hasLength(1));
  });

  testWidgets('every step is a 48 dp target', (tester) async {
    await tester.pumpWidget(_app(_slider('b')));
    for (final s in _steps) {
      final size = tester.getSize(find.byKey(ValueKey('step-$s')));
      expect(size.height, greaterThanOrEqualTo(48), reason: s);
      expect(size.width, greaterThanOrEqualTo(48), reason: s);
    }
  });

  testWidgets('dragging the track chooses the nearest step', (tester) async {
    final seen = <String>[];
    await tester.pumpWidget(_app(_slider('a', onChanged: seen.add)));
    final first = tester.getCenter(find.byKey(const ValueKey('step-a')));
    final last = tester.getCenter(find.byKey(const ValueKey('step-d')));
    final gesture = await tester.startGesture(first);
    await gesture.moveTo(last);
    await gesture.up();
    expect(seen.last, 'd');
  });

  testWidgets('arrow keys move one step and stop at the ends', (tester) async {
    final seen = <String>[];
    Widget host(String selected) =>
        _app(_slider(selected, onChanged: seen.add));
    await tester.pumpWidget(host('b'));
    await tester.tap(find.byKey(const ValueKey('step-b')));
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
    expect(seen.last, 'c');
    await tester.pumpWidget(host('a'));
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
    expect(seen.last, 'c', reason: 'already at the first step: no change');
  });

  testWidgets('is one slider node with its value and neighbours', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_app(_slider('b')));
    final node = find.semantics.byLabel('Text size').evaluate().single;
    expect(node.value, 'Small');
    expect(node.increasedValue, 'Default');
    expect(node.decreasedValue, 'Smallest');
    handle.dispose();
  });

  testWidgets('a screen reader increase moves one step', (tester) async {
    final handle = tester.ensureSemantics();
    String? chosen;
    await tester.pumpWidget(_app(_slider('b', onChanged: (v) => chosen = v)));
    tester.semantics.increase(find.semantics.byLabel('Text size'));
    expect(chosen, 'c');
    handle.dispose();
  });

  testWidgets('disabled does not respond', (tester) async {
    await tester.pumpWidget(_app(_slider('b', enabled: false)));
    await tester.tap(find.byKey(const ValueKey('step-d')));
    await tester.pump();
    expect(find.text('Small'), findsOneWidget);
  });

  testWidgets('right to left puts the least step at the start edge', (
    tester,
  ) async {
    await tester.pumpWidget(_app(_slider('b'), direction: TextDirection.rtl));
    final a = tester.getCenter(find.byKey(const ValueKey('step-a')));
    final d = tester.getCenter(find.byKey(const ValueKey('step-d')));
    expect(a.dx, greaterThan(d.dx));
  });

  testWidgets('keeps its 48 dp track at 200 % text without overflow', (
    tester,
  ) async {
    await tester.pumpWidget(_app(_slider('c'), scale: 2));
    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(find.byKey(const ValueKey('step-c'))).height,
      greaterThanOrEqualTo(48),
    );
  });
}

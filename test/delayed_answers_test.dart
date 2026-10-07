import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/delayed_answers.dart';

void main() {
  const window = Duration(seconds: 3);

  testWidgets('an answer is held, then sent once after the window', (
    tester,
  ) async {
    {
      final answers = DelayedAnswers();
      var sent = 0;
      var notified = 0;
      answers.addListener(() => notified++);

      answers.hold('r1', label: 'Allowed', send: () async => sent++);
      expect(answers.isHeld('r1'), isTrue);
      expect(answers.heldLabel('r1'), 'Allowed');
      expect(notified, 1);

      await tester.pump(window - const Duration(milliseconds: 1));
      expect(sent, 0);
      expect(answers.isHeld('r1'), isTrue);

      await tester.pump(const Duration(milliseconds: 1));
      expect(sent, 1);
      expect(answers.isHeld('r1'), isFalse);
      expect(answers.heldLabel('r1'), isNull);
      expect(notified, 2);

      await tester.pump(window * 2);
      expect(sent, 1);
    }
  });

  testWidgets('undo sends nothing and notifies', (tester) async {
    {
      final answers = DelayedAnswers();
      var sent = 0;
      var notified = 0;
      answers.hold('r1', label: 'Rejected', send: () async => sent++);
      answers.addListener(() => notified++);

      answers.undo('r1');
      expect(answers.isHeld('r1'), isFalse);
      expect(notified, 1);
      answers.undo('r1');
      expect(notified, 1, reason: 'nothing held, nothing to announce');

      await tester.pump(window * 2);
      expect(sent, 0);
    }
  });

  testWidgets(
    'holding again replaces the earlier answer and restarts the window',
    (tester) async {
      {
        final answers = DelayedAnswers();
        final sent = <String>[];
        answers.hold('r1', label: 'Allowed', send: () async => sent.add('a'));
        await tester.pump(const Duration(seconds: 2));
        answers.hold('r1', label: 'Rejected', send: () async => sent.add('b'));
        expect(answers.heldLabel('r1'), 'Rejected');

        await tester.pump(const Duration(seconds: 2));
        expect(sent, isEmpty);
        await tester.pump(const Duration(seconds: 1));
        expect(sent, ['b']);
      }
    },
  );

  testWidgets('requests are held independently', (tester) async {
    {
      final answers = DelayedAnswers();
      final sent = <String>[];
      answers.hold('r1', label: 'A', send: () async => sent.add('r1'));
      await tester.pump(const Duration(seconds: 1));
      answers.hold('r2', label: 'B', send: () async => sent.add('r2'));
      answers.undo('r1');
      await tester.pump(window);
      expect(sent, ['r2']);
    }
  });

  testWidgets('flush sends every held answer at once and clears them', (
    tester,
  ) async {
    {
      final answers = DelayedAnswers();
      final sent = <String>[];
      answers.hold('r1', label: 'A', send: () async => sent.add('r1'));
      answers.hold('r2', label: 'B', send: () async => sent.add('r2'));

      answers.flush();
      await tester.pump();
      expect(sent.toSet(), {'r1', 'r2'});
      expect(answers.isHeld('r1'), isFalse);
      expect(answers.isHeld('r2'), isFalse);

      answers.flush();
      await tester.pump(window * 2);
      expect(sent.length, 2, reason: 'sent exactly once');
    }
  });

  testWidgets('a failing send does not escape the timer', (tester) async {
    {
      final answers = DelayedAnswers();
      answers.hold('r1', label: 'A', send: () async => throw StateError('x'));
      await tester.pump(window);
      await tester.pump();
      expect(answers.isHeld('r1'), isFalse);
    }
  });

  testWidgets('dispose sends what is held and later calls are safe', (
    tester,
  ) async {
    {
      final answers = DelayedAnswers();
      var sent = 0;
      answers.hold('r1', label: 'A', send: () async => sent++);
      answers.dispose();
      await tester.pump();
      expect(sent, 1);
      await tester.pump(window * 2);
      expect(sent, 1);

      answers.undo('r1');
      answers.flush();
      answers.hold('r2', label: 'B', send: () async => sent++);
      await tester.pump();
      expect(sent, 2, reason: 'a dead holder sends at once, never loses');
    }
  });
}

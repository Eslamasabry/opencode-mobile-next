// KitStatusLine v2 (docs/ux-system/kit-api/KitStatusLine.md): the escalation
// on KitSince's fake clock, the Now line, KitStatus and its priority order,
// KitStatusLine.of, the risky-switch rule, the v1 constructor's keys and
// stacking, the LOOK-4/LOOK-5 tone map, reduced motion, and the status slot
// (kit_status_slot.dart, frozen in KitScreen.md "The status slot").
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_since.dart';
import 'package:opencode_mobile/ui/kit/kit_status_line.dart';
import 'package:opencode_mobile/ui/kit/kit_status_slot.dart';

const _messageKey = ValueKey('status-message');

/// Now on the clock KitSince reads (package:clock, faked by testWidgets),
/// derived through KitSince itself so the test needs no clock import.
DateTime _now() {
  final epoch = DateTime.utc(2000);
  return epoch.add(KitSince.statusOf(epoch).elapsed);
}

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Size size = const Size(412, 915),
  double textScale = 1,
  bool reduced = false,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, inner) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          disableAnimations: reduced,
          textScaler: TextScaler.linear(textScale),
        ),
        child: inner!,
      ),
      home: Scaffold(body: Column(children: [child, const Spacer()])),
    ),
  );
}

/// The words the line's one live region holds right now: what a screen
/// reader announces when it changes.
String _announced(WidgetTester tester) {
  final node = tester.getSemantics(find.byKey(_messageKey));
  expect(node.flagsCollection.isLiveRegion, isTrue);
  return node.label;
}

KitAction _act(String label, [VoidCallback? onPressed]) => KitAction(
  label: label,
  key: ValueKey('act-$label'),
  onPressed: onPressed ?? () {},
);

void main() {
  group('escalation (fake clock)', () {
    testWidgets('7 s: unchanged; 8 s: "Still waiting after 8 s", the first '
        'onSlow action in the slot, the rest in More, announced once', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      var retried = 0;
      var left = 0;
      final since = _now().subtract(const Duration(seconds: 7));
      await _pump(
        tester,
        KitStatusLine(
          icon: AppIconography.cloudOff,
          message: 'Reconnecting to laptop…',
          messageKey: _messageKey,
          tone: AppStatusTone.progress,
          supporting: '2 drafts will send when connected',
          since: since,
          onSlow: [
            _act('Retry', () => retried++),
            _act('Leave it running', () => left++),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final labels = <String>[_announced(tester)];
      expect(find.text('2 drafts will send when connected'), findsOneWidget);
      expect(find.text('Retry'), findsNothing);
      expect(find.byKey(const ValueKey('kit-status-more')), findsNothing);

      // Just under 8 s: still unchanged.
      final toSlow = KitMotion.escalateAfter - _now().difference(since);
      await tester.pump(toSlow - const Duration(milliseconds: 10));
      labels.add(_announced(tester));
      expect(find.text('Retry'), findsNothing);

      await tester.pump(const Duration(milliseconds: 10));
      await tester.pumpAndSettle();
      labels.add(_announced(tester));
      expect(find.text('Still waiting after 8 s'), findsOneWidget);
      expect(find.text('2 drafts will send when connected'), findsNothing);
      expect(find.text('Retry'), findsOneWidget);
      expect(find.text('Leave it running'), findsNothing);

      // More holds the second way out.
      await tester.tap(find.byKey(const ValueKey('kit-status-more')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Leave it running'));
      await tester.pumpAndSettle();
      expect(left, 1);
      await tester.tap(find.text('Retry'));
      expect(retried, 1);

      // A long while later: no further announcement.
      await tester.pump(const Duration(minutes: 3));
      labels.add(_announced(tester));
      final changes = [
        for (var i = 1; i < labels.length; i++)
          if (labels[i] != labels[i - 1]) labels[i],
      ];
      expect(changes, ['Reconnecting to laptop…\nStill waiting after 8 s']);
      semantics.dispose();
    });

    testWidgets('with an action already there, the onSlow actions go first '
        'in More (Retry / Restart / Leave it running)', (tester) async {
      await _pump(
        tester,
        KitStatusLine(
          icon: AppIconography.server,
          message: 'Starting the server…',
          tone: AppStatusTone.progress,
          action: _act('Restart'),
          more: [_act('Details')],
          since: _now().subtract(const Duration(seconds: 20)),
          onSlow: [_act('Retry'), _act('Leave it running')],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Restart'), findsOneWidget);
      expect(find.text('Retry'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('kit-status-more')));
      await tester.pumpAndSettle();
      final order = [
        for (final label in ['Retry', 'Leave it running', 'Details'])
          tester.getTopLeft(find.text(label)).dy,
      ];
      expect(order, orderedEquals([...order]..sort()));
    });

    testWidgets('onSlow with more than two actions asserts', (tester) async {
      await _pump(
        tester,
        SizedBox(
          height: 120,
          child: KitStatusLine(
            icon: AppIconography.cloudOff,
            message: 'Reconnecting',
            onSlow: [_act('Retry'), _act('Restart'), _act('Leave it running')],
          ),
        ),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });
  });

  group('the Now line', () {
    testWidgets('renders under the message; new numbers are not '
        're-announced', (tester) async {
      final semantics = tester.ensureSemantics();
      Widget line(String next) => KitStatusLine(
        icon: AppIconography.agent,
        message: 'Working on the login fix',
        messageKey: _messageKey,
        next: next,
        nextKey: const ValueKey('now-next'),
      );
      await _pump(tester, line('A reviewer checks it next · about 6 min'));
      await tester.pumpAndSettle();
      final before = _announced(tester);
      expect(
        tester.getTopLeft(find.byKey(const ValueKey('now-next'))).dy,
        greaterThan(tester.getBottomLeft(find.byKey(_messageKey)).dy - 1),
      );

      await _pump(tester, line('A reviewer checks it next · about 5 min'));
      await tester.pumpAndSettle();
      expect(find.text('A reviewer checks it next · about 5 min'), findsOne);
      expect(
        find.text('A reviewer checks it next · about 6 min'),
        findsNothing,
      );
      expect(_announced(tester), before);
      semantics.dispose();
    });

    testWidgets('a next line arriving later unfolds', (tester) async {
      Widget line(String? next) => KitStatusLine(
        icon: AppIconography.agent,
        message: 'Working',
        next: next,
      );
      await _pump(tester, line(null));
      await tester.pumpAndSettle();
      final closed = tester.getSize(find.byType(KitStatusLine)).height;
      await _pump(tester, line('Tests run next'));
      await tester.pump(const Duration(milliseconds: 50));
      final opening = tester.getSize(find.byType(KitStatusLine)).height;
      await tester.pumpAndSettle();
      final open = tester.getSize(find.byType(KitStatusLine)).height;
      expect(opening, greaterThanOrEqualTo(closed));
      expect(opening, lessThan(open));
    });
  });

  group('KitStatus', () {
    KitStatus status(KitStatusKind kind, [String? id]) => KitStatus(
      kind: kind,
      id: id,
      icon: AppIconography.info,
      message: kind.name,
    );

    test('highest follows KitStatusKind; ties keep the first; nulls are '
        'ignored', () {
      final update = status(KitStatusKind.update);
      final connection = status(KitStatusKind.connection);
      final work = status(KitStatusKind.work);
      expect(KitStatus.highest([update, connection, work]), connection);
      expect(KitStatus.highest([update, work]), work);
      final first = status(KitStatusKind.work, 'a');
      final second = status(KitStatusKind.work, 'b');
      expect(KitStatus.highest([first, second]), same(first));
      expect(KitStatus.highest([null, update, null]), update);
      expect(KitStatus.highest(const []), isNull);
      expect(KitStatusKind.values.map((k) => k.name), [
        'connection',
        'appStopped',
        'heat',
        'riskySwitch',
        'work',
        'update',
        'info',
      ]);
      expect(connection.priority, lessThan(work.priority));
    });

    test('a risky switch line is never dismissible', () {
      expect(
        () => KitStatus(
          kind: KitStatusKind.riskySwitch,
          icon: AppIconography.shield,
          message: 'Auto-approve is on',
          onDismiss: () {},
        ),
        throwsAssertionError,
      );
      expect(
        KitStatus(
          kind: KitStatusKind.riskySwitch,
          icon: AppIconography.shield,
          message: 'Auto-approve is on',
          action: _act('Turn off'),
        ).onDismiss,
        isNull,
      );
    });

    testWidgets('KitStatusLine.of renders the same text, actions and keys as '
        'the constructor', (tester) async {
      var dismissed = 0;
      final status = KitStatus(
        kind: KitStatusKind.connection,
        id: 'connection:laptop',
        icon: AppIconography.cloudOff,
        message: 'Offline',
        supporting: 'Drafts wait here',
        next: 'Trying again in 10 s',
        action: _act('Try again'),
        more: [_act('Servers')],
        onDismiss: () => dismissed++,
      );
      await _pump(tester, KitStatusLine.of(status, messageKey: _messageKey));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('kit-status-connection:laptop')),
        findsOneWidget,
      );
      for (final text in [
        'Offline',
        'Drafts wait here',
        'Trying again in 10 s',
        'Try again',
      ]) {
        expect(find.text(text), findsOneWidget, reason: text);
      }
      expect(find.byKey(_messageKey), findsOneWidget);
      expect(find.byKey(const ValueKey('act-Try again')), findsOneWidget);
      expect(find.byKey(const ValueKey('kit-status-more')), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('kit-status-dismiss')));
      expect(dismissed, 1);

      final line = tester.widget<KitStatusLine>(find.byType(KitStatusLine));
      expect(line.message, status.message);
      expect(line.supporting, status.supporting);
      expect(line.next, status.next);
      expect(line.action, same(status.action));
      expect(line.more, same(status.more));
      expect(line.tone, status.tone);

      // Without an id the key names the kind.
      await _pump(
        tester,
        KitStatusLine.of(
          const KitStatus(
            kind: KitStatusKind.update,
            icon: AppIconography.download,
            message: 'Update ready',
          ),
        ),
      );
      expect(find.byKey(const ValueKey('kit-status-update')), findsOneWidget);
    });
  });

  group('v1 constructor (unchanged)', () {
    testWidgets('keeps kit-status-more and kit-status-dismiss, its '
        'supporting and dismiss keys, and their behaviour', (tester) async {
      var picked = 0;
      var dismissed = 0;
      await _pump(
        tester,
        KitStatusLine(
          icon: AppIconography.info,
          message: 'Shared link copied',
          supporting: 'Anyone with it can read',
          supportingKey: const ValueKey('sup'),
          action: _act('Stop sharing'),
          more: [_act('Open', () => picked++)],
          onDismiss: () => dismissed++,
          dismissKey: const ValueKey('custom-dismiss'),
          dismissTooltip: 'Hide',
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('sup')), findsOneWidget);
      expect(find.byTooltip('Hide'), findsOneWidget);
      expect(find.byTooltip('More'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('kit-status-more')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('act-Open')));
      await tester.pumpAndSettle();
      expect(picked, 1);
      await tester.tap(find.byKey(const ValueKey('custom-dismiss')));
      expect(dismissed, 1);

      await _pump(
        tester,
        KitStatusLine(
          icon: AppIconography.info,
          message: 'Hello',
          onDismiss: () {},
          moreKey: const ValueKey('own-more'),
          more: [_act('Open')],
        ),
      );
      expect(find.byKey(const ValueKey('kit-status-dismiss')), findsOneWidget);
      expect(find.byKey(const ValueKey('own-more')), findsOneWidget);
      expect(find.byTooltip('Dismiss'), findsOneWidget);
    });

    testWidgets('at large text the action stacks under the words', (
      tester,
    ) async {
      await _pump(
        tester,
        KitStatusLine(
          icon: AppIconography.cloudOff,
          message: 'Can’t reach the laptop over Tailscale right now',
          messageKey: _messageKey,
          action: _act('Try again'),
        ),
        size: const Size(360, 800),
        textScale: 2,
      );
      await tester.pumpAndSettle();
      expect(
        tester.getTopLeft(find.text('Try again')).dy,
        greaterThan(tester.getBottomLeft(find.byKey(_messageKey)).dy - 1),
      );
    });

    testWidgets('controlsTogether keeps action, More and dismiss on one row', (
      tester,
    ) async {
      await _pump(
        tester,
        KitStatusLine(
          icon: AppIconography.idea,
          message: 'Long-press a message to copy, edit or share it',
          action: _act('Got it'),
          more: [_act('Learn more')],
          onDismiss: () {},
          controlsTogether: true,
        ),
        size: const Size(360, 800),
        textScale: 2,
      );
      await tester.pumpAndSettle();
      final action = tester.getCenter(find.text('Got it')).dy;
      final more = tester.getCenter(
        find.byKey(const ValueKey('kit-status-more')),
      );
      final dismiss = tester.getCenter(
        find.byKey(const ValueKey('kit-status-dismiss')),
      );
      expect((more.dy - action).abs(), lessThan(2));
      expect((dismiss.dy - action).abs(), lessThan(2));
    });

    testWidgets('a disabled action says why under the line', (tester) async {
      await _pump(
        tester,
        const KitStatusLine(
          icon: AppIconography.download,
          message: 'Update ready',
          action: KitAction(
            label: 'Restart',
            onPressed: null,
            disabledReason: 'Finish sending first',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Finish sending first'), findsOneWidget);
    });

    testWidgets('a glyph outside the app set still draws', (tester) async {
      await _pump(
        tester,
        const KitStatusLine(icon: Icons.memory, message: 'Busy'),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('Busy'), findsOneWidget);
    });
  });

  group('tone (LOOK-4, LOOK-5)', () {
    for (final (tone, role) in [
      (AppStatusTone.attention, 'text1'),
      (AppStatusTone.failure, 'text1'),
      (AppStatusTone.progress, 'accent'),
      (AppStatusTone.neutral, 'text2'),
    ]) {
      testWidgets('$tone paints the icon in $role', (tester) async {
        await _pump(
          tester,
          KitStatusLine(
            icon: AppIconography.warning,
            message: 'Something',
            tone: tone,
          ),
        );
        await tester.pumpAndSettle();
        final roles = ThemeRoles.resolve(AppTheme.dark());
        final expected = switch (role) {
          'text1' => roles.text1,
          'accent' => roles.accent,
          _ => roles.text2,
        };
        final icon = tester.widget<Icon>(
          find.byWidgetPredicate(
            (w) => w is Icon && w.icon == AppIconography.warning,
          ),
        );
        expect(icon.color, expected);
        if (tone == AppStatusTone.attention) {
          expect(icon.color, isNot(roles.attention));
        }
      });
    }
  });

  testWidgets('reduced motion settles after one pump (G8)', (tester) async {
    await _pump(
      tester,
      KitStatusLine(
        icon: AppIconography.cloudOff,
        message: 'Offline',
        next: 'Trying again soon',
        since: _now(),
        onSlow: [_act('Retry')],
      ),
      reduced: true,
    );
    await tester.pump();
    // Only KitSince's escalation timer is pending; no animation runs.
    expect(tester.binding.transientCallbackCount, 0);
    await tester.pump(KitMotion.escalateAfter);
    await tester.pump();
    expect(tester.binding.transientCallbackCount, 0);
    expect(find.text('Still waiting after 8 s'), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);
  });

  group('status slot', () {
    KitStatus status(KitStatusKind kind, String message, {String? id}) =>
        KitStatus(
          kind: kind,
          id: id,
          icon: AppIconography.info,
          message: message,
        );

    testWidgets('shows the highest of the app-wide conditions and its own; '
        'nothing when all are gone', (tester) async {
      final appWide = ValueNotifier<List<KitStatus>>(const []);
      addTearDown(appWide.dispose);
      final own = ValueNotifier<KitStatus?>(
        status(KitStatusKind.work, 'Team is working'),
      );
      addTearDown(own.dispose);
      await _pump(
        tester,
        KitStatusScope(
          conditions: appWide,
          child: ValueListenableBuilder<KitStatus?>(
            valueListenable: own,
            builder: (_, value, _) => KitStatusLineSlot(
              status: value,
              slotKey: const ValueKey('slot'),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('slot')), findsOneWidget);
      expect(find.text('Team is working'), findsOneWidget);

      appWide.value = [
        status(KitStatusKind.update, 'Update ready'),
        status(KitStatusKind.connection, 'Offline', id: 'connection:laptop'),
      ];
      await tester.pumpAndSettle();
      expect(find.text('Offline'), findsOneWidget);
      expect(find.byType(KitStatusLine), findsOneWidget);
      expect(
        find.byKey(const ValueKey('kit-status-connection:laptop')),
        findsOneWidget,
      );

      appWide.value = [status(KitStatusKind.update, 'Update ready')];
      await tester.pumpAndSettle();
      expect(find.text('Team is working'), findsOneWidget);

      appWide.value = const [];
      own.value = null;
      await tester.pumpAndSettle();
      expect(find.byType(KitStatusLine), findsNothing);
    });

    testWidgets('without a scope or a slot above: empty conditions, '
        'existsAbove false, a contribution draws only its child', (
      tester,
    ) async {
      late bool above;
      late int conditions;
      await _pump(
        tester,
        KitStatusContribution(
          status: status(KitStatusKind.heat, 'Phone is hot'),
          child: Builder(
            builder: (context) {
              above = KitStatusLineSlot.existsAbove(context);
              conditions = KitStatusScope.of(context).value.length;
              return const Text('body');
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(above, isFalse);
      expect(conditions, 0);
      expect(find.text('body'), findsOneWidget);
      expect(find.text('Phone is hot'), findsNothing);
    });
  });

  group('countdown (vision 2026-10-20)', () {
    // The test font draws every glyph a square: short words keep the line
    // whole at 412 dp.
    KitStatusLine line({String message = 'Offline'}) => KitStatusLine(
      icon: AppIconography.cloudOff,
      message: message,
      messageKey: _messageKey,
      countdownTo: _now().add(const Duration(milliseconds: 3400)),
      countdownLine: (left) => left == Duration.zero
          ? 'Trying again now'
          : 'Trying again in ${(left.inMilliseconds / 1000).ceil()} s',
      action: KitAction(label: 'Try now', onPressed: () {}),
    );

    testWidgets('the second line counts down once a second, the live region '
        'keeps the message', (tester) async {
      await _pump(tester, line());
      expect(find.text('Trying again in 4 s'), findsOneWidget);
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('Trying again in 3 s'), findsOneWidget);
      await tester.pump(const Duration(seconds: 3));
      expect(find.text('Trying again now'), findsOneWidget);
      expect(_announced(tester), 'Offline');
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('large text: one quiet line keeps Try now in sight', (
      tester,
    ) async {
      await _pump(
        tester,
        line(message: 'Laptop isn’t answering'),
        size: const Size(320, 693),
        textScale: 2,
      );
      expect(
        find.text('Laptop isn’t answering · Trying again in 4 s'),
        findsOneWidget,
      );
      expect(find.text('Try now'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });
}

// Usage › one subscription: exact resets for every window, this plan's own
// alert level and reset switch, and (with several connections only) where it
// is signed in and the folded details.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/usage_subscriptions.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/quota/antigravity_quota_client.dart';
import 'package:opencode_mobile/state/usage_signin_destination.dart';
import 'package:opencode_mobile/state/usage_subscriptions_controller.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/usage/usage_subscription_screen.dart';
import 'package:opencode_mobile/ui/screens/usage/usage_text.dart';

import 'support/usage_ui_harness.dart';

final _en = lookupAppLocalizations(const Locale('en'));
const _key = 'codex|a';

class _Notes implements UsageReaderNotes {
  _Notes(this.problem);
  ComputerUsageProblem? problem;
  @override
  ComputerUsageProblem? problemFor(String connectionId) => problem;
}

Future<void> _open(
  WidgetTester tester,
  UsageHarness h, {
  String key = _key,
  bool Function(String)? phoneHasAgent,
}) async {
  usagePhoneView(tester, height: 1600);
  await h.usage.refresh();
  await tester.pumpWidget(
    usageApp(
      UsageSubscriptionScreen(
        controller: h.connection,
        usage: h.usage,
        subscriptionKey: key,
        launcher: h.launcher,
        clock: () => usageNow,
        phoneHasAgent: phoneHasAgent,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  usageTestWidgets('every window shows its exact reset and how long is left', (
    tester,
  ) async {
    final h = await usageHarness(
      connections: const [usageInApp],
      result: UsageSourceResult(
        readings: [
          usageReading(
            'codex',
            'inapp',
            account: 'a',
            plan: 'plus',
            windows: [
              ...usageBoth(),
              usageWindow(
                'monthly',
                12,
                30 * 86400,
                resetIn: const Duration(days: 20),
              ),
            ],
          ),
        ],
      ),
    );
    await _open(tester, h);
    expect(find.text('Codex'), findsWidgets);
    expect(find.text('Plus · ${_en.usageReadAgo('2 min')}'), findsOneWidget);
    expect(usageKey('usage-sub-window-primary'), findsOneWidget);
    expect(usageKey('usage-sub-window-secondary'), findsOneWidget);
    expect(usageKey('usage-sub-window-monthly'), findsOneWidget);
    expect(
      find.text(_en.usageResetsIn('1:25 PM', '1 h 25 min')),
      findsOneWidget,
    );
    expect(find.textContaining(_en.usageLeft(82)), findsOneWidget);
    expect(find.text(_en.usageWindowMonth), findsOneWidget);
  });

  usageTestWidgets('windows are one KitWindowRow group filled with the share '
      'left, each reset under its own bar, tools window named', (tester) async {
    final h = await usageHarness(
      result: UsageSourceResult(
        readings: [
          usageReading(
            'codex',
            'inapp',
            account: 'a',
            windows: [
              usageWindow('primary', 0, 18000),
              UsageWindow(id: 'mcp', usedPercent: 10),
            ],
          ),
        ],
      ),
    );
    await _open(tester, h);
    expect(find.byType(KitWindowRow), findsNWidgets(2));
    expect(
      tester.widget<KitWindowRow>(usageKey('usage-sub-window-primary')).left,
      1.0,
    );
    expect(
      tester.widget<KitWindowRow>(usageKey('usage-sub-window-primary')).note,
      isNotNull,
    );
    final tools = tester.widget<KitWindowRow>(usageKey('usage-sub-window-mcp'));
    expect(tools.title, _en.usageWindowTools);
    expect(tools.left, 0.9);
    expect(tools.note, isNull);
    expect(find.text('mcp'), findsNothing);
    expect(
      UsageText.windowTitle(
        _en,
        UsageWindow(id: 'daily_cap', usedPercent: 1, label: 'daily_cap'),
      ),
      'Daily Cap',
    );
  });

  usageTestWidgets('Alert me at says "same as your other plans" without an '
      'override', (tester) async {
    final h = await usageHarness(
      result: UsageSourceResult(
        readings: [usageReading('codex', 'inapp', account: 'a')],
      ),
    );
    await _open(tester, h);
    expect(find.text(_en.usageAlertAtSame), findsOneWidget);
    expect(find.text(_en.usageAlertAtPlan('Codex', 80)), findsNothing);
    await h.usage.setThresholdFor(_key, 90);
    await tester.pumpAndSettle();
    expect(find.text(_en.usageAlertAtPlan('Codex', 80)), findsOneWidget);
  });

  usageTestWidgets('one connection: no "Signed in on" and no Details', (
    tester,
  ) async {
    final h = await usageHarness(
      result: UsageSourceResult(
        readings: [usageReading('codex', 'inapp', account: 'a')],
      ),
    );
    await _open(tester, h);
    expect(usageKey('usage-sub-signed-in'), findsNothing);
    expect(usageKey('usage-sub-details'), findsNothing);
    expect(find.text(_en.usageSignedInOn), findsNothing);
  });

  usageTestWidgets('Alert me at writes this plan\'s override and "same as '
      'other plans" clears it', (tester) async {
    final h = await usageHarness(
      result: UsageSourceResult(
        readings: [usageReading('codex', 'inapp', account: 'a')],
      ),
    );
    await _open(tester, h);
    expect(find.text(_en.usageAlertAtSame), findsOneWidget);

    await tester.tap(usageKey('usage-sub-alert-at'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('90% used').last);
    await tester.pumpAndSettle();
    var s = h.usage.subscription(_key)!;
    expect(s.alertThreshold, 90);
    expect(s.hasAlertOverride, isTrue);
    expect(h.usage.defaultThreshold, 80);
    expect(h.usage.alertOverrides.map((x) => x.key), [_key]);

    await tester.tap(usageKey('usage-sub-alert-at'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(_en.usageAlertSameAsOthers(80)).last);
    await tester.pumpAndSettle();
    s = h.usage.subscription(_key)!;
    expect(s.hasAlertOverride, isFalse);
    expect(s.alertThreshold, 80);
  });

  usageTestWidgets('Tell me when it resets is saved for this plan', (
    tester,
  ) async {
    final h = await usageHarness(
      result: UsageSourceResult(
        readings: [usageReading('codex', 'inapp', account: 'a')],
      ),
    );
    await _open(tester, h);
    expect(h.usage.subscription(_key)!.notifyOnReset, isFalse);
    await tester.tap(usageKey('usage-sub-notify-reset'));
    await tester.pumpAndSettle();
    expect(h.usage.subscription(_key)!.notifyOnReset, isTrue);
    expect(h.usage.notifyOnResetDefault, isFalse);
  });

  usageTestWidgets('several connections: every connection with its sign-in '
      'state, a Sign in where one is missing, and folded details', (
    tester,
  ) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc, usageLaptop],
      result: UsageSourceResult(
        readings: [usageReading('codex', 'pc', account: 'a', plan: 'plus')],
        signIns: const [UsageSignIn(providerId: 'codex', connectionId: 'pc')],
        authoritativeSignIns: {'pc'},
      ),
    );
    await _open(tester, h);
    expect(find.text(_en.usageConnectionsHeader), findsOneWidget);
    expect(find.text(_en.usageSignedInOn), findsNothing);
    expect(usageKey('usage-sub-on-pc'), findsOneWidget);
    expect(
      find.textContaining(_en.usageConnReadsHere, findRichText: true),
      findsOneWidget,
    );
    // The other two connections can hold it too: Sign in on each.
    expect(usageKey('usage-sub-sign-in-laptop'), findsOneWidget);
    expect(usageKey('usage-sub-sign-in-inapp'), findsOneWidget);
    expect(usageKey('usage-sub-sign-in-pc'), findsNothing);

    // Never asked, so "not signed in" is not claimed.
    expect(find.text(_en.usageStateCouldntCheck), findsNWidgets(2));
    expect(find.text(_en.usageStateNotSignedIn), findsNothing);

    await tester.tap(usageKey('usage-sub-sign-in-laptop'));
    await tester.pumpAndSettle();
    final (destination, profile) = h.launcher.opened.single;
    expect(destination.kind, UsageSignInKind.openCodeProvider);
    expect(destination.providerId, 'openai');
    expect(profile, 'laptop');

    // Details: the reader, when it was read and whether an account is known;
    // never the account hash.
    await tester.ensureVisible(usageKey('usage-sub-details'));
    await tester.tap(find.text(_en.usageDetails));
    await tester.pumpAndSettle();
    expect(find.text(_en.usageDetailReader), findsOneWidget);
    expect(find.text('PC'), findsWidgets);
    expect(find.text(_en.usageYes), findsOneWidget);
    expect(find.textContaining('a' * 8), findsNothing);
  });

  usageTestWidgets(
    'Details: shows logged-in account email when accountRef is a readable email',
    (tester) async {
      final h = await usageHarness(
        connections: const [usageInApp, usagePc],
        result: UsageSourceResult(
          readings: [
            usageReading(
              'antigravity',
              'inapp',
              account: 'altaf.alu@gmail.com',
            ),
          ],
        ),
      );
      await _open(tester, h, key: 'antigravity|altaf.alu@gmail.com');
      await tester.ensureVisible(usageKey('usage-sub-details'));
      await tester.tap(find.text(_en.usageDetails));
      await tester.pumpAndSettle();
      expect(find.text(_en.usageDetailAccount), findsOneWidget);
      expect(find.text('altaf.alu@gmail.com'), findsOneWidget);
    },
  );

  usageTestWidgets(
    'account email falls back to AntigravityQuotaClient when reading accountRef was null',
    (tester) async {
      AntigravityQuotaClient.lastKnownAccountEmail = 'fallback@example.com';
      final h = await usageHarness(
        connections: const [usageInApp, usagePc],
        result: UsageSourceResult(
          readings: [
            usageReading(
              'antigravity',
              'inapp',
            ),
          ],
        ),
      );
      await _open(tester, h, key: 'antigravity|conn:inapp');
      await tester.ensureVisible(usageKey('usage-sub-details'));
      await tester.tap(find.text(_en.usageDetails));
      await tester.pumpAndSettle();
      expect(find.text(_en.usageDetailAccount), findsOneWidget);
      expect(find.text('fallback@example.com'), findsOneWidget);
      AntigravityQuotaClient.lastKnownAccountEmail = null;
    },
  );

  usageTestWidgets('a plan that vanished says so instead of showing nothing', (
    tester,
  ) async {
    final h = await usageHarness();
    await _open(tester, h, key: 'codex|gone');
    expect(usageKey('usage-sub-gone'), findsOneWidget);
  });

  usageTestWidgets('U9, U10: a plan with no numbers has no alerts block, no '
      '"Read" line and says "Not read yet"', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: UsageSourceResult(
        readings: [
          usageReading(
            'codex',
            'inapp',
            status: UsageReadingStatus.notSignedIn,
          ),
        ],
      ),
    );
    await _open(tester, h, key: 'codex|conn:inapp');
    expect(find.text(_en.usageAlertsTitle), findsNothing);
    expect(usageKey('usage-sub-alert-at'), findsNothing);
    expect(usageKey('usage-sub-notify-reset'), findsNothing);
    expect(find.textContaining('Read '), findsNothing);
    await tester.ensureVisible(usageKey('usage-sub-details'));
    await tester.tap(find.text(_en.usageDetails));
    await tester.pumpAndSettle();
    expect(find.text(_en.usageNotReadYet), findsOneWidget);
    expect(find.text(_en.usageDetailReader), findsNothing);
  });

  usageTestWidgets('U25 and U8: an offline connection says Offline, not '
      '"Not signed in"', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: UsageSourceResult(
        readings: [usageReading('codex', 'inapp', account: 'a')],
        unreached: const {'pc'},
      ),
    );
    await _open(tester, h);
    expect(find.text(_en.usageConnectionsHeader), findsOneWidget);
    expect(find.text(_en.usageConnOffline), findsOneWidget);
    expect(find.text(_en.usageStateNotSignedIn), findsNothing);
    expect(usageKey('usage-sub-sign-in-pc'), findsNothing);
  });

  group('this phone is listed only when its agent is installed', () {
    UsageSourceResult stopped({bool signedIn = false}) => UsageSourceResult(
      readings: [
        usageReading('codex', 'inapp', account: 'a'),
        if (signedIn)
          usageReading('codex', UsageConnection.phoneId, account: 'a'),
      ],
      unreached: const {UsageConnection.phoneId},
    );

    usageTestWidgets('no Codex agent on the phone: not listed, not Offline', (
      tester,
    ) async {
      final h = await usageHarness(
        connections: const [usageInApp, usagePc, usagePhone],
        result: stopped(),
      );
      await _open(tester, h, phoneHasAgent: (_) => false);
      expect(usageKey('usage-sub-signed-in'), findsOneWidget);
      expect(usageKey('usage-sub-on-phone'), findsNothing);
      expect(find.text(_en.usageThisPhone), findsNothing);
      expect(find.text(_en.usagePhoneNotRunning), findsNothing);
      // The saved server still lists, as before.
      expect(usageKey('usage-sub-on-pc'), findsOneWidget);
    });

    usageTestWidgets('agent installed but stopped: "Not running", never '
        '"Offline"', (tester) async {
      final h = await usageHarness(
        connections: const [usageInApp, usagePc, usagePhone],
        result: stopped(),
      );
      await _open(tester, h, phoneHasAgent: (id) => id == 'codex');
      expect(usageKey('usage-sub-on-phone'), findsOneWidget);
      expect(find.text(_en.usagePhoneNotRunning), findsOneWidget);
      expect(find.text(_en.usageConnOffline), findsNothing);
    });

    usageTestWidgets('signed in on the phone while its host is stopped says '
        '"Not running" too', (tester) async {
      final h = await usageHarness(
        connections: const [usageInApp, usagePhone],
        result: stopped(signedIn: true),
      );
      await _open(tester, h, phoneHasAgent: (_) => true);
      expect(
        find.textContaining(_en.usagePhoneNotRunning, findRichText: true),
        findsOneWidget,
      );
      expect(
        find.textContaining(_en.usageConnOffline, findRichText: true),
        findsNothing,
      );
    });

    usageTestWidgets('with no agent list at all (this app, no phone host) the '
        'phone is not listed', (tester) async {
      final h = await usageHarness(
        connections: const [usageInApp, usagePc, usagePhone],
        result: stopped(),
      );
      await _open(tester, h);
      expect(usageKey('usage-sub-on-phone'), findsNothing);
    });
  });

  group('a computer read through OpenCode', () {
    usageTestWidgets(
      'names the check, offers Turn off, and Turn off stops it',
      (tester) async {
        final h = await usageHarness(
          connections: const [usageInApp, usagePc],
          result: UsageSourceResult(
            readings: [usageReading('codex', 'pc', account: 'a')],
            signIns: const [
              UsageSignIn(providerId: 'codex', connectionId: 'pc'),
            ],
          ),
        );
        // Switched on after the app's own controller started, so it reads
        // nothing from a real server.
        await h.usage.prefs.setBool('oc.usageComputerReader.pc', true);
        await _open(tester, h);
        expect(usageKey('usage-sub-computer-pc'), findsOneWidget);
        expect(find.text(_en.usageComputerRowTitle('PC')), findsOneWidget);
        await tester.ensureVisible(usageKey('usage-sub-computer-off-pc'));
        await tester.tap(usageKey('usage-sub-computer-off-pc'));
        await tester.pumpAndSettle();
        expect(h.usage.prefs.containsKey('oc.usageComputerReader.pc'), isFalse);
        expect(usageKey('usage-sub-computer-pc'), findsNothing);
      },
    );

    usageTestWidgets('not turned on: no such row', (tester) async {
      final h = await usageHarness(
        connections: const [usageInApp, usagePc],
        result: UsageSourceResult(
          readings: [usageReading('codex', 'pc', account: 'a')],
        ),
      );
      await _open(tester, h);
      expect(usageKey('usage-sub-computer-pc'), findsNothing);
    });

    usageTestWidgets('a reader that cannot run says why in the row and in '
        'Details', (tester) async {
      final notes = _Notes(ComputerUsageProblem.nodeMissing);
      final h = await usageHarness(
        connections: const [usageInApp, usagePc],
        readerNotes: notes,
        result: UsageSourceResult(
          readings: [usageReading('codex', 'pc', account: 'a')],
          signIns: const [UsageSignIn(providerId: 'codex', connectionId: 'pc')],
        ),
      );
      // Switched on after the app's own controller started, so it reads
      // nothing from a real server.
      await h.usage.prefs.setBool('oc.usageComputerReader.pc', true);
      await _open(tester, h);
      expect(
        find.textContaining(
          _en.usageComputerNeedsNode('PC'),
          findRichText: true,
        ),
        findsOneWidget,
      );
      await tester.ensureVisible(usageKey('usage-sub-details'));
      await tester.tap(find.text(_en.usageDetails));
      await tester.pumpAndSettle();
      expect(find.text(_en.usageDetailLastCheck), findsOneWidget);
    });
  });
}

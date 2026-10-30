// Usage › Plans: the states the UI decides, by row of the combination table
// in docs/design/usage-subscriptions-contract-2026-10-13.md (rows 1, 3, 5, 11,
// 13, 14, 21, 25, 28 and 29), plus loading, refresh and the paired bars.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/domain/usage_subscriptions.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/usage_signin_destination.dart';
import 'package:opencode_mobile/state/usage_subscriptions_controller.dart';
import 'package:opencode_mobile/ui/screens/usage/usage_text.dart';
import 'package:opencode_mobile/ui/screens/usage_hub_screen.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';

import 'support/usage_ui_harness.dart';

final _en = lookupAppLocalizations(const Locale('en'));

Future<void> _open(WidgetTester tester, UsageHarness h) async {
  usagePhoneView(tester);
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pumpWidget(
    usageApp(
      UsageHubScreen(
        controller: h.connection,
        usage: h.usage,
        launcher: h.launcher,
        clock: () => usageNow,
        prepare: false,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

UsageSourceResult _result({
  List<UsageReading> readings = const [],
  List<UsageSignIn> signIns = const [],
  Set<String> authoritative = const {},
}) => UsageSourceResult(
  readings: readings,
  signIns: signIns,
  authoritativeSignIns: authoritative,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  usageTestWidgets('row 1: nothing signed in shows the empty state and the add '
      'list, with no server names', (tester) async {
    final h = await usageHarness();
    await _open(tester, h);
    expect(find.text(_en.usageEmptyTitle), findsOneWidget);
    // The add flow is the one row below, not also a button in the empty
    // state; never-used providers are listed in the picker, not inline.
    expect(usageKey('usage-empty-add'), findsNothing);
    expect(find.text(_en.usageAddSection), findsOneWidget);
    expect(usageKey('usage-add-codex'), findsNothing);
    await tester.tap(usageKey('usage-add-row'));
    await tester.pumpAndSettle();
    for (final id in usagePlanProviders) {
      expect(usageKey('usage-add-$id'), findsOneWidget);
    }
    expect(find.textContaining('On this phone'), findsNothing);
    // One section and no tab strip.
    expect(find.text(_en.usageTabPlans), findsNothing);
  });

  usageTestWidgets('before the first reading finishes the page is quiet, not '
      '"nothing"', (tester) async {
    final h = await usageHarness(
      result: _result(readings: [usageReading('codex', 'inapp', account: 'a')]),
    );
    h.source.hold = Completer<void>();
    usagePhoneView(tester);
    await tester.pumpWidget(
      usageApp(
        UsageHubScreen(
          controller: h.connection,
          usage: h.usage,
          clock: () => usageNow,
          prepare: false,
        ),
      ),
    );
    await tester.pump();
    await tester.pump();
    expect(usageKey('usage-plans-loading'), findsOneWidget);
    expect(find.text(_en.usageEmptyTitle), findsNothing);
    h.source.hold!.complete();
    await tester.pumpAndSettle();
    expect(usageKey('usage-plans-loading'), findsNothing);
    expect(usageKey('usage-card-codex|a'), findsOneWidget);
  });

  usageTestWidgets(
    'a card shows the 5-hour and weekly bars together, with what is '
    'left and one reset line',
    (tester) async {
      final h = await usageHarness(
        result: _result(
          readings: [
            usageReading('codex', 'inapp', account: 'a', plan: 'plus'),
          ],
        ),
      );
      await _open(tester, h);
      // The plan name with its tier in small text, on one line.
      expect(find.text('Codex Plus'), findsOneWidget);
      expect(usageKey('usage-bar-codex|a-primary'), findsOneWidget);
      expect(usageKey('usage-bar-codex|a-secondary'), findsOneWidget);
      expect(find.text(_en.usageWindowFiveHour), findsOneWidget);
      expect(find.text(_en.usageWindowWeek), findsOneWidget);
      // One small summary on the right: the tightest window, "% left".
      final summary = tester.widget<KitText>(usageKey('usage-summary-codex|a'));
      expect(summary.text, '59% left');
      // One line per window: bar, then "<n>% · <reset>", never "used".
      expect(find.textContaining('82% · '), findsOneWidget);
      expect(find.textContaining('59% · '), findsOneWidget);
      expect(find.textContaining('used'), findsNothing);
      // Healthy: no tag and no button.
      expect(usageKey('usage-fix-codex|a'), findsNothing);
    },
  );

  usageTestWidgets('bars fill with what is left, so a short bar always means '
      'little left, and every bar is the one accent colour', (tester) async {
    final h = await usageHarness(
      result: _result(
        readings: [
          usageReading(
            'codex',
            'inapp',
            account: 'a',
            windows: usageBoth(five: 100, week: 86),
          ),
          usageReading(
            'glm',
            'inapp',
            account: 'g',
            windows: usageBoth(five: 18, week: 41),
          ),
        ],
      ),
    );
    await _open(tester, h);
    KitWindowRow row(String key) =>
        tester.widget<KitWindowRow>(usageKey('usage-bar-$key'));
    double fill(String key) => tester
        .widget<FractionallySizedBox>(
          find.descendant(
            of: usageKey('usage-bar-$key'),
            matching: find.byType(FractionallySizedBox),
          ),
        )
        .widthFactor!;
    expect(fill('glm|g-primary'), closeTo(0.82, 0.001));
    expect(fill('glm|g-secondary'), closeTo(0.59, 0.001));
    // One colour for every bar: the numbers say how low a plan is.
    for (final id in ['glm|g-primary', 'glm|g-secondary']) {
      expect(row(id).tone, KitWindowTone.normal);
    }
    expect(fill('codex|a-primary'), 0);
    expect(row('codex|a-primary').tone, KitWindowTone.normal);
    expect(fill('codex|a-secondary'), closeTo(0.14, 0.001));
    expect(row('codex|a-secondary').tone, KitWindowTone.normal);
  });

  usageTestWidgets('row 3: signed in on a PC nothing reads says so and offers '
      '"Read usage", which opens that screen', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _result(
        signIns: const [
          UsageSignIn(
            providerId: 'codex',
            connectionId: 'pc',
            displayName: 'Codex',
          ),
        ],
        authoritative: {'pc'},
      ),
    );
    await _open(tester, h);
    expect(find.text(_en.usageSetupSignedInOn('PC')), findsOneWidget);
    // One status line and a short button; the connection is named once.
    expect(find.text(_en.usageReadUsage), findsOneWidget);
    expect(find.text(_en.usagePillCantRead), findsNothing);
    expect(find.textContaining('PC'), findsOneWidget);
    await tester.tap(usageKey('usage-fix-codex|conn:pc'));
    await tester.pumpAndSettle();
    expect(usageKey('usage-read-from'), findsOneWidget);
    expect(find.text(_en.usageComputerTurnOn), findsOneWidget);
  });

  usageTestWidgets(
    'row 5: a connection that is offline keeps the last numbers and '
    'says what happened since',
    (tester) async {
      final h = await usageHarness(
        connections: const [usageInApp, usagePc],
        result: _result(
          readings: [
            usageReading(
              'codex',
              'pc',
              account: 'a',
              age: const Duration(hours: 3),
              windows: [
                UsageWindow(
                  id: 'primary',
                  usedPercent: 90,
                  durationSeconds: 18000,
                  resetsAt: usageNow.subtract(const Duration(minutes: 30)),
                ),
              ],
            ),
          ],
        ),
      );
      await _open(tester, h);
      // The PC stops answering on the next read; the last numbers stay.
      h.source.result = const UsageSourceResult(unreached: {'pc'});
      await h.usage.refresh(force: true);
      await tester.pumpAndSettle();
      expect(find.text(_en.usagePillOffline), findsNothing);
      expect(find.text(_en.usageOfflineStale('PC', '3 h')), findsOneWidget);
      expect(usageKey('usage-bar-codex|a-primary'), findsOneWidget);
      expect(find.textContaining('since the last reading'), findsOneWidget);
    },
  );

  usageTestWidgets(
    'row 11: signed out where it was used stays listed with Sign in '
    'and Add key, and the fix goes to that provider\'s sign-in',
    (tester) async {
      final h = await usageHarness(
        result: _result(
          readings: [
            usageReading(
              'codex',
              'inapp',
              status: UsageReadingStatus.notSignedIn,
            ),
            usageReading(
              'glm',
              'inapp',
              status: UsageReadingStatus.notSignedIn,
            ),
          ],
        ),
      );
      await _open(tester, h);
      expect(find.text(_en.usageNotSignedInOne), findsOneWidget);
      expect(find.text(_en.usageNoKey), findsOneWidget);
      expect(find.text(_en.usageSignIn), findsOneWidget);
      expect(find.text(_en.usageAddKey), findsOneWidget);
      await tester.tap(usageKey('usage-fix-codex|conn:inapp'));
      await tester.pumpAndSettle();
      expect(h.launcher.opened, hasLength(1));
      final (destination, profile) = h.launcher.opened.single;
      expect(destination.kind, UsageSignInKind.openCodeProvider);
      expect(destination.providerId, 'openai');
      expect(profile, 'inapp');
    },
  );

  usageTestWidgets(
    'row 13: an expired sign-in turns the card to "Sign in again"',
    (tester) async {
      final h = await usageHarness(
        connections: const [usageInApp, usagePc],
        result: _result(
          readings: [
            usageReading(
              'codex',
              'pc',
              account: 'a',
              status: UsageReadingStatus.authRequired,
            ),
          ],
        ),
      );
      await _open(tester, h);
      expect(find.text(_en.usagePillSignInRanOut), findsNothing);
      expect(find.text(_en.usageSetupRanOutOn('PC')), findsOneWidget);
      expect(find.text(_en.usageSignInAgain), findsOneWidget);
      await tester.tap(usageKey('usage-fix-codex|a'));
      await tester.pumpAndSettle();
      expect(h.launcher.opened.single.$2, 'pc');
    },
  );

  usageTestWidgets(
    'row 14: an API key is not a plan card; it sits under Details',
    (tester) async {
      final h = await usageHarness(
        connections: const [usageInApp, usagePc],
        result: _result(
          signIns: const [
            UsageSignIn(
              providerId: 'codex',
              connectionId: 'pc',
              method: SignInMethod.apiKey,
              displayName: 'OpenAI',
            ),
          ],
          authoritative: {'pc'},
        ),
      );
      await _open(tester, h);
      expect(find.text(_en.usageEmptyTitle), findsOneWidget);
      expect(usageKey('usage-api-keys'), findsOneWidget);
      await tester.ensureVisible(usageKey('usage-api-keys'));
      await tester.tap(find.text(_en.usageDetails));
      await tester.pumpAndSettle();
      expect(find.text(_en.usageApiKeyOnly('Codex', 'PC')), findsOneWidget);
    },
  );

  usageTestWidgets('row 21: a limit reached says when it is back', (
    tester,
  ) async {
    final h = await usageHarness(
      result: _result(
        readings: [
          usageReading('codex', 'inapp', account: 'a'),
          usageReading(
            'glm',
            'inapp',
            account: 'b',
            windows: usageBoth(five: 100, week: 30),
          ),
        ],
      ),
    );
    await _open(tester, h);
    // A used-up plan says when it is back, in small text and never a pill.
    expect(find.textContaining('Back at '), findsOneWidget);
    expect(find.textContaining('Out until'), findsNothing);
    // Ordered by the least left among windows that still hold something:
    // the used-up plan refills on its own, so Codex (59% left) is first.
    final glm = tester.getTopLeft(usageKey('usage-card-glm|b'));
    final codex = tester.getTopLeft(usageKey('usage-card-codex|a'));
    expect(codex.dy, lessThan(glm.dy));
  });

  usageTestWidgets(
    'row 25: a broken reader says so in plain words and Try again '
    'reads again',
    (tester) async {
      final h = await usageHarness(
        result: _result(
          readings: [usageReading('codex', 'inapp', account: 'a')],
        ),
      );
      await _open(tester, h);
      h.source.result = const UsageSourceResult(
        failures: [UsageFailure('inapp', 'codex', UsageProblem.broken)],
      );
      await h.usage.refresh(force: true);
      await tester.pumpAndSettle();
      expect(find.text(_en.usagePillBroken), findsNothing);
      expect(find.text(_en.usageReaderBroken('Codex')), findsOneWidget);
      final before = h.source.calls;
      await tester.tap(usageKey('usage-fix-codex|a'));
      await tester.pumpAndSettle();
      expect(h.source.calls, greaterThan(before));
      expect(h.source.force, isTrue);
    },
  );

  usageTestWidgets('rows 28 and 29: server names show only with several '
      'connections', (tester) async {
    final one = await usageHarness(
      result: _result(readings: [usageReading('codex', 'inapp', account: 'a')]),
    );
    await _open(tester, one);
    expect(find.textContaining('On this phone'), findsNothing);

    final several = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _result(readings: [usageReading('codex', 'pc', account: 'a')]),
    );
    await _open(tester, several);
    expect(find.text('Codex PC'), findsOneWidget);
  });

  usageTestWidgets('opening the page, the top bar and a pull each read again', (
    tester,
  ) async {
    final h = await usageHarness(
      result: _result(readings: [usageReading('codex', 'inapp', account: 'a')]),
    );
    await _open(tester, h);
    final opened = h.source.calls;
    expect(opened, greaterThanOrEqualTo(1));

    await tester.tap(usageKey('plans-refresh'));
    await tester.pumpAndSettle();
    final bar = h.source.calls;
    expect(bar, greaterThan(opened));
    expect(h.source.force, isTrue);

    await tester.fling(usageKey('usage-plans'), const Offset(0, 400), 1000);
    await tester.pumpAndSettle();
    expect(h.source.calls, greaterThan(bar));
  });

  usageTestWidgets('the Alerts and widget rows say where things stand', (
    tester,
  ) async {
    final h = await usageHarness(
      result: _result(
        readings: [
          usageReading('codex', 'inapp', account: 'a'),
          usageReading('glm', 'inapp', account: 'b'),
        ],
      ),
    );
    await _open(tester, h);
    expect(find.text(_en.usageAlertsSummaryOff), findsOneWidget);
    expect(find.text(_en.usageWidgetPlans(2)), findsOneWidget);
    await h.usage.setAlertsOn(true);
    await tester.pumpAndSettle();
    expect(find.text(_en.usageAlertsOnWord), findsOneWidget);
  });

  usageTestWidgets('a plan still opens its own page', (tester) async {
    final h = await usageHarness(
      result: _result(
        readings: [usageReading('codex', 'inapp', account: 'a', plan: 'plus')],
      ),
    );
    await _open(tester, h);
    await tester.tap(usageKey('usage-card-codex|a'));
    await tester.pumpAndSettle();
    expect(usageKey('usage-subscription'), findsOneWidget);
  });

  usageTestWidgets('U22: Codex signed in on the PC is one card, not two', (
    tester,
  ) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _result(
        readings: [
          usageReading(
            'codex',
            'inapp',
            status: UsageReadingStatus.notSignedIn,
          ),
        ],
        signIns: const [
          UsageSignIn(
            providerId: 'codex',
            connectionId: 'pc',
            displayName: 'Codex',
          ),
        ],
        authoritative: {'pc'},
      ),
    );
    await _open(tester, h);
    expect(find.text('Codex'), findsOneWidget);
    expect(find.text(_en.usageNotSignedInAnywhere), findsNothing);
    expect(usageKey('usage-card-codex|conn:pc'), findsOneWidget);
    expect(usageKey('usage-card-codex|conn:inapp'), findsNothing);
  });

  usageTestWidgets('U19 and U20: only readable plans become cards; Claude '
      'through OpenCode is a Details line', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _result(
        signIns: const [
          UsageSignIn(providerId: 'claude', connectionId: 'pc'),
          UsageSignIn(
            providerId: 'copilot',
            connectionId: 'pc',
            displayName: 'GitHub Copilot',
          ),
          UsageSignIn(
            providerId: 'codex',
            connectionId: 'pc',
            displayName: 'Codex',
          ),
        ],
        authoritative: {'pc'},
      ),
    );
    await _open(tester, h);
    expect(find.text('Codex'), findsOneWidget);
    expect(find.text('GitHub Copilot'), findsNothing);
    expect(usageKey('usage-card-claude|conn:pc'), findsNothing);
    expect(usageKey('usage-card-copilot|conn:pc'), findsNothing);
    // Claude stays on the add list; its Details line names the limit.
    expect(h.usage.snapshot.addable, contains('claude'));
    await tester.ensureVisible(usageKey('usage-api-keys'));
    await tester.tap(find.text(_en.usageDetails));
    await tester.pumpAndSettle();
    expect(find.text(_en.usageViaOpenCodeNoPlan('PC')), findsOneWidget);
  });

  usageTestWidgets('U1 and U7: In-app Ubuntu is named by its server name, '
      '"This phone" is only the agents host', (tester) async {
    final h = await usageHarness(
      connections: const [
        UsageConnection(
          id: 'inapp',
          name: 'This phone',
          kind: UsageConnectionKind.inAppUbuntu,
        ),
        usagePhone,
      ],
      result: _result(
        readings: [
          usageReading('codex', 'inapp', account: 'a'),
          usageReading('claude', UsageConnection.phoneId, account: 'c'),
        ],
      ),
    );
    await _open(tester, h);
    expect(find.text('Codex ${_en.phoneServerCardTitle}'), findsOneWidget);
    expect(find.text('Claude ${_en.usageThisPhone}'), findsOneWidget);
  });

  usageTestWidgets('U1b and U2: a "not signed in" card never names a '
      'connection and says one thing', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _result(
        readings: [
          usageReading(
            'codex',
            'inapp',
            status: UsageReadingStatus.notSignedIn,
          ),
        ],
      ),
    );
    await _open(tester, h);
    expect(find.textContaining('On this phone'), findsNothing);
    expect(find.text(_en.usageNotSignedInOne), findsOneWidget);
    expect(find.text(_en.usageSignIn), findsOneWidget);
  });

  usageTestWidgets('U3: a setup card is one compact row with the action at '
      'its end', (tester) async {
    final h = await usageHarness(
      result: _result(
        readings: [
          usageReading(
            'codex',
            'inapp',
            status: UsageReadingStatus.notSignedIn,
          ),
        ],
      ),
    );
    await _open(tester, h);
    final card = tester.getSize(usageKey('usage-card-codex|conn:inapp'));
    final fix = tester.getRect(usageKey('usage-fix-codex|conn:inapp'));
    final cardRect = tester.getRect(usageKey('usage-card-codex|conn:inapp'));
    expect(card.height, lessThan(100));
    // The button sits in the row, beside the text, not under it.
    expect(fix.center.dy, lessThan(cardRect.bottom - 8));
    expect(fix.left, greaterThan(cardRect.center.dx));
  });

  usageTestWidgets('U6: tapping the card body opens the plan page, and the '
      'action button keeps its own tap', (tester) async {
    final h = await usageHarness(
      result: _result(
        readings: [
          usageReading(
            'codex',
            'inapp',
            status: UsageReadingStatus.notSignedIn,
          ),
          usageReading('glm', 'inapp', account: 'g'),
        ],
      ),
    );
    await _open(tester, h);
    // The number card: tap on its bars, not on the header row.
    await tester.tap(usageKey('usage-bar-glm|g-primary'));
    await tester.pumpAndSettle();
    expect(usageKey('usage-subscription'), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(usageKey('usage-fix-codex|conn:inapp'));
    await tester.pumpAndSettle();
    expect(usageKey('usage-subscription'), findsNothing);
    expect(h.launcher.opened, hasLength(1));
  });

  usageTestWidgets('U4: plan ids ask the logo catalog for the right slug', (
    tester,
  ) async {
    final asked = <String>[];
    ProviderLogo.logoLoaderOverride = (id) {
      asked.add(id);
      return null;
    };
    addTearDown(() => ProviderLogo.logoLoaderOverride = null);
    final h = await usageHarness(
      result: _result(
        readings: [
          // Plans that need setup are rows with their logo.
          usageReading(
            'codex',
            'inapp',
            status: UsageReadingStatus.notSignedIn,
          ),
          usageReading('glm', 'inapp', status: UsageReadingStatus.notSignedIn),
          usageReading(
            'claude',
            'inapp',
            status: UsageReadingStatus.notSignedIn,
          ),
        ],
      ),
    );
    await _open(tester, h);
    expect(asked, containsAll(['openai', 'zai', 'anthropic']));
    expect(asked, isNot(contains('codex')));
    expect(asked, isNot(contains('glm')));
    expect(UsageText.logoId('copilot'), 'github-copilot');
    expect(UsageText.logoId('kimi'), 'moonshotai');
    expect(UsageText.logoId('grok'), 'xai');
    expect(UsageText.logoId('minimax'), 'minimax');
    expect(UsageText.logoId('cursor'), 'cursor');
  });

  usageTestWidgets('structure: cards with numbers first, then one "Needs '
      'setup" group, then Add / Alerts / widget', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _result(
        readings: [
          usageReading('codex', 'inapp', account: 'a', plan: 'plus'),
          usageReading('glm', 'inapp', status: UsageReadingStatus.notSignedIn),
          usageReading(
            'minimax',
            'inapp',
            status: UsageReadingStatus.notSignedIn,
          ),
        ],
        signIns: const [
          UsageSignIn(
            providerId: 'claude',
            connectionId: 'pc',
            displayName: 'Claude',
          ),
        ],
        authoritative: {'pc'},
      ),
    );
    await _open(tester, h);
    final card = tester.getTopLeft(usageKey('usage-card-codex|a')).dy;
    final group = tester.getTopLeft(usageKey('usage-needs-setup')).dy;
    final add = tester.getTopLeft(usageKey('usage-add-row')).dy;
    final alerts = tester.getTopLeft(usageKey('usage-alerts-row')).dy;
    expect(card, lessThan(group));
    expect(group, lessThan(add));
    expect(add, lessThan(alerts));
    expect(find.text(_en.usageNeedsSetupHeader), findsOneWidget);
    // Two rows in the one group, "No key added" for the key plans, and the
    // muted line is the only place a status is worded.
    expect(find.text(_en.usageNoKey), findsNWidgets(2));
    expect(find.text(_en.usageAddKey), findsNWidgets(2));
    expect(usageKey('usage-no-numbers'), findsNothing);
    // A plan card: the plan and connection under the name, no chevron.
    expect(find.text('Codex Plus · On this phone'), findsOneWidget);
  });

  usageTestWidgets('nothing readable yet: one quiet line above Needs setup', (
    tester,
  ) async {
    final h = await usageHarness(
      result: _result(
        readings: [
          usageReading('glm', 'inapp', status: UsageReadingStatus.notSignedIn),
        ],
      ),
    );
    await _open(tester, h);
    expect(find.text(_en.usageNoNumbersYet), findsOneWidget);
    expect(usageKey('usage-needs-setup'), findsOneWidget);
    expect(find.text(_en.usageEmptyTitle), findsNothing);
  });

  usageTestWidgets('plans sit in one group, the plan with the least left '
      'first, with no pill and no icon tiles on the three rows', (
    tester,
  ) async {
    final h = await usageHarness(
      result: _result(
        readings: [
          // Left: Codex 59%, GLM 10%, MiniMax 50%; Claude is used up in its
          // 5 hours and holds 36% for the week, so it sits between GLM and
          // MiniMax: a window that ran out refills on its own.
          usageReading('codex', 'inapp', account: 'a'),
          usageReading(
            'claude',
            'inapp',
            account: 'c',
            windows: usageBoth(five: 100, week: 64),
          ),
          usageReading(
            'glm',
            'inapp',
            account: 'g',
            windows: usageBoth(five: 20, week: 90),
          ),
          usageReading(
            'minimax',
            'inapp',
            account: 'm',
            windows: usageBoth(five: 10, week: 50),
          ),
        ],
      ),
    );
    await _open(tester, h);
    final group = usageKey('usage-plans-group');
    expect(group, findsOneWidget);
    for (final id in ['codex|a', 'glm|g', 'minimax|m']) {
      expect(
        find.descendant(of: group, matching: usageKey('usage-card-$id')),
        findsOneWidget,
      );
    }
    double top(String id) => tester.getTopLeft(usageKey('usage-card-$id')).dy;
    expect(top('glm|g'), lessThan(top('claude|c')));
    expect(top('claude|c'), lessThan(top('minimax|m')));
    expect(top('minimax|m'), lessThan(top('codex|a')));
    // The summary says how low, in text: never a pill.
    expect(find.byType(KitStatusTag), findsNothing);
    expect(
      tester.widget<KitText>(usageKey('usage-summary-glm|g')).text,
      '10% left',
    );
    // The three rows below are plain rows: no icon tile in front.
    for (final key in ['usage-alerts-row', 'usage-widget-row']) {
      expect(tester.widget<KitRow>(usageKey(key)).leading, isNull, reason: key);
    }
  });

  usageTestWidgets('a used-up plan says when it is back, not a percent', (
    tester,
  ) async {
    final h = await usageHarness(
      result: _result(
        readings: [
          usageReading(
            'glm',
            'inapp',
            account: 'g',
            windows: usageBoth(five: 100, week: 36),
          ),
        ],
      ),
    );
    await _open(tester, h);
    final summary = tester
        .widget<KitText>(usageKey('usage-summary-glm|g'))
        .text;
    expect(summary, startsWith('Back at '));
    expect(summary, isNot(contains('left')));
  });

  for (final (name, locale) in [
    ('English', const Locale('en')),
    ('Arabic', const Locale('ar')),
  ]) {
    usageTestWidgets('$name at 320 dp and 2.0x text: nothing overflows', (
      tester,
    ) async {
      final h = await usageHarness(
        connections: const [usageInApp, usagePc],
        result: _result(
          readings: [
            usageReading('codex', 'inapp', account: 'a', plan: 'plus'),
            usageReading(
              'glm',
              'inapp',
              account: 'b',
              windows: usageBoth(five: 100, week: 91),
            ),
            usageReading(
              'minimax',
              'inapp',
              status: UsageReadingStatus.notSignedIn,
            ),
          ],
          signIns: const [
            UsageSignIn(
              providerId: 'codex',
              connectionId: 'pc',
              displayName: 'Codex',
            ),
          ],
          authoritative: {'pc'},
        ),
      );
      tester.view.physicalSize = const Size(320, 1800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        usageApp(
          Builder(
            builder: (context) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(2)),
              child: UsageHubScreen(
                controller: h.connection,
                usage: h.usage,
                launcher: h.launcher,
                clock: () => usageNow,
                prepare: false,
              ),
            ),
          ),
          locale: locale,
        ),
      );
      await tester.pumpAndSettle();
      expect(usageKey('usage-card-glm|b'), findsOneWidget);
      expect(usageKey('usage-needs-setup'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }
}

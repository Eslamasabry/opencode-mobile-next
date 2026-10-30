// What the owner's phone pass on APK 2226 (2026-10-12) found: Memory kept
// reminders in UTC on a phone in Asia/Dubai, a reminder could be saved with
// no word that this phone cannot ring it, and the locked-folder copy
// promised an unlock the node cannot do yet.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/factsbook/fake_factsbook_gateway.dart';
import 'package:opencode_mobile/state/factsbook/phone_zone.dart';
import 'package:opencode_mobile/ui/screens/factsbook/about_you_screen.dart';
import 'package:opencode_mobile/ui/screens/factsbook/reminders_screen.dart';
import 'package:opencode_mobile/ui/screens/factsbook/settings_screen.dart';

import '../support/part_semantics.dart';
import 'factsbook_test_support.dart';

Future<void> _tapKey(WidgetTester tester, String key) async {
  final finder = find.byKey(ValueKey(key));
  await tester.ensureVisible(finder);
  await tester.tap(finder);
  await settle(tester);
}

void main() {
  final original = phoneTimeZoneLoader;
  tearDown(() => phoneTimeZoneLoader = original);

  factsTest('Memory offers this phone\'s time zone when the home zone differs, '
      'and switches with one approval', (tester) async {
    phoneTimeZoneLoader = () async => 'Asia/Dubai';
    final node = FakeFactsBookGateway.sample();
    expect((await node.settings()).vaultZone, 'UTC');
    await pumpFacts(tester, const FactsBookSettingsScreen(), gateway: node);
    expect(find.text("Use this phone's time zone"), findsOneWidget);
    expect(rich('Asia/Dubai'), findsOneWidget);
    expect(rich('Reminders keep their clock times.'), findsOneWidget);
    await _tapKey(tester, 'factsbook-settings-use-phone-zone');
    await tester.pump(const Duration(milliseconds: 600));
    await settle(tester);
    expect(node.calls, contains('settings:zone:Asia/Dubai'));
    expect((await node.settings()).vaultZone, 'Asia/Dubai');
    expect(find.text("Use this phone's time zone"), findsNothing);
  });

  factsTest('no offer when the zones already match or Android does not say', (
    tester,
  ) async {
    phoneTimeZoneLoader = () async => 'UTC';
    await pumpFacts(tester, const FactsBookSettingsScreen());
    expect(find.text("Use this phone's time zone"), findsNothing);

    phoneTimeZoneLoader = () async => null;
    await pumpFacts(tester, const FactsBookSettingsScreen());
    expect(find.text("Use this phone's time zone"), findsNothing);
  });

  factsTest('no offer without an approval key on this phone', (tester) async {
    phoneTimeZoneLoader = () async => 'Asia/Dubai';
    await pumpFacts(tester, const FactsBookSettingsScreen(), canApprove: false);
    expect(find.text("Use this phone's time zone"), findsNothing);
  });

  factsTest('the reminder check says this phone cannot ring it yet, '
      'before saving', (tester) async {
    final node = FakeFactsBookGateway.sample();
    final id = (await node.reminders()).single.id;
    await pumpFacts(tester, const FactsBookRemindersScreen(), gateway: node);
    await _tapKey(tester, 'factsbook-reminder-$id');
    expect(find.text('Check this reminder'), findsOneWidget);
    final notice = find.byKey(const ValueKey('factsbook-reminder-cannot-ring'));
    await tester.ensureVisible(notice);
    expect(notice, findsOneWidget);
    expect(
      find.textContaining("This phone can't ring reminders yet."),
      findsOneWidget,
    );
  });

  factsTest('the home zone says what it does, without contradicting itself', (
    tester,
  ) async {
    final node = FakeFactsBookGateway.sample();
    final id = (await node.reminders()).single.id;
    await pumpFacts(tester, const FactsBookRemindersScreen(), gateway: node);
    await _tapKey(tester, 'factsbook-reminder-$id');
    expect(rich('It moves if you travel'), findsNothing);
  });

  factsTest('the Needs you row says "Needs you" once to a screen reader', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await pumpFacts(tester, const FactsBookAboutYouScreen());
    final label = partSemantics(
      tester,
      find.byKey(const ValueKey('factsbook-about-needs-you')),
    ).label;
    expect(RegExp('Needs you').allMatches(label).length, 1, reason: label);
    semantics.dispose();
  });
}

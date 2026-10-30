import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/factsbook_node_host.dart';
import 'package:opencode_mobile/domain/factsbook/fake_factsbook_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/factsbook/factsbook_controller.dart';
import 'package:opencode_mobile/state/factsbook/factsbook_outbox.dart';
import 'package:opencode_mobile/state/factsbook/factsbook_phone_node.dart';
import 'package:opencode_mobile/state/factsbook/factsbook_session.dart';
import 'package:opencode_mobile/state/factsbook/factsbook_flag.dart';
import 'package:opencode_mobile/ui/app_theme.dart';

/// A runner without the built-in Linux: the default in widget tests, which
/// would otherwise count as an Android phone and reach for the native bridge.
class NoNodeHost implements FactsBookNodeHost {
  const NoNodeHost();

  @override
  bool get supported => false;
  @override
  String get address => '127.0.0.1:8787';
  @override
  Future<NodeHostFacts> inspect() async => const NodeHostFacts();
  @override
  Future<void> installBinary() async {}
  @override
  Future<void> createVault() async {}
  @override
  Future<void> startNode() async {}
  @override
  Future<void> stopNode() async {}
  @override
  Future<NodePairing> pairingCode({required String ownerKeyHex}) async =>
      throw const NodeHostException(NodeHostFailure.pair);
  @override
  Future<String> log() async => '';
  @override
  Future<void> removeData() async {}
}

/// "Now" for every FactsBook widget test: the fake node's dates are
/// relative to it, so "3 hours ago" is the same day on every run.
final factsNow = DateTime(2026, 10, 11, 12);

/// A test inside a fixed clock on a phone-sized window.
void factsTest(
  String name,
  Future<void> Function(WidgetTester tester) body, {
  Size size = const Size(412, 915),
}) {
  testWidgets(name, (tester) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await withClock(Clock.fixed(factsNow), () => body(tester));
  });
}

/// Pumps [home] over the fake node. The flag is on unless [flag] is false.
Future<void> pumpFacts(
  WidgetTester tester,
  Widget home, {
  FakeFactsBookGateway? gateway,
  Locale locale = const Locale('en'),
  double textScale = 1,
  bool flag = true,
  bool canApprove = true,
  MemoryOutboxStore? outbox,
  FactsBookSession? session,
  List<Override> overrides = const [],
  FactsBookNodeHost nodeHost = const NoNodeHost(),
}) async {
  final node = gateway ?? FakeFactsBookGateway.sample();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        factsBookSessionProvider.overrideWithValue(
          session ??
              FactsBookSession.fixed(
                node,
                outboxStore: outbox,
                canApprove: canApprove,
              ),
        ),
        factsBookEnabledProvider.overrideWithValue(flag),
        factsBookPhoneNodeHostProvider.overrideWithValue(nodeHost),
        ...overrides,
      ],
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark(),
        locale: locale,
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: Material(child: home),
      ),
    ),
  );
  await settle(tester);
}

/// Lets the fake node's answers land and the page draw. Never
/// `pumpAndSettle`: skeleton rows and the loading bar keep animating.
Future<void> settle(WidgetTester tester) async {
  // Past a page transition after its first (hero) frame: Android's own is
  // 450 ms (2026-10-26).
  for (var i = 0; i < 5; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

/// Text inside a row's rich supporting line.
Finder rich(String text) => find.textContaining(text, findRichText: true);

/// The same as [rich] for a whole line.
Finder richExact(String text) => find.byWidgetPredicate(
  (widget) => widget is RichText && widget.text.toPlainText() == text,
);

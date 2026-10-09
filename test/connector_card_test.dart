// The suggested-connector card (FC8): each state of the BD3 contract, with a
// fake of the conversation-bound controller.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart' show GenUiParsed;
import 'package:opencode_mobile/domain/mcp_chat.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connector_card_host.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitBidi;
import 'package:opencode_mobile/ui/widgets/agent_card_view.dart';

import '../tool/capture/fixtures.dart' show captureTheme;
import 'support/agent_card_fakes.dart';
import 'support/connector_card_fakes.dart';

final _en = lookupAppLocalizations(const Locale('en'));

Future<void> _pump(
  WidgetTester tester,
  ConnectorCardHost? host, {
  VoidCallback? onTools,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: captureTheme(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Material(
        child: SingleChildScrollView(
          child: AgentCardView(
            controller: FakeGenUi(),
            parse: GenUiParsed(connectorCard()),
            agentLabel: 'OpenCode',
            connectors: host,
            onOpenConnectors: onTools,
          ),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 600));
}

ConnectorReady _ready(FakeConnectorChat chat, {bool signIn = true}) =>
    ConnectorReady(item: connectorItem(), chat: chat, canSignIn: signIn);

void main() {
  testWidgets('suggested shows name, reason, facts and Connect', (t) async {
    final chat = FakeConnectorChat();
    await _pump(t, FakeConnectorHost(_ready(chat)));
    expect(find.text(KitBidi.auto('Design Reference')), findsOneWidget);
    expect(find.textContaining('Find design references'), findsOneWidget);
    expect(
      find.text(_en.mcpCatalogHostedBy(KitBidi.ltr('mcp.acme.example'))),
      findsOneWidget,
    );
    expect(find.text(_en.connectorCardLastsValue), findsOneWidget);
    await t.tap(find.byKey(const Key('connector-card-connect')));
    expect(chat.calls, ['connect:design-reference']);
  });

  testWidgets('connecting and checking show progress in place', (t) async {
    final chat = FakeConnectorChat(
      const McpChatSnapshot(McpChatPhase.connecting),
    );
    await _pump(t, FakeConnectorHost(_ready(chat)));
    expect(find.text(_en.connectorCardConnecting), findsOneWidget);
    chat.snapshot = const McpChatSnapshot(McpChatPhase.checkingTools);
    await t.pump(const Duration(milliseconds: 100));
    expect(find.text(_en.connectorCardChecking), findsOneWidget);
  });

  testWidgets('needs sign-in offers Sign in; OAuth off says unavailable', (
    t,
  ) async {
    final chat = FakeConnectorChat(
      const McpChatSnapshot(McpChatPhase.needsAuthentication),
    );
    await _pump(t, FakeConnectorHost(_ready(chat)));
    await t.tap(find.byKey(const Key('connector-card-sign-in')));
    await t.pump();
    expect(chat.calls, ['startOAuth']);
  });

  testWidgets('sign-in unavailable says so and offers no Sign in', (t) async {
    final chat = FakeConnectorChat(
      const McpChatSnapshot(McpChatPhase.needsAuthentication),
    );
    await _pump(t, FakeConnectorHost(_ready(chat, signIn: false)));
    expect(find.text(_en.connectorCardFailureOauthUnavailable), findsOneWidget);
    expect(find.byKey(const Key('connector-card-sign-in')), findsNothing);
  });

  testWidgets('authorizing with manual code finishes sign-in', (t) async {
    final chat = FakeConnectorChat(
      McpChatSnapshot(
        McpChatPhase.authorizing,
        authorizationUrl: Uri.parse('https://auth.example/o?state=s'),
        manualCodeRequired: true,
      ),
    );
    await _pump(t, FakeConnectorHost(_ready(chat)));
    expect(find.text(_en.connectorCardManualCode), findsOneWidget);
    await t.enterText(find.byType(EditableText).first, 'code123');
    await t.pump();
    await t.tap(find.byKey(const Key('connector-card-finish')));
    expect(chat.calls, ['complete']);
  });

  testWidgets('tools ready says Connected and Tools ready', (t) async {
    final chat = FakeConnectorChat(
      const McpChatSnapshot(McpChatPhase.toolsReady),
    );
    await _pump(t, FakeConnectorHost(_ready(chat)));
    expect(find.text(_en.connectorCardConnected), findsOneWidget);
    expect(find.text(_en.connectorCardToolsReady), findsOneWidget);
    expect(find.text(_en.connectorCardLoadedTools), findsOneWidget);
    expect(find.byKey(const Key('connector-card-connect')), findsNothing);
  });

  testWidgets('readiness unknown never says tools are ready', (t) async {
    final chat = FakeConnectorChat(
      const McpChatSnapshot(McpChatPhase.connectedReadinessUnknown),
    );
    await _pump(t, FakeConnectorHost(_ready(chat)));
    expect(find.text(_en.connectorCardConnectedUnconfirmed), findsOneWidget);
    expect(find.text(_en.connectorCardToolsReady), findsNothing);
    expect(find.text(_en.connectorCardLoadedTools), findsNothing);
  });

  for (final failure in McpChatFailure.values) {
    testWidgets('failure ${failure.name} uses its fixed sentence', (t) async {
      final chat = FakeConnectorChat(
        McpChatSnapshot(
          failure == McpChatFailure.unavailable
              ? McpChatPhase.unavailable
              : McpChatPhase.failed,
          failure: failure,
        ),
      );
      var tools = 0;
      await _pump(t, FakeConnectorHost(_ready(chat)), onTools: () => tools++);
      expect(
        find.byKey(Key('connector-card-failure-${failure.name}')),
        findsOneWidget,
      );
      expect(find.byKey(const Key('connector-card-connect')), findsNothing);
      final tryAgain = find.byKey(const Key('connector-card-try-again'));
      final check = find.byKey(const Key('connector-card-check-status'));
      final reopen = find.byKey(const Key('connector-card-reopen'));
      final toolsBtn = find.byKey(const Key('connector-card-open-tools'));
      switch (failure) {
        case McpChatFailure.connectFailed:
          await t.tap(tryAgain);
          expect(chat.calls, ['connect:design-reference']);
        case McpChatFailure.authenticationFailed || McpChatFailure.notConnected:
          await t.tap(check);
          expect(chat.calls, ['refresh']);
        case McpChatFailure.sourceChanged:
          expect(reopen, findsOneWidget);
        default:
          await t.tap(toolsBtn);
          expect(tools, 1);
      }
    });
  }

  testWidgets('unsupported runtime never shows Connect', (t) async {
    await _pump(
      t,
      FakeConnectorHost(ConnectorUnsupported(item: connectorItem())),
    );
    expect(find.byKey(const Key('connector-card-connect')), findsNothing);
    expect(find.text(_en.connectorCardFailureUnavailable), findsOneWidget);
  });

  testWidgets('already connected shows Connected without a button', (t) async {
    await _pump(
      t,
      FakeConnectorHost(ConnectorAlreadyConnected(item: connectorItem())),
    );
    expect(find.text(_en.connectorCardConnected), findsOneWidget);
    expect(find.byKey(const Key('connector-card-connect')), findsNothing);
  });

  testWidgets('a listing that needs setup points to Tools, no Connect', (
    t,
  ) async {
    await _pump(
      t,
      FakeConnectorHost(
        ConnectorNeedsSetup(item: connectorItem(hosted: false)),
      ),
    );
    expect(find.text(_en.connectorCardFailureSetupRequired), findsOneWidget);
    expect(find.byKey(const Key('connector-card-connect')), findsNothing);
  });

  testWidgets('no host draws the suggestion without Connect', (t) async {
    await _pump(t, null);
    expect(find.textContaining('Find design references'), findsOneWidget);
    expect(find.byKey(const Key('connector-card-connect')), findsNothing);
  });
}

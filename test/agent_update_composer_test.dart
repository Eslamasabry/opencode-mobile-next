// While an automatic agent update cannot let a message through, the composer
// says so before Send (disabled, with progress), keeps the draft, and Send
// comes back by itself: the app never accepts a send it would refuse.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/phone_agents_auto_update.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';

Widget _app(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(
    body: Align(alignment: Alignment.bottomCenter, child: child),
  ),
);

void main() {
  testWidgets('Send is off with the progress line, the draft stays, and Send '
      'returns when the update ends', (t) async {
    var sends = 0;
    final c = TextEditingController(text: 'fix the build');
    final f = FocusNode();
    addTearDown(c.dispose);
    addTearDown(f.dispose);
    Widget composer(String? blocked) => _app(
      KitComposer(
        controller: c,
        focusNode: f,
        hint: 'Ask',
        onSend: () => sends++,
        sendBlockedNote: blocked,
        sendKey: const Key('s'),
        voiceButtonKey: const Key('v'),
      ),
    );

    await t.pumpWidget(
      composer("Claude Code is updating, 40%. You can send when it's done."),
    );
    expect(
      find.textContaining('Claude Code is updating, 40%', findRichText: true),
      findsOneWidget,
    );
    await t.tap(find.byKey(const Key('s')), warnIfMissed: false);
    await t.pump();
    expect(sends, 0, reason: 'Send must not accept what would be refused');
    expect(c.text, 'fix the build', reason: 'the draft stays');

    await t.pumpWidget(composer(null));
    await t.tap(find.byKey(const Key('s')));
    await t.pump();
    expect(sends, 1, reason: 'it enables itself when the update ends');
    expect(find.textContaining('is updating'), findsNothing);
  });

  test('the block says whose update it is and whose Send it holds', () {
    const own = AgentUpdateBlock(agentName: 'Claude Code', fraction: 0.4);
    expect(own.blocks('Claude Code'), isTrue);
    expect(own.blocks('Kilo'), isFalse);
    const all = AgentUpdateBlock(agentName: 'Claude Code', everyAgent: true);
    expect(all.blocks('Kilo'), isTrue);
    expect(all.blocks(null), isFalse);
  });
}

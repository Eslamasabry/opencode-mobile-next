// "Summarize on this phone" in the conversation menu: offered when the
// assistant is on and downloaded (SessionMenuOffer.localSummary), whatever
// the server can do, and greyed with a reason while the conversation has no
// words to summarize.
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/session_menu.dart';

import '../support/fake_server_capabilities.dart';

void main() {
  final en = lookupAppLocalizations(const Locale('en'));
  const key = ValueKey('session-menu-summarizeLocal');

  List<KitMenuItem> items({
    required bool localSummary,
    bool hasPrompt = true,
    ServerCapabilities capabilities = testServerCapabilities,
    List<SessionMenuAction>? picked,
  }) => sessionMenuItems(
    en,
    SessionMenuOffer.of(
      capabilities,
      shared: false,
      savedServer: true,
      hasPrompt: hasPrompt,
      localSummary: localSummary,
    ),
    onSelected: (action) => picked?.add(action),
  );

  KitMenuItem? entry(List<KitMenuItem> menu) =>
      menu.where((item) => item.key == key).firstOrNull;

  test('the entry is absent until the assistant is on and downloaded', () {
    expect(entry(items(localSummary: false)), isNull);
    final item = entry(items(localSummary: true));
    expect(item, isNotNull);
    expect(item!.label, en.localAssistantSummarizeMenu);
    expect(item.supporting, en.localAssistantSummarizeSupporting);
  });

  test('it sits in the Do group between Fork and Rename', () {
    final menu = items(localSummary: true);
    final labels = [for (final item in menu) item.label];
    final fork = labels.indexOf(en.chatUiForkSession);
    final summarize = labels.indexOf(en.localAssistantSummarizeMenu);
    final rename = labels.indexOf(en.chatUiRenameSession);
    expect(fork, isNonNegative);
    expect(summarize, greaterThan(fork));
    expect(rename, greaterThan(summarize));
    expect(entry(menu)!.group, KitMenuGroup(en.sessionMenuDo));
  });

  test('a server that cannot fork or compact does not take it away', () {
    const bare = ServerCapabilities(
      sessionFork: false,
      sessionCompact: false,
      sessionShare: false,
    );
    expect(entry(items(localSummary: true, capabilities: bare)), isNotNull);
  });

  test('with nothing said yet it is greyed, with the reason', () {
    final item = entry(items(localSummary: true, hasPrompt: false))!;
    expect(item.enabled, isFalse);
    expect(item.disabledReason, en.sessionMenuNeedsPrompt);
  });

  test('choosing it reports the summarize action', () {
    final picked = <SessionMenuAction>[];
    entry(items(localSummary: true, picked: picked))!.onSelected();
    expect(picked, [SessionMenuAction.summarizeLocal]);
  });
}

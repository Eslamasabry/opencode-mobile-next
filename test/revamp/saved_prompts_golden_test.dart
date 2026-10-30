// Golden renders of slice-P3.2, Saved prompts absorb drafts: an older draft
// (saved before drafts named their server) listed in Saved prompts after its
// one-time move; a restore that acts at once with Undo (it replaced the
// "Restore saved prompt?" question); a delete that acts at once with Undo;
// and the notice when older drafts cannot move in because Saved prompts is
// full. Phone 412x915 and one wide window (1280x800), dark, with the app's
// real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/saved_prompts_golden_test.dart
// and look at every changed image before committing it.
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import '../support/fake_server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/prompt_shelf.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/complete_message_history.dart';
import '../support/stash_memory_vault.dart';
import '../support/composer_tools.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size) => [
  'saved_prompts_$shot',
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  'dark',
].join('_');

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');
  @override
  Future<List<MessageWithParts>> messages(String id) async => [
    MessageWithParts(
      info: MessageInfo(
        id: 'm1',
        sessionID: id,
        role: 'user',
        time: MsgTime(created: 1),
      ),
      parts: [Part(type: 'text', text: 'Run the checkout tests on CI too')],
    ),
  ];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];
}

class _Profiles extends ProfileStore {
  _Profiles(SharedPreferences prefs) : super(prefs: prefs);
  final saved = ServerProfile(
    id: 'laptop',
    name: 'Laptop',
    baseUrl: 'http://localhost',
  );
  @override
  List<ServerProfile> get profiles => [saved];
}

class _Controller extends ConnectionController {
  _Controller(super.store)
    : super(
        stashAttachmentVault: StashMemoryVault(),
        draftAttachmentVault: StashMemoryVault(),
      );
  @override
  ServerProfile get profile => (store as _Profiles).saved;
}

final _day = DateTime(2026, 9, 20, 10).millisecondsSinceEpoch;

/// 1.0.44 preferences: one older draft with no server recorded.
Map<String, Object> _olderDraft() => {
  'oc.sessionDrafts': jsonEncode([
    {
      'sessionID': 'ses_old',
      'text':
          'Ship the checkout redesign before Friday review — add empty '
          'states and update the changelog.',
      'updatedAt': _day - 9 * 24 * 60 * 60 * 1000,
    },
  ]),
};

Future<void> _frames(WidgetTester tester) async {
  for (var frame = 0; frame < 10; frame++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _openSaved(WidgetTester tester) async {
  await openComposerTools(tester);
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tools-prompts')));
  await tester.pumpAndSettle();
  await Scrollable.ensureVisible(
    tester.element(find.byKey(const Key('composer-tool-saved'))),
    alignment: .5,
  );
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tool-saved')));
  await _frames(tester);
}

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  Size size = _phone,
  Map<String, Object> prefs = const {},
  Future<void> Function(ConnectionController c)? seed,
  required Future<void> Function(ConnectionController c) then,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  SharedPreferences.setMockInitialValues(prefs);
  final c = _Controller(_Profiles(await SharedPreferences.getInstance()))
    ..api = _Api()
    ..status = StreamStatus.connected;
  c.sessionsById['s'] = Session(id: 's', title: 'Checkout redesign');
  await seed?.call(c);
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(c)],
        child: RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: child!,
            ),
            home: const ChatScreen(sessionID: 's'),
          ),
        ),
      ),
    );
    await _frames(tester);
    await then(c);
    await _frames(tester);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size)}.png'),
    );
  } finally {
    KitUndo.commitPending();
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
    c.dispose();
  }
}

Future<void> _saved(ConnectionController c, String id, String text, int at) =>
    c.savePromptStash(
      StashedPrompt(id: id, text: text, createdAt: at),
      locationRevision: c.locationRevision,
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final size in [_phone, _wide]) {
    final wide = size == _wide ? ' wide' : '';
    testWidgets('older draft in Saved prompts$wide', (tester) async {
      await _shot(
        tester,
        'older_draft',
        size: size,
        prefs: _olderDraft(),
        seed: (c) => _saved(
          c,
          'coupon',
          'Add a test for an expired coupon: the total must stay the same.',
          _day,
        ),
        then: (_) => _openSaved(tester),
      );
    });
    testWidgets('restored at once with Undo$wide', (tester) async {
      await _shot(
        tester,
        'restored_undo',
        size: size,
        seed: (c) => _saved(
          c,
          'coupon',
          'Add a test for an expired coupon: the total must stay the same.',
          _day,
        ),
        then: (_) async {
          await tester.enterText(
            find.byKey(const Key('chat-composer-field')),
            'Check the payment form on a small phone',
          );
          await _openSaved(tester);
          await tester.tap(find.byKey(const ValueKey('restore-stash-coupon')));
          await _frames(tester);
          // Slice-polish 2026-09-28: the Undo bar floats above the composer
          // (phone and wide), never over it.
          final bar = tester.getRect(
            find.byKey(const Key('prompt-restored-undo')),
          );
          final composer = tester.getRect(find.byType(KitComposer));
          expect(bar.bottom, lessThanOrEqualTo(composer.top));
        },
      );
    });
    testWidgets('deleted at once with Undo$wide', (tester) async {
      await _shot(
        tester,
        'deleted_undo',
        size: size,
        seed: (c) async {
          await _saved(c, 'coupon', 'Add a test for an expired coupon.', _day);
          await _saved(c, 'ci', 'Run the checkout tests on CI too', _day + 1);
        },
        then: (_) async {
          await _openSaved(tester);
          await tester.longPress(
            find.byKey(const ValueKey('restore-stash-coupon')),
          );
          await _frames(tester);
          await tester.tap(find.text('Delete saved prompt'));
          await _frames(tester);
        },
      );
    });
  }
  testWidgets('older drafts waiting because Saved prompts is full', (
    tester,
  ) async {
    await _shot(
      tester,
      'older_drafts_full',
      prefs: _olderDraft(),
      seed: (c) async {
        for (var i = 0; i < PromptShelfStore.capacity; i++) {
          await _saved(c, 'p$i', 'Saved prompt number ${i + 1}', _day + i);
        }
      },
      then: (_) => _openSaved(tester),
    );
  });
}

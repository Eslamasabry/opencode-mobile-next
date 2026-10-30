// Behaviour tests for KitComposer (docs/ux-system/kit-api/KitComposer.md
// "Tests required"). Owner decision 2026-09-27: English only, no RTL.
import 'package:flutter/material.dart';
import 'package:flutter/gestures.dart' show PointerDeviceKind;
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import '../support/no_red.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/effects.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer_chips.dart';
import 'package:opencode_mobile/ui/kit/glass/kit_glass.dart';
import 'package:opencode_mobile/ui/kit/kit_bottom_inset.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_segmented.dart';
import 'package:opencode_mobile/ui/kit/kit_tappable.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_harness.dart';

import 'kit_motion_still.dart';

const _send = Key('chat-send-button');
const _stop = Key('chat-stop-button');
const _field = Key('chat-composer-field');
const _tools = Key('composer-tools-button');
const _editor = Key('prompt-editor-button');
const _voice = Key('voice-button');

class _Host {
  _Host(String text) : controller = TextEditingController(text: text);
  final TextEditingController controller;
  final FocusNode focus = FocusNode();
  int sends = 0, voices = 0, tools = 0, editors = 0;

  void dispose() {
    controller.dispose();
    focus.dispose();
  }
}

_Host _host(String text) {
  final host = _Host(text);
  addTearDown(host.dispose);
  return host;
}

KitComposer _composer(
  _Host h, {
  bool busy = false,
  bool sending = false,
  bool canSendWhileBusy = false,
  KitComposerDelivery delivery = KitComposerDelivery.afterThisReply,
  bool offline = false,
  String? readOnlyReason,
  bool voiceButton = true,
  KitComposerVoice? voice,
  KitComposerChips? suggestions,
  KitComposerFailure? failure,
  List<KitComposerTrayAction> tray = const [],
  List<KitMenuItem> sendMenu = const [],
}) => KitComposer(
  controller: h.controller,
  focusNode: h.focus,
  hint: 'Ask OpenCode…',
  onSend: () => h.sends++,
  busy: busy,
  sending: sending,
  canSendWhileBusy: canSendWhileBusy,
  delivery: delivery,
  offline: offline,
  readOnlyReason: readOnlyReason,
  onVoice: voiceButton ? () => h.voices++ : null,
  voice: voice,
  suggestions: suggestions,
  onTools: () => h.tools++,
  tray: tray,
  sendMenu: sendMenu,
  onOpenEditor: () => h.editors++,
  fieldKey: _field,
  sendKey: _send,
  toolsKey: _tools,
  voiceButtonKey: _voice,
  editorKey: _editor,
  failure: failure,
);

Future<void> _pump(
  WidgetTester tester,
  Widget composer, {
  Size size = const Size(412, 915),
  double textScale = 1,
  bool disableAnimations = false,
  KitEffects effects = KitEffects.defaults,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    KitEffectsScope(
      effects: effects,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: TextScaler.linear(textScale),
            disableAnimations: disableAnimations,
          ),
          child: child!,
        ),
        home: Scaffold(
          body: Align(
            alignment: Alignment.bottomCenter,
            child: Padding(padding: const EdgeInsets.all(16), child: composer),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

String? _label(WidgetTester tester, Key key) =>
    tester.getSemantics(find.byKey(key)).label;

KitSuggestion _suggestion(String id) =>
    KitSuggestion(id: id, label: '/$id', kind: KitSuggestionKind.command);

void main() {
  kitMotionStillTests(
    'KitComposer',
    builds: {
      'idle': () => _composer(_host('Review the checkout changes')),
      'busy': () => _composer(_host(''), busy: true),
      'sending': () =>
          _composer(_host('Review the checkout changes'), sending: true),
    },
    changes: {
      'reply starts': KitMotionChange(
        build: () => _composer(_host('Review the checkout changes')),
        act: (tester, stage) => stage.rebuild(_composer(_host(''), busy: true)),
        shows: 'Ask OpenCode…',
      ),
    },
  );

  tearDown(() => debugPlatformCapabilities = null);

  group('trailing control (the table)', () {
    testWidgets('idle, no text, voice: the mic', (tester) async {
      final semantics = tester.ensureSemantics();
      final h = _host('');
      await _pump(tester, _composer(h));
      expect(find.byKey(_send), findsNothing);
      expect(_label(tester, _voice), 'Talk instead of typing');
      await tester.tap(find.byKey(_voice));
      expect(h.voices, 1);
      semantics.dispose();
    });

    testWidgets('idle, no text, no voice: a disabled Send', (tester) async {
      final semantics = tester.ensureSemantics();
      final h = _host('');
      await _pump(tester, _composer(h, voiceButton: false));
      final node = tester.getSemantics(find.byKey(_send));
      expect(node.label, 'Send');
      await tester.tap(find.byKey(_send), warnIfMissed: false);
      expect(h.sends, 0);
      semantics.dispose();
    });

    testWidgets('idle, text: Send', (tester) async {
      final semantics = tester.ensureSemantics();
      final h = _host('hello');
      await _pump(tester, _composer(h));
      expect(_label(tester, _send), 'Send');
      expect(find.byKey(_voice), findsNothing);
      await tester.tap(find.byKey(_send));
      expect(h.sends, 1);
      semantics.dispose();
    });

    testWidgets('sending: this tap only says Sending', (tester) async {
      final semantics = tester.ensureSemantics();
      final h = _host('hello');
      await _pump(tester, _composer(h, sending: true), disableAnimations: true);
      expect(_label(tester, _send), 'Sending');
      await tester.tap(find.byKey(_send), warnIfMissed: false);
      expect(h.sends, 0);
      semantics.dispose();
    });

    // Stop is not in the box: it sits at the end of the chips row above it
    // (KitComposerStatusStrip.stop), with or without text. While a reply runs
    // the field keeps its mic, so a message can be spoken and sent after it.
    testWidgets('busy, no text: the mic stays and the box has no Stop', (
      tester,
    ) async {
      final h = _host('');
      await _pump(tester, _composer(h, busy: true));
      expect(find.byKey(_stop), findsNothing);
      expect(find.byKey(const ValueKey('kit-composer-stop')), findsNothing);
      expect(find.byKey(_send), findsNothing);
      expect(find.byKey(_voice), findsOneWidget);
      await tester.tap(find.byKey(_voice));
      expect(h.voices, 1);
    });

    testWidgets('busy, text, can send: Send only, no Stop beside the field', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      final h = _host('more');
      await _pump(tester, _composer(h, busy: true, canSendWhileBusy: true));
      expect(find.byKey(_stop), findsNothing);
      expect(_label(tester, _send), 'Send after this reply');
      await tester.tap(find.byKey(_send));
      expect(h.sends, 1);
      semantics.dispose();
    });

    testWidgets('busy, text, cannot send: no Send, no Stop, the reason', (
      tester,
    ) async {
      final h = _host('more');
      await _pump(tester, _composer(h, busy: true));
      expect(find.byKey(_stop), findsNothing);
      expect(find.byKey(_send), findsNothing);
      expect(
        find.text('You can send when this reply finishes'),
        findsOneWidget,
      );
    });
  });

  testWidgets('words typed under a running reply: Send is 48 dp and there is '
      'no Stop beside the field (it sits in the chips row)', (tester) async {
    final h = _host('more');
    await _pump(tester, _composer(h, busy: true, canSendWhileBusy: true));
    expect(find.byKey(_stop), findsNothing);
    final send = tester.getRect(find.byKey(_send));
    expect(send.width, greaterThanOrEqualTo(48));
    expect(send.height, greaterThanOrEqualTo(48));
  });

  // Owner decision 2 Oct (02B): Stop is neutral, a text-colour circle with a
  // ground square; red is for destructive actions only. Today that circle is
  // the voice mode's "Stop reading" (the chat's Stop is a chip above the box).
  testWidgets('Stop reading is a neutral text-colour circle with a turning '
      'ring', (tester) async {
    final h = _host('');
    await _pump(
      tester,
      _composer(
        h,
        voice: KitComposerVoice(
          phase: KitVoicePhase.speakingReply,
          onExit: () {},
          onStopSpeaking: () {},
        ),
      ),
    );
    final roles = KitTokens.of(tester.element(find.byType(KitComposer))).roles;
    final circle = tester.widget<DecoratedBox>(
      find
          .descendant(
            of: find.byKey(const ValueKey('kit-composer-circle-stop')),
            matching: find.byType(DecoratedBox),
          )
          .first,
    );
    expect((circle.decoration as BoxDecoration).color, roles.text1);
    expect(
      find.byKey(const ValueKey('kit-composer-stop-ring')),
      findsOneWidget,
    );
  });

  testWidgets('idle: no ring, and nothing about the reply is written on the '
      'box', (tester) async {
    final h = _host('');
    await _pump(tester, _composer(h));
    expect(find.byKey(const ValueKey('kit-composer-stop-ring')), findsNothing);
  });

  // Owner, 24 Oct 2026: "+", Stop, the editor and Send beside a growing
  // message left it a narrow column. Once the words run past one line of
  // that row, they take the whole width and the controls sit under them.
  group('a long message takes the whole width', () {
    testWidgets('busy with words typed: the field spans the composer, "+", '
        'and Send sit in a row under it', (tester) async {
      final h = _host('Short');
      await _pump(
        tester,
        _composer(h, busy: true, canSendWhileBusy: true),
        size: const Size(412, 915),
      );
      final field = find.byKey(_field);
      // One short line: one row, the field beside Send.
      expect(
        tester.getCenter(find.byKey(_send)).dy,
        moreOrLessEquals(tester.getCenter(field).dy, epsilon: 24),
      );
      await tester.tap(field);
      await tester.pump();
      final editing = tester.state<EditableTextState>(
        find.descendant(of: field, matching: find.byType(EditableText)),
      );
      h.controller.text =
          'I want one agent to super focus on the media player control and '
          'scroll and seeking';
      await tester.pumpAndSettle();
      final composerWidth = 412 - 32.0;
      expect(tester.getSize(field).width, greaterThan(composerWidth * .75));
      for (final control in [_tools, _send]) {
        expect(
          tester.getTopLeft(find.byKey(control)).dy,
          greaterThanOrEqualTo(tester.getBottomLeft(field).dy - 1),
          reason: '$control sits under the words',
        );
      }
      // The same field, still focused: the keyboard stays.
      expect(
        tester.state<EditableTextState>(
          find.descendant(of: field, matching: find.byType(EditableText)),
        ),
        same(editing),
      );
      expect(h.focus.hasFocus, isTrue);
      // Back to a few words: one row again.
      h.controller.text = 'Short';
      await tester.pumpAndSettle();
      expect(
        tester.getCenter(find.byKey(_send)).dy,
        moreOrLessEquals(tester.getCenter(field).dy, epsilon: 24),
      );
      expect(h.focus.hasFocus, isTrue);
    });
  });

  group('delivery', () {
    testWidgets('there is never a delivery choice; the fixed delivery is said '
        'on Send and in the note', (tester) async {
      final semantics = tester.ensureSemantics();
      final h = _host('more');
      await _pump(tester, _composer(h, busy: true, canSendWhileBusy: true));
      expect(find.byType(KitSegmented<KitComposerDelivery>), findsNothing);
      expect(find.text('Sends after this reply'), findsOneWidget);
      expect(_label(tester, _send), 'Send after this reply');

      await _pump(
        tester,
        _composer(
          h,
          busy: true,
          canSendWhileBusy: true,
          delivery: KitComposerDelivery.addToThisTurn,
        ),
      );
      expect(find.byType(KitSegmented<KitComposerDelivery>), findsNothing);
      expect(_label(tester, _send), 'Add to this turn');
      expect(find.text('Adds to this reply'), findsOneWidget);
      expect(find.text('Sends after this reply'), findsNothing);
      semantics.dispose();
    });

    testWidgets('an agent that restarts: Stop and send, said why, no choice', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      final h = _host('more');
      await _pump(
        tester,
        _composer(
          h,
          busy: true,
          canSendWhileBusy: true,
          delivery: KitComposerDelivery.stopAndSend,
        ),
      );
      expect(_label(tester, _send), 'Stop and send');
      expect(
        find.textContaining("can't take a message mid-reply."),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('kit-composer-circle-stopAndSend')),
        findsOneWidget,
      );
      expect(find.text('Sends after this reply'), findsNothing);
      expect(find.byType(KitSegmented<KitComposerDelivery>), findsNothing);
      semantics.dispose();
    });
  });

  testWidgets('a failed send is neutral words with Try again, never red', (
    tester,
  ) async {
    final h = _host('');
    var retries = 0;
    await _pump(
      tester,
      _composer(
        h,
        failure: KitComposerFailure(
          words: "Didn't send",
          onRetry: () => retries++,
        ),
      ),
    );
    final roles = KitTokens.of(tester.element(find.byType(KitComposer))).roles;
    Color? colorOf(String text) {
      final rich = tester.widget<RichText>(
        find.descendant(
          of: find.byType(KitComposer),
          matching: find.byWidgetPredicate(
            (w) => w is RichText && w.text.toPlainText() == text,
          ),
        ),
      );
      return rich.text.style?.color;
    }

    expect(colorOf("Didn't send"), roles.text1);
    expect(colorOf('· Try again'), roles.text1);
    expect(colorOf("Didn't send"), isNotRed);
    await tester.tap(find.text('· Try again'));
    expect(retries, 1);
  });

  testWidgets('a failed send keeps Retry and Details at 48 dp', (tester) async {
    final h = _host('');
    const retryKey = ValueKey('failure-retry');
    await _pump(
      tester,
      _composer(
        h,
        failure: KitComposerFailure(
          words: "Didn't send",
          retryKey: retryKey,
          onRetry: () {},
          onDetails: () {},
        ),
      ),
    );
    final retry = tester.getSize(find.byKey(retryKey));
    expect(retry.height, greaterThanOrEqualTo(48));
    final details = tester.getSize(
      find.ancestor(
        of: find.byIcon(AppIconography.info),
        matching: find.byType(KitTappable),
      ),
    );
    expect(details.width, greaterThanOrEqualTo(48));
    expect(details.height, greaterThanOrEqualTo(48));
  });

  testWidgets('a failure with its own fix shows it in Retry\'s place', (
    tester,
  ) async {
    final h = _host('');
    var retried = 0, fixed = 0;
    const fixKey = ValueKey('failure-fix');
    await _pump(
      tester,
      _composer(
        h,
        failure: KitComposerFailure(
          words: "This project's Git history can't be read.",
          onRetry: () => retried++,
          actionLabel: 'Repair',
          onAction: () => fixed++,
          actionKey: fixKey,
        ),
      ),
    );
    expect(find.text('· Repair'), findsOne);
    expect(find.text('· Retry'), findsNothing);
    expect(tester.getSize(find.byKey(fixKey)).height, greaterThanOrEqualTo(48));
    await tester.tap(find.byKey(fixKey));
    expect((fixed, retried), (1, 0));
  });

  testWidgets('offline: Send says it queues and still sends', (tester) async {
    final semantics = tester.ensureSemantics();
    final h = _host('later');
    await _pump(tester, _composer(h, offline: true));
    expect(_label(tester, _send), 'Send when back online');
    expect(find.text("Sends when OpenCode answers"), findsOne);
    await tester.tap(find.byKey(_send));
    expect(h.sends, 1);
    semantics.dispose();
  });

  group('keys and haptics', () {
    testWidgets('a Send tap sends once and ticks once', (tester) async {
      final haptics = recordHaptics(tester);
      final h = _host('hello');
      await _pump(tester, _composer(h));
      await tester.tap(find.byKey(_send));
      expect(h.sends, 1);
      expect(haptics, ['HapticFeedbackType.lightImpact']);
    });

    testWidgets('a finger sends on press, so a button that moves under it '
        'cannot lose the Send, and the release does not send again', (
      tester,
    ) async {
      final h = _host('hello');
      await _pump(tester, _composer(h));
      final press = await tester.startGesture(
        tester.getCenter(find.byKey(_send)),
      );
      expect(h.sends, 1);
      await press.up();
      await tester.pump();
      expect(h.sends, 1);
    });

    testWidgets('a mouse click still sends on release, once', (tester) async {
      final h = _host('hello');
      await _pump(tester, _composer(h));
      final mouse = await tester.startGesture(
        tester.getCenter(find.byKey(_send)),
        kind: PointerDeviceKind.mouse,
      );
      expect(h.sends, 0);
      await mouse.up();
      expect(h.sends, 1);
    });

    testWidgets('Vibration off: no haptic', (tester) async {
      final haptics = recordHaptics(tester);
      final h = _host('hello');
      await _pump(
        tester,
        _composer(h),
        effects: KitEffects.defaults.copyWith(haptics: false),
      );
      await tester.tap(find.byKey(_send));
      expect(h.sends, 1);
      expect(haptics, isEmpty);
    });

    testWidgets('Ctrl+Enter sends on touch; Enter does not', (tester) async {
      final haptics = recordHaptics(tester);
      final h = _host('hello');
      await _pump(tester, _composer(h));
      await tester.tap(find.byKey(_field));
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      expect(h.sends, 0);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      expect(h.sends, 1);
      expect(haptics, hasLength(1));
    });

    testWidgets('desktop: Enter sends, Shift+Enter does not', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.linux,
        isWeb: false,
      );
      final h = _host('hello\nthere');
      await _pump(tester, _composer(h), size: const Size(1280, 800));
      await tester.tap(find.byKey(_field));
      await tester.pump();
      await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
      expect(h.sends, 0);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      expect(h.sends, 1);
    });

    testWidgets('busy without send-while-busy: Ctrl+Enter does nothing', (
      tester,
    ) async {
      final h = _host('hello');
      await _pump(tester, _composer(h, busy: true));
      await tester.tap(find.byKey(_field));
      await tester.pump();
      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      expect(h.sends, 0);
    });
  });

  testWidgets('the composer never changes the text', (tester) async {
    final h = _host('keep me');
    await _pump(tester, _composer(h, busy: true, canSendWhileBusy: true));
    await tester.tap(find.byKey(_send));
    await tester.tap(find.byKey(_field));
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();
    expect(h.controller.text, 'keep me');

    var exits = 0;
    await _pump(
      tester,
      _composer(
        h,
        voice: KitComposerVoice(
          phase: KitVoicePhase.listening,
          onExit: () => exits++,
        ),
      ),
    );
    await tester.tap(find.bySemanticsLabel('Leave voice mode'));
    await _pump(tester, _composer(h));
    expect(exits, 1);
    expect(h.controller.text, 'keep me');
  });

  testWidgets('read-only: the reason, no typing, no Send, "+", voice or '
      'model', (tester) async {
    final h = _host('');
    await _pump(
      tester,
      _composer(h, readOnlyReason: 'This session is archived'),
    );
    expect(find.text('This session is archived'), findsOneWidget);
    final field = tester.widget<TextField>(
      find.descendant(of: find.byKey(_field), matching: find.byType(TextField)),
    );
    expect(field.enabled, isFalse);
    expect(find.byKey(_send), findsNothing);
    expect(find.byKey(_tools), findsNothing);
    expect(find.byKey(_voice), findsNothing);
    expect(find.byType(KitComposerChips), findsNothing);
  });

  group('voice', () {
    const words = {
      KitVoicePhase.starting: 'Getting the microphone ready…',
      KitVoicePhase.listening: 'Listening…',
      KitVoicePhase.transcribing: 'Writing down what you said…',
      KitVoicePhase.waitingReply: 'Waiting for the reply…',
      KitVoicePhase.speakingReply: 'Reading the reply aloud',
      KitVoicePhase.replyReady: 'The reply is ready',
      KitVoicePhase.paused: 'Paused · the agent needs you',
      KitVoicePhase.micDenied: 'The microphone is off for this app',
      KitVoicePhase.failed: 'Voice stopped',
    };

    for (final MapEntry(key: phase, value: said) in words.entries) {
      testWidgets('$phase: its words, in a live region', (tester) async {
        final h = _host('');
        await _pump(
          tester,
          _composer(
            h,
            voice: KitComposerVoice(
              phase: phase,
              onExit: () {},
              onListen: () {},
              onStopListening: () {},
              onStopSpeaking: () {},
              onReadReply: () {},
            ),
          ),
        );
        expect(find.text(said), findsOneWidget);
        expect(
          find.byWidgetPredicate(
            (w) =>
                w is Semantics &&
                w.properties.liveRegion == true &&
                w.properties.label == said,
          ),
          findsOneWidget,
        );
        expect(find.byType(TextField), findsNothing);
      });
    }

    testWidgets('listening: Send (conversation) or Done (dictation)', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      final haptics = recordHaptics(tester);
      var stopped = 0;
      final h = _host('');
      KitComposer build(bool conversation) => _composer(
        h,
        voice: KitComposerVoice(
          phase: KitVoicePhase.listening,
          onExit: () {},
          conversation: conversation,
          level: ValueNotifier(0.5),
          listeningSince: DateTime.now(),
          onStopListening: () => stopped++,
        ),
      );
      await _pump(tester, build(false));
      expect(find.bySemanticsLabel('Done'), findsOneWidget);
      await tester.tap(find.bySemanticsLabel('Done'));
      expect(stopped, 1);
      expect(haptics, isEmpty);
      await _pump(tester, build(true));
      expect(find.bySemanticsLabel('Send'), findsOneWidget);
      await tester.tap(find.bySemanticsLabel('Send'));
      expect(stopped, 2);
      expect(haptics, hasLength(1));
      semantics.dispose();
    });

    testWidgets('speakingReply: Stop reading; replyReady: Read it aloud and '
        'Listen', (tester) async {
      final semantics = tester.ensureSemantics();
      var stopSpeaking = 0, read = 0, listen = 0;
      final h = _host('');
      await _pump(
        tester,
        _composer(
          h,
          voice: KitComposerVoice(
            phase: KitVoicePhase.speakingReply,
            onExit: () {},
            onStopSpeaking: () => stopSpeaking++,
          ),
        ),
      );
      await tester.tap(find.bySemanticsLabel('Stop reading'));
      expect(stopSpeaking, 1);
      await _pump(
        tester,
        _composer(
          h,
          voice: KitComposerVoice(
            phase: KitVoicePhase.replyReady,
            onExit: () {},
            onReadReply: () => read++,
            onListen: () => listen++,
          ),
        ),
      );
      await tester.tap(find.text('Read it aloud'));
      await tester.tap(find.bySemanticsLabel('Listen'));
      expect((read, listen), (1, 1));
      semantics.dispose();
    });

    testWidgets('micDenied: the reason and the fix', (tester) async {
      var fixed = 0;
      final h = _host('');
      await _pump(
        tester,
        _composer(
          h,
          voice: KitComposerVoice(
            phase: KitVoicePhase.micDenied,
            onExit: () {},
            reason: 'Android settings keep the microphone off',
            fix: KitAction(label: 'Allow microphone', onPressed: () => fixed++),
          ),
        ),
      );
      expect(
        find.text('Android settings keep the microphone off'),
        findsOneWidget,
      );
      await tester.tap(find.text('Allow microphone'));
      expect(fixed, 1);
    });

    testWidgets('Esc and the exit button leave voice mode', (tester) async {
      var exits = 0;
      final h = _host('');
      await _pump(
        tester,
        _composer(
          h,
          voice: KitComposerVoice(
            phase: KitVoicePhase.waitingReply,
            onExit: () => exits++,
          ),
        ),
      );
      await tester.tap(find.bySemanticsLabel('Leave voice mode'));
      expect(exits, 1);
      // Tab puts focus on the exit button; Esc bubbles to the composer.
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      expect(exits, 2);
    });

    testWidgets('the read-aloud toggle reports the new value', (tester) async {
      final changes = <bool>[];
      final h = _host('');
      await _pump(
        tester,
        _composer(
          h,
          voice: KitComposerVoice(
            phase: KitVoicePhase.waitingReply,
            onExit: () {},
            conversation: true,
            onReadRepliesAloudChanged: changes.add,
          ),
        ),
      );
      await tester.tap(find.text('Read replies aloud'));
      expect(changes, [true]);
    });
  });

  testWidgets('the pill is one solid KitGlass surface', (tester) async {
    final h = _host('');
    await _pump(tester, _composer(h));
    expect(find.byType(KitGlass), findsOneWidget);
  });

  group('layer', () {
    Future<double> pumpLayer(WidgetTester tester, {double keyboard = 0}) async {
      final h = _host('hello');
      late double bodyBottom;
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      tester.view.viewInsets = FakeViewPadding(bottom: keyboard);
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: KitComposer.layer(
            body: Builder(
              builder: (context) {
                bodyBottom = KitBottomInset.of(context).bottom;
                return const SizedBox.expand();
              },
            ),
            composer: _composer(h),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return bodyBottom;
    }

    testWidgets('the body clears the composer', (tester) async {
      final bottom = await pumpLayer(tester);
      final rect = tester.getRect(find.byType(KitComposer));
      // The composer plus the space2 gap under it; nothing inherited here.
      expect(bottom, rect.height + 8);
      expect(rect.bottom, 915 - 8);
    });

    testWidgets('the band paints no ground: the live background shows', (
      tester,
    ) async {
      await pumpLayer(tester);
      final band = tester.widget<ColoredBox>(
        find.byKey(const ValueKey('kit-composer-band')),
      );
      expect(band.color.a, 0);
    });

    testWidgets('the transcript is clipped at the band, not painted under it', (
      tester,
    ) async {
      await pumpLayer(tester);
      final band = tester.getRect(
        find.byKey(const ValueKey('kit-composer-band')),
      );
      final clip = tester.widget<ClipRect>(
        find
            .ancestor(
              of: find.byType(SizedBox),
              matching: find.byType(ClipRect),
            )
            .first,
      );
      final rect = clip.clipper!.getClip(const Size(412, 915));
      expect(rect.bottom, lessThanOrEqualTo(band.top + 0.5));
    });

    testWidgets('the keyboard lifts the composer', (tester) async {
      final bottom = await pumpLayer(tester, keyboard: 300);
      final rect = tester.getRect(find.byType(KitComposer));
      expect(rect.bottom, 915 - 300 - 8);
      expect(bottom, 300 + rect.height + 8);
    });
  });

  testWidgets('field height: capped at 40 % of the window and scrolls', (
    tester,
  ) async {
    final h = _host(List.generate(20, (i) => 'line $i').join('\n'));
    await _pump(tester, _composer(h), textScale: 2);
    final field = tester.getRect(find.byKey(_field));
    expect(field.height, lessThanOrEqualTo(915 * 0.4));
    expect(
      find.descendant(
        of: find.byKey(_field),
        matching: find.byType(Scrollable),
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('every control is labelled and at least 48 dp', (tester) async {
    final semantics = tester.ensureSemantics();
    // Words past one line: the editor's corner button shows.
    final h = _host('more\nwords');
    await _pump(tester, _composer(h, busy: true, canSendWhileBusy: true));
    for (final (key, label) in [
      (_tools, 'Attach and more'),
      (_editor, 'Open full-screen editor'),
      (_send, 'Send after this reply'),
    ]) {
      final size = tester.getSize(find.byKey(key));
      expect(size.width, greaterThanOrEqualTo(48), reason: label);
      expect(size.height, greaterThanOrEqualTo(48), reason: label);
      expect(find.bySemanticsLabel(label), findsWidgets, reason: label);
    }
    expect(find.bySemanticsLabel('Message'), findsOneWidget);
    semantics.dispose();
  });

  group('Esc and Tab (desktop)', () {
    testWidgets('Esc: suggestions first, then unfocus; text kept', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.linux,
        isWeb: false,
      );
      final h = _host('/co');
      await _pump(
        tester,
        _composer(
          h,
          suggestions: KitComposerChips.suggestions(
            suggestions: [_suggestion('compact')],
            onSelected: (_) {},
          ),
        ),
        size: const Size(1280, 800),
      );
      await tester.tap(find.byKey(_field));
      await tester.pump();
      expect(find.text('/compact'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.text('/compact'), findsNothing);
      expect(h.focus.hasFocus, isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pump();
      expect(h.focus.hasFocus, isFalse);
      expect(h.controller.text, '/co');
    });

    testWidgets('Tab from the field reaches the editor in its corner, then '
        '"+"', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.linux,
        isWeb: false,
      );
      final h = _host('hello\nthere');
      await _pump(tester, _composer(h), size: const Size(1280, 800));
      await tester.tap(find.byKey(_field));
      await tester.pump();
      bool focusedIn(Key key) {
        final focused = FocusManager.instance.primaryFocus!.context!;
        return find
            .descendant(
              of: find.byKey(key),
              matching: find.byWidgetPredicate(
                (w) => identical(w, focused.widget),
              ),
            )
            .evaluate()
            .isNotEmpty;
      }

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(focusedIn(_editor), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(focusedIn(_tools), isTrue);
    });
  });

  testWidgets('reduced motion: one pump settles a swap', (tester) async {
    final h = _host('');
    await _pump(tester, _composer(h), disableAnimations: true);
    await _pump(tester, _composer(h, busy: true), disableAnimations: true);
    h.controller.text = 'now';
    await tester.pump();
    expect(tester.hasRunningAnimations, isFalse);
    expect(find.byKey(_stop), findsNothing);
  });

  testWidgets('200 % text at 320 dp: no overflow; Send trails "+" and there '
      'is no Stop beside them', (tester) async {
    final h = _host('a long message to the agent');
    await _pump(
      tester,
      _composer(h, busy: true, canSendWhileBusy: true, offline: true),
      size: const Size(320, 800),
      textScale: 2,
    );
    expect(tester.takeException(), isNull);
    expect(find.byKey(_stop), findsNothing);
    final send = tester.getRect(find.byKey(_send));
    final tools = tester.getRect(find.byKey(_tools));
    expect(send.left, greaterThanOrEqualTo(tools.right));
    expect(find.byKey(_tools), findsOneWidget);
  });

  group('Send\'s long-press menu ("Send later or repeat")', () {
    testWidgets('a long-press opens it without sending; a tap still sends '
        'once', (tester) async {
      final h = _host('Run the tests tonight');
      var later = 0;
      await _pump(
        tester,
        _composer(
          h,
          sendMenu: [
            KitMenuItem(
              key: const Key('send-later'),
              label: 'Send later or repeat',
              onSelected: () => later++,
            ),
          ],
        ),
      );
      await tester.longPress(find.byKey(_send));
      await tester.pumpAndSettle();
      expect(h.sends, 0);
      expect(find.text('Send later or repeat'), findsOneWidget);
      await tester.tap(find.text('Send later or repeat'));
      await tester.pumpAndSettle();
      expect(later, 1);
      expect(h.sends, 0);
      await tester.tap(find.byKey(_send));
      await tester.pumpAndSettle();
      expect(h.sends, 1);
    });

    List<KitMenuItem> laterMenu() => [
      KitMenuItem(
        key: const Key('send-later'),
        label: 'Send later or repeat',
        onSelected: () {},
      ),
    ];

    testWidgets('with the menu a finger sends once, when it lifts', (
      tester,
    ) async {
      final h = _host('Ship it');
      await _pump(tester, _composer(h, sendMenu: laterMenu()));
      final press = await tester.startGesture(
        tester.getCenter(find.byKey(_send)),
      );
      await tester.pump(const Duration(milliseconds: 80));
      expect(h.sends, 0);
      await press.up();
      await tester.pump();
      expect(h.sends, 1);
      await tester.pumpAndSettle();
      expect(h.sends, 1);
      expect(find.text('Send later or repeat'), findsNothing);
    });

    testWidgets('with the menu the lift still sends when the composer slid '
        'down under the finger (the keyboard hiding)', (tester) async {
      final h = _host('Ship it');
      final keyboard = ValueNotifier<double>(300);
      addTearDown(keyboard.dispose);
      await _pump(
        tester,
        ValueListenableBuilder<double>(
          valueListenable: keyboard,
          builder: (context, inset, child) => Padding(
            padding: EdgeInsets.only(bottom: inset),
            child: child,
          ),
          child: _composer(h, sendMenu: laterMenu()),
        ),
      );
      final at = tester.getCenter(find.byKey(_send));
      final press = await tester.startGesture(at);
      keyboard.value = 0;
      await tester.pump();
      expect(tester.getCenter(find.byKey(_send)).dy, greaterThan(at.dy + 200));
      await press.up();
      await tester.pump();
      expect(h.sends, 1);
    });

    testWidgets('with the menu the lift still sends when the keyboard hides '
        'during the press', (tester) async {
      final h = _host('Ship it');
      await _pump(tester, _composer(h, sendMenu: laterMenu()));
      h.focus.requestFocus();
      await tester.pumpAndSettle();
      final press = await tester.startGesture(
        tester.getCenter(find.byKey(_send)),
      );
      h.focus.unfocus();
      await tester.pump(const Duration(milliseconds: 120));
      await press.up();
      await tester.pump();
      expect(h.sends, 1);
    });

    testWidgets('with the menu a finger that slides off Send sends nothing', (
      tester,
    ) async {
      final h = _host('Ship it');
      await _pump(tester, _composer(h, sendMenu: laterMenu()));
      final at = tester.getCenter(find.byKey(_send));
      final press = await tester.startGesture(at);
      await press.moveTo(at - const Offset(120, 0));
      await press.up();
      await tester.pumpAndSettle();
      expect(h.sends, 0);
    });

    testWidgets('no menu: a finger still sends on press', (tester) async {
      final h = _host('Now');
      await _pump(tester, _composer(h));
      final gesture = await tester.startGesture(
        tester.getCenter(find.byKey(_send)),
      );
      await tester.pump();
      expect(h.sends, 1);
      await gesture.up();
      await tester.pump();
      expect(h.sends, 1);
    });
  });

  group('the "+" tray (plus-menu concept C)', () {
    List<KitComposerTrayAction> trayFor(List<String> picked) => [
      for (final (key, label) in [
        ('photos', 'Photos'),
        ('camera', 'Camera'),
        ('file', 'File'),
        ('more', 'More'),
      ])
        KitComposerTrayAction(
          buttonKey: Key('tray-$key'),
          icon: AppIconography.add,
          label: label,
          onTap: () => picked.add(key),
        ),
    ];
    const tray = ValueKey('kit-composer-tray');

    testWidgets('"+" opens the row in place instead of the sheet, turns '
        'into ×, and closes again', (tester) async {
      final h = _host('');
      final picked = <String>[];
      await _pump(tester, _composer(h, tray: trayFor(picked)));
      expect(find.byKey(tray), findsNothing);
      await tester.tap(find.byKey(_tools));
      await tester.pumpAndSettle();
      expect(h.tools, 0);
      expect(find.byKey(tray), findsOneWidget);
      for (final label in ['Photos', 'Camera', 'File', 'More']) {
        expect(find.text(label), findsOneWidget);
      }
      // One row, above the field, inside the box.
      final rows = {
        for (final key in ['photos', 'camera', 'file', 'more'])
          tester.getCenter(find.byKey(Key('tray-$key'))).dy.round(),
      };
      expect(rows, hasLength(1));
      expect(
        tester.getRect(find.byKey(tray)).bottom,
        lessThanOrEqualTo(tester.getRect(find.byKey(_field)).top + 1),
      );
      expect(
        tester
            .widget<AnimatedRotation>(
              find.ancestor(
                of: find.byKey(_tools),
                matching: find.byType(AnimatedRotation),
              ),
            )
            .turns,
        1 / 8,
      );
      expect(find.byTooltip('Close'), findsOneWidget);
      await tester.tap(find.byKey(_tools));
      await tester.pumpAndSettle();
      expect(find.byKey(tray), findsNothing);
      expect(picked, isEmpty);
    });

    testWidgets('a button closes the row, then runs its action', (
      tester,
    ) async {
      for (final key in ['photos', 'camera', 'file', 'more']) {
        final h = _host('');
        final picked = <String>[];
        await _pump(tester, _composer(h, tray: trayFor(picked)));
        await tester.tap(find.byKey(_tools));
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(Key('tray-$key')));
        await tester.pumpAndSettle();
        expect(picked, [key]);
        expect(find.byKey(tray), findsNothing);
      }
    });

    testWidgets('every button is a labelled 48 dp target; a blocked one '
        'stays, says why and does nothing', (tester) async {
      final semantics = tester.ensureSemantics();
      final h = _host('');
      final picked = <String>[];
      await _pump(
        tester,
        _composer(
          h,
          tray: [
            ...trayFor(picked).take(3),
            KitComposerTrayAction(
              buttonKey: const Key('tray-blocked'),
              icon: AppIconography.add,
              label: 'Blocked',
              disabledReason: 'Available when the run finishes',
              onTap: () => picked.add('blocked'),
            ),
          ],
        ),
      );
      await tester.tap(find.byKey(_tools));
      await tester.pumpAndSettle();
      for (final key in ['photos', 'camera', 'file', 'blocked']) {
        final size = tester.getSize(find.byKey(Key('tray-$key')));
        expect(size.width, greaterThanOrEqualTo(48));
        expect(size.height, greaterThanOrEqualTo(48));
      }
      expect(find.bySemanticsLabel(RegExp('Photos')), findsWidgets);
      await tester.tap(find.byKey(const Key('tray-blocked')));
      await tester.pumpAndSettle();
      expect(picked, isEmpty);
      semantics.dispose();
    });

    testWidgets('reduced motion: the row and the × arrive at once', (
      tester,
    ) async {
      final h = _host('');
      await _pump(
        tester,
        _composer(h, tray: trayFor([])),
        disableAnimations: true,
      );
      await tester.tap(find.byKey(_tools));
      await tester.pump();
      expect(tester.hasRunningAnimations, isFalse);
      expect(find.byKey(tray), findsOneWidget);
    });

    testWidgets('Esc closes the row first', (tester) async {
      final h = _host('');
      await _pump(tester, _composer(h, tray: trayFor([])));
      await tester.tap(find.byKey(_tools));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(_field));
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.byKey(tray), findsNothing);
      expect(h.focus.hasFocus, isTrue);
    });

    testWidgets('no tray: "+" opens the host\'s sheet as before', (
      tester,
    ) async {
      final h = _host('');
      await _pump(tester, _composer(h));
      await tester.tap(find.byKey(_tools));
      await tester.pumpAndSettle();
      expect(h.tools, 1);
      expect(find.byKey(tray), findsNothing);
    });
  });

  // Concept C (owner 20 Oct 2026): one row, "+" at the start, the field,
  // and the one trailing control at the end, on the same line.
  testWidgets('one row: "+", the field and Send share a line; a one-line '
      'message has no editor button', (tester) async {
    final h = _host('hello');
    await _pump(tester, _composer(h));
    final tools = tester.getRect(find.byKey(_tools));
    final field = tester.getRect(find.byKey(_field));
    final send = tester.getRect(find.byKey(_send));
    expect(field.left, greaterThanOrEqualTo(tools.right - 1));
    expect(send.left, greaterThanOrEqualTo(field.right - 1));
    expect((tools.center.dy - send.center.dy).abs(), lessThan(1));
    expect((field.center.dy - send.center.dy).abs(), lessThan(1));
    expect(field.height, greaterThanOrEqualTo(48));
    expect(find.byKey(_editor), findsNothing);
  });

  testWidgets('the full-screen editor opens from the field\'s top corner', (
    tester,
  ) async {
    // Three lines: the field is taller than its one-line row.
    final h = _host('hello\nthere\nfriend');
    await _pump(tester, _composer(h));
    final editor = tester.getRect(find.byKey(_editor));
    final field = tester.getRect(find.byKey(_field));
    final send = tester.getRect(find.byKey(_send));
    // Beside the words, above the send row.
    expect(editor.bottom, lessThanOrEqualTo(send.top + 1));
    expect(editor.left, greaterThanOrEqualTo(field.right - 1));
    await tester.tap(find.byKey(_editor));
    await tester.pump();
    expect(h.editors, 1);
  });
}

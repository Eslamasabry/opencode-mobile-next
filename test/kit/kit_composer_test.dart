// Behaviour tests for KitComposer (docs/ux-system/kit-api/KitComposer.md
// "Tests required"). Owner decision 2026-09-27: English only, no RTL.
import 'package:flutter/material.dart';
import 'package:flutter/gestures.dart' show PointerDeviceKind;
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/effects.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer_chips.dart';
import 'package:opencode_mobile/ui/kit/glass/kit_glass.dart';
import 'package:opencode_mobile/ui/kit/kit_bottom_inset.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_segmented.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_harness.dart';

import 'kit_motion_still.dart';

const _send = Key('chat-send-button');
const _stop = Key('chat-stop-button');
const _field = Key('chat-composer-field');
const _tools = Key('composer-tools-button');
const _editor = Key('prompt-editor-button');
const _voice = Key('voice-button');
const _delivery = Key('composer-delivery-control');

class _Host {
  _Host(String text) : controller = TextEditingController(text: text);
  final TextEditingController controller;
  final FocusNode focus = FocusNode();
  int sends = 0, stops = 0, voices = 0, tools = 0, editors = 0;
  final deliveries = <KitComposerDelivery>[];

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
  bool stop = true,
  bool sending = false,
  bool canSendWhileBusy = false,
  KitComposerDelivery delivery = KitComposerDelivery.afterThisReply,
  bool deliveryChoice = false,
  bool offline = false,
  String? readOnlyReason,
  bool voiceButton = true,
  KitComposerVoice? voice,
  KitComposerChips? suggestions,
  bool model = false,
  KitComposerFailure? failure,
}) => KitComposer(
  controller: h.controller,
  focusNode: h.focus,
  hint: 'Ask OpenCode…',
  onSend: () => h.sends++,
  busy: busy,
  onStop: stop ? () => h.stops++ : null,
  sending: sending,
  canSendWhileBusy: canSendWhileBusy,
  delivery: delivery,
  onDeliveryChanged: deliveryChoice ? h.deliveries.add : null,
  offline: offline,
  readOnlyReason: readOnlyReason,
  onVoice: voiceButton ? () => h.voices++ : null,
  voice: voice,
  suggestions: suggestions,
  model: model
      ? KitComposerChips.model(label: 'Sonnet 4.5 · High', onPressed: () {})
      : null,
  onTools: () => h.tools++,
  onOpenEditor: () => h.editors++,
  fieldKey: _field,
  sendKey: _send,
  stopKey: _stop,
  toolsKey: _tools,
  voiceButtonKey: _voice,
  editorKey: _editor,
  deliveryKey: _delivery,
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

    testWidgets('busy, no text: Stop', (tester) async {
      final semantics = tester.ensureSemantics();
      final h = _host('');
      await _pump(tester, _composer(h, busy: true));
      expect(_label(tester, _stop), 'Stop the reply');
      expect(find.byKey(_send), findsNothing);
      await tester.tap(find.byKey(_stop));
      expect(h.stops, 1);
      semantics.dispose();
    });

    testWidgets('busy with Stop on the running turn (no onStop), no text: '
        'the mic stays, so a message can be spoken while the reply runs', (
      tester,
    ) async {
      final h = _host('');
      await _pump(tester, _composer(h, busy: true, stop: false));
      expect(find.byKey(_stop), findsNothing);
      expect(find.byKey(_voice), findsOneWidget);
      await tester.tap(find.byKey(_voice));
      expect(h.voices, 1);
    });

    testWidgets('busy, text, can send: Stop and Send', (tester) async {
      final semantics = tester.ensureSemantics();
      final h = _host('more');
      await _pump(tester, _composer(h, busy: true, canSendWhileBusy: true));
      expect(_label(tester, _stop), 'Stop the reply');
      expect(_label(tester, _send), 'Send after this reply');
      await tester.tap(find.byKey(_send));
      expect(h.sends, 1);
      semantics.dispose();
    });

    testWidgets('busy, text, cannot send: Stop and the reason', (tester) async {
      final h = _host('more');
      await _pump(tester, _composer(h, busy: true));
      expect(find.byKey(_stop), findsOneWidget);
      expect(find.byKey(_send), findsNothing);
      expect(
        find.text('You can send when this reply finishes'),
        findsOneWidget,
      );
    });
  });

  testWidgets('Stop and Send: 48 dp each, at least 8 dp apart', (tester) async {
    final h = _host('more');
    await _pump(tester, _composer(h, busy: true, canSendWhileBusy: true));
    final stop = tester.getRect(find.byKey(_stop));
    final send = tester.getRect(find.byKey(_send));
    expect(stop.width, greaterThanOrEqualTo(48));
    expect(stop.height, greaterThanOrEqualTo(48));
    expect(send.width, greaterThanOrEqualTo(48));
    expect(send.height, greaterThanOrEqualTo(48));
    expect(send.left - stop.right, greaterThanOrEqualTo(8));
  });

  // Owner decision (critique 2026-09-29): Stop is always red.
  testWidgets('Stop is a red circle', (tester) async {
    final h = _host('');
    await _pump(tester, _composer(h, busy: true));
    final roles = KitTokens.of(tester.element(find.byType(KitComposer))).roles;
    final circle = tester.widget<DecoratedBox>(
      find.byKey(const ValueKey('kit-composer-circle-stop')),
    );
    expect(
      (circle.decoration as BoxDecoration).color,
      anyOf(roles.danger, roles.dangerFill),
    );
  });

  group('delivery', () {
    testWidgets('segments only when busy, text, can send and a choice', (
      tester,
    ) async {
      final h = _host('more');
      await _pump(tester, _composer(h, busy: true, canSendWhileBusy: true));
      expect(find.byType(KitSegmented<KitComposerDelivery>), findsNothing);
      expect(find.text('Sends after this reply'), findsOneWidget);

      await _pump(
        tester,
        _composer(h, busy: false, canSendWhileBusy: true, deliveryChoice: true),
      );
      expect(find.byKey(_delivery), findsNothing);
    });

    testWidgets('default Send after; a change is reported once and Send '
        'follows', (tester) async {
      final semantics = tester.ensureSemantics();
      final h = _host('more');
      await _pump(
        tester,
        _composer(h, busy: true, canSendWhileBusy: true, deliveryChoice: true),
      );
      final segmented = tester.widget<KitSegmented<KitComposerDelivery>>(
        find.byKey(_delivery),
      );
      expect(segmented.selected, KitComposerDelivery.afterThisReply);
      expect(find.text('Send after'), findsOneWidget);
      await tester.tap(find.text('Add to this turn'));
      await tester.pumpAndSettle();
      expect(h.deliveries, [KitComposerDelivery.addToThisTurn]);

      await _pump(
        tester,
        _composer(
          h,
          busy: true,
          canSendWhileBusy: true,
          deliveryChoice: true,
          delivery: KitComposerDelivery.addToThisTurn,
        ),
      );
      expect(_label(tester, _send), 'Add to this turn');
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
    expect(colorOf("Didn't send"), isNot(roles.danger));
    await tester.tap(find.text('· Try again'));
    expect(retries, 1);
  });

  testWidgets('offline: Send says it queues and still sends', (tester) async {
    final semantics = tester.ensureSemantics();
    final h = _host('later');
    await _pump(tester, _composer(h, offline: true));
    expect(_label(tester, _send), 'Send when back online');
    expect(find.text("Offline · sends when you're back online"), findsOne);
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
      final h = _host('hello');
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
    await tester.tap(find.byKey(_stop));
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
      _composer(h, readOnlyReason: 'This session is archived', model: true),
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

  testWidgets('glass: a dimmed KitGlass, solid under remove animations', (
    tester,
  ) async {
    final h = _host('');
    await _pump(tester, _composer(h), disableAnimations: true);
    final glass = tester.widget<KitGlass>(find.byType(KitGlass));
    expect(glass.dim, isTrue);
    expect(
      KitGlass.lookOf(tester.element(find.byType(KitGlass))),
      KitGlassLook.solid,
    );
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
    final h = _host('more');
    await _pump(
      tester,
      _composer(h, busy: true, canSendWhileBusy: true, model: true),
    );
    for (final (key, label) in [
      (_tools, 'Attach and more'),
      (_editor, 'Open full-screen editor'),
      (_stop, 'Stop the reply'),
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
      final h = _host('hello');
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
    expect(find.byKey(_stop), findsOneWidget);
  });

  testWidgets('200 % text at 320 dp: no overflow; Stop leads the row and '
      'Send trails it, never side by side', (tester) async {
    final h = _host('a long message to the agent');
    await _pump(
      tester,
      _composer(
        h,
        busy: true,
        canSendWhileBusy: true,
        deliveryChoice: true,
        model: true,
        offline: true,
      ),
      size: const Size(320, 800),
      textScale: 2,
    );
    expect(tester.takeException(), isNull);
    final stop = tester.getRect(find.byKey(_stop));
    final send = tester.getRect(find.byKey(_send));
    final tools = tester.getRect(find.byKey(_tools));
    // One trailing control (owner Fix): Send. Stop sits after "+".
    expect(stop.left, greaterThanOrEqualTo(tools.right));
    expect(send.left - stop.right, greaterThanOrEqualTo(48));
    expect(find.byKey(_tools), findsOneWidget);
  });

  testWidgets('the full-screen editor opens from the field\'s top corner', (
    tester,
  ) async {
    final h = _host('hello');
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

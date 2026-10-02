// Gallery (gate G4) for KitComposer
// (docs/ux-system/kit-api/KitComposer.md "Galleries required"): every
// declared state over a transcript-like backdrop, at DPR 3. Owner decision
// 2026-09-27: galleries at the phone size (412x915) and one wide size
// (1280x800) only, light and dark, English only (no Arabic or RTL shots).
//
// The gallery harness renders with remove animations on, so KitGlass is
// solid in every shot (its own rule, LOOK-29); `glass_off` pins the
// Effects › Glass off path explicitly.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_composer_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer_chips.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_layout.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_turn.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_gallery.dart';

void _noop() {}

/// A transcript under the composer, so the pill reads over content.
class _Backdrop extends StatelessWidget {
  const _Backdrop({required this.composer});

  final Widget composer;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final height = MediaQuery.sizeOf(context).height;
    return SizedBox(
      height: height - 32,
      child: Stack(
        children: [
          PositionedDirectional(
            start: 0,
            end: 0,
            top: 0,
            bottom: 0,
            // Decoration only: the glass is what the shot is about, and
            // text passing under the pill must not be read or measured.
            child: ExcludeSemantics(
              child: Padding(
                padding: EdgeInsets.all(tokens.gutter),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  spacing: tokens.space3,
                  children: const [
                    KitText(
                      'Can you check why the release build fails on the '
                      'signing step?',
                      role: KitTextRole.body,
                    ),
                    KitText(
                      'The release config reads android/key.properties, '
                      'which is missing here, so validateSigningRelease '
                      'stops the build before packaging. The Kotlin and '
                      'Dart sides compile.',
                      role: KitTextRole.body,
                      tone: KitTextTone.secondary,
                    ),
                    KitText(
                      'I will look at the Gradle task graph next and '
                      'confirm the debug variant is not affected.',
                      role: KitTextRole.body,
                      tone: KitTextTone.secondary,
                    ),
                  ],
                ),
              ),
            ),
          ),
          PositionedDirectional(
            start: tokens.gutter,
            end: tokens.gutter,
            bottom: tokens.space2,
            child: Center(
              heightFactor: 1,
              child: ConstrainedBox(
                constraints: const BoxConstraints(
                  maxWidth: KitLayout.paneDetailMaxWidth,
                ),
                child: composer,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// The controllers live for the whole test file; each shot builds a fresh
/// composer around them.
final _controllers = <String, TextEditingController>{};
final _focus = FocusNode();

TextEditingController _text(String text) =>
    _controllers.putIfAbsent(text, () => TextEditingController(text: text));

KitComposerChips _model() => const KitComposerChips.model(
  label: 'build · Sonnet 4.5 · High',
  onPressed: _noop,
);

KitComposerChips _attachments() => KitComposerChips.attachments(
  items: const [
    KitAttachment(
      id: 'log',
      label: 'build-log.txt',
      kind: KitAttachmentKind.file,
      detail: '2.1 MB',
      onOpen: _noop,
    ),
  ],
  onRemove: (_) {},
);

KitComposer _composer({
  String text = '',
  bool busy = false,
  bool sending = false,
  bool canSendWhileBusy = false,
  bool deliveryChoice = false,
  bool offline = false,
  String? readOnlyReason,
  bool attachments = false,
  KitComposerChips? suggestions,
  KitComposerVoice? voice,
  KitTurnLive? rail,
  bool? activityGlow,
}) => KitComposer(
  controller: _text(text),
  focusNode: _focus,
  hint: 'Ask OpenCode…',
  onSend: _noop,
  busy: busy,
  onStop: _noop,
  sending: sending,
  canSendWhileBusy: canSendWhileBusy,
  onDeliveryChanged: deliveryChoice ? (_) {} : null,
  offline: offline,
  readOnlyReason: readOnlyReason,
  model: _model(),
  attachments: attachments ? _attachments() : null,
  suggestions: suggestions,
  onTools: _noop,
  onVoice: _noop,
  onOpenEditor: _noop,
  voice: voice,
  rail: rail,
  activityGlow: activityGlow,
);

const _draft = 'Also run the unit tests for the signing config';

final _states = <String, Widget Function()>{
  'idle_empty': () => _Backdrop(composer: _composer()),
  'idle_text': () =>
      _Backdrop(composer: _composer(text: _draft, attachments: true)),
  'sending': () => _Backdrop(composer: _composer(text: _draft, sending: true)),
  'busy_empty': () => _Backdrop(composer: _composer(busy: true)),
  'busy_text': () => _Backdrop(
    composer: _composer(
      text: _draft,
      busy: true,
      canSendWhileBusy: true,
      deliveryChoice: true,
    ),
  ),
  'busy_cannot_send': () =>
      _Backdrop(composer: _composer(text: _draft, busy: true)),
  'offline': () => _Backdrop(composer: _composer(text: _draft, offline: true)),
  'read_only': () => _Backdrop(
    composer: _composer(
      readOnlyReason: 'This conversation is archived. Restore it to write.',
    ),
  ),
  'suggestions': () => _Backdrop(
    composer: _composer(
      text: '/co',
      suggestions: KitComposerChips.suggestions(
        suggestions: const [
          KitSuggestion(
            id: 'compact',
            label: '/compact',
            kind: KitSuggestionKind.command,
            description: 'Summarise the conversation so far',
          ),
          KitSuggestion(
            id: 'commit',
            label: '/commit',
            kind: KitSuggestionKind.command,
            description: 'Commit the staged changes',
          ),
        ],
        onSelected: (_) {},
      ),
    ),
  ),
  'voice_listening': () => _Backdrop(
    composer: _composer(
      voice: KitComposerVoice(
        phase: KitVoicePhase.listening,
        onExit: _noop,
        conversation: true,
        level: ValueNotifier(0.55),
        onStopListening: _noop,
        readRepliesAloud: true,
        onReadRepliesAloudChanged: (_) {},
      ),
    ),
  ),
  'voice_speaking': () => _Backdrop(
    composer: _composer(
      voice: KitComposerVoice(
        phase: KitVoicePhase.speakingReply,
        onExit: _noop,
        conversation: true,
        onStopSpeaking: _noop,
        readRepliesAloud: true,
        onReadRepliesAloudChanged: (_) {},
      ),
    ),
  ),
  'voice_mic_denied': () => _Backdrop(
    composer: _composer(
      voice: const KitComposerVoice(
        phase: KitVoicePhase.micDenied,
        onExit: _noop,
        reason: 'Android keeps the microphone off for this app.',
        fix: KitAction(label: 'Allow microphone', onPressed: _noop),
      ),
    ),
  ),
  // The chosen "Glowing border while replying": one frame of the ring sweep
  // around the box, over the living edge.
  'running_glow': () => _Backdrop(
    composer: _composer(
      busy: true,
      rail: const KitTurnLive(
        activity: KitTurnActivity.writing,
        pace: .5,
        onStop: _noop,
      ),
      activityGlow: true,
    ),
  ),
};

Widget _default() => _Backdrop(composer: _composer(text: _draft));

void main() {
  setUpAll(loadKitGalleryFonts);
  tearDownAll(() {
    for (final c in _controllers.values) {
      c.dispose();
    }
    _focus.dispose();
  });

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final MapEntry(key: state, value: build) in _states.entries) {
      testWidgets('kit_composer $state · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_composer_$state',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          // The glow is drawn only where the system allows motion; its one
          // frame is the shot.
          removeAnimations: state != 'running_glow',
          child: build(),
        );
      });
    }

    testWidgets('kit_composer default · 1280x800 · $mode', (tester) async {
      const size = Size(1280, 800);
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_composer_default', size, light: light),
        size: size,
        light: light,
        child: _default(),
      );
    });

    // P9.5: no 200 % regressions (Stop and Send 8 dp apart, the model chip
    // shrinks first).
    testWidgets('kit_composer busy_text · text2 · $mode', (tester) async {
      const size = Size(412, 915);
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_composer_busy_text',
          size,
          light: light,
          text2: true,
        ),
        size: size,
        light: light,
        textScale: 2,
        child: _states['busy_text']!(),
      );
    });
  }
}

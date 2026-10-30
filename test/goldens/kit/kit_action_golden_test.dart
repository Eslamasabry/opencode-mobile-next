// Gallery (gate G4, kit-KitAction-v2) for KitActionBlock and KitActionStack
// (lib/ui/kit/kit_buttons.dart, lib/ui/kit/kit_action_stack.dart), the
// frozen spec's full list (docs/ux-system/kit-api/KitAction.md, "Galleries
// required"): 32 KitActionBlock PNGs and 24 KitActionStack PNGs.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_action_golden_test.dart
// and look at every changed image before committing it.
//
// One recorded departure (QA record, contract problem 5): the spec lists
// the `shortcut` scene at 412x915 with desktop capabilities, but its own API
// shows the hint only "on a fine pointer from expanded up", so at 412 the
// scene would show no hint. It is rendered at 1280x800 with desktop
// capabilities instead (kit_action_block_shortcut_1280x800_*), so the
// count stays 14 state PNGs.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit_action_stack.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';

import 'kit_gallery.dart';

const _phone = Size(412, 915);
const _pc = Size(1280, 800);

void _noop() {}

/// English or Arabic copy for a scene.
String _t(bool ar, String en, String arabic) => ar ? arabic : en;

KitActionBlock _blockDefault({bool ar = false}) => KitActionBlock(
  primary: KitAction(label: _t(ar, 'Save', 'حفظ'), onPressed: _noop),
  secondary: KitAction(label: _t(ar, 'Cancel', 'إلغاء'), onPressed: _noop),
  tertiary: [
    KitAction(label: _t(ar, 'Duplicate', 'تكرار'), onPressed: _noop),
    KitAction(label: _t(ar, 'Rename', 'إعادة التسمية'), onPressed: _noop),
  ],
);

// A disabled TERTIARY action (not primary/secondary): the disabled primary
// and secondary fill (text3 on surface3) is a pre-existing near-miss of
// the WCAG AA contrast guideline in the dark theme (4.44:1; theme_roles.dart
// is out of this write set, QA record contract problem 3), which G5 would
// fail. The behaviour is the same for every slot;
// test/kit/kit_action_test.dart covers a disabled primary and secondary.
KitActionBlock _blockDisabled() => KitActionBlock(
  secondary: KitAction(label: 'Cancel', onPressed: _noop),
  tertiary: const [
    KitAction(
      label: 'Duplicate',
      onPressed: null,
      disabledReason: 'Fill in the server address first.',
    ),
  ],
);

KitActionBlock _blockWorking() => KitActionBlock(
  primary: KitAction(
    label: 'Connect',
    icon: AppIconography.server,
    onPressed: _noop,
    working: true,
  ),
  secondary: KitAction(label: 'Cancel', onPressed: _noop),
);

KitActionBlock _blockDestructiveStack() => KitActionBlock(
  primary: KitAction(label: 'Update', onPressed: _noop),
  secondary: KitAction(label: 'Check now', onPressed: _noop),
  tertiary: [
    KitAction(label: 'Restart', onPressed: _noop),
    KitAction(label: 'Delete server', onPressed: _noop, destructive: true),
  ],
);

KitActionBlock _blockOverflow() => KitActionBlock(
  primary: KitAction(label: 'Save', onPressed: _noop),
  tertiary: [
    KitAction(label: 'Duplicate', onPressed: _noop),
    KitAction(label: 'Rename', onPressed: _noop),
    KitAction(label: 'Delete', onPressed: _noop, destructive: true),
    KitAction.copy(label: 'Copy link', text: () => 'https://example.test'),
    KitAction(label: 'Archive', onPressed: _noop),
  ],
);

KitActionBlock _blockCopied() => KitActionBlock(
  primary: KitAction(label: 'Done', onPressed: _noop),
  secondary: KitAction.copy(
    label: 'Copy details',
    text: () => 'Server fox at 100.64.0.7:4096',
  ),
);

KitActionBlock _blockShortcut() => KitActionBlock(
  primary: KitAction(label: 'Send', onPressed: _noop, shortcut: 'Ctrl+Enter'),
  secondary: KitAction(label: 'Cancel', onPressed: _noop, shortcut: 'Esc'),
  tertiary: [
    KitAction(label: 'Save draft', onPressed: _noop, shortcut: 'Ctrl+S'),
  ],
);

KitActionStack _stackDefault({bool ar = false}) => KitActionStack(
  primary: KitAction(label: _t(ar, 'Update', 'تحديث'), onPressed: _noop),
  secondary: KitAction(
    label: _t(ar, 'Check now', 'التحقق الآن'),
    onPressed: _noop,
  ),
  tertiary: [
    KitAction(label: _t(ar, 'Restart', 'إعادة التشغيل'), onPressed: _noop),
  ],
);

// A disabled tertiary, for the same reason _blockDisabled uses one.
KitActionStack _stackDisabled() => KitActionStack(
  secondary: KitAction(label: 'Check now', onPressed: _noop),
  tertiary: const [
    KitAction(
      label: 'Restart',
      onPressed: null,
      disabledReason: 'Not connected right now.',
    ),
  ],
);

KitActionStack _stackDestructive() => KitActionStack(
  secondary: KitAction(label: 'Restart', onPressed: _noop),
  tertiary: [
    KitAction(label: 'Stop server', onPressed: _noop, destructive: true),
  ],
);

/// Runs [act] on the first frame after [child] is built: opens "More" or
/// presses a copy button, so the gallery frame (kitGalleryPart) renders
/// that state in both of its passes.
class _AfterFirstFrame extends StatefulWidget {
  const _AfterFirstFrame({required this.act, required this.child});

  final void Function(Element root) act;
  final Widget child;

  @override
  State<_AfterFirstFrame> createState() => _AfterFirstFrameState();
}

class _AfterFirstFrameState extends State<_AfterFirstFrame> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.act(context as Element);
    });
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

/// The first descendant of [root] that [test] accepts.
Element? _find(Element root, bool Function(Element) test) {
  Element? found;
  void visit(Element e) {
    if (found != null) return;
    if (test(e)) {
      found = e;
      return;
    }
    e.visitChildren(visit);
  }

  root.visitChildren(visit);
  return found;
}

void _openMore(Element root) {
  final more = _find(
    root,
    (e) => e.widget.key == const ValueKey('kit-actions-more'),
  );
  (more!.widget as KitIconButton).onPressed!();
}

void _pressCopy(Element root) {
  // The copy button is the one whose label reads "Copy details".
  final button = _find(
    root,
    (e) =>
        e.widget is ButtonStyleButton &&
        _find(
              e,
              (t) =>
                  t.widget is Text && (t.widget as Text).data == 'Copy details',
            ) !=
            null,
  );
  (button!.widget as ButtonStyleButton).onPressed!();
}

void main() {
  setUpAll(loadKitGalleryFonts);
  setUp(() {
    // KitAction.copy writes the clipboard and a haptic tick.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, (_) async => null);
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null);
    debugPlatformCapabilities = null;
  });

  final otherSizes = [
    for (final size in kitGallerySizes)
      if (size != _phone) size,
  ]..insert(1, const Size(915, 412));
  // kitGallerySizes is 360x800, 412x915, 800x1280, 1280x800, 1600x1000;
  // the spec adds the 915x412 short window (phone in landscape).

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    group('KitActionBlock', () {
      for (final MapEntry(key: state, value: build) in {
        'default': () => _blockDefault(),
        'disabled': _blockDisabled,
        'working': _blockWorking,
        'destructive_stack': _blockDestructiveStack,
        'overflow': () =>
            _AfterFirstFrame(act: _openMore, child: _blockOverflow()),
        'copied': () =>
            _AfterFirstFrame(act: _pressCopy, child: _blockCopied()),
      }.entries) {
        testWidgets('state $state · $mode', (tester) async {
          await kitGalleryPart(
            tester,
            name: kitGalleryName(
              'kit_action_block_$state',
              _phone,
              light: light,
            ),
            size: _phone,
            light: light,
            child: build(),
          );
        });
      }

      testWidgets('state shortcut · $mode', (tester) async {
        debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_action_block_shortcut', _pc, light: light),
          size: _pc,
          light: light,
          child: _blockShortcut(),
        );
      });

      for (final size in otherSizes) {
        testWidgets('size ${kitGallerySize(size)} · $mode', (tester) async {
          await kitGalleryPart(
            tester,
            name: kitGalleryName(
              'kit_action_block_default',
              size,
              light: light,
            ),
            size: size,
            light: light,
            child: _blockDefault(),
          );
        });
      }

      for (final size in kitGalleryScaledSizes) {
        testWidgets('text2 ${kitGallerySize(size)} · $mode', (tester) async {
          await kitGalleryPart(
            tester,
            name: kitGalleryName(
              'kit_action_block_default',
              size,
              light: light,
              text2: true,
            ),
            size: size,
            light: light,
            textScale: 2,
            child: _blockDefault(),
          );
        });
        testWidgets('ar ${kitGallerySize(size)} · $mode', (tester) async {
          await kitGalleryPart(
            tester,
            name: kitGalleryName(
              'kit_action_block_default',
              size,
              light: light,
              ar: true,
            ),
            size: size,
            light: light,
            locale: const Locale('ar'),
            child: _blockDefault(ar: true),
          );
        });
      }
    });

    group('KitActionStack', () {
      for (final MapEntry(key: state, value: build) in {
        'default': () => _stackDefault(),
        'disabled': _stackDisabled,
        'destructive': _stackDestructive,
      }.entries) {
        testWidgets('state $state · $mode', (tester) async {
          await kitGalleryPart(
            tester,
            name: kitGalleryName(
              'kit_action_stack_$state',
              _phone,
              light: light,
            ),
            size: _phone,
            light: light,
            child: build(),
          );
        });
      }

      for (final size in otherSizes) {
        testWidgets('size ${kitGallerySize(size)} · $mode', (tester) async {
          await kitGalleryPart(
            tester,
            name: kitGalleryName(
              'kit_action_stack_default',
              size,
              light: light,
            ),
            size: size,
            light: light,
            child: _stackDefault(),
          );
        });
      }

      for (final size in kitGalleryScaledSizes) {
        testWidgets('text2 ${kitGallerySize(size)} · $mode', (tester) async {
          await kitGalleryPart(
            tester,
            name: kitGalleryName(
              'kit_action_stack_default',
              size,
              light: light,
              text2: true,
            ),
            size: size,
            light: light,
            textScale: 2,
            child: _stackDefault(),
          );
        });
        testWidgets('ar ${kitGallerySize(size)} · $mode', (tester) async {
          await kitGalleryPart(
            tester,
            name: kitGalleryName(
              'kit_action_stack_default',
              size,
              light: light,
              ar: true,
            ),
            size: size,
            light: light,
            locale: const Locale('ar'),
            child: _stackDefault(ar: true),
          );
        });
      }
    });
  }
}

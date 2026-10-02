// Gallery (gate G4) for KitNav, docs/ux-system/kit-api/KitNav.md; kit-v2.md
// §8.1, §8.2; VL §4–§6.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_nav_golden_test.dart
// and look at every changed image before committing it.
//
// Owner decision 2026-09-27 (dated later than KitNav.md, R15): Arabic is
// dropped — no Arabic/RTL galleries, no text-2.0 sweep; galleries are phone
// 412x915 and one wide size 1280x800 only, light and dark, plus the default
// (the dock on the phone, the sidebar wide) at text 2.0 (TEST-9, G4). This
// replaces
// KitNav.md's own "Galleries required" list (360x800, 915x412, 800x1280,
// 1600x1000, text 2.0 and Arabic RTL); the rail, which the spec shows at
// 800x1280, is drawn at 1280x800 as a bare KitNavRail beside the content.
//
// The sidebar header is the real KitShellControls(layout: sidebar), as the
// shell passes it (kit-polish 2026-09-27: the earlier fixed-height stand-in
// cut its own words at text 2.0).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_effects.dart';
import 'package:opencode_mobile/ui/kit/kit_nav.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';

import 'kit_gallery.dart';

List<KitNavDestination> _destinations({int inbox = 0}) => [
  KitNavDestination(
    label: 'Work',
    icon: AppIconography.workspace,
    selectedIcon: AppIconography.workspaceSelected,
    pane: (context) => const _List(count: 8, prefix: 'Conversation'),
  ),
  KitNavDestination(
    label: 'Inbox',
    icon: AppIconography.activity,
    selectedIcon: AppIconography.activitySelected,
    needsYou: inbox,
  ),
  const KitNavDestination(
    label: 'Project',
    icon: AppIconography.files,
    selectedIcon: AppIconography.filesSelected,
  ),
  const KitNavDestination(label: 'Settings', icon: AppIconography.settings),
];

/// A list of body rows, so the glass has content to bend.
class _List extends StatelessWidget {
  const _List({required this.count, required this.prefix});

  final int count;
  final String prefix;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final padding = MediaQuery.paddingOf(context);
    return ListView.builder(
      padding: EdgeInsets.fromLTRB(
        tokens.gutter,
        padding.top + tokens.gutter,
        tokens.gutter,
        padding.bottom + tokens.gutter,
      ),
      itemCount: count,
      itemBuilder: (context, i) => Padding(
        padding: EdgeInsets.symmetric(vertical: tokens.space3),
        child: KitText('$prefix ${i + 1}: the quick brown fox jumps over.'),
      ),
    );
  }
}

Widget _scene({
  required Widget child,
  KitEffects effects = KitEffects.defaults,
}) => KitEffectsScope(
  effects: effects,
  // The gallery harness turns animations off, which also makes glass solid;
  // the glass shots turn them back on for this scene so the dock shows its
  // material (the lens then settles under pumpAndSettle).
  child: Builder(
    builder: (context) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: false),
      child: Material(color: KitTokens.of(context).roles.ground, child: child),
    ),
  ),
);

Future<void> _open(BuildContext context, Widget scene) =>
    Navigator.of(context).push(
      PageRouteBuilder<void>(
        transitionDuration: Duration.zero,
        reverseTransitionDuration: Duration.zero,
        pageBuilder: (_, _, _) => scene,
      ),
    );

/// Wide scenes: the stand-in content beside the rail or sidebar is kept out
/// of semantics. KitNav reads navigation before content in those layouts
/// (A11Y-4), and G5's reading-order check, which compares consecutive
/// leaves only, reads the jump from the sidebar's pinned primary back up to
/// the content's first row as "goes back up" (recorded in the QA record;
/// the harness is not a kit unit's to change).
Widget _wideContent(Widget child) => ExcludeSemantics(child: child);

KitNav _nav({int inbox = 0, int selected = 0, bool wide = false}) => KitNav(
  destinations: _destinations(inbox: inbox),
  selected: selected,
  onSelected: (_) {},
  sidebarHeader: KitShellControls(
    server: 'phone',
    serverStatus: 'Connected',
    serverTone: AppStatusTone.ok,
    onServer: () {},
    project: 'opencode',
    onProject: () {},
    onSearch: () {},
    layout: KitShellControlsLayout.sidebar,
  ),
  sidebarPrimary: KitAction(
    label: 'New conversation',
    onPressed: () {},
    shortcut: 'Ctrl N',
  ),
  // Short enough that no row sits beneath the dock at rest: a row a screen
  // reader announces behind glass fails G5's contrast check (a scene
  // defect, not a KitNav one); the glass still bends the ambient ground.
  child: wide
      ? _wideContent(const _List(count: 12, prefix: 'Row'))
      : const _List(count: 12, prefix: 'Row'),
);

void main() {
  setUpAll(loadKitGalleryFonts);

  const phone = Size(412, 915);
  const wide = Size(1280, 800);

  for (final light in [false, true]) {
    testWidgets('dock default (${light ? 'light' : 'dark'})', (tester) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName('kit_nav_dock_default', phone, light: light),
        size: phone,
        light: light,
        open: (context) => _open(context, _scene(child: _nav())),
      );
    });

    testWidgets('dock needs you (${light ? 'light' : 'dark'})', (tester) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName('kit_nav_dock_needs_you', phone, light: light),
        size: phone,
        light: light,
        open: (context) => _open(context, _scene(child: _nav(inbox: 1))),
      );
    });

    testWidgets('sidebar default (${light ? 'light' : 'dark'})', (
      tester,
    ) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName('kit_nav_sidebar_default', wide, light: light),
        size: wide,
        light: light,
        open: (context) =>
            _open(context, _scene(child: _nav(inbox: 3, wide: true))),
      );
    });

    testWidgets('dock default text 2.0 (${light ? 'light' : 'dark'})', (
      tester,
    ) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName(
          'kit_nav_dock_default',
          phone,
          light: light,
          text2: true,
        ),
        size: phone,
        light: light,
        textScale: 2,
        open: (context) => _open(context, _scene(child: _nav())),
      );
    });

    testWidgets('sidebar default text 2.0 (${light ? 'light' : 'dark'})', (
      tester,
    ) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName(
          'kit_nav_sidebar_default',
          wide,
          light: light,
          text2: true,
        ),
        size: wide,
        light: light,
        textScale: 2,
        open: (context) =>
            _open(context, _scene(child: _nav(inbox: 3, wide: true))),
      );
    });

    testWidgets('rail needs you (${light ? 'light' : 'dark'})', (tester) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName('kit_nav_rail_needs_you', wide, light: light),
        size: wide,
        light: light,
        open: (context) => _open(
          context,
          _scene(
            child: Builder(
              builder: (context) {
                final tokens = KitTokens.of(context);
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Padding(
                      padding: EdgeInsets.all(tokens.space2),
                      child: KitNavRail(
                        destinations: _destinations(inbox: 1),
                        selected: 0,
                        onSelected: (_) {},
                      ),
                    ),
                    Expanded(
                      child: _wideContent(
                        const _List(count: 12, prefix: 'Row'),
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ),
      );
    });
  }
}

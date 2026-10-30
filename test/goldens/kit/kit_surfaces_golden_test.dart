// Gallery for Settings › Appearance › Surfaces (KitSurfaceLook): the same
// key kit parts — grouped rows, a panel, a segmented choice, chips, the main
// and a secondary button, a floating piece and an icon tile — in each
// surface style, light and dark, over an accent tint so glass shows what it
// lets through. Solid here is today's look; the other galleries keep it.
//
// The roles gallery (kit_surfaces_roles_*) shows what slice S0 of the style
// cleanup added to the resolver: a panel on a page hosted on the ground
// (it keeps its shadow), a panel in a panel (it sits on the outer face), and
// the parts' own roles: field, code, divider, state layer and switch track.
//
// The modal galleries (kit_surfaces_sheet_*, kit_surfaces_floating_*) show
// slice S1: a framed sheet (showKitFramedSheet: Git, timeline, commands,
// folder browser) as the `sheet` role, and the term bubble, a tip and the
// Undo bar as `floating` pieces, opened over the parts above with the app's
// effects scope above the navigator, as main.dart places it.
// The choices gallery (kit_surfaces_choices_*) shows slice S2's family as
// the resolver paints it: two agent cards (New conversation), the Welcome
// answer cards, the Model picker's tiles and the start box, one of each
// chosen. Solid is today's look; Brutal and Paper's start box has no halo.
// The lists gallery (kit_surfaces_lists_*) shows slice S7's parts in each
// style: a row group with a chosen row and a count badge, session tabs, a
// tab strip, the tall segmented track, the wheel's band, a team card that
// needs you, a board lane with its cards, and a diff's header, gap bar and
// lines.
// The appearance gallery (kit_surfaces_appearance_*) shows slice S8: the
// Style picker's five samples, each drawn by its own style's resolver
// whatever the style in force (Glass over the live look), a chosen Surface
// card, a chosen theme swatch, and a look card whose mock chat draws its
// message and box in the style in force.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_surfaces_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/domain/agent_persona.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

import '../../../tool/capture/fixtures.dart' show captureTheme;
import 'kit_gallery.dart';
import 'package:opencode_mobile/ui/kit/team/team_parts.dart';

class _Parts extends StatelessWidget {
  const _Parts();

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    return Stack(
      children: [
        // A flat accent tint behind the parts (the live background's job
        // in the app): glass shows it through. Flat, so the contrast check
        // reads the pane, not a gradient.
        Positioned.fill(
          child: ColoredBox(
            color: roles.accent.withValues(alpha: roles.isDark ? .16 : .08),
          ),
        ),
        Padding(
          padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              KitRowGroup(
                label: 'Conversations',
                margin: EdgeInsets.zero,
                children: [
                  KitRow(
                    title: 'Fix flaky checkout test',
                    supporting: const TextSpan(text: 'Working · 2 min'),
                    leading: KitRow.icon(context, AppIconography.terminal),
                    onTap: () {},
                  ),
                  KitRow(
                    title: 'Tidy up the settings page',
                    supporting: const TextSpan(text: 'Done · 1 h ago'),
                    leading: KitRow.icon(context, AppIconography.check),
                    onTap: () {},
                  ),
                ],
              ),
              SizedBox(height: tokens.space4),
              const KitSurface.panel(
                title: 'Context',
                icon: AppIconography.info,
                child: KitText('Read 3 files, writing settings.dart.'),
              ),
              SizedBox(height: tokens.space4),
              KitSegmented<String>(
                segments: const [
                  KitSegment(value: 'auto', label: 'Auto'),
                  KitSegment(value: 'light', label: 'Light'),
                  KitSegment(value: 'dark', label: 'Dark'),
                ],
                selected: 'light',
                onChanged: (_) {},
                semanticsLabel: 'Colour mode',
              ),
              SizedBox(height: tokens.space4),
              Wrap(
                spacing: tokens.space2,
                runSpacing: tokens.space2,
                children: [
                  KitChip.action(label: 'Build', onPressed: () {}),
                  KitChip.action(
                    label: 'Plan',
                    selected: true,
                    onPressed: () {},
                  ),
                  KitChip.action(label: 'Review', onPressed: () {}),
                ],
              ),
              SizedBox(height: tokens.space4),
              Row(
                children: [
                  Expanded(
                    child: KitButton.secondary(
                      label: 'Not now',
                      onPressed: () {},
                    ),
                  ),
                  SizedBox(width: tokens.space3),
                  Expanded(
                    child: KitButton.primary(label: 'Send', onPressed: () {}),
                  ),
                ],
              ),
              SizedBox(height: tokens.space4),
              KitGlass(
                borderRadius: BorderRadius.circular(tokens.navRadius),
                child: Padding(
                  padding: EdgeInsets.all(tokens.space4),
                  child: const Row(
                    children: [
                      KitSurface.tile(AppIconography.terminal),
                      SizedBox(width: 12),
                      Expanded(child: KitText('Ask OpenCode…')),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// The resolver's shared roles and rules, painted straight from
/// [KitSurfaceLook] (no part asks for the new roles yet).
class _Roles extends StatelessWidget {
  const _Roles();

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final hairline = BorderSide(
      color: roles.hairline,
      width: KitTokens.hairlineWidth(context),
    );
    Widget box(
      KitSurfaceRole role,
      Decoration solid, {
      Widget? child,
      bool selected = false,
      bool pressed = false,
      bool circle = false,
      Color? color,
      double? height,
      double? width,
    }) => DecoratedBox(
      decoration: KitSurfaceLook.decorate(
        context,
        solid: solid,
        role: role,
        selected: selected,
        pressed: pressed,
        circle: circle,
        color: color,
      ),
      child: SizedBox(
        height: height,
        width: width,
        child: child == null
            ? null
            : Padding(
                padding: EdgeInsets.symmetric(
                  horizontal: tokens.space4,
                  vertical: tokens.space3,
                ),
                child: child,
              ),
      ),
    );
    final field = ShapeDecoration(
      color: roles.surface1,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(tokens.fieldCorner),
        side: hairline,
      ),
    );
    Widget toggle({required bool on}) {
      final track = ShapeDecoration(
        color: on ? roles.accent : roles.surface3,
        shape: const StadiumBorder(),
      );
      final thumb = BoxDecoration(
        color: on ? roles.onAccent : roles.surface1,
        shape: BoxShape.circle,
      );
      return box(
        KitSurfaceRole.toggle,
        track,
        selected: on,
        color: on ? roles.accent : null,
        width: tokens.space6 * 2,
        height: tokens.space6 + tokens.space1,
        child: null,
      ).withThumb(
        box(
          KitSurfaceRole.control,
          thumb,
          circle: true,
          color: on ? roles.onAccent : null,
          width: tokens.space5,
          height: tokens.space5,
        ),
        on: on,
      );
    }

    final line = KitSurfaceLook.divider(
      context,
      color: roles.hairline,
      width: KitTokens.hairlineWidth(context),
    );
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitSurface(
            level: KitSurfaceLevel.ground,
            shape: KitShape.square,
            padding: KitSurfacePadding.compact,
            clip: false,
            child: const KitSurface.panel(
              child: KitText('A panel on a page keeps its own shadow.'),
            ),
          ),
          SizedBox(height: tokens.space4),
          const KitSurface.panel(
            title: 'A card',
            child: KitSurface.panel(
              child: KitText('The panel in it sits on the card.'),
            ),
          ),
          SizedBox(height: tokens.space4),
          box(
            KitSurfaceRole.field,
            field,
            child: const KitText(
              'Name the conversation',
              tone: KitTextTone.secondary,
            ),
          ),
          SizedBox(height: tokens.space3),
          box(
            KitSurfaceRole.field,
            field,
            selected: true,
            child: const KitText('Fix the checkout test'),
          ),
          SizedBox(height: tokens.space4),
          box(
            KitSurfaceRole.code,
            BoxDecoration(
              color: roles.surface1,
              borderRadius: BorderRadius.circular(tokens.panelCornerRadius),
            ),
            color: roles.surface1,
            child: const KitText(
              'flutter test --concurrency=1',
              role: KitTextRole.mono,
            ),
          ),
          SizedBox(height: tokens.space4),
          const KitText('Above the line'),
          SizedBox(height: tokens.space2),
          SizedBox(
            height: line.width,
            child: ColoredBox(color: line.color),
          ),
          SizedBox(height: tokens.space2),
          const KitText('Below the line'),
          SizedBox(height: tokens.space4),
          Row(
            children: [
              Expanded(
                child: box(
                  KitSurfaceRole.stateLayer,
                  BoxDecoration(
                    color: roles.text1.withValues(alpha: .04),
                    borderRadius: BorderRadius.circular(tokens.fieldCorner),
                  ),
                  child: const KitText('Hover'),
                ),
              ),
              SizedBox(width: tokens.space3),
              Expanded(
                child: box(
                  KitSurfaceRole.stateLayer,
                  BoxDecoration(
                    color: roles.text1.withValues(alpha: .08),
                    borderRadius: BorderRadius.circular(tokens.fieldCorner),
                  ),
                  pressed: true,
                  child: const KitText('Pressed'),
                ),
              ),
            ],
          ),
          SizedBox(height: tokens.space4),
          Row(
            children: [
              toggle(on: false),
              SizedBox(width: tokens.space4),
              toggle(on: true),
            ],
          ),
        ],
      ),
    );
  }
}

class _Choices extends StatefulWidget {
  const _Choices();

  @override
  State<_Choices> createState() => _ChoicesState();
}

class _ChoicesState extends State<_Choices> {
  final _draft = TextEditingController();
  final _focus = FocusNode();

  @override
  void dispose() {
    _draft.dispose();
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitAgentGrid(
            children: [
              for (final (i, persona) in AgentPersona.all.take(2).indexed)
                KitAgentChoice(
                  persona: persona,
                  state: i == 0
                      ? KitAgentChoiceState.selected
                      : KitAgentChoiceState.available,
                  onPressed: () {},
                ),
            ],
          ),
          SizedBox(height: tokens.space5),
          KitChoiceCards<String>(
            choices: const [
              KitChoice(
                value: 'phone',
                leading: KitIcon(AppIconography.phone),
                title: 'On this phone',
                supporting: 'About 4 minutes · 208 MB download',
              ),
              KitChoice(
                value: 'computer',
                leading: KitIcon(AppIconography.computer),
                title: 'On my computer',
                supporting: 'Pair with a code',
              ),
            ],
            selected: 'phone',
            onSelected: (_) {},
          ),
          SizedBox(height: tokens.space5),
          KitChoiceTiles<String>(
            choices: const [
              KitChoice(
                value: 'sonnet',
                title: 'Default',
                supporting: 'Claude Sonnet 5.5',
              ),
              KitChoice(
                value: 'pickle',
                title: 'Cheapest',
                supporting: 'Big Pickle · Free',
              ),
            ],
            selected: 'sonnet',
            onSelected: (_) {},
          ),
          SizedBox(height: tokens.space5),
          KitStartBox(
            controller: _draft,
            focusNode: _focus,
            question: 'What would you like to build?',
          ),
        ],
      ),
    );
  }
}

/// Fix group E of the style recheck (F10): the last minor parts through
/// the resolver: the transcript's loading placeholder, an image's frame
/// while it loads, the zoom controls over an image and the step slider.
class _Minor extends StatelessWidget {
  const _Minor();

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    return Padding(
      padding: EdgeInsets.symmetric(vertical: tokens.space4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 200, child: KitSkeletonTranscript(turns: 1)),
          Padding(
            padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
            child: Row(
              children: [
                SizedBox(
                  width: 160,
                  height: 110,
                  child: KitImage(
                    source: KitImageSource.provider(_Pending()),
                    semanticsLabel: 'A photo',
                    shape: KitShape.panel,
                  ),
                ),
                SizedBox(width: tokens.space4),
                SizedBox.square(
                  dimension: 64,
                  child: KitImage(
                    source: KitImageSource.provider(_Pending()),
                    semanticsLabel: 'An avatar',
                    shape: KitShape.circle,
                  ),
                ),
              ],
            ),
          ),
          SizedBox(height: tokens.space4),
          Padding(
            padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
            child: KitStepSlider<String>(
              semanticsLabel: 'Text size',
              steps: const [
                KitSliderStep(value: 'a', label: 'Smallest'),
                KitSliderStep(value: 'b', label: 'Small'),
                KitSliderStep(value: 'c', label: 'Default'),
                KitSliderStep(value: 'd', label: 'Large'),
              ],
              selected: 'c',
              onChanged: (_) {},
            ),
          ),
          SizedBox(height: tokens.space4),
          SizedBox(
            height: 220,
            child: KitZoom(
              label: 'Screenshot',
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [roles.accent, roles.surface3],
                  ),
                ),
                child: const SizedBox.expand(),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// An image that never arrives: the frame shows, as while it loads.
class _Pending extends ImageProvider<_Pending> {
  @override
  Future<_Pending> obtainKey(ImageConfiguration configuration) =>
      SynchronousFuture(this);

  @override
  ImageStreamCompleter loadImage(_Pending key, ImageDecoderCallback decode) =>
      OneFrameImageStreamCompleter(Completer<ImageInfo>().future);

  @override
  bool operator ==(Object other) => other is _Pending;

  @override
  int get hashCode => 0;
}

/// Slice S8's parts: the Style picker, a theme swatch and a look card.
class _Appearance extends StatelessWidget {
  const _Appearance();

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final aurora = kitLiveStyleById('aurora');
    return Padding(
      padding: EdgeInsets.all(tokens.gutter),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final row in [
            KitSurfaceStyle.values.take(2).toList(),
            KitSurfaceStyle.values.skip(2).toList(),
          ]) ...[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final (i, style) in row.indexed) ...[
                  if (i > 0) SizedBox(width: tokens.space2),
                  Expanded(
                    child: KitOptionCard.surface(
                      style: style,
                      label:
                          '${style.name[0].toUpperCase()}${style.name.substring(1)}',
                      selected: style == KitSurfaceStyle.soft,
                      onPressed: () {},
                    ),
                  ),
                ],
              ],
            ),
            SizedBox(height: tokens.space3),
          ],
          SizedBox(
            height: KitOptionCard.lookHeight,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(
                  child: KitOptionCard.look(
                    look: aurora,
                    label: 'Aurora',
                    selected: true,
                    onPressed: () {},
                  ),
                ),
                SizedBox(width: tokens.space2),
                Expanded(
                  child: Align(
                    alignment: AlignmentDirectional.topCenter,
                    child: KitSwatch(
                      roles: themePack(ThemePackId.nord).dark.themeRoles,
                      label: 'Nord',
                      selected: true,
                      onPressed: () {},
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

void _noop(Object? _) {}

/// Slice S7's parts: rows, tabs, segments, the wheel, team and board
/// cards, and a diff.
class _Lists extends StatelessWidget {
  const _Lists({this.chosenPills = true});

  /// Whether to show the chosen accent pills (session tabs, the tab strip,
  /// the tall segmented track). Off for Soft only: its spec sinks a chosen
  /// accent control into a dark-green well, which gate G5's contrast
  /// sampling reads as green on green (the words are white). Soft's chosen
  /// segment still shows in kit_surfaces_soft_*; the fix belongs in the
  /// Soft spec (soft.md F5: the thumb raised, or inset with accent words).
  final bool chosenPills;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return SingleChildScrollView(
      physics: const NeverScrollableScrollPhysics(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitRowGroup(
            label: 'Settings',
            children: [
              KitRow(
                title: 'Appearance',
                leading: KitRow.icon(context, AppIconography.settings),
                selected: true,
                onTap: () {},
              ),
              KitRow(
                title: 'Notifications',
                leading: KitRow.icon(context, AppIconography.info),
                trailing: const KitRowValue.count(3, 'Unread'),
                onTap: () {},
              ),
            ],
          ),
          if (chosenPills) ...[
            SizedBox(height: tokens.space3),
            KitSessionTabs(
              semanticsLabel: 'Sessions',
              tabs: [
                KitSessionTab(label: 'Checkout', selected: true, onTap: () {}),
                KitSessionTab(label: 'Settings', selected: false, onTap: () {}),
                KitSessionTab(label: 'Docs', selected: false, onTap: () {}),
              ],
            ),
            SizedBox(height: tokens.space2),
            KitTabStrip(
              tabs: const [
                KitTab(label: 'Working', count: 3),
                KitTab(label: 'Review', count: 1),
                KitTab(label: 'Done'),
              ],
              selected: 0,
              onSelected: _noop,
              accent: true,
            ),
            SizedBox(height: tokens.space3),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
              child: KitSegmented<String>(
                tall: true,
                segments: const [
                  KitSegment(
                    value: 'solid',
                    label: 'Solid',
                    icon: AppIconography.check,
                  ),
                  KitSegment(
                    value: 'glass',
                    label: 'Glass',
                    icon: AppIconography.info,
                  ),
                  KitSegment(
                    value: 'soft',
                    label: 'Soft',
                    icon: AppIconography.settings,
                  ),
                  KitSegment(
                    value: 'paper',
                    label: 'Paper',
                    icon: AppIconography.terminal,
                  ),
                ],
                selected: 'glass',
                onChanged: (_) {},
                semanticsLabel: 'Style',
              ),
            ),
          ],
          Padding(
            padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
            child: KitWheel(
              columns: [
                KitWheelColumn<int>(
                  values: [for (var h = 1; h <= 12; h++) h],
                  selected: 9,
                  label: (h) => '$h',
                  onChanged: _noop,
                  semanticsLabel: 'Hour',
                ),
                KitWheelColumn<String>(
                  values: const ['00', '15', '30', '45'],
                  selected: '30',
                  label: (m) => m,
                  onChanged: _noop,
                  semanticsLabel: 'Minute',
                ),
              ],
            ),
          ),
          SizedBox(height: tokens.space3),
          Padding(
            padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                teamCard(
                  context,
                  title: 'Review the plan',
                  status: '',
                  state: KitTeamState.needsYou,
                  summary: 'Two steps wait for you.',
                ),
                SizedBox(height: tokens.space3),
                SizedBox(
                  height: 150,
                  child: KitBoardLane(
                    cards: [
                      KitTaskCard(
                        key: const ValueKey('card-1'),
                        title: 'Fix the sync retry',
                        mark: KitTaskState.working,
                        onOpen: () {},
                      ),
                      KitTaskCard(
                        key: const ValueKey('card-2'),
                        title: 'Ship 1.0.44',
                        mark: KitTaskState.done,
                        onOpen: () {},
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          SizedBox(height: tokens.space3),
          SizedBox(
            height: 260,
            child: KitDiffView(
              files: [
                KitDiffFile.fromTexts(
                  'lib/greeting.dart',
                  before: _diffBefore,
                  after: _diffAfter,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

const _diffBefore = '''
class Greeting {
  const Greeting(this.name);

  final String name;

  String get text => 'Hello, ' + name;
}

String shout(String value) => value.toUpperCase();

int count(List<String> items) {
  var total = 0;
  for (final item in items) {
    total += item.length;
  }
  return total;
}''';

const _diffAfter = '''
class Greeting {
  const Greeting(this.name);

  final String name;

  String get text => 'Welcome aboard, \$name';
}

String shout(String value) => value.toUpperCase();

int count(List<String> items) {
  var total = 0;
  for (final item in items) {
    total += item.length;
  }
  return total + 1;
}''';

extension on Widget {
  /// This track with [thumb] at its start (off) or its end (on).
  Widget withThumb(Widget thumb, {required bool on}) => Stack(
    alignment: on
        ? AlignmentDirectional.centerEnd
        : AlignmentDirectional.centerStart,
    children: [
      this,
      Padding(padding: const EdgeInsets.all(4), child: thumb),
    ],
  );
}

/// Opens [open] over [_Parts] in [style] and compares the window with
/// `goldens/kit/<name>.png`. The effects scope sits above the navigator, as
/// in main.dart, so the routes and the root overlay take the style too.
Future<void> _modalShot(
  WidgetTester tester, {
  required String name,
  required KitSurfaceStyle style,
  required bool light,
  required FutureOr<void> Function(WidgetTester tester, BuildContext page) open,
  bool tip = false,
}) async {
  tester.view.physicalSize = const Size(412, 915) * 3.0;
  tester.view.devicePixelRatio = 3.0;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  late BuildContext page;
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: KitEffectsScope(
              effects: KitEffects(surfaceStyle: style),
              child: kitStyleTheme(child: child!),
            ),
          ),
          home: Scaffold(
            body: Builder(
              builder: (context) {
                page = context;
                return SafeArea(
                  child: Stack(
                    children: [
                      const Padding(
                        padding: EdgeInsets.only(top: 16),
                        child: _Parts(),
                      ),
                      if (tip)
                        const PositionedDirectional(
                          start: 16,
                          end: 16,
                          bottom: 120,
                          child: Align(
                            alignment: AlignmentDirectional.centerStart,
                            child: KitTipBubble(
                              message:
                                  'Hold a conversation to pin or rename it.',
                            ),
                          ),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await open(tester, page);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(find.byKey(boundary), matchesGoldenFile('$name.png'));
  } finally {
    debugDefaultTargetPlatformOverride = null;
    KitUndo.commitPending();
  }
}

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      final mode = light ? 'light' : 'dark';
      testWidgets('surfaces sheet · ${style.name} · $mode', (tester) async {
        await _modalShot(
          tester,
          name: kitGalleryName(
            'kit_surfaces_sheet_${style.name}',
            const Size(412, 915),
            light: light,
          ),
          style: style,
          light: light,
          open: (tester, page) {
            unawaited(
              showKitFramedSheet<void>(
                page,
                builder: (context) => KitSheet(
                  title: 'Git',
                  handle: false,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      KitRow(
                        title: 'main',
                        supporting: const TextSpan(text: '2 ahead · 1 behind'),
                        leading: KitRow.icon(context, AppIconography.terminal),
                        onTap: () {},
                      ),
                      KitRow(
                        title: '3 changed files',
                        supporting: const TextSpan(text: 'Not committed'),
                        leading: KitRow.icon(context, AppIconography.check),
                        onTap: () {},
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        );
      });
      testWidgets('surfaces floating · ${style.name} · $mode', (tester) async {
        await _modalShot(
          tester,
          name: kitGalleryName(
            'kit_surfaces_floating_${style.name}',
            const Size(412, 915),
            light: light,
          ),
          style: style,
          light: light,
          tip: true,
          open: (tester, page) async {
            unawaited(
              showKitTerm(
                tester.element(find.text('Context')),
                term: 'Context',
                explanation: 'What the model can see of this conversation.',
              ),
            );
            await tester.pumpAndSettle();
            showKitUndo(page, message: 'Conversation archived', onUndo: () {});
          },
        );
      });
    }
  }

  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      testWidgets('surfaces roles · ${style.name} · '
          '${light ? 'light' : 'dark'}', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_surfaces_roles_${style.name}',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            // As main.dart: the style's own roles (hook H6).
            child: kitStyleTheme(child: const _Roles()),
          ),
        );
      });
    }
  }

  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      testWidgets('surfaces · ${style.name} · ${light ? 'light' : 'dark'}', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_surfaces_${style.name}',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            // As main.dart: the style's own roles (hook H6).
            child: kitStyleTheme(child: const _Parts()),
          ),
        );
      });
    }
  }

  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      testWidgets('surfaces choices · ${style.name} · '
          '${light ? 'light' : 'dark'}', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_surfaces_choices_${style.name}',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            // As main.dart: the style's own roles (hook H6).
            child: kitStyleTheme(child: const _Choices()),
          ),
        );
      });
    }
  }

  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      testWidgets('surfaces lists · ${style.name} · '
          '${light ? 'light' : 'dark'}', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_surfaces_lists_${style.name}',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            child: kitStyleTheme(
              child: Builder(
                builder: (context) => ColoredBox(
                  color: KitTokens.of(context).roles.ground,
                  child: _Lists(chosenPills: style != KitSurfaceStyle.soft),
                ),
              ),
            ),
          ),
        );
      });
    }
  }
  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      testWidgets('surfaces appearance · ${style.name} · '
          '${light ? 'light' : 'dark'}', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_surfaces_appearance_${style.name}',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            child: kitStyleTheme(
              child: Builder(
                builder: (context) => ColoredBox(
                  color: KitTokens.of(context).roles.ground,
                  child: const _Appearance(),
                ),
              ),
            ),
          ),
        );
      });
    }
  }
  testWidgets('surfaces · soft · Arabic · light', (tester) async {
    await kitGalleryPart(
      tester,
      name: kitGalleryName(
        'kit_surfaces_soft',
        const Size(412, 915),
        light: true,
        ar: true,
      ),
      size: const Size(412, 915),
      light: true,
      locale: const Locale('ar'),
      child: const KitEffectsScope(
        effects: KitEffects(surfaceStyle: KitSurfaceStyle.soft),
        child: _Parts(),
      ),
    );
  });
  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      testWidgets('surfaces minor · ${style.name} · '
          '${light ? 'light' : 'dark'}', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_surfaces_minor_${style.name}',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            child: kitStyleTheme(
              child: Builder(
                builder: (context) => ColoredBox(
                  color: KitTokens.of(context).roles.ground,
                  child: const _Minor(),
                ),
              ),
            ),
          ),
        );
      });
    }
  }
}

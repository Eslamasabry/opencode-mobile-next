import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

void main() {
  test('dark theme keeps the app coherent, legible, and touch friendly', () {
    final theme = AppTheme.dark();
    final scheme = theme.colorScheme;

    expect(theme.brightness, Brightness.dark);
    expect(theme.scaffoldBackgroundColor, AppTheme.background);
    expect(theme.appBarTheme.surfaceTintColor, Colors.transparent);
    // No navigation-bar theme: the kit draws the rail itself, and a phone
    // has no bar (owner, 2026-10-20).
    expect(theme.navigationBarTheme.backgroundColor, isNull);
    expect(theme.inputDecorationTheme.filled, isTrue);

    final foreground = scheme.onSurface.computeLuminance();
    final background = scheme.surface.computeLuminance();
    final lighter = foreground > background ? foreground : background;
    final darker = foreground > background ? background : foreground;
    expect((lighter + .05) / (darker + .05), greaterThanOrEqualTo(7));

    final minimumButtonSize = theme.filledButtonTheme.style?.minimumSize
        ?.resolve(const <WidgetState>{});
    expect(minimumButtonSize?.width, greaterThanOrEqualTo(48));
    expect(minimumButtonSize?.height, greaterThanOrEqualTo(48));
  });

  test('light theme keeps the same deliberate contrast and touch targets', () {
    final theme = AppTheme.light();
    final scheme = theme.colorScheme;

    expect(theme.brightness, Brightness.light);
    expect(theme.scaffoldBackgroundColor, AppTheme.lightBackground);
    expect(theme.appBarTheme.surfaceTintColor, Colors.transparent);
    expect(
      theme.appBarTheme.systemOverlayStyle?.statusBarIconBrightness,
      Brightness.dark,
    );
    // No navigation-bar theme: the kit draws the rail itself, and a phone
    // has no bar (owner, 2026-10-20).
    expect(theme.navigationBarTheme.backgroundColor, isNull);
    expect(theme.inputDecorationTheme.filled, isTrue);

    final foreground = scheme.onSurface.computeLuminance();
    final background = scheme.surface.computeLuminance();
    final lighter = foreground > background ? foreground : background;
    final darker = foreground > background ? background : foreground;
    expect((lighter + .05) / (darker + .05), greaterThanOrEqualTo(7));

    final minimumButtonSize = theme.filledButtonTheme.style?.minimumSize
        ?.resolve(const <WidgetState>{});
    expect(minimumButtonSize?.width, greaterThanOrEqualTo(48));
    expect(minimumButtonSize?.height, greaterThanOrEqualTo(48));
  });

  test('supporting text remains readable on every default surface tier', () {
    for (final theme in [AppTheme.dark(), AppTheme.light()]) {
      final scheme = theme.colorScheme;
      final foreground = AppTheme.mutedOf(theme).computeLuminance();
      for (final surface in [
        theme.scaffoldBackgroundColor,
        scheme.surface,
        scheme.surfaceContainerLowest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
      ]) {
        final background = surface.computeLuminance();
        final lighter = foreground > background ? foreground : background;
        final darker = foreground > background ? background : foreground;
        expect(
          (lighter + .05) / (darker + .05),
          greaterThanOrEqualTo(4.5),
          reason: '${theme.brightness}: supporting text on $surface',
        );
      }
    }
  });

  test('every role carries Geist; the one shadow is the elevation shadow', () {
    for (final theme in [AppTheme.dark(), AppTheme.light()]) {
      expect(theme.textTheme.headlineSmall?.fontFamily, AppTheme.sansFamily);
      expect(theme.textTheme.titleLarge?.fontFamily, AppTheme.sansFamily);
      expect(theme.textTheme.bodyMedium?.fontFamily, AppTheme.sansFamily);
      expect(AppTheme.liveTint(theme).a, closeTo(.06, .001));
      // One shadow, under floating surfaces only: y 6, blur 16, the
      // elevationShadow role at 30 % black in both brightnesses; pulled in
      // 6 so no halo shows above the surface.
      final shadows = theme.extension<KitTokens>()!.surfaceShadows;
      expect(shadows, hasLength(1));
      expect(shadows.single.color, const Color(0x4D000000));
      expect(shadows.single.offset, const Offset(0, 6));
      expect(shadows.single.blurRadius, 16);
      expect(shadows.single.spreadRadius, -6);
    }
  });

  test('no theme elevation lifts content (LOOK-20)', () {
    for (final theme in [AppTheme.dark(), AppTheme.light()]) {
      expect(theme.dialogTheme.elevation, 0);
      expect(theme.bottomSheetTheme.elevation, 0);
      expect(theme.bottomSheetTheme.modalElevation, 0);
      expect(theme.snackBarTheme.elevation, 0);
      expect(theme.cardTheme.elevation, 0);
      expect(theme.popupMenuTheme.elevation, 0);
      expect(theme.floatingActionButtonTheme.elevation, 0);
      expect(theme.appBarTheme.elevation, 0);
      expect(theme.appBarTheme.scrolledUnderElevation, 0);
    }
  });

  test('the top bar title is the headline role (LOOK-17)', () {
    final headline = KitText.styleFor(KitTextRole.headline);
    for (final theme in [AppTheme.dark(), AppTheme.light()]) {
      final title = theme.appBarTheme.titleTextStyle!;
      expect(title.fontSize, headline.fontSize);
      expect(title.height, headline.height);
      expect(title.fontWeight, headline.fontWeight);
      expect(title.fontFamily, AppTheme.sansFamily);
      expect(title.color, ThemeRoles.resolve(theme).text1);
    }
  });

  test('a switch that cannot act looks unavailable, on or off (N1)', () {
    for (final theme in [AppTheme.dark(), AppTheme.light()]) {
      final switches = theme.switchTheme;
      const live = {WidgetState.selected};
      const rest = {WidgetState.selected, WidgetState.disabled};
      final accent = ThemeRoles.resolve(theme).accent;
      expect(switches.trackColor!.resolve(live), accent);
      expect(switches.trackColor!.resolve(rest), isNot(accent));
      expect(
        switches.thumbColor!.resolve(rest),
        isNot(switches.thumbColor!.resolve(live)),
      );
    }
  });

  group('corners come from the kit tokens (style audit 2026-10-21, R8)', () {
    double tooltip(ThemeData t) =>
        ((t.tooltipTheme.decoration! as BoxDecoration).borderRadius!
                as BorderRadius)
            .topLeft
            .x;
    double button(ButtonStyle? style) =>
        ((style!.shape!.resolve(const {})! as RoundedRectangleBorder)
                    .borderRadius
                as BorderRadius)
            .topLeft
            .x;

    test('the built theme takes its own tokens: tooltips are popovers', () {
      for (final theme in [AppTheme.dark(), AppTheme.light()]) {
        final kit = theme.extension<KitTokens>()!;
        expect(tooltip(theme), kit.popoverCorner);
        expect(button(theme.filledButtonTheme.style), kit.buttonRadius);
        expect(button(theme.iconButtonTheme.style), kit.buttonRadius);
        final field =
            theme.inputDecorationTheme.enabledBorder! as OutlineInputBorder;
        expect(field.borderRadius.topLeft.x, kit.fieldCorner);
        final menu = theme.popupMenuTheme.shape! as RoundedRectangleBorder;
        expect(
          (menu.borderRadius as BorderRadius).topLeft.x,
          kit.popoverCorner,
        );
        expect(theme.chipTheme.shape, isA<StadiumBorder>());
      }
    });

    test('shaped under Corners › Sharp: tooltips, buttons, fields and chips '
        'follow', () {
      final theme = AppTheme.dark();
      final sharp = theme.extension<KitTokens>()!.styled(
        const KitKnobs(corners: KitCorners.sharp),
        sharpRadius: 2,
      );
      final shaped = AppTheme.shaped(theme, sharp);
      expect(tooltip(shaped), 2);
      expect(button(shaped.filledButtonTheme.style), 2);
      expect(button(shaped.textButtonTheme.style), 2);
      final field =
          shaped.inputDecorationTheme.focusedBorder! as OutlineInputBorder;
      expect(field.borderRadius.topLeft.x, 2);
      // The field keeps its own colour and width.
      expect(
        field.borderSide,
        (theme.inputDecorationTheme.focusedBorder! as OutlineInputBorder)
            .borderSide,
      );
      expect(shaped.chipTheme.shape, isA<RoundedRectangleBorder>());
      expect(
        shaped.floatingActionButtonTheme.shape,
        isA<RoundedRectangleBorder>(),
      );
      // Everything else is the theme's own; memoised per theme and tokens.
      expect(shaped.colorScheme, theme.colorScheme);
      expect(identical(AppTheme.shaped(theme, sharp), shaped), isTrue);
    });
  });
}

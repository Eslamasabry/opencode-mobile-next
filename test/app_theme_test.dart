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
    expect(theme.navigationBarTheme.backgroundColor, isNotNull);
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
    expect(theme.navigationBarTheme.backgroundColor, isNotNull);
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
}

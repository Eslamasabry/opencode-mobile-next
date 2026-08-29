import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'theme_packs.dart';

/// Pack-provided colors that live outside Material's scheme, carried on the
/// ThemeData so widgets resolve them from context.
class AppSemanticColors extends ThemeExtension<AppSemanticColors> {
  final Color success;

  const AppSemanticColors({required this.success});

  @override
  AppSemanticColors copyWith({Color? success}) =>
      AppSemanticColors(success: success ?? this.success);

  @override
  AppSemanticColors lerp(AppSemanticColors? other, double t) => other == null
      ? this
      : AppSemanticColors(
          success: Color.lerp(success, other.success, t) ?? success,
        );
}

/// The semantic states every status dot, chip, badge, and status icon in the
/// product maps onto. Screens name the *state*; the palette stays here so a
/// theme pack reaches every surface at once instead of screens reaching for
/// raw `Colors.green`/`Colors.orange`.
enum AppStatusTone {
  /// Idle, unknown, disconnected — nothing is happening and nothing is wrong.
  neutral,

  /// Something is under way: connecting, installing, running.
  progress,

  /// Healthy, connected, completed, diff addition.
  ok,

  /// Needs the user: authentication required, degraded, warning.
  attention,

  /// Failed, errored, diff removal.
  failure,
}

/// One glyph per verb. Icon synonyms for the same action (three copies, two
/// bolts, two stops) read as different actions, so the vocabulary lives here
/// and call sites name the verb.
abstract final class AppIcons {
  static const copy = Icons.copy_rounded;
  static const run = Icons.electric_bolt_outlined;
  static const stop = Icons.stop_rounded;
  static const send = Icons.arrow_upward_rounded;
  static const queue = Icons.hourglass_bottom_rounded;
  static const retry = Icons.refresh_rounded;
  static const externalLink = Icons.open_in_new_rounded;
}

/// The shared visual system for the mobile client.
///
/// Keeping component defaults here prevents individual screens from drifting
/// back to stock Material styling as the product grows. Color comes from a
/// [ThemePack]; structure never changes between packs.
abstract final class AppTheme {
  /// Bundled JetBrains Mono; code is this product's primary material.
  static const monoFamily = 'AppMono';

  /// Ceiling for the global text scaler. The system setting passes through
  /// untouched below this — smaller-than-default choices included — and only
  /// runaway scales are capped. Critical flows are tested at this value.
  static const maxTextScale = 2.5;

  /// Font sizes for the roles that sit outside Material's type scale.
  static const codeFontSize = 12.0;
  static const captionFontSize = 11.0;
  static const bodyFontSize = 14.0;

  /// The canonical corner-radius grid. Controls and inline surfaces take
  /// [radiusControl]; cards and raised surfaces take [radiusCard].
  static const radiusControl = 12.0;
  static const radiusCard = 14.0;

  /// Pack-aware success green for status dots, done states, and diff
  /// additions. Prefer this over [success] wherever a ThemeData is in reach.
  static Color successOf(ThemeData theme) =>
      theme.extension<AppSemanticColors>()?.success ??
      success(theme.colorScheme);

  /// Brightness-based fallback with the OpenCode pack's values, for the rare
  /// place that has only a scheme.
  static Color success(ColorScheme scheme) =>
      scheme.brightness == Brightness.dark
      ? const Color(0xFF86D8A5)
      : const Color(0xFF1E7A44);

  /// The single source of status color. [AppStatusTone.progress] and
  /// [AppStatusTone.attention] share the tertiary role deliberately: no
  /// Material scheme carries a warning slot, and both states are always
  /// carried by a distinct icon and label as well as color.
  static Color statusColor(ThemeData theme, AppStatusTone tone) =>
      switch (tone) {
        AppStatusTone.neutral => theme.colorScheme.onSurfaceVariant,
        AppStatusTone.progress => theme.colorScheme.tertiary,
        AppStatusTone.ok => successOf(theme),
        AppStatusTone.attention => theme.colorScheme.tertiary,
        AppStatusTone.failure => theme.colorScheme.error,
      };

  /// The muted-text role. `theme.hintColor` is a fixed black54/white60 that
  /// does not track the pack palette and drops under 4:1 on tinted light
  /// packs; every supporting label resolves through here instead.
  static Color mutedOf(ThemeData theme) => theme.colorScheme.onSurfaceVariant;

  /// One hairline recipe, matching [DividerThemeData], instead of five
  /// hand-picked alphas over `outlineVariant`.
  static Color hairline(ThemeData theme) =>
      theme.colorScheme.outlineVariant.withValues(alpha: .7);

  /// True once the text scale makes side-by-side action buttons too narrow
  /// to hold their labels; action bars stack vertically past this point
  /// instead of wrapping every label into a four-line block.
  static bool stackedActions(BuildContext context) =>
      MediaQuery.textScalerOf(context).scale(bodyFontSize) >
      bodyFontSize * 1.6;

  static const background = Color(0xFF101310);
  static const surface = Color(0xFF151A17);
  static const accent = Color(0xFF83CDAA);
  static const lightBackground = Color(0xFFF6F9F6);
  static const lightSurface = Color(0xFFFFFFFF);
  static const lightAccent = Color(0xFF176B4B);

  static ThemeData dark([ThemePack? pack]) =>
      fromPalette((pack ?? themePack(ThemePackId.opencode)).dark);

  static ThemeData light([ThemePack? pack]) =>
      fromPalette((pack ?? themePack(ThemePackId.opencode)).light);

  static ThemeData fromPalette(ThemePalette palette) {
    final dark = palette.scheme.brightness == Brightness.dark;
    return _build(
      scheme: palette.scheme,
      backgroundColor: palette.background,
      navigationColor: palette.navigation,
      successColor: palette.success,
      overlayStyle: SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: dark ? Brightness.light : Brightness.dark,
        statusBarBrightness: dark ? Brightness.dark : Brightness.light,
        systemNavigationBarColor: palette.background,
        systemNavigationBarIconBrightness: dark
            ? Brightness.light
            : Brightness.dark,
      ),
    );
  }

  static ThemeData _build({
    required ColorScheme scheme,
    required Color backgroundColor,
    required Color navigationColor,
    required Color successColor,
    required SystemUiOverlayStyle overlayStyle,
  }) {
    const roundedRectangle = RoundedRectangleBorder(
      borderRadius: BorderRadius.all(Radius.circular(14)),
    );
    final base = ThemeData(
      useMaterial3: true,
      brightness: scheme.brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: backgroundColor,
      canvasColor: backgroundColor,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      visualDensity: VisualDensity.standard,
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          // The M3 horizontal fade-through keeps pushes light and fast and
          // respects predictive back on current Android.
          TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
          TargetPlatform.fuchsia: FadeForwardsPageTransitionsBuilder(),
          TargetPlatform.linux: FadeForwardsPageTransitionsBuilder(),
        },
      ),
    );

    return base.copyWith(
      extensions: [AppSemanticColors(success: successColor)],
      textTheme: base.textTheme.copyWith(
        headlineSmall: base.textTheme.headlineSmall?.copyWith(
          fontWeight: FontWeight.w700,
          letterSpacing: -0.45,
        ),
        titleLarge: base.textTheme.titleLarge?.copyWith(
          fontWeight: FontWeight.w700,
          letterSpacing: -0.25,
        ),
        titleMedium: base.textTheme.titleMedium?.copyWith(
          fontWeight: FontWeight.w600,
        ),
        labelLarge: base.textTheme.labelLarge?.copyWith(
          fontWeight: FontWeight.w600,
          letterSpacing: 0.1,
        ),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: backgroundColor,
        foregroundColor: scheme.onSurface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        toolbarHeight: 64,
        systemOverlayStyle: overlayStyle,
      ),
      cardTheme: CardThemeData(
        color: scheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        shape: roundedRectangle.copyWith(
          side: BorderSide(color: scheme.outlineVariant.withValues(alpha: .7)),
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: scheme.surfaceContainerHigh,
        surfaceTintColor: Colors.transparent,
        elevation: 8,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(22)),
        ),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: scheme.surfaceContainerLow,
        modalBackgroundColor: scheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        showDragHandle: true,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        height: 68,
        elevation: 0,
        backgroundColor: navigationColor,
        surfaceTintColor: Colors.transparent,
        indicatorColor: scheme.primary.withValues(alpha: .17),
        indicatorShape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(12)),
        ),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          return IconThemeData(
            color: states.contains(WidgetState.selected)
                ? scheme.primary
                : scheme.onSurfaceVariant,
          );
        }),
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          return base.textTheme.labelSmall?.copyWith(
            color: states.contains(WidgetState.selected)
                ? scheme.primary
                : scheme.onSurfaceVariant,
            fontWeight: states.contains(WidgetState.selected)
                ? FontWeight.w700
                : FontWeight.w500,
          );
        }),
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: navigationColor,
        indicatorColor: scheme.primary.withValues(alpha: .17),
        selectedIconTheme: IconThemeData(color: scheme.primary),
        unselectedIconTheme: IconThemeData(color: scheme.onSurfaceVariant),
        selectedLabelTextStyle: TextStyle(
          color: scheme.primary,
          fontWeight: FontWeight.w700,
        ),
        unselectedLabelTextStyle: TextStyle(color: scheme.onSurfaceVariant),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: scheme.surfaceContainerLow,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 15,
        ),
        border: OutlineInputBorder(
          borderRadius: const BorderRadius.all(Radius.circular(14)),
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: const BorderRadius.all(Radius.circular(14)),
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: const BorderRadius.all(Radius.circular(14)),
          borderSide: BorderSide(color: scheme.primary, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: const BorderRadius.all(Radius.circular(14)),
          borderSide: BorderSide(color: scheme.error),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: const BorderRadius.all(Radius.circular(14)),
          borderSide: BorderSide(color: scheme.error, width: 1.5),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          shape: roundedRectangle,
          textStyle: base.textTheme.labelLarge,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          side: BorderSide(color: scheme.outlineVariant),
          shape: roundedRectangle,
          textStyle: base.textTheme.labelLarge,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          shape: roundedRectangle,
          textStyle: base.textTheme.labelLarge,
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          minimumSize: const Size.square(48),
          shape: const RoundedRectangleBorder(
            borderRadius: BorderRadius.all(Radius.circular(12)),
          ),
        ),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: scheme.primaryContainer,
        foregroundColor: scheme.onPrimaryContainer,
        elevation: 3,
        focusElevation: 3,
        hoverElevation: 4,
        highlightElevation: 1,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(17)),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: scheme.surfaceContainerHighest,
        contentTextStyle: TextStyle(color: scheme.onSurface),
        actionTextColor: scheme.primary,
        elevation: 5,
        insetPadding: const EdgeInsets.all(12),
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(14)),
        ),
      ),
      chipTheme: const ChipThemeData(
        // Comfortable density: chips act as primary filters in this product
        // (model intents, variants, file breadcrumbs), so raise them from
        // M3's 32dp toward a >=40dp visual target.
        padding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      ),
      dividerTheme: DividerThemeData(
        color: scheme.outlineVariant.withValues(alpha: .7),
        thickness: 1,
        space: 1,
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: scheme.primary,
        linearTrackColor: scheme.surfaceContainerHighest,
        circularTrackColor: scheme.surfaceContainerHighest,
      ),
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: scheme.primary,
        selectionColor: scheme.primary.withValues(alpha: .28),
        selectionHandleColor: scheme.primary,
      ),
    );
  }
}

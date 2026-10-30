import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app_iconography.dart';
import 'kit/kit_text.dart';
import 'kit/kit_tokens.dart';
import 'kit/motion/kit_page_transitions.dart';
import 'theme_packs.dart';

export 'app_iconography.dart';
export 'theme_roles.dart';

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
  static const copy = AppIconography.copy;
  static const run = AppIconography.lightning;
  static const stop = AppIconography.stop;
  static const send = AppIconography.send;
  static const queue = AppIconography.waiting;
  static const retry = AppIconography.retry;
  static const externalLink = AppIconography.externalLink;
}

/// The shared visual system for the mobile client: the visual language v1
/// (docs/design/visual-language-2026-09-26.md).
///
/// Keeping component defaults here prevents individual screens from drifting
/// back to stock Material styling as the product grows. Colour comes from a
/// theme ([ThemePack] → [ThemeRoles]); type, shape and spacing never change
/// between themes.
abstract final class AppTheme {
  /// Bundled Geist: every word of the interface (§2).
  static const sansFamily = 'AppSans';

  /// Bundled Geist Mono: code, commands, paths.
  static const monoFamily = 'AppMono';

  /// Headlines and titles share the one face; kept for callers that name
  /// the display role.
  static const displayFamily = sansFamily;

  /// Arabic uses the platform's Arabic-capable sans fallback rather than
  /// Latin display metrics. Zero tracking preserves connected glyph shaping.
  static ThemeData forLocale(ThemeData theme, Locale locale) {
    if (locale.languageCode != 'ar') return theme;
    TextStyle? arabic(TextStyle? style) => style?.copyWith(
      fontFamily: 'sans-serif',
      fontFamilyFallback: const [
        'Noto Sans Arabic',
        'Noto Naskh Arabic',
        'Arial',
      ],
      letterSpacing: 0,
    );
    TextTheme adapt(TextTheme text) => TextTheme(
      displayLarge: arabic(text.displayLarge),
      displayMedium: arabic(text.displayMedium),
      displaySmall: arabic(text.displaySmall),
      headlineLarge: arabic(text.headlineLarge),
      headlineMedium: arabic(text.headlineMedium),
      headlineSmall: arabic(text.headlineSmall),
      titleLarge: arabic(text.titleLarge),
      titleMedium: arabic(text.titleMedium),
      titleSmall: arabic(text.titleSmall),
      bodyLarge: arabic(text.bodyLarge),
      bodyMedium: arabic(text.bodyMedium),
      bodySmall: arabic(text.bodySmall),
      labelLarge: arabic(text.labelLarge),
      labelMedium: arabic(text.labelMedium),
      labelSmall: arabic(text.labelSmall),
    );
    final text = adapt(theme.textTheme);
    // The buttons carry their own label style (the kit's button role); it
    // follows the Arabic face too, or its words fall back to nothing wherever
    // the bundled face has no Arabic.
    ButtonStyle? button(ButtonStyle? style) => style?.copyWith(
      textStyle: WidgetStateProperty.resolveWith(
        (states) => arabic(style.textStyle?.resolve(states)),
      ),
    );
    return theme.copyWith(
      textTheme: text,
      primaryTextTheme: adapt(theme.primaryTextTheme),
      filledButtonTheme: FilledButtonThemeData(
        style: button(theme.filledButtonTheme.style),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: button(theme.outlinedButtonTheme.style),
      ),
      textButtonTheme: TextButtonThemeData(
        style: button(theme.textButtonTheme.style),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: button(theme.elevatedButtonTheme.style),
      ),
      // The kit's styles follow the face.
      extensions: [
        ...theme.extensions.values,
        KitTokens.fromRoles(rolesOf(theme), text),
      ],
    );
  }

  /// Ceiling for the global text scaler. The system setting passes through
  /// untouched below this — smaller-than-default choices included — and only
  /// runaway scales are capped. Critical flows are tested at this value.
  static const maxTextScale = 2.5;

  /// Font sizes for the roles that sit outside Material's type scale
  /// (§2, integers only per §7).
  static const codeFontSize = 13.0;
  static const codeLineHeight = 19 / codeFontSize;
  static const captionFontSize = 12.0;
  static const bodyFontSize = 16.0;

  /// The colour roles in force.
  static ThemeRoles rolesOf(ThemeData theme) => ThemeRoles.resolve(theme);

  /// Pack-aware success green for status dots, done states, and diff
  /// additions. Prefer this over [success] wherever a ThemeData is in reach.
  static Color successOf(ThemeData theme) =>
      theme.extension<AppSemanticColors>()?.success ??
      success(theme.colorScheme);

  /// Brightness-based fallback with the default theme's values, for the
  /// rare place that has only a scheme.
  static Color success(ColorScheme scheme) =>
      scheme.brightness == Brightness.dark
      ? graphiteDark.success
      : graphiteLight.success;

  /// The single source of status color. Progress is the accent ("working",
  /// §1); attention is amber ("needs you"), never shared with anything else.
  static Color statusColor(ThemeData theme, AppStatusTone tone) {
    final roles = rolesOf(theme);
    return switch (tone) {
      AppStatusTone.neutral => roles.text2,
      AppStatusTone.progress => roles.accent,
      AppStatusTone.ok => successOf(theme),
      AppStatusTone.attention => roles.attention,
      AppStatusTone.failure => roles.text1,
    };
  }

  /// The muted-text role (`text2`). `theme.hintColor` is a fixed
  /// black54/white60 that does not track the theme; every supporting label
  /// resolves through here instead.
  static Color mutedOf(ThemeData theme) => rolesOf(theme).text2;

  /// The one hairline (translucent, so it reads the same on any surface).
  static Color hairline(ThemeData theme) => rolesOf(theme).hairline;

  /// A faint wash of the accent for surfaces that are "live": the
  /// assistant block still streaming, the tool card still running. Strong
  /// enough to register, weak enough to sit under text.
  static Color liveTint(ThemeData theme, {double alpha = .06}) =>
      theme.colorScheme.primary.withValues(alpha: alpha);

  /// True once the text scale makes side-by-side action buttons too narrow
  /// to hold their labels; action bars stack vertically past this point
  /// instead of wrapping every label into a four-line block.
  static bool stackedActions(BuildContext context) =>
      MediaQuery.textScalerOf(context).scale(bodyFontSize) > bodyFontSize * 1.6;

  /// The default theme's grounds and accents (Graphite).
  static const background = Color(0xFF0B0C0E);
  static const surface = Color(0xFF141518);
  static const accent = Color(0xFF3DDC8A);
  static const lightBackground = Color(0xFFF3F3F1);
  static const lightSurface = Color(0xFFFFFFFF);
  static const lightAccent = Color(0xFF087F43);

  /// [partner] is the Accent swatch the person picked (null: the pack's
  /// own partner colour).
  static ThemeData dark([ThemePack? pack, Color? partner]) => fromPalette(
    (pack ?? themePack(ThemePackId.opencode)).dark,
    partner: partner,
  );

  static ThemeData light([ThemePack? pack, Color? partner]) => fromPalette(
    (pack ?? themePack(ThemePackId.opencode)).light,
    partner: partner,
  );

  /// Saver: the pack's dark theme on a true black ground
  /// ([ThemeRoles.saver]).
  static ThemeData saver([ThemePack? pack, Color? partner]) => fromPalette(
    (pack ?? themePack(ThemePackId.opencode)).dark,
    partner: partner,
    saver: true,
  );

  static ThemeData fromPalette(
    ThemePalette palette, {
    Color? partner,
    bool saver = false,
  }) {
    var roles = palette.themeRoles;
    if (partner != null) roles = roles.withPartner(partner);
    if (saver) roles = roles.saver;
    return fromRoles(roles, base: palette.scheme);
  }

  /// The whole theme from a role set: the seam a custom theme uses
  /// (`AppTheme.fromRoles(deriveRoles(accent: …, ground: …, …))`).
  static ThemeData fromRoles(ThemeRoles roles, {ColorScheme? base}) {
    final dark = roles.isDark;
    return _build(
      roles: roles,
      scheme: schemeFromRoles(roles, base: base),
      overlayStyle: SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: dark ? Brightness.light : Brightness.dark,
        statusBarBrightness: dark ? Brightness.dark : Brightness.light,
        systemNavigationBarColor: roles.ground,
        systemNavigationBarIconBrightness: dark
            ? Brightness.light
            : Brightness.dark,
      ),
    );
  }

  static ThemeData _build({
    required ThemeRoles roles,
    required ColorScheme scheme,
    required SystemUiOverlayStyle overlayStyle,
  }) {
    final r = roles;
    final base = ThemeData(
      useMaterial3: true,
      brightness: scheme.brightness,
      colorScheme: scheme,
      fontFamily: sansFamily,
      scaffoldBackgroundColor: r.ground,
      canvasColor: r.ground,
      dividerColor: r.hairline,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      visualDensity: VisualDensity.standard,
      // Android moves the Android way, with the predictive back gesture
      // (owner rule 2026-10-26, kit-v2 §7.1); the other platforms keep the
      // kit's M3 shared axis, timed by KitMotion, instant under reduced
      // motion. iOS keeps its native back-swipe.
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          TargetPlatform.android: KitAndroidPageTransitionsBuilder(),
          TargetPlatform.fuchsia: KitPageTransitionsBuilder(),
          TargetPlatform.linux: KitPageTransitionsBuilder(),
          TargetPlatform.windows: KitPageTransitionsBuilder(),
          TargetPlatform.macOS: KitPageTransitionsBuilder(),
        },
      ),
    );
    final text = KitText.textTheme(base.textTheme, r);
    // A button's style replaces the ambient text style, so it carries the
    // face itself.
    final button = text.labelLarge!.merge(KitText.styleFor(KitTextRole.button));
    final kit = KitTokens.fromRoles(r, text);

    final theme = base.copyWith(
      extensions: [
        AppSemanticColors(success: r.success),
        r,
        kit,
      ],
      textTheme: text,
      primaryTextTheme: KitText.textTheme(base.primaryTextTheme, r),
      iconTheme: IconThemeData(color: r.text1, size: 22),
      appBarTheme: AppBarThemeData(
        backgroundColor: r.ground,
        foregroundColor: r.text1,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        toolbarHeight: 64,
        // The top bar's title is the headline role (LOOK-17: no 20/26).
        titleTextStyle: text.titleMedium?.merge(
          KitText.styleFor(KitTextRole.headline),
        ),
        systemOverlayStyle: overlayStyle,
      ),
      cardTheme: CardThemeData(
        color: r.surface1,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: EdgeInsets.zero,
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: r.surface2,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        barrierColor: r.scrim,
        titleTextStyle: text.titleLarge,
        contentTextStyle: text.bodyMedium?.copyWith(color: r.text2),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: r.surface2,
        modalBackgroundColor: r.surface2,
        modalBarrierColor: r.scrim,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        modalElevation: 0,
        showDragHandle: true,
        dragHandleColor: kit.handleColor,
        dragHandleSize: kit.handleSize,
      ),
      inputDecorationTheme: InputDecorationThemeData(
        filled: true,
        fillColor: r.surface1,
        hintStyle: text.bodyLarge?.copyWith(color: r.text3),
        labelStyle: text.bodyLarge?.copyWith(color: r.text2),
        floatingLabelStyle: text.bodyMedium?.copyWith(color: r.text2),
        helperStyle: text.bodySmall?.copyWith(color: r.text2),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 15,
        ),
        border: _field(r.hairline),
        enabledBorder: _field(r.hairline),
        disabledBorder: _field(r.hairline),
        focusedBorder: _field(r.accent, width: 1.5),
        errorBorder: _field(r.text1),
        focusedErrorBorder: _field(r.text1, width: 1.5),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: Size(48, kit.buttonHeight),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          elevation: 0,
          textStyle: button,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: Size(48, kit.buttonHeight),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          foregroundColor: r.text1,
          side: BorderSide(color: r.hairline, width: 0),
          textStyle: button,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          textStyle: button,
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          minimumSize: Size(48, kit.buttonHeight),
          backgroundColor: r.surface3,
          foregroundColor: r.text1,
          elevation: 0,
          textStyle: button,
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          minimumSize: const Size.square(48),
          iconSize: 22,
        ),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: r.accent,
        foregroundColor: r.onAccent,
        elevation: 0,
        focusElevation: 0,
        hoverElevation: 0,
        highlightElevation: 0,
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: r.surface3,
        contentTextStyle: text.bodyMedium?.copyWith(color: r.text1),
        actionTextColor: r.accent,
        elevation: 0,
        insetPadding: const EdgeInsets.all(12),
      ),
      chipTheme: ChipThemeData(
        // Comfortable density: chips act as primary filters in this product
        // (model intents, variants, file breadcrumbs), so raise them from
        // M3's 32dp toward a >=40dp visual target. Pills (§4: 999).
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        backgroundColor: r.surface3,
        selectedColor: Color.alphaBlend(
          r.accent.withValues(alpha: .18),
          r.surface3,
        ),
        side: BorderSide.none,
        labelStyle: text.labelLarge?.copyWith(color: r.text1),
        secondaryLabelStyle: text.labelLarge?.copyWith(color: r.text1),
      ),
      popupMenuTheme: PopupMenuThemeData(
        color: r.surface2,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        textStyle: text.bodyLarge,
      ),
      menuTheme: MenuThemeData(
        style: MenuStyle(
          backgroundColor: WidgetStatePropertyAll(r.surface2),
          surfaceTintColor: const WidgetStatePropertyAll(Colors.transparent),
          elevation: const WidgetStatePropertyAll(0),
        ),
      ),
      tooltipTheme: TooltipThemeData(
        textStyle: text.bodySmall?.copyWith(color: r.text1),
      ),
      badgeTheme: BadgeThemeData(
        backgroundColor: r.attentionFill,
        textColor: r.onAttentionFill,
        textStyle: text.labelSmall,
      ),
      switchTheme: SwitchThemeData(
        trackOutlineColor: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected)
              ? Colors.transparent
              : r.hairline,
        ),
        // A switch that cannot act rests dimmed, on or off: the saved value
        // stays readable but it no longer looks like a live control.
        trackColor: WidgetStateProperty.resolveWith((states) {
          final on = states.contains(WidgetState.selected);
          if (states.contains(WidgetState.disabled)) {
            return on ? r.text3.withValues(alpha: .35) : r.surface3;
          }
          return on ? r.accent : r.surface3;
        }),
        thumbColor: WidgetStateProperty.resolveWith((states) {
          final on = states.contains(WidgetState.selected);
          if (states.contains(WidgetState.disabled)) {
            return on ? r.text2 : r.text3;
          }
          return on ? r.onAccent : r.text2;
        }),
      ),
      listTileTheme: ListTileThemeData(
        iconColor: r.text2,
        textColor: r.text1,
        titleTextStyle: text.bodyLarge?.copyWith(fontWeight: FontWeight.w500),
        subtitleTextStyle: text.bodySmall?.copyWith(color: r.text2),
        minVerticalPadding: 8,
      ),
      // Thickness 0 is Flutter's hairline: exactly one physical pixel at any
      // device pixel ratio (§7).
      dividerTheme: DividerThemeData(color: r.hairline, thickness: 0, space: 1),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: r.accent,
        linearTrackColor: r.surface3,
        circularTrackColor: r.surface3,
      ),
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: r.accent,
        selectionColor: r.accent.withValues(alpha: .28),
        selectionHandleColor: r.accent,
      ),
    );
    return shaped(theme, kit);
  }

  static final Expando<Map<KitTokens, ThemeData>> _shapedCache = Expando(
    'AppTheme.shaped',
  );

  /// [theme] with every corner of its Material parts taken from [kit]: the
  /// buttons, chips and the floating action button, fields, cards, dialogs,
  /// sheets, snack bars, menus and tooltips. [kit] is the theme's own
  /// tokens when the theme is built; pass the tokens under the person's
  /// Fine-tune knobs (`KitTokens.of(context)`) and the Material parts follow
  /// Corners as the kit does. Memoised per theme and tokens.
  static ThemeData shaped(ThemeData theme, KitTokens kit) {
    final cache = _shapedCache[theme] ??= {};
    return cache[kit] ??= _shaped(theme, kit);
  }

  static ThemeData _shaped(ThemeData theme, KitTokens kit) {
    RoundedRectangleBorder box(double radius, {BorderSide? side}) =>
        RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(radius),
          side: side ?? BorderSide.none,
        );
    final control = box(kit.buttonRadius);
    final popover = box(
      kit.popoverCorner,
      side: BorderSide(color: kit.roles.hairline),
    );
    ButtonStyle? button(ButtonStyle? style) =>
        style?.copyWith(shape: WidgetStatePropertyAll(control));
    final fields = theme.inputDecorationTheme;
    InputBorder? field(InputBorder? border) => border is OutlineInputBorder
        ? border.copyWith(
            borderRadius: BorderRadius.all(Radius.circular(kit.fieldCorner)),
          )
        : border;
    return theme.copyWith(
      cardTheme: theme.cardTheme.copyWith(shape: box(kit.panelCornerRadius)),
      dialogTheme: theme.dialogTheme.copyWith(shape: box(kit.panelRadius)),
      bottomSheetTheme: theme.bottomSheetTheme.copyWith(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(
            top: Radius.circular(kit.sheetRadius),
          ),
        ),
      ),
      inputDecorationTheme: fields.copyWith(
        border: field(fields.border),
        enabledBorder: field(fields.enabledBorder),
        disabledBorder: field(fields.disabledBorder),
        focusedBorder: field(fields.focusedBorder),
        errorBorder: field(fields.errorBorder),
        focusedErrorBorder: field(fields.focusedErrorBorder),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: button(theme.filledButtonTheme.style),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: button(theme.outlinedButtonTheme.style),
      ),
      textButtonTheme: TextButtonThemeData(
        style: button(theme.textButtonTheme.style),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: button(theme.elevatedButtonTheme.style),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: button(theme.iconButtonTheme.style),
      ),
      floatingActionButtonTheme: theme.floatingActionButtonTheme.copyWith(
        shape: kit.pillBorder(),
      ),
      snackBarTheme: theme.snackBarTheme.copyWith(
        shape: box(kit.popoverCorner),
      ),
      chipTheme: theme.chipTheme.copyWith(shape: kit.pillBorder()),
      popupMenuTheme: theme.popupMenuTheme.copyWith(shape: popover),
      menuTheme: MenuThemeData(
        style: theme.menuTheme.style?.copyWith(
          shape: WidgetStatePropertyAll(popover),
        ),
      ),
      tooltipTheme: theme.tooltipTheme.copyWith(
        decoration: BoxDecoration(
          color: kit.roles.surface3,
          borderRadius: BorderRadius.circular(kit.popoverCorner),
        ),
      ),
    );
  }

  static OutlineInputBorder _field(Color color, {double width = 1}) =>
      OutlineInputBorder(
        borderRadius: const BorderRadius.all(
          Radius.circular(KitTokens.fieldRadius),
        ),
        borderSide: BorderSide(color: color, width: width),
      );
}

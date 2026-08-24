import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';

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
}

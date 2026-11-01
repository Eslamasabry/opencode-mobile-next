import 'package:flutter/widgets.dart';

/// The most the app's frame lets the system text size grow its words
/// (docs/ux-system/kit-v2.md "Text scale: frame and reading").
const double kKitChromeMaxTextScale = 1.3;

/// Marks a part as the app's frame (top bar, chips, buttons, the composer,
/// sheet headers, tabs): the words inside never scale above
/// [kKitChromeMaxTextScale] whatever the system font size, so the frame
/// keeps its shape at the largest text. Reading text (replies, tool output,
/// settings rows, help) stays outside it and keeps the full system scale.
///
/// The clamp is on the system's [TextScaler], so Android's nonlinear scaling
/// is kept below the cap. Screen-reader labels are not touched.
class KitChrome extends StatelessWidget {
  const KitChrome({super.key, required this.child}) : builder = null;

  /// For a part that reads the text scale itself: [builder] gets a context
  /// already inside the clamp.
  const KitChrome.builder({super.key, required WidgetBuilder this.builder})
    : child = null;

  final Widget? child;
  final WidgetBuilder? builder;

  @override
  Widget build(BuildContext context) => MediaQuery.withClampedTextScaling(
    maxScaleFactor: kKitChromeMaxTextScale,
    child: builder == null ? child! : Builder(builder: builder!),
  );
}

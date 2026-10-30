import 'package:flutter/widgets.dart';

/// Tells the card inside that it spans the whole row and should lay out
/// sideways. Cards read it with [wideOf].
///
/// States: none — plumbing; it draws nothing.
class KitAgentGridSpan extends InheritedWidget {
  const KitAgentGridSpan({super.key, required super.child});

  static bool wideOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<KitAgentGridSpan>() != null;

  @override
  bool updateShouldNotify(KitAgentGridSpan old) => false;
}

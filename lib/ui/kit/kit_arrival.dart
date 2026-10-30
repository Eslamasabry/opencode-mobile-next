import 'dart:async';

import 'package:flutter/semantics.dart';
import 'package:flutter/widgets.dart';

import 'kit_motion.dart';
import 'kit_tokens.dart';

/// Where a search result arrives on the page it opens (P9.4): one row, named
/// by a stable anchor id such as `effects-vibration`, never a route string
/// or a widget key. The page is pushed under this scope; the first
/// [KitArrival] below it with that id claims the request, and nothing else
/// can claim it again: a rebuild, or the same row built anew after a reload,
/// never repeats the arrival.
///
/// Build it once per opened page (outside the route's builder), so the
/// request lives as long as that page.
class KitArrivalScope extends InheritedWidget {
  KitArrivalScope({super.key, required String rowId, required super.child})
    : _request = _KitArrivalRequest(rowId);

  final _KitArrivalRequest _request;

  /// The row this page was opened for.
  String get rowId => _request.rowId;

  /// The row the page under [context] was opened for, or null when it was
  /// opened some other way: a page with tabs picks the tab that holds it.
  static String? maybeRowId(BuildContext context) =>
      context.getInheritedWidgetOfExactType<KitArrivalScope>()?.rowId;

  /// Whether the row [id] under [context] is the one this page was opened
  /// for, still unclaimed. True at most once per scope.
  static bool claim(BuildContext context, String id) =>
      context.getInheritedWidgetOfExactType<KitArrivalScope>()?._request.claim(
        id,
      ) ??
      false;

  @override
  bool updateShouldNotify(KitArrivalScope oldWidget) => false;
}

class _KitArrivalRequest {
  _KitArrivalRequest(this.rowId);

  final String rowId;
  bool _claimed = false;

  bool claim(String id) {
    if (_claimed || id != rowId) return false;
    return _claimed = true;
  }
}

/// One row a search result can arrive at. When the page was opened for this
/// row ([KitArrivalScope]), it waits until the row is laid out, scrolls it
/// into view, moves the screen reader to it and washes it in the accent for
/// [hold]; otherwise it draws only [child].
///
/// The wash is the find mark's (the accent behind the row, never a text
/// colour), so the words keep their contrast. Reduced motion (the system
/// setting or Animations: Off) jumps instead of scrolling and drops the
/// wash at once instead of fading it.
///
/// States: none — a passive mark around one row.
class KitArrival extends StatefulWidget {
  const KitArrival({super.key, required this.id, required this.child});

  /// The row's anchor id, as a search target names it.
  final String id;

  final Widget child;

  /// How long the wash stays after the row came into view.
  static const hold = Duration(milliseconds: 2400);

  /// The wash's strength: the find mark's passive hit, light enough that a
  /// switch or a value on the row keeps its contrast.
  static const washAlpha = .18;

  @override
  State<KitArrival> createState() => _KitArrivalState();
}

class _KitArrivalState extends State<KitArrival> {
  bool _asked = false;
  bool _claimed = false;
  bool _marked = false;
  Timer? _hold;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_asked) return;
    _asked = true;
    if (!KitArrivalScope.claim(context, widget.id)) return;
    _claimed = true;
    // After this frame: the row and everything above it are laid out.
    WidgetsBinding.instance.addPostFrameCallback((_) => unawaited(_arrive()));
  }

  Future<void> _arrive() async {
    if (!mounted) return;
    final still = KitMotion.reduced(context);
    setState(() => _marked = true);
    await Scrollable.ensureVisible(
      context,
      alignment: .3,
      duration: still ? Duration.zero : KitMotion.standard,
      curve: KitMotion.emphasized,
    );
    if (!mounted) return;
    context.findRenderObject()?.sendSemanticsEvent(const FocusSemanticEvent());
    _hold = Timer(KitArrival.hold, () {
      if (mounted) setState(() => _marked = false);
    });
  }

  @override
  void dispose() {
    _hold?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_claimed) return widget.child;
    final wash = KitTokens.of(
      context,
    ).roles.accent.withValues(alpha: KitArrival.washAlpha);
    return Semantics(
      // Its own node, so the focus event lands on this row, not the list.
      container: true,
      child: Stack(
        // The row keeps the constraints it would have had on its own.
        fit: StackFit.passthrough,
        children: [
          Positioned.fill(
            child: ExcludeSemantics(
              child: AnimatedOpacity(
                opacity: _marked ? 1 : 0,
                duration: _marked || KitMotion.reduced(context)
                    ? Duration.zero
                    : KitMotion.entrance,
                curve: KitMotion.enter,
                child: ColoredBox(color: wash),
              ),
            ),
          ),
          widget.child,
        ],
      ),
    );
  }
}

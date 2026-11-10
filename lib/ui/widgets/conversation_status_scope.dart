import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../../state/connection.dart';
import '../kit/kit.dart';
import 'connection_status_banner.dart';

/// The connection line of a conversation that runs on its own connection (an
/// agent on this phone, another server): that connection's health, never the
/// app's main connection's. The app-wide line above the navigator reads the
/// main connection, so under this scope it is replaced by the conversation's
/// own, while the other app-wide lines (Android stopped the app, heat) stay.
class ConversationStatusScope extends StatefulWidget {
  const ConversationStatusScope({
    super.key,
    required this.connection,
    required this.child,
  });

  final ConnectionController connection;
  final Widget child;

  @override
  State<ConversationStatusScope> createState() =>
      _ConversationStatusScopeState();
}

class _ConversationStatusScopeState extends State<ConversationStatusScope> {
  final _conditions = ValueNotifier<List<KitStatus>>(const []);
  ValueListenable<List<KitStatus>>? _outer;

  @override
  void initState() {
    super.initState();
    widget.connection.addListener(_merge);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final outer = KitStatusScope.of(context);
    if (!identical(outer, _outer)) {
      _outer?.removeListener(_merge);
      _outer = outer..addListener(_merge);
    }
    _merge();
  }

  @override
  void didUpdateWidget(ConversationStatusScope oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.connection, widget.connection)) {
      oldWidget.connection.removeListener(_merge);
      widget.connection.addListener(_merge);
    }
    _merge();
  }

  void _merge() {
    if (!mounted) return;
    final own = connectionKitStatus(context, widget.connection);
    _conditions.value = [
      for (final status in _outer?.value ?? const <KitStatus>[])
        if (status.kind != KitStatusKind.connection) status,
      ?own,
    ];
  }

  @override
  Widget build(BuildContext context) =>
      KitStatusScope(conditions: _conditions, child: widget.child);

  @override
  void dispose() {
    widget.connection.removeListener(_merge);
    _outer?.removeListener(_merge);
    _conditions.dispose();
    super.dispose();
  }
}

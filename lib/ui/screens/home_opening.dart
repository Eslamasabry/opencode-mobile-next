import 'package:flutter/widgets.dart';

import '../../state/session_inventory_cache.dart' show SessionPreview;
import '../app_theme.dart' show AppStatusTone;

/// The home while the saved server is still starting or connecting on a
/// cold start (the opening shell): last time's conversation titles,
/// read-only, under the same bar, title, filters and list as the live home.
///
/// It is the same [HomeScreen] before and after the server answers, so the
/// bar and the rows the person is reading stay where they are (jitter J2,
/// 2026-10-29). Nothing here marks the server connected or enables a live
/// action: the rows open nothing and the + is not floated until it answers.
@immutable
class HomeOpening {
  const HomeOpening({
    required this.rows,
    required this.status,
    required this.tone,
    required this.onServer,
    this.notice,
  });

  /// Last time's titles (this server's and the phone agents'), newest first.
  final List<SessionPreview> rows;

  /// The server pill's status word ("Starting", "Connecting", "Offline").
  final String status;
  final AppStatusTone tone;

  /// The pill: pick another server while this one opens.
  final VoidCallback onServer;

  /// The saved server's card when it needs the person (it failed, is not
  /// answering, did not start): above the rows, with its own Try again and
  /// Details. Null while it simply starts or connects: the pill says that.
  final Widget? notice;
}

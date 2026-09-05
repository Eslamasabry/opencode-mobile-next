import '../api/models.dart';
import 'server_gateway.dart';

/// V2's newest raw pages can consist entirely of staged-away rows. Skip
/// through those pages to the visible boundary without inventing a cursor.
/// Retain the raw boundary page so clear can rehydrate the complete transcript.
Future<ServerPage<MessageWithParts>> readHistoryAtStagedBoundary(
  ServerGateway api,
  String sessionID, {
  String? boundary,
  String? cursor,
  required bool Function() isCurrent,
}) async {
  final cursors = <String>{};
  while (true) {
    final page = await api.messagePage(sessionID, cursor: cursor);
    if (!isCurrent()) {
      throw const ProductException(
        'The session changed while loading history.',
      );
    }
    if (boundary == null ||
        page.items.any((message) => message.info.id.compareTo(boundary) < 0) ||
        !page.hasMore) {
      return page;
    }
    final next = page.nextCursor!;
    if (next == cursor || !cursors.add(next)) {
      throw const ProductException(
        'The history cursor expired. Reload the session.',
      );
    }
    cursor = next;
  }
}

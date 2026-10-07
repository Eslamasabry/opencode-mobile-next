import '../../api/models.dart';

/// Bounded transcript reads for recovery. Items are oldest-first within a page.
abstract interface class GenUiHistoryGateway {
  Future<bool> genUiSessionIdle(String sessionID);

  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  });
}

class GenUiHistoryPage {
  final List<MessageWithParts> items;
  final String? olderCursor;
  final bool hasMore;

  const GenUiHistoryPage({
    required this.items,
    this.olderCursor,
    required this.hasMore,
  });
}

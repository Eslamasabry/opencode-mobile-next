import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

/// Existing non-pagination fixtures expose their transcript as one complete
/// chronological page. Pagination-specific fakes override messagePage directly.
mixin CompleteMessageHistory on OpenCodeApi {
  // Fixtures override this with their transcript. An empty default prevents
  // falling back into OpenCodeApi.messages -> messagePage -> messages.
  @override
  Future<List<MessageWithParts>> messages(String id) async => const [];

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

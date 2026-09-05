import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

/// Existing non-pagination fixtures expose their transcript as one complete
/// chronological page. Pagination-specific fakes override messagePage directly.
mixin CompleteMessageHistory on OpenCodeApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: await messages(id));
}

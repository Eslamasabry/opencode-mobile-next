import '../domain/attention_item.dart';
import 'connection.dart';

/// Read-only projection of already loaded profile metadata and connection data.
///
/// Does not connect, refresh, load credentials, acknowledge reads, or persist a
/// second cache. Inactive data is deliberately unknown: neither the four-row
/// widget snapshot nor read watermarks establish a per-server attention total.
class AttentionOverview {
  const AttentionOverview._(this.items);

  final List<AttentionItem> items;

  factory AttentionOverview.fromController(ConnectionController controller) {
    final selectedID = controller.profile?.id;
    final canReadCache = controller.isConnected && !controller.locationLoading;
    int? positive(int count) => count > 0 ? count : null;

    return AttentionOverview._(
      List.unmodifiable([
        for (final profile in controller.store.profiles)
          if (controller.isProfileReadable(profile.id))
            AttentionItem(
              profileID: profile.id,
              profileName: profile.name,
              isSelected: profile.id == selectedID,
              hasConnectionCache: profile.id == selectedID && canReadCache,
              // Even an empty collection can mean not hydrated, unsupported,
              // or incomplete. Only positive observations are presented.
              pendingRequests: profile.id == selectedID && canReadCache
                  ? positive(
                      controller.permissions.length +
                          controller.questions.length +
                          controller.forms.length,
                    )
                  : null,
              runningSessions: profile.id == selectedID && canReadCache
                  ? positive(controller.busySessions.length)
                  : null,
              unreadSessions: profile.id == selectedID && canReadCache
                  ? positive(
                      controller.sessionsById.values
                          .where(controller.isSessionUnread)
                          .length,
                    )
                  : null,
            ),
      ]),
    );
  }
}

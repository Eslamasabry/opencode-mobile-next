//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_tool_called_sync_event_data_provider.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextToolCalledSyncEventDataProvider {
  /// Returns a new [SyncEventSessionNextToolCalledSyncEventDataProvider] instance.
  SyncEventSessionNextToolCalledSyncEventDataProvider({
    required this.executed,

    this.metadata,
  });

  @JsonKey(name: r'executed', required: true, includeIfNull: false)
  final bool executed;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Map<String, Object>? metadata;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextToolCalledSyncEventDataProvider &&
            runtimeType == other.runtimeType &&
            equals([executed, metadata], [other.executed, other.metadata]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([executed, metadata]);

  factory SyncEventSessionNextToolCalledSyncEventDataProvider.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextToolCalledSyncEventDataProviderFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextToolCalledSyncEventDataProviderToJson(this);

  String toString() {
    return toJson().toString();
  }
}

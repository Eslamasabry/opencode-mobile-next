//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_context_updated_sync_event.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_context_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextContextUpdated {
  /// Returns a new [SyncEventSessionNextContextUpdated] instance.
  SyncEventSessionNextContextUpdated({
    required this.type,

    required this.id,

    required this.syncEvent,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        SyncEventSessionNextContextUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final SyncEventSessionNextContextUpdatedTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'syncEvent', required: true, includeIfNull: false)
  final SyncEventSessionNextContextUpdatedSyncEvent syncEvent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextContextUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, syncEvent],
              [other.type, other.id, other.syncEvent],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, id, syncEvent]);

  factory SyncEventSessionNextContextUpdated.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextContextUpdatedFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextContextUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventSessionNextContextUpdatedTypeEnum {
  @JsonValue(r'sync')
  sync_(r'sync'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventSessionNextContextUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

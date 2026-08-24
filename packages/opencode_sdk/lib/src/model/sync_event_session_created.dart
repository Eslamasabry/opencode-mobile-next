//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_created_sync_event.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_created.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionCreated {
  /// Returns a new [SyncEventSessionCreated] instance.
  SyncEventSessionCreated({
    required this.type,

    required this.id,

    required this.syncEvent,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SyncEventSessionCreatedTypeEnum.unknownDefaultOpenApi,
  )
  final SyncEventSessionCreatedTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'syncEvent', required: true, includeIfNull: false)
  final SyncEventSessionCreatedSyncEvent syncEvent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionCreated &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, syncEvent],
              [other.type, other.id, other.syncEvent],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, id, syncEvent]);

  factory SyncEventSessionCreated.fromJson(Map<String, dynamic> json) =>
      _$SyncEventSessionCreatedFromJson(json);

  Map<String, dynamic> toJson() => _$SyncEventSessionCreatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventSessionCreatedTypeEnum {
  @JsonValue(r'sync')
  sync_(r'sync'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventSessionCreatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

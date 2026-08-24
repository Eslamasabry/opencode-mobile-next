//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/prompt.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_prompted_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextPromptedSyncEventData {
  /// Returns a new [SyncEventSessionNextPromptedSyncEventData] instance.
  SyncEventSessionNextPromptedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.messageID,

    required this.prompt,

    required this.delivery,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'prompt', required: true, includeIfNull: false)
  final Prompt prompt;

  @JsonKey(
    name: r'delivery',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SyncEventSessionNextPromptedSyncEventDataDeliveryEnum
        .unknownDefaultOpenApi,
  )
  final SyncEventSessionNextPromptedSyncEventDataDeliveryEnum delivery;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextPromptedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, messageID, prompt, delivery],
              [
                other.timestamp,
                other.sessionID,
                other.messageID,
                other.prompt,
                other.delivery,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, messageID, prompt, delivery]);

  factory SyncEventSessionNextPromptedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextPromptedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextPromptedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventSessionNextPromptedSyncEventDataDeliveryEnum {
  @JsonValue(r'steer')
  steer(r'steer'),
  @JsonValue(r'queue')
  queue(r'queue'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventSessionNextPromptedSyncEventDataDeliveryEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

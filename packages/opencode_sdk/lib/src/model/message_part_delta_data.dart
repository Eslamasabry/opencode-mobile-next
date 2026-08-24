//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'message_part_delta_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MessagePartDeltaData {
  /// Returns a new [MessagePartDeltaData] instance.
  MessagePartDeltaData({
    required this.sessionID,

    required this.messageID,

    required this.partID,

    required this.field,

    required this.delta,
  });

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'partID', required: true, includeIfNull: false)
  final String partID;

  @JsonKey(name: r'field', required: true, includeIfNull: false)
  final String field;

  @JsonKey(name: r'delta', required: true, includeIfNull: false)
  final String delta;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MessagePartDeltaData &&
            runtimeType == other.runtimeType &&
            equals(
              [sessionID, messageID, partID, field, delta],
              [
                other.sessionID,
                other.messageID,
                other.partID,
                other.field,
                other.delta,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([sessionID, messageID, partID, field, delta]);

  factory MessagePartDeltaData.fromJson(Map<String, dynamic> json) =>
      _$MessagePartDeltaDataFromJson(json);

  Map<String, dynamic> toJson() => _$MessagePartDeltaDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

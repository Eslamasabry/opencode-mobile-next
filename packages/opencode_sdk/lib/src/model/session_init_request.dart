//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_init_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionInitRequest {
  /// Returns a new [SessionInitRequest] instance.
  SessionInitRequest({
    required this.modelID,

    required this.providerID,

    required this.messageID,
  });

  @JsonKey(name: r'modelID', required: true, includeIfNull: false)
  final String modelID;

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionInitRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [modelID, providerID, messageID],
              [other.modelID, other.providerID, other.messageID],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([modelID, providerID, messageID]);

  factory SessionInitRequest.fromJson(Map<String, dynamic> json) =>
      _$SessionInitRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SessionInitRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}

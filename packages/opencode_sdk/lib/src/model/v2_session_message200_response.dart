//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_message.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_message200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionMessage200Response {
  /// Returns a new [V2SessionMessage200Response] instance.
  V2SessionMessage200Response({required this.data});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final SessionMessage data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionMessage200Response &&
            runtimeType == other.runtimeType &&
            equals([data], [other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([data]);

  factory V2SessionMessage200Response.fromJson(Map<String, dynamic> json) =>
      _$V2SessionMessage200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$V2SessionMessage200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}

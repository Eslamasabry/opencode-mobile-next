//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_message.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_context200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionContext200Response {
  /// Returns a new [V2SessionContext200Response] instance.
  V2SessionContext200Response({required this.data});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final List<SessionMessage> data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionContext200Response &&
            runtimeType == other.runtimeType &&
            equals([data], [other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([data]);

  factory V2SessionContext200Response.fromJson(Map<String, dynamic> json) =>
      _$V2SessionContext200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$V2SessionContext200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}

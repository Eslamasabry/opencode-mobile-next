//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_ref.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_switch_model_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionSwitchModelRequest {
  /// Returns a new [V2SessionSwitchModelRequest] instance.
  V2SessionSwitchModelRequest({required this.model});

  @JsonKey(name: r'model', required: true, includeIfNull: false)
  final ModelRef model;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionSwitchModelRequest &&
            runtimeType == other.runtimeType &&
            equals([model], [other.model]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([model]);

  factory V2SessionSwitchModelRequest.fromJson(Map<String, dynamic> json) =>
      _$V2SessionSwitchModelRequestFromJson(json);

  Map<String, dynamic> toJson() => _$V2SessionSwitchModelRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}

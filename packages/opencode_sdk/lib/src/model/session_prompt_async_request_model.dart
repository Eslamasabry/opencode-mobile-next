//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_prompt_async_request_model.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionPromptAsyncRequestModel {
  /// Returns a new [SessionPromptAsyncRequestModel] instance.
  SessionPromptAsyncRequestModel({
    required this.providerID,

    required this.modelID,
  });

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  @JsonKey(name: r'modelID', required: true, includeIfNull: false)
  final String modelID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionPromptAsyncRequestModel &&
            runtimeType == other.runtimeType &&
            equals([providerID, modelID], [other.providerID, other.modelID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([providerID, modelID]);

  factory SessionPromptAsyncRequestModel.fromJson(Map<String, dynamic> json) =>
      _$SessionPromptAsyncRequestModelFromJson(json);

  Map<String, dynamic> toJson() => _$SessionPromptAsyncRequestModelToJson(this);

  String toString() {
    return toJson().toString();
  }
}

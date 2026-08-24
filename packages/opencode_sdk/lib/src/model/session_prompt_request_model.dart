//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_prompt_request_model.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionPromptRequestModel {
  /// Returns a new [SessionPromptRequestModel] instance.
  SessionPromptRequestModel({required this.modelID, required this.providerID});

  @JsonKey(name: r'modelID', required: true, includeIfNull: false)
  final String modelID;

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionPromptRequestModel &&
            runtimeType == other.runtimeType &&
            equals([modelID, providerID], [other.modelID, other.providerID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([modelID, providerID]);

  factory SessionPromptRequestModel.fromJson(Map<String, dynamic> json) =>
      _$SessionPromptRequestModelFromJson(json);

  Map<String, dynamic> toJson() => _$SessionPromptRequestModelToJson(this);

  String toString() {
    return toJson().toString();
  }
}

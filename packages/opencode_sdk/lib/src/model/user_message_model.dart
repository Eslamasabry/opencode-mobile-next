//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'user_message_model.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class UserMessageModel {
  /// Returns a new [UserMessageModel] instance.
  UserMessageModel({
    required this.providerID,

    required this.modelID,

    this.variant,
  });

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  @JsonKey(name: r'modelID', required: true, includeIfNull: false)
  final String modelID;

  @JsonKey(name: r'variant', required: false, includeIfNull: false)
  final String? variant;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is UserMessageModel &&
            runtimeType == other.runtimeType &&
            equals(
              [providerID, modelID, variant],
              [other.providerID, other.modelID, other.variant],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([providerID, modelID, variant]);

  factory UserMessageModel.fromJson(Map<String, dynamic> json) =>
      _$UserMessageModelFromJson(json);

  Map<String, dynamic> toJson() => _$UserMessageModelToJson(this);

  String toString() {
    return toJson().toString();
  }
}

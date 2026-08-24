//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_create_request_model.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionCreateRequestModel {
  /// Returns a new [SessionCreateRequestModel] instance.
  SessionCreateRequestModel({
    required this.id,

    required this.providerID,

    this.variant,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  @JsonKey(name: r'variant', required: false, includeIfNull: false)
  final String? variant;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionCreateRequestModel &&
            runtimeType == other.runtimeType &&
            equals(
              [id, providerID, variant],
              [other.id, other.providerID, other.variant],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, providerID, variant]);

  factory SessionCreateRequestModel.fromJson(Map<String, dynamic> json) =>
      _$SessionCreateRequestModelFromJson(json);

  Map<String, dynamic> toJson() => _$SessionCreateRequestModelToJson(this);

  String toString() {
    return toJson().toString();
  }
}

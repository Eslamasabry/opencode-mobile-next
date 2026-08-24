//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/provider_request.dart';
import 'package:opencode_sdk/src/model/provider_api_model.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_v2_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderV2Info {
  /// Returns a new [ProviderV2Info] instance.
  ProviderV2Info({
    required this.id,

    this.integrationID,

    required this.name,

    this.disabled,

    required this.api,

    required this.request,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'integrationID', required: false, includeIfNull: false)
  final String? integrationID;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'disabled', required: false, includeIfNull: false)
  final bool? disabled;

  @JsonKey(name: r'api', required: true, includeIfNull: false)
  final ProviderApiModel api;

  @JsonKey(name: r'request', required: true, includeIfNull: false)
  final ProviderRequest request;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderV2Info &&
            runtimeType == other.runtimeType &&
            equals(
              [id, integrationID, name, disabled, api, request],
              [
                other.id,
                other.integrationID,
                other.name,
                other.disabled,
                other.api,
                other.request,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, integrationID, name, disabled, api, request]);

  factory ProviderV2Info.fromJson(Map<String, dynamic> json) =>
      _$ProviderV2InfoFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderV2InfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}

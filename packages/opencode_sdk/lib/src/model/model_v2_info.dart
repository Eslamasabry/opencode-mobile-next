//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_api.dart';
import 'package:opencode_sdk/src/model/model_v2_info_request.dart';
import 'package:opencode_sdk/src/model/model_v2_capabilities.dart';
import 'package:opencode_sdk/src/model/model_v2_info_time.dart';
import 'package:opencode_sdk/src/model/model_v2_info_variants_inner.dart';
import 'package:opencode_sdk/src/model/model_v2_info_limit.dart';
import 'package:opencode_sdk/src/model/model_cost.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_v2_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelV2Info {
  /// Returns a new [ModelV2Info] instance.
  ModelV2Info({
    required this.id,

    required this.providerID,

    this.family,

    required this.name,

    required this.api,

    required this.capabilities,

    required this.request,

    required this.variants,

    required this.time,

    required this.cost,

    required this.status,

    required this.enabled,

    required this.limit,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  @JsonKey(name: r'family', required: false, includeIfNull: false)
  final String? family;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'api', required: true, includeIfNull: false)
  final ModelApi api;

  @JsonKey(name: r'capabilities', required: true, includeIfNull: false)
  final ModelV2Capabilities capabilities;

  @JsonKey(name: r'request', required: true, includeIfNull: false)
  final ModelV2InfoRequest request;

  @JsonKey(name: r'variants', required: true, includeIfNull: false)
  final List<ModelV2InfoVariantsInner> variants;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final ModelV2InfoTime time;

  @JsonKey(name: r'cost', required: true, includeIfNull: false)
  final List<ModelCost> cost;

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ModelV2InfoStatusEnum.unknownDefaultOpenApi,
  )
  final ModelV2InfoStatusEnum status;

  @JsonKey(name: r'enabled', required: true, includeIfNull: false)
  final bool enabled;

  @JsonKey(name: r'limit', required: true, includeIfNull: false)
  final ModelV2InfoLimit limit;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelV2Info &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                providerID,
                family,
                name,
                api,
                capabilities,
                request,
                variants,
                time,
                cost,
                status,
                enabled,
                limit,
              ],
              [
                other.id,
                other.providerID,
                other.family,
                other.name,
                other.api,
                other.capabilities,
                other.request,
                other.variants,
                other.time,
                other.cost,
                other.status,
                other.enabled,
                other.limit,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        providerID,
        family,
        name,
        api,
        capabilities,
        request,
        variants,
        time,
        cost,
        status,
        enabled,
        limit,
      ]);

  factory ModelV2Info.fromJson(Map<String, dynamic> json) =>
      _$ModelV2InfoFromJson(json);

  Map<String, dynamic> toJson() => _$ModelV2InfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ModelV2InfoStatusEnum {
  @JsonValue(r'alpha')
  alpha(r'alpha'),
  @JsonValue(r'beta')
  beta(r'beta'),
  @JsonValue(r'deprecated')
  deprecated(r'deprecated'),
  @JsonValue(r'active')
  active(r'active'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ModelV2InfoStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

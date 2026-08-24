//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_api.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_limit.dart';
import 'package:opencode_sdk/src/model/model_cost.dart';
import 'package:opencode_sdk/src/model/model_capabilities.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Model {
  /// Returns a new [Model] instance.
  Model({
    required this.id,

    required this.providerID,

    required this.api,

    required this.name,

    this.family,

    required this.capabilities,

    required this.cost,

    required this.limit,

    required this.status,

    required this.options,

    required this.headers,

    required this.releaseDate,

    this.variants,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  @JsonKey(name: r'api', required: true, includeIfNull: false)
  final ModelApi api;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'family', required: false, includeIfNull: false)
  final String? family;

  @JsonKey(name: r'capabilities', required: true, includeIfNull: false)
  final ModelCapabilities capabilities;

  @JsonKey(name: r'cost', required: true, includeIfNull: false)
  final ModelCost cost;

  @JsonKey(name: r'limit', required: true, includeIfNull: false)
  final ProviderConfigModelsValueLimit limit;

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ModelStatusEnum.unknownDefaultOpenApi,
  )
  final ModelStatusEnum status;

  @JsonKey(name: r'options', required: true, includeIfNull: false)
  final Object options;

  @JsonKey(name: r'headers', required: true, includeIfNull: false)
  final Map<String, String> headers;

  @JsonKey(name: r'release_date', required: true, includeIfNull: false)
  final String releaseDate;

  @JsonKey(name: r'variants', required: false, includeIfNull: false)
  final Map<String, Object>? variants;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Model &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                providerID,
                api,
                name,
                family,
                capabilities,
                cost,
                limit,
                status,
                options,
                headers,
                releaseDate,
                variants,
              ],
              [
                other.id,
                other.providerID,
                other.api,
                other.name,
                other.family,
                other.capabilities,
                other.cost,
                other.limit,
                other.status,
                other.options,
                other.headers,
                other.releaseDate,
                other.variants,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        providerID,
        api,
        name,
        family,
        capabilities,
        cost,
        limit,
        status,
        options,
        headers,
        releaseDate,
        variants,
      ]);

  factory Model.fromJson(Map<String, dynamic> json) => _$ModelFromJson(json);

  Map<String, dynamic> toJson() => _$ModelToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ModelStatusEnum {
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

  const ModelStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

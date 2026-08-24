//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_cost_tier.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelCostTier {
  /// Returns a new [ModelCostTier] instance.
  ModelCostTier({required this.type, required this.size});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ModelCostTierTypeEnum.unknownDefaultOpenApi,
  )
  final ModelCostTierTypeEnum type;

  @JsonKey(name: r'size', required: true, includeIfNull: false)
  final int size;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelCostTier &&
            runtimeType == other.runtimeType &&
            equals([type, size], [other.type, other.size]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type, size]);

  factory ModelCostTier.fromJson(Map<String, dynamic> json) =>
      _$ModelCostTierFromJson(json);

  Map<String, dynamic> toJson() => _$ModelCostTierToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ModelCostTierTypeEnum {
  @JsonValue(r'context')
  context(r'context'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ModelCostTierTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_v2_info_variants_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelV2InfoVariantsInner {
  /// Returns a new [ModelV2InfoVariantsInner] instance.
  ModelV2InfoVariantsInner({
    required this.id,

    required this.headers,

    required this.body,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'headers', required: true, includeIfNull: false)
  final Map<String, String> headers;

  @JsonKey(name: r'body', required: true, includeIfNull: false)
  final Object body;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelV2InfoVariantsInner &&
            runtimeType == other.runtimeType &&
            equals([id, headers, body], [other.id, other.headers, other.body]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, headers, body]);

  factory ModelV2InfoVariantsInner.fromJson(Map<String, dynamic> json) =>
      _$ModelV2InfoVariantsInnerFromJson(json);

  Map<String, dynamic> toJson() => _$ModelV2InfoVariantsInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}

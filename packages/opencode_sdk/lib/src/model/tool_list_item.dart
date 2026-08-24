//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_list_item.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolListItem {
  /// Returns a new [ToolListItem] instance.
  ToolListItem({
    required this.id,

    required this.description,

    required this.parameters,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'description', required: true, includeIfNull: false)
  final String description;

  @JsonKey(name: r'parameters', required: true, includeIfNull: true)
  final Object? parameters;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolListItem &&
            runtimeType == other.runtimeType &&
            equals(
              [id, description, parameters],
              [other.id, other.description, other.parameters],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, description, parameters]);

  factory ToolListItem.fromJson(Map<String, dynamic> json) =>
      _$ToolListItemFromJson(json);

  Map<String, dynamic> toJson() => _$ToolListItemToJson(this);

  String toString() {
    return toJson().toString();
  }
}

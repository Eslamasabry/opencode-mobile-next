//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'lsp_status.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class LSPStatus {
  /// Returns a new [LSPStatus] instance.
  LSPStatus({
    required this.id,

    required this.name,

    required this.root,

    required this.status,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'root', required: true, includeIfNull: false)
  final String root;

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: LSPStatusStatusEnum.unknownDefaultOpenApi,
  )
  final LSPStatusStatusEnum status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is LSPStatus &&
            runtimeType == other.runtimeType &&
            equals(
              [id, name, root, status],
              [other.id, other.name, other.root, other.status],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, name, root, status]);

  factory LSPStatus.fromJson(Map<String, dynamic> json) =>
      _$LSPStatusFromJson(json);

  Map<String, dynamic> toJson() => _$LSPStatusToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum LSPStatusStatusEnum {
  @JsonValue(r'connected')
  connected(r'connected'),
  @JsonValue(r'error')
  error(r'error'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const LSPStatusStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

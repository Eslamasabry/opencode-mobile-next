part of 'errors.dart';

final Map<OpenCodeErrorContractKey, OpenCodeErrorContract>
openCodeErrorContracts = {
  const OpenCodeErrorContractKey(
    operationId: "auth.set",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "auth.set",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion037",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion037", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "auth.remove",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "auth.remove",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion038",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion038", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "app.log",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "app.log",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion039",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion039", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.controlPlane.moveSession",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/MoveSessionError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.controlPlane.moveSession",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/MoveSessionError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion040",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion040", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "global.health",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "global.health",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "global.event",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "global.event",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "global.config.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "global.config.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "global.config.update",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "global.config.update",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion041",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion041", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "global.dispose",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "global.dispose",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "global.upgrade",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "global.upgrade",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion043",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion043", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "config.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "config.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "config.update",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "config.update",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion044",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion044", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "config.providers",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "config.providers",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.capabilities.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.capabilities.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.console.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.console.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.console.get",
    status: 500,
    mediaType: "application/json",
    schemaJson:
        "{\"\$ref\":\"#/components/schemas/effect_HttpApiError_InternalServerError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.console.get",
      status: 500,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/effect_HttpApiError_InternalServerError\"}",
    ),
    payloadType: "EffectHttpApiErrorInternalServerError",
    decoder: (payload, contract) => decodeOpenCodeErrorModel(
      payload,
      "EffectHttpApiErrorInternalServerError",
      contract,
    ),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.console.listOrgs",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.console.listOrgs",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.console.listOrgs",
    status: 500,
    mediaType: "application/json",
    schemaJson:
        "{\"\$ref\":\"#/components/schemas/effect_HttpApiError_InternalServerError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.console.listOrgs",
      status: 500,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/effect_HttpApiError_InternalServerError\"}",
    ),
    payloadType: "EffectHttpApiErrorInternalServerError",
    decoder: (payload, contract) => decodeOpenCodeErrorModel(
      payload,
      "EffectHttpApiErrorInternalServerError",
      contract,
    ),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tool.list",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tool.list",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion045",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion045", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tool.ids",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tool.ids",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion046",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion046", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "worktree.list",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorktreeError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "worktree.list",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorktreeError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion047",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion047", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "worktree.create",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorktreeError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "worktree.create",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorktreeError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion048",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion048", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "worktree.remove",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorktreeError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "worktree.remove",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorktreeError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion049",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion049", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "worktree.reset",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorktreeError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "worktree.reset",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorktreeError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion050",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion050", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.session.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.session.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.session.background",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.session.background",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion053",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion053", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.resource.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.resource.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "find.text",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "find.text",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "find.files",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "find.files",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "find.symbols",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "find.symbols",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "file.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "file.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "file.read",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "file.read",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "file.status",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "file.status",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "instance.dispose",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "instance.dispose",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "path.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "path.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "vcs.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "vcs.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "vcs.status",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "vcs.status",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "vcs.diff",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "vcs.diff",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "vcs.diff.raw",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "vcs.diff.raw",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "vcs.apply",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/VcsApplyError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "vcs.apply",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/VcsApplyError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion054",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion054", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "command.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "command.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "app.agents",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "app.agents",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "app.skills",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "app.skills",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "lsp.status",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "lsp.status",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "formatter.status",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "formatter.status",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.status",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.status",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.add",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.add",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion055",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion055", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.auth.start",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/McpUnsupportedOAuthError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.auth.start",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/McpUnsupportedOAuthError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion057",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion057", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.auth.start",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.auth.start",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
    ),
    payloadType: "McpServerNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "McpServerNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.auth.remove",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.auth.remove",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.auth.remove",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.auth.remove",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
    ),
    payloadType: "McpServerNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "McpServerNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.auth.callback",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.auth.callback",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion058",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion058", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.auth.callback",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.auth.callback",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
    ),
    payloadType: "McpServerNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "McpServerNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.auth.authenticate",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/McpUnsupportedOAuthError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.auth.authenticate",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/McpUnsupportedOAuthError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion059",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion059", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.auth.authenticate",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.auth.authenticate",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
    ),
    payloadType: "McpServerNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "McpServerNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.connect",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.connect",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.connect",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.connect",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
    ),
    payloadType: "McpServerNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "McpServerNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.disconnect",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.disconnect",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "mcp.disconnect",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "mcp.disconnect",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/McpServerNotFoundError\"}",
    ),
    payloadType: "McpServerNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "McpServerNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "project.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "project.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "project.current",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "project.current",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "project.initGit",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "project.initGit",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "project.update",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "project.update",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion060",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion060", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "project.update",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/ProjectNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "project.update",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/ProjectNotFoundError\"}",
    ),
    payloadType: "ProjectNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "ProjectNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "project.directories",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "project.directories",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.projectCopy.generateName",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.projectCopy.generateName",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.shells",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.shells",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.create",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.create",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion061",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion061", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.get",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.get",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
    ),
    payloadType: "PtyNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PtyNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.update",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.update",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion062",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion062", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.update",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.update",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
    ),
    payloadType: "PtyNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PtyNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.remove",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.remove",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.remove",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.remove",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
    ),
    payloadType: "PtyNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PtyNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.connectToken",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.connectToken",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.connectToken",
    status: 403,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PtyForbiddenError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.connectToken",
      status: 403,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/PtyForbiddenError\"}",
    ),
    payloadType: "PtyForbiddenError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PtyForbiddenError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.connectToken",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.connectToken",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
    ),
    payloadType: "PtyNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PtyNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "question.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "question.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "question.reply",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "question.reply",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion063",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion063", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "question.reply",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/QuestionNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "question.reply",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/QuestionNotFoundError\"}",
    ),
    payloadType: "QuestionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "QuestionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "question.reject",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "question.reject",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion064",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion064", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "question.reject",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/QuestionNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "question.reject",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/QuestionNotFoundError\"}",
    ),
    payloadType: "QuestionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "QuestionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "permission.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "permission.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "permission.reply",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "permission.reply",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion065",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion065", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "permission.reply",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PermissionNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "permission.reply",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/PermissionNotFoundError\"}",
    ),
    payloadType: "PermissionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PermissionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "provider.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "provider.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "provider.auth",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "provider.auth",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "provider.oauth.authorize",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/ProviderAuthError1\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "provider.oauth.authorize",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/ProviderAuthError1\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion066",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion066", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "provider.oauth.callback",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/ProviderAuthError1\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "provider.oauth.callback",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/ProviderAuthError1\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion067",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion067", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.create",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.create",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion069",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion069", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.status",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.status",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion070",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion070", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.get",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.get",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion071",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion071", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.get",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.get",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.delete",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.delete",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion072",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion072", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.delete",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.delete",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.update",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.update",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion073",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion073", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.update",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.update",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.children",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.children",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion074",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion074", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.children",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.children",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.todo",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.todo",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion075",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion075", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.todo",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.todo",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.diff",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.diff",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.messages",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.messages",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion076",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion076", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.messages",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.messages",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.prompt",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.prompt",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion077",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion077", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.prompt",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.prompt",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.message",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.message",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion079",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion079", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.message",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.message",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.deleteMessage",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.deleteMessage",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion080",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion080", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.deleteMessage",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.deleteMessage",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.deleteMessage",
    status: 409,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/SessionBusyError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.deleteMessage",
      status: 409,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/SessionBusyError\"}",
    ),
    payloadType: "SessionBusyError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionBusyError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.fork",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.fork",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion081",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion081", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.fork",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.fork",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.abort",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.abort",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion082",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion082", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.init",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.init",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion083",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion083", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.init",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.init",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.share",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.share",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.share",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.share",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.share",
    status: 500,
    mediaType: "application/json",
    schemaJson:
        "{\"\$ref\":\"#/components/schemas/effect_HttpApiError_InternalServerError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.share",
      status: 500,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/effect_HttpApiError_InternalServerError\"}",
    ),
    payloadType: "EffectHttpApiErrorInternalServerError",
    decoder: (payload, contract) => decodeOpenCodeErrorModel(
      payload,
      "EffectHttpApiErrorInternalServerError",
      contract,
    ),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.unshare",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.unshare",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.unshare",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.unshare",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.unshare",
    status: 500,
    mediaType: "application/json",
    schemaJson:
        "{\"\$ref\":\"#/components/schemas/effect_HttpApiError_InternalServerError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.unshare",
      status: 500,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/effect_HttpApiError_InternalServerError\"}",
    ),
    payloadType: "EffectHttpApiErrorInternalServerError",
    decoder: (payload, contract) => decodeOpenCodeErrorModel(
      payload,
      "EffectHttpApiErrorInternalServerError",
      contract,
    ),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.summarize",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.summarize",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion084",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion084", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.summarize",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.summarize",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.prompt_async",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.prompt_async",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion085",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion085", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.prompt_async",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.prompt_async",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.command",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.command",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion087",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion087", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.command",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.command",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.shell",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.shell",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion088",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion088", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.shell",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.shell",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.shell",
    status: 409,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/SessionBusyError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.shell",
      status: 409,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/SessionBusyError\"}",
    ),
    payloadType: "SessionBusyError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionBusyError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.revert",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.revert",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion089",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion089", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.revert",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.revert",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.revert",
    status: 409,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/SessionBusyError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.revert",
      status: 409,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/SessionBusyError\"}",
    ),
    payloadType: "SessionBusyError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionBusyError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.unrevert",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.unrevert",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion090",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion090", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.unrevert",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.unrevert",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "session.unrevert",
    status: 409,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/SessionBusyError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "session.unrevert",
      status: 409,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/SessionBusyError\"}",
    ),
    payloadType: "SessionBusyError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionBusyError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "permission.respond",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "permission.respond",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion091",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion091", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "permission.respond",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/NotFoundError\"},{\"\$ref\":\"#/components/schemas/PermissionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "permission.respond",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/NotFoundError\"},{\"\$ref\":\"#/components/schemas/PermissionNotFoundError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion092",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion092", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "part.delete",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "part.delete",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion093",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion093", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "part.delete",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "part.delete",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "part.update",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "part.update",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion094",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion094", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "part.update",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "part.update",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "sync.start",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "sync.start",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "sync.replay",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "sync.replay",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion095",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion095", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "sync.steal",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "sync.steal",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion096",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion096", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "sync.history.list",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "sync.history.list",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion097",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion097", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.appendPrompt",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.appendPrompt",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion098",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion098", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.openHelp",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.openHelp",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.openSessions",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.openSessions",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.openThemes",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.openThemes",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.openModels",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.openModels",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.submitPrompt",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.submitPrompt",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.clearPrompt",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.clearPrompt",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.executeCommand",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.executeCommand",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion099",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion099", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.showToast",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.showToast",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.publish",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.publish",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion100",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion100", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.selectSession",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.selectSession",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion102",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion102", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.selectSession",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.selectSession",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.control.next",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.control.next",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "tui.control.response",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "tui.control.response",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.workspace.adapter.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.workspace.adapter.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.workspace.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.workspace.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.workspace.create",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorkspaceCreateError\"},{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.workspace.create",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorkspaceCreateError\"},{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion103",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion103", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.workspace.syncList",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.workspace.syncList",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.workspace.status",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.workspace.status",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/BadRequestError\"}",
    ),
    payloadType: "BadRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "BadRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.workspace.remove",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.workspace.remove",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/effect_HttpApiError_BadRequest\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion104",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion104", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.workspace.warp",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorkspaceWarpError\"},{\"\$ref\":\"#/components/schemas/VcsApplyError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.workspace.warp",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/WorkspaceWarpError\"},{\"\$ref\":\"#/components/schemas/VcsApplyError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion105",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion105", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "experimental.workspace.warp",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "experimental.workspace.warp",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.health.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.health.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.health.get",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.health.get",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.location.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.location.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.location.get",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.location.get",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.agent.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.agent.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.agent.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.agent.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.list",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/InvalidCursorError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.list",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/InvalidCursorError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion106",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion106", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.create",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.create",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.create",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.create",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.active",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.active",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.active",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.active",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.get",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.get",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.get",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.get",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.switchAgent",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.switchAgent",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.switchAgent",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.switchAgent",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.switchAgent",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.switchAgent",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.switchModel",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.switchModel",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.switchModel",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.switchModel",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.switchModel",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.switchModel",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.prompt",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.prompt",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.prompt",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.prompt",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.prompt",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.prompt",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.prompt",
    status: 409,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/ConflictError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.prompt",
      status: 409,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/ConflictError\"}",
    ),
    payloadType: "ConflictError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "ConflictError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.compact",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.compact",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.compact",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.compact",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.compact",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.compact",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.compact",
    status: 503,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/ServiceUnavailableError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.compact",
      status: 503,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/ServiceUnavailableError\"}",
    ),
    payloadType: "ServiceUnavailableError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "ServiceUnavailableError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.wait",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.wait",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.wait",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.wait",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.wait",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.wait",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.wait",
    status: 503,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/ServiceUnavailableError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.wait",
      status: 503,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/ServiceUnavailableError\"}",
    ),
    payloadType: "ServiceUnavailableError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "ServiceUnavailableError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.stage",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.stage",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.stage",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.stage",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.stage",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/MessageNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.stage",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/MessageNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion107",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion107", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.stage",
    status: 500,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnknownError1\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.stage",
      status: 500,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnknownError1\"}",
    ),
    payloadType: "UnknownError1",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnknownError1", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.clear",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.clear",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.clear",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.clear",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.clear",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.clear",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.clear",
    status: 500,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnknownError1\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.clear",
      status: 500,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnknownError1\"}",
    ),
    payloadType: "UnknownError1",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnknownError1", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.commit",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.commit",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.commit",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.commit",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.revert.commit",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.revert.commit",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.context",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.context",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.context",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.context",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.context",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.context",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.context",
    status: 500,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnknownError1\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.context",
      status: 500,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnknownError1\"}",
    ),
    payloadType: "UnknownError1",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnknownError1", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.history",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.history",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.history",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.history",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.history",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.history",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.events",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.events",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.events",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.events",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.events",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.events",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.interrupt",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.interrupt",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.interrupt",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.interrupt",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.interrupt",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.interrupt",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.message",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.message",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.message",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.message",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.message",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/MessageNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.message",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/MessageNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion108",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion108", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.messages",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/InvalidCursorError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.messages",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/InvalidCursorError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion109",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion109", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.messages",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.messages",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.messages",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.messages",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.messages",
    status: 500,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnknownError1\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.messages",
      status: 500,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnknownError1\"}",
    ),
    payloadType: "UnknownError1",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnknownError1", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.model.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.model.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.model.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.model.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.model.list",
    status: 503,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/ServiceUnavailableError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.model.list",
      status: 503,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/ServiceUnavailableError\"}",
    ),
    payloadType: "ServiceUnavailableError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "ServiceUnavailableError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.provider.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.provider.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.provider.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.provider.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.provider.list",
    status: 503,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/ServiceUnavailableError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.provider.list",
      status: 503,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/ServiceUnavailableError\"}",
    ),
    payloadType: "ServiceUnavailableError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "ServiceUnavailableError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.provider.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.provider.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.provider.get",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.provider.get",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.provider.get",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/ProviderNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.provider.get",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/ProviderNotFoundError\"}",
    ),
    payloadType: "ProviderNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "ProviderNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.provider.get",
    status: 503,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/ServiceUnavailableError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.provider.get",
      status: 503,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/ServiceUnavailableError\"}",
    ),
    payloadType: "ServiceUnavailableError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "ServiceUnavailableError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.get",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.get",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.connect.key",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/InvalidRequestError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.connect.key",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/InvalidRequestError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.connect.key",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.connect.key",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.connect.oauth",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/InvalidRequestError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.connect.oauth",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/InvalidRequestError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.connect.oauth",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.connect.oauth",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.attempt.status",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.attempt.status",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.attempt.status",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.attempt.status",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.attempt.cancel",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.attempt.cancel",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.attempt.cancel",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.attempt.cancel",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.attempt.complete",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/InvalidRequestError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.attempt.complete",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/InvalidRequestError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.integration.attempt.complete",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.integration.attempt.complete",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.credential.remove",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.credential.remove",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.credential.remove",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.credential.remove",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.credential.update",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.credential.update",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.credential.update",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.credential.update",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.permission.request.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.permission.request.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.permission.request.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.permission.request.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.permission.saved.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.permission.saved.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.permission.saved.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.permission.saved.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.permission.saved.remove",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.permission.saved.remove",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.permission.saved.remove",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.permission.saved.remove",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.list",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.list",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.create",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.create",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.create",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.create",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.create",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.create",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.get",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.get",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.get",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/PermissionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.get",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/PermissionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion110",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion110", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.reply",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.reply",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.reply",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.reply",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.permission.reply",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/PermissionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.permission.reply",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/PermissionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion111",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion111", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.fs.read",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.fs.read",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.fs.read",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.fs.read",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.fs.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.fs.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.fs.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.fs.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.fs.find",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.fs.find",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.fs.find",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.fs.find",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.command.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.command.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.command.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.command.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.skill.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.skill.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.skill.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.skill.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.event.subscribe",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.event.subscribe",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.event.subscribe",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.event.subscribe",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.create",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.create",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.create",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.create",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.get",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.get",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.get",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.get",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.get",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.get",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
    ),
    payloadType: "PtyNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PtyNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.update",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.update",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.update",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.update",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.update",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.update",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
    ),
    payloadType: "PtyNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PtyNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.remove",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.remove",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.remove",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.remove",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.remove",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.remove",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
    ),
    payloadType: "PtyNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PtyNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.connectToken",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.connectToken",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.connectToken",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.connectToken",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.connectToken",
    status: 403,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/ForbiddenError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.connectToken",
      status: 403,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/ForbiddenError\"}",
    ),
    payloadType: "ForbiddenError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "ForbiddenError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.connectToken",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.connectToken",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
    ),
    payloadType: "PtyNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PtyNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.connect",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.connect",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.connect",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.connect",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.connect",
    status: 403,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/ForbiddenError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.connect",
      status: 403,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/ForbiddenError\"}",
    ),
    payloadType: "ForbiddenError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "ForbiddenError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.pty.connect",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.pty.connect",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/PtyNotFoundError\"}",
    ),
    payloadType: "PtyNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "PtyNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.question.request.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.question.request.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.question.request.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.question.request.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.question.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.question.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.question.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.question.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.question.list",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.question.list",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "SessionNotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "SessionNotFoundError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.question.reply",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.question.reply",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.question.reply",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.question.reply",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.question.reply",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/QuestionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.question.reply",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/QuestionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion112",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion112", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.question.reject",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.question.reject",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.question.reject",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.question.reject",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.session.question.reject",
    status: 404,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/QuestionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.session.question.reject",
      status: 404,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/QuestionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"},{\"\$ref\":\"#/components/schemas/SessionNotFoundError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion113",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion113", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.reference.list",
    status: 400,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.reference.list",
      status: 400,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}",
    ),
    payloadType: "InvalidRequestError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "InvalidRequestError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.reference.list",
    status: 401,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.reference.list",
      status: 401,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/UnauthorizedError\"}",
    ),
    payloadType: "UnauthorizedError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "UnauthorizedError", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.projectCopy.create",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/ProjectCopyError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.projectCopy.create",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/ProjectCopyError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion114",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion114", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.projectCopy.remove",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/ProjectCopyError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.projectCopy.remove",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/ProjectCopyError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion115",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion115", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "v2.projectCopy.refresh",
    status: 400,
    mediaType: "application/json",
    schemaJson:
        "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/ProjectCopyError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "v2.projectCopy.refresh",
      status: 400,
      mediaType: "application/json",
      schemaJson:
          "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/ProjectCopyError\"},{\"\$ref\":\"#/components/schemas/InvalidRequestError\"}]}",
    ),
    payloadType: "OpencodeSdkRawUnion116",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "OpencodeSdkRawUnion116", contract),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.connect",
    status: 403,
    mediaType: "application/json",
    schemaJson:
        "{\"\$ref\":\"#/components/schemas/effect_HttpApiError_Forbidden\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.connect",
      status: 403,
      mediaType: "application/json",
      schemaJson:
          "{\"\$ref\":\"#/components/schemas/effect_HttpApiError_Forbidden\"}",
    ),
    payloadType: "EffectHttpApiErrorForbidden",
    decoder: (payload, contract) => decodeOpenCodeErrorModel(
      payload,
      "EffectHttpApiErrorForbidden",
      contract,
    ),
  ),
  const OpenCodeErrorContractKey(
    operationId: "pty.connect",
    status: 404,
    mediaType: "application/json",
    schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
  ): OpenCodeErrorContract(
    OpenCodeErrorContractKey(
      operationId: "pty.connect",
      status: 404,
      mediaType: "application/json",
      schemaJson: "{\"\$ref\":\"#/components/schemas/NotFoundError\"}",
    ),
    payloadType: "NotFoundError",
    decoder: (payload, contract) =>
        decodeOpenCodeErrorModel(payload, "NotFoundError", contract),
  ),
};

const Set<String> openCodeErrorFallbackOperations = {
  'global.event',
  'event.subscribe',
  'v2.session.events',
  'v2.event.subscribe',
};

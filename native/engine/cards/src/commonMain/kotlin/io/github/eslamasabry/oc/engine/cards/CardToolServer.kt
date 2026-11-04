package io.github.eslamasabry.oc.engine.cards

import kotlinx.serialization.json.JsonPrimitive

/**
 * The agent-cards tool server (Flutter `genUiServerScript`, `lib/builtin/agents/gen_ui_server.dart`; B-CHAT-047,
 * B-CHAT-048): a zero-dependency Node.js MCP stdio helper installed only at app-owned fixed paths inside the built-in
 * Linux. It advertises `show` and `find_connectors`, returns at once without claiming delivery, answers every invalid,
 * disabled or unknown call with one generic safe error, re-reads its enabled marker (refusing symlinks) on every call,
 * bounds frames to 64 KiB and recovers after split or invalid frames. Connector search reads its bridge file
 * (`<marker>.search.json`) and validates every bounded response.
 */
public object CardToolServer {
    public const val ENABLED_CONTENT: String = "enabled\n"

    /** The bridge file the app writes for `find_connectors` next to the marker. */
    public fun searchBridgePath(enabledMarkerPath: String): String = "$enabledMarkerPath.search.json"

    /** The script text; refuses a marker path that is relative or carries a control character. */
    public fun script(enabledMarkerPath: String): String {
        require(
            enabledMarkerPath.startsWith("/") &&
                '\u0000' !in enabledMarkerPath &&
                '\n' !in enabledMarkerPath &&
                '\r' !in enabledMarkerPath,
        ) { "Agent cards require an absolute marker path." }
        return "'use strict';\n" +
            "const enabledMarkerPath = ${JsonPrimitive(enabledMarkerPath)};\n" +
            "const connectorSearchPath = ${JsonPrimitive(searchBridgePath(enabledMarkerPath))};\n" +
            CardToolJs.VALIDATION + "\n" +
            CardToolJs.CONNECTOR_SEARCH + "\n" +
            CardToolJs.SERVER + "\n"
    }
}

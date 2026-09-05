# MCP setup scope — beta-18600

Verified 2026-09-05 against the authenticated pinned Windows binary, SHA-256
`29443AC011DC37896A650A7267C8FA232533B9410E8185EA099C1D5214E658E0`.
The fixture used fresh configuration/data directories and two separate Git
projects. A disabled MCP pointing at loopback port 9 avoided external services
and tool execution. See [captured results](mcp-scope-beta-18600.json).

| Check | Result |
|---|---|
| Initial list in each project | Empty |
| PUT MCP in the first location | 204; first location lists one disabled entry |
| Second location after that PUT | Empty |
| Stop and restart the same server with the same data/config directories | Both locations list no entries |

The captured contract describes add/replace as a runtime operation and exposes
no project/global persistent write selector. The restart check confirms that
the prior persistence claim was incorrect for beta-18600. The owned helper was
stopped and its fixture files preserved afterward.

The client now distinguishes persistent v1 project/global configuration writes
from v2 runtime location additions. V2 displays the pinned directory/workspace
or server-default location, explains the restart limit, and uses Add rather
than Save. A v2 call requesting persistent scope is rejected. The adapter checks
for an existing name before PUT and pins both reads/writes to the same location.
The server has no create-only conditional operation; another client adding the
same name between that check and PUT remains an upstream race limitation.

The form keeps its original location while open and blocks dispatch if a wake
changes the connection/location. A completed runtime add returns directly to
the refreshed list without a configuration reconnect. V1 retains its existing
save/reconnect workflow. Phone-width rendering and focused scope/request checks
cover this correction; final native release validation remains separate.

Paseo agent snapshots and status events publish current busy/idle state to the existing ConnectionController.busySessions contract.
Reconnect refetches current agent state; resumed running turns keep Stop and lose the interrupted line, while idle turns hydrate as finished.
No UI API, copy, persistence, capability, authentication or replay changes; existing transport errors and reconnect controls remain authoritative.

# BA5 verification — 2026-10-07

Implemented stable phone-agent ownership and alias-aware deletion, no UI/Kotlin edits.

Pinned Flutter pub get passed. Affected serial tests, through machine_lock:
`flutter test --no-pub --concurrency=1 test/phone_agent_owner_test.dart test/phone_agents_controller_test.dart`
passed (67 tests). The new protocol-switch controller test fails with the production
fix reverted (`Expected local, Actual two`), then passes restored.

Tests cover checked-home migration, protocol switching in both directions,
restart, conversation visibility, excluded Termux/remote profiles, deleting the
original owner while another protocol remains, and deleting the final alias.

Device switch/restart evidence is pending candidate APK build/install. This
record does not claim the full item Done until that journey is captured.

Migration edge-case follow-up: an empty gate left by a failed phone check must
not outrank an actually checked home. The legacy migration test failed before
this refinement (expected checked, actual empty), then all three ownership
cases passed after requiring a recorded fingerprint/nonempty chat cache.

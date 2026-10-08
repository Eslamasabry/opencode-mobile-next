# BB integration checkpoint — 2026-10-08

Merge `feat/genui-fe` at `77f3999f` into `sol/bb-runtime` after BB2 `e657025d`, without rebasing. Only content conflict: Android Gradle QA runner/dependencies. Preserve BB runtime, BD9 smoke and default phone-engine instrumentation, reject mutually exclusive simultaneous runner flags, keep JUnit and conditional BD9 release integration-test bridge.

MainActivity automatic merge retains native crash bridge initialization/disposal and all BB2 recovery methods. NativeCrashStore, PhoneAgentHost and SetupRunner retain the incoming lane changes. BA5 shared-phone-agent ownership migration is included; no credential movement.

[122 focused Flutter/native harness checks pass](focused.txt), through pinned Flutter and machine_lock test, concurrency1/no-pub. Files: builtin_linux, builtin_performance_diagnostics, builtin_server_recovery, builtin_server_recovery_native, phone_server_healing, phone_agent_owner, crash_diagnostics, native_crash, phone_agent_host_native, setup_runner_native. [Analyzer clean](analyze.txt),39.9s; no ignores. Dependency package configuration [refreshed](pub-get.txt) because incoming pubspec adds SDK integration_test. No conflict markers remain; owned source diff is clean. Incoming BA evidence logs and tool/release/test_verify_cold_start.py retain their existing trailing whitespace; those unrelated lane files were preserved.

This is a focused merge checkpoint, not a full-suite or merged native APK/device claim. Premerge BB2 actual emulator evidence remains tied to its original source/APK hashes in BB2 README. Coordinator explicitly holds APK/Gradle builds and emulator sessions during its full suite until go; no owned build/device session remains. BB3 proceeds with code/lightweight-unit/docs work only.

Coordinator relaxed pre-integration restoration: installed app/data +runnable server. Met on dev2201, real OpenCode2 healthy and Connected. Prior ClaudeReady discrepancy is explained by absent BA5 shared-owner migration, now merged; no authentication or account files changed. Future device APKs must come from this merged branch. Native changes remain unreleased; no push/PR/tag/publication.

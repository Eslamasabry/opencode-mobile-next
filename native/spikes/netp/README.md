# netp spike (S0-NETP, plan 04 §17 P-N1..P-N6)

This is a standalone Gradle build, not part of `native/settings.gradle.kts`. It uses JVM only, so it needs no
Android SDK. The code here is a proof, not production code. The production versions live in `:core:net-okhttp`
(S1-SECNET/WP-04.3) and `:core:net` (S1-NET/WP-04.4).

```bash
# offline proofs (≈ 20 s)
native/gradlew -p native/spikes/netp test
# live proofs against scratch servers on loopback (≈ 2.5 min); the password is read from a file, never argv
NETP_OC2_URL=http://127.0.0.1:4097 NETP_OC2_URL_NEXT=http://127.0.0.1:4098 \
NETP_PASEO_URL=ws://127.0.0.1:4099/ws NETP_PASSWORD_FILE=/path/to/pw \
native/gradlew -p native/spikes/netp liveTest
```

If Maven Central returns HTTP 429 in the cloud container, use the untracked init script from
`docs/native/slice0/P1-skeleton.md` (Google's Central mirror).

| Proof | Main code | Tests |
|---|---|---|
| P-N1 WebSocket | `OkHttpWebSockets.kt` (OkHttp HTTP/1.1 upgrade, then Ktor `RawWebSocket`) | `PN1WebSocketTest` |
| P-N2 pinned TLS | `TrustGate.kt` (`PinnedLeafTrustManager`, `TrustGateInterceptor`) | `PN2PinnedTlsTest` (Conscrypt and SunJSSE) |
| P-N3 SSE | `MiniStreamLoop.kt` | `PN3SseTest` |
| P-N4 2.1.1 vs 2.2.5 | `probe/route_diff.py`, `recorded/` | `PN4ProbeTest` |
| P-N5 retry trap | `HardenedOkHttp.kt` (`OneShotMutationBodies`, `NoStatusFollowUps`) | `PN5RetryTrapTest` |
| P-N6 network change | `MiniStreamLoop.retryNow()` | `PN6NetworkChangeTest` |

Results: `docs/native/units/S0-NETP.md` and `docs/qa/native-oc2-probe-2026-11/README.md`.

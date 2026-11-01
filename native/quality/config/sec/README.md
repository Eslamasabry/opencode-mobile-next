# K-SEC configuration (plan 16 §2.1–§2.3)

Read by `:quality:secTest` (`native/quality/src/secTest/`). Every list may change only with a CR16 row; the
pending lists may only shrink. Plan 16 names these files under `native/config/`; they live here because the S1-SECGATES
write set is `native/quality/**` (move them when the coordinator owns `native/config/`).

| File | Rule | Meaning |
|---|---|---|
| `secret-dto-allowlist.txt` | K-SEC-4 | package prefixes of wire DTOs whose secret-named `String` is decoded only to become a `Secret` at once |
| `secret-name-allowlist.txt` | K-SEC-4 | property names that stay `String`, each with its reason |
| `secret-reveal-allowlist.txt` | K-SEC-4 | file names that may call `Secret.reveal()` (transports, `SecretStore`, `Redactor`, migration) |
| `mutable-pendingintent-allowlist.txt` | K-SEC-7 | the three reviewed mutable `PendingIntent` files (§1.8.2) |
| `knownhosts-pending.txt` | K-SEC-10 | hosts allowed in URL literals until `KnownHosts` (`:core:security`, WP-16.1) lists them; shrink-only |
| `exported-surface.txt` | ExportedSurfaceTest | the reviewed component snapshot of `androidApp/src/main/AndroidManifest.xml` |
| `sec-pending.txt` | all gates | findings tolerated until the owning slice lands; a listed id that no longer fires fails |
